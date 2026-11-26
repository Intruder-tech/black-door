package io.github.intruder_tech.blackdoor.protocol.packet;

/** Packets sent before a player is in the game; only what connection protection needs. */
public sealed interface PreLogin {

    /** First packet of every connection. */
    record Handshake(int hostnameLength) implements PreLogin {
    }

    /** Server list ping. */
    record StatusRequest() implements PreLogin {
    }

    /** Start of login. */
    record LoginStart(String name) implements PreLogin {
    }
}
