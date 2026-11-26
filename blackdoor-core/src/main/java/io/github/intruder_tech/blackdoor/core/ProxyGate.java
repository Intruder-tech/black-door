package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.network.ConnectionGuard;

import java.net.InetAddress;
import java.util.function.BooleanSupplier;

/**
 * The connection guard as proxies see it. Each method returns true when the connection should be
 * refused. Fails open: an unknown address or an internal error lets the connection through.
 *
 * <p>BungeeCord can refuse at connect and at pre-login: use {@link #connect} and
 * {@link #login(InetAddress, String)}. Velocity cannot refuse at handshake, so its connection and
 * hostname limits apply later: use {@link #ping} and {@link #login(InetAddress, int, String)}.
 */
public final class ProxyGate {

    /** Shown to a player whose login was refused. */
    public static final String REFUSAL_MESSAGE = "Connection refused. Wait a moment and try again.";

    private final ConnectionGuard guard;
    private final RateLimitedLogger errors;

    ProxyGate(ConnectionGuard guard, RateLimitedLogger errors) {
        this.guard = guard;
        this.errors = errors;
    }

    public boolean connect(InetAddress address) {
        return guarded(address, () -> guard.handshake(address, 0));
    }

    public boolean login(InetAddress address, String name) {
        return guarded(address, () -> guard.loginStart(address, name));
    }

    public boolean login(InetAddress address, int hostnameLength, String name) {
        return guarded(address, () -> guard.handshake(address, hostnameLength) || guard.loginStart(address, name));
    }

    public boolean ping(InetAddress address, int hostnameLength) {
        return guarded(address, () -> guard.handshake(address, hostnameLength) || guard.statusPing(address));
    }

    private boolean guarded(InetAddress address, BooleanSupplier check) {
        if (address == null) {
            return false;
        }
        try {
            return check.getAsBoolean();
        } catch (RuntimeException e) {
            errors.error("proxy-gate", "Connection guard error (connection allowed)", e);
            return false;
        }
    }
}
