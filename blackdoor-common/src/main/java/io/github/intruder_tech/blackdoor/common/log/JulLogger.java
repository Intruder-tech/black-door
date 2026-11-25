package io.github.intruder_tech.blackdoor.common.log;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@link BdLogger} backed by {@link java.util.logging.Logger} (Bukkit and BungeeCord). */
public final class JulLogger implements BdLogger {

    private final Logger delegate;

    public JulLogger(Logger delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void debug(String message) {
        delegate.fine(message);
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warning(message);
    }

    @Override
    public void error(String message, Throwable error) {
        delegate.log(Level.SEVERE, message, error);
    }
}
