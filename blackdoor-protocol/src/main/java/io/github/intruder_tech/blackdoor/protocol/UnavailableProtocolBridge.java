package io.github.intruder_tech.blackdoor.protocol;

import java.util.Objects;
import java.util.Optional;

/** Used when no packet library is present (proxies in Phase 1, or a missing dependency). */
public final class UnavailableProtocolBridge implements ProtocolBridge {

    private final String reason;

    public UnavailableProtocolBridge(String reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String reason() {
        return reason;
    }

    @Override
    public String name() {
        return "none (" + reason + ")";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String libraryVersion() {
        return "n/a";
    }

    @Override
    public Optional<MinecraftVersion> serverVersion() {
        return Optional.empty();
    }

    @Override
    public Optional<MinecraftVersion> clientVersion(Object platformPlayer) {
        return Optional.empty();
    }
}
