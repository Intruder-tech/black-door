package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.state.PlayerSession;

/**
 * Observes packets for golden-trace recording (milestone 2.4b). Called on network threads only
 * while at least one recording is active; implementations must be fast and non-blocking.
 */
public interface TraceListener {

    void inbound(PlayerSession session, ServerboundPacket packet, long nanoTime);

    void outbound(PlayerSession session, ClientboundPacket packet, long nanoTime);

    void disconnected(PlayerSession session);
}
