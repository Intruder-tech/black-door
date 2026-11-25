package io.github.intruder_tech.blackdoor.protocol.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;

import java.util.Optional;

/**
 * {@link ProtocolBridge} backed by the PacketEvents plugin. Every call is defensive: a missing,
 * uninitialized or incompatible PacketEvents yields "unavailable"/empty rather than an exception,
 * so a broken dependency degrades Black Door instead of crashing the server (fail-open for
 * detection, see docs/ARCHITECTURE.md section 7).
 */
public final class PacketEventsBridge implements ProtocolBridge {

    @Override
    public String name() {
        return "PacketEvents";
    }

    @Override
    public boolean isAvailable() {
        PacketEventsAPI<?> api = api();
        try {
            return api != null && api.isInitialized();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Override
    public String libraryVersion() {
        PacketEventsAPI<?> api = api();
        try {
            return api == null ? "not loaded" : String.valueOf(api.getVersion());
        } catch (RuntimeException | LinkageError e) {
            return "unknown (" + e.getClass().getSimpleName() + ")";
        }
    }

    @Override
    public Optional<MinecraftVersion> serverVersion() {
        PacketEventsAPI<?> api = api();
        if (api == null) {
            return Optional.empty();
        }
        try {
            return PacketEventsVersions.fromServer(api.getServerManager().getVersion());
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MinecraftVersion> clientVersion(Object platformPlayer) {
        PacketEventsAPI<?> api = api();
        if (api == null || platformPlayer == null) {
            return Optional.empty();
        }
        try {
            return PacketEventsVersions.fromClient(api.getPlayerManager().getClientVersion(platformPlayer));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    private com.github.retrooper.packetevents.event.PacketListenerCommon registered;
    private BlackDoorPacketListener listener;

    /**
     * Registers at LOWEST priority so Black Door sees packets first and a dropped packet reaches
     * other listeners already cancelled.
     */
    @Override
    public synchronized boolean attach(io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler handler) {
        if (registered != null || !isAvailable()) {
            return registered != null;
        }
        try {
            listener = new BlackDoorPacketListener(handler);
            registered = api().getEventManager().registerListener(listener,
                    com.github.retrooper.packetevents.event.PacketListenerPriority.LOWEST);
            return true;
        } catch (RuntimeException | LinkageError e) {
            registered = null;
            listener = null;
            return false;
        }
    }

    @Override
    public synchronized void detach() {
        if (registered == null) {
            return;
        }
        try {
            PacketEventsAPI<?> api = api();
            if (api != null) {
                api.getEventManager().unregisterListener(registered);
            }
        } catch (RuntimeException | LinkageError e) {
            // PacketEvents may already be shut down; nothing else to release.
        } finally {
            registered = null;
            listener = null;
        }
    }

    private final PacketEventsShapes shapes = new PacketEventsShapes();

    @Override
    public io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry shapeRegistry() {
        return shapes;
    }

    @Override
    public boolean usesLegacyTransactions() {
        PacketEventsAPI<?> api = api();
        try {
            return api != null && api.getServerManager().getVersion()
                    .isOlderThan(com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_17);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Sends silently so other plugins' listeners never see Black Door's transactions. */
    @Override
    public boolean sendTransaction(Object connectionKey, int id) {
        PacketEventsAPI<?> api = api();
        if (api == null) {
            return false;
        }
        try {
            com.github.retrooper.packetevents.protocol.player.User user = api.getProtocolManager().getUser(connectionKey);
            if (user == null) {
                return false;
            }
            if (usesLegacyTransactions()) {
                user.sendPacketSilently(new com.github.retrooper.packetevents.wrapper.play.server
                        .WrapperPlayServerWindowConfirmation(0, (short) id, false));
            } else {
                user.sendPacketSilently(new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing(id));
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Packets that failed to translate (and were passed through untouched). */
    public synchronized long translationErrors() {
        return listener == null ? 0 : listener.translationErrors.sum();
    }

    private static PacketEventsAPI<?> api() {
        try {
            return PacketEvents.getAPI();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
