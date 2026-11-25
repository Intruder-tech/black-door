package io.github.intruder_tech.blackdoor.protocol;

/**
 * Version-dependent behaviours that Black Door logic branches on. Checks consult features, never
 * raw version numbers (docs/PROTOCOL_ARCHITECTURE.md section 1).
 *
 * <p>{@code verified == false} marks entries that still need confirmation against primary sources
 * (tagged [verify] in the docs). Logic depending on an unverified feature must stay in monitor-only
 * mode until it is verified.
 */
public enum ProtocolFeature {

    /** Client sends a movement packet every tick even when idle. Removed in 1.9. */
    IDLE_FLYING_PACKET(Side.CLIENT, MinecraftVersion.of(1, 8), MinecraftVersion.of(1, 9), true),
    /** Attack cooldown / 1.9 combat rules. */
    ATTACK_COOLDOWN(Side.SERVER, MinecraftVersion.of(1, 9), null, true),
    /** Off-hand slot. */
    OFFHAND(Side.SERVER, MinecraftVersion.of(1, 9), null, true),
    /** Block states replaced numeric block IDs ("the flattening"). */
    BLOCK_STATE_FLATTENING(Side.SERVER, MinecraftVersion.of(1, 13), null, true),
    /** Client-side swimming movement. */
    SWIMMING(Side.CLIENT, MinecraftVersion.of(1, 13), null, true),
    /** Play-state Ping/Pong packets usable as transactions (replaces window confirmation). */
    PING_PONG_TRANSACTIONS(Side.CLIENT, MinecraftVersion.of(1, 17), null, true),
    /** Signed chat messages. */
    SIGNED_CHAT(Side.SERVER, MinecraftVersion.of(1, 19), null, true),
    /** CONFIGURATION connection state between LOGIN and PLAY. */
    CONFIGURATION_STATE(Side.SERVER, MinecraftVersion.of(1, 20, 2), null, true),
    /** Item data components replace item NBT. */
    ITEM_COMPONENTS(Side.SERVER, MinecraftVersion.of(1, 20, 5), null, true),
    /** Player input packet carries directional key state. */
    DIRECTIONAL_INPUT_PACKET(Side.CLIENT, MinecraftVersion.of(1, 21, 2), null, false);

    /** Whose version decides the feature: the client's (physics, packet cadence) or the server's (rules, wire format seen by Black Door). */
    public enum Side { CLIENT, SERVER }

    private final Side side;
    private final MinecraftVersion since;
    private final MinecraftVersion until;
    private final boolean verified;

    ProtocolFeature(Side side, MinecraftVersion since, MinecraftVersion until, boolean verified) {
        this.side = side;
        this.since = since;
        this.until = until;
        this.verified = verified;
    }

    public Side side() {
        return side;
    }

    /** First version with the feature (inclusive). */
    public MinecraftVersion since() {
        return since;
    }

    /** First version without the feature (exclusive), or {@code null} if still present. */
    public MinecraftVersion until() {
        return until;
    }

    public boolean verified() {
        return verified;
    }

    /** Whether the feature exists in the given version. */
    public boolean presentIn(MinecraftVersion version) {
        return version.isAtLeast(since) && (until == null || version.isBefore(until));
    }
}
