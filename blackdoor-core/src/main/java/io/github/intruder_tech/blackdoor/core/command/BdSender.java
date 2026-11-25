package io.github.intruder_tech.blackdoor.core.command;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-neutral command sender. Implementations must make {@link #sendMessage(String)} safe to
 * call from any thread (they hop to the owning thread when the platform requires it).
 */
public interface BdSender {

    String name();

    boolean isConsole();

    boolean hasPermission(String node);

    /** Sends a message written with {@code &}-style colour codes (see {@link Text}). */
    void sendMessage(String message);

    Optional<UUID> uniqueId();
}
