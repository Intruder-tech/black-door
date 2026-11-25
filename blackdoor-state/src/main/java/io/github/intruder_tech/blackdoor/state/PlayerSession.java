package io.github.intruder_tech.blackdoor.state;

import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Black Door's model of one connected client, built only from packets. Written by the
 * connection's network thread; other threads read through {@link #snapshot(long)}, which may
 * see slightly stale but never torn reference values.
 */
public final class PlayerSession {

    public static final int HISTORY_CAPACITY = 40;
    public static final int TELEPORT_CAPACITY = 16;

    private final Object key;
    private final long createdMillis;
    private final MovementHistory history = new MovementHistory(HISTORY_CAPACITY);
    private final PendingTeleports teleports = new PendingTeleports(TELEPORT_CAPACITY);
    private final Object[] attachments = new Object[AttachmentKey.MAX_KEYS];

    private volatile UUID uniqueId;
    private volatile String name;
    private volatile MinecraftVersion clientVersion;
    private volatile int entityId = -1;
    private volatile long inboundPackets;

    PlayerSession(Object key, long createdMillis) {
        this.key = key;
        this.createdMillis = createdMillis;
    }

    /** Refreshes identity fields that become known during login. */
    public void update(ConnectionInfo info) {
        if (info.uniqueId() != null) {
            uniqueId = info.uniqueId();
        }
        if (info.name() != null) {
            name = info.name();
        }
        if (info.clientVersion() != null) {
            clientVersion = info.clientVersion();
        }
    }

    public Object key() {
        return key;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public String name() {
        return name;
    }

    public MinecraftVersion clientVersion() {
        return clientVersion;
    }

    public int entityId() {
        return entityId;
    }

    public void entityId(int id) {
        entityId = id;
    }

    public MovementHistory history() {
        return history;
    }

    public PendingTeleports teleports() {
        return teleports;
    }

    public void countInbound() {
        inboundPackets++;
    }

    /** Returns the attachment for {@code key}, creating it on first use. Network thread only. */
    @SuppressWarnings("unchecked")
    public <T> T attachment(AttachmentKey<T> attachmentKey, Supplier<T> factory) {
        Object value = attachments[attachmentKey.index()];
        if (value == null) {
            value = factory.get();
            attachments[attachmentKey.index()] = value;
        }
        return (T) value;
    }

    public SessionSnapshot snapshot(long nowMillis) {
        boolean hasPosition = history.size() > 0;
        return new SessionSnapshot(uniqueId, name, clientVersion, entityId, nowMillis - createdMillis, inboundPackets,
                hasPosition,
                hasPosition ? history.x(0) : 0, hasPosition ? history.y(0) : 0, hasPosition ? history.z(0) : 0,
                hasPosition ? history.yaw(0) : 0, hasPosition ? history.pitch(0) : 0,
                hasPosition && history.onGround(0), teleports.size(), teleports.overflowed());
    }
}
