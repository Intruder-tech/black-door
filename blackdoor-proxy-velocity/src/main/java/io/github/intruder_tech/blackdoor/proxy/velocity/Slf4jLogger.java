package io.github.intruder_tech.blackdoor.proxy.velocity;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import org.slf4j.Logger;

record Slf4jLogger(Logger delegate) implements BdLogger {

    @Override
    public void debug(String message) {
        delegate.debug(message);
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warn(message);
    }

    @Override
    public void error(String message, Throwable error) {
        delegate.error(message, error);
    }
}
