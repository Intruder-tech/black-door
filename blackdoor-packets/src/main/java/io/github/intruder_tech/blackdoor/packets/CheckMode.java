package io.github.intruder_tech.blackdoor.packets;

/** Per-check operating mode. MITIGATE only blocks packets when the global mode is "enforce". */
public enum CheckMode { DISABLED, MONITOR, MITIGATE }
