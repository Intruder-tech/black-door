package io.github.intruder_tech.blackdoor.protocol.packet;

/** Minecraft connection states. CONFIGURATION exists from 1.20.2. */
public enum ConnectionState { HANDSHAKING, STATUS, LOGIN, CONFIGURATION, PLAY, UNKNOWN }
