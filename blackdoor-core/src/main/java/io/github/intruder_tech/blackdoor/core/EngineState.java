package io.github.intruder_tech.blackdoor.core;

/** Lifecycle state of the engine. */
public enum EngineState {
    STOPPED,
    STARTING,
    /** Fully operational for the current phase's feature set. */
    RUNNING,
    /** Running with reduced function (e.g. bundled default config, or packet library unavailable). */
    DEGRADED,
    STOPPING
}
