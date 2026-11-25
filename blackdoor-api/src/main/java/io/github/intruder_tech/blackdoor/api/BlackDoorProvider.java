package io.github.intruder_tech.blackdoor.api;

import java.util.Objects;
import java.util.Optional;

/** Static access to the running {@link BlackDoor} instance. */
public final class BlackDoorProvider {

    private static volatile BlackDoor instance;

    private BlackDoorProvider() {
    }

    /**
     * Returns the running instance.
     *
     * @throws IllegalStateException if Black Door is not enabled
     */
    public static BlackDoor get() {
        BlackDoor current = instance;
        if (current == null) {
            throw new IllegalStateException("Black Door is not enabled");
        }
        return current;
    }

    /** Returns the running instance, or empty if Black Door is not enabled. */
    public static Optional<BlackDoor> find() {
        return Optional.ofNullable(instance);
    }

    /** Internal: called by Black Door itself on enable. Not for third-party use. */
    public static synchronized void register(BlackDoor blackDoor) {
        Objects.requireNonNull(blackDoor, "blackDoor");
        if (instance != null) {
            throw new IllegalStateException("Black Door is already registered");
        }
        instance = blackDoor;
    }

    /** Internal: called by Black Door itself on disable. Not for third-party use. */
    public static synchronized void unregister(BlackDoor blackDoor) {
        if (instance == blackDoor) {
            instance = null;
        }
    }
}
