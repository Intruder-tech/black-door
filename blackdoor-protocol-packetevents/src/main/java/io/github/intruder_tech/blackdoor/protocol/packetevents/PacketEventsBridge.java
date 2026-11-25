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

    private static PacketEventsAPI<?> api() {
        try {
            return PacketEvents.getAPI();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
