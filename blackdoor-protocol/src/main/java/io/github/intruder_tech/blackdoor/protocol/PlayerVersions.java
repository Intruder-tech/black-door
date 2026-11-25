package io.github.intruder_tech.blackdoor.protocol;

import java.util.Objects;

/**
 * The two versions that matter for one player session: the client's protocol version and the
 * server's gameplay version. They differ when ViaVersion/ViaBackwards/ViaRewind translate.
 */
public record PlayerVersions(MinecraftVersion client, MinecraftVersion server) {

    public PlayerVersions {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(server, "server");
    }

    /** Evaluates a feature against the version on the side that governs it. */
    public boolean has(ProtocolFeature feature) {
        MinecraftVersion relevant = feature.side() == ProtocolFeature.Side.CLIENT ? client : server;
        return feature.presentIn(relevant);
    }

    /** True when protocol translation is in effect. */
    public boolean translated() {
        return !client.equals(server);
    }
}
