package io.github.intruder_tech.blackdoor.common.log;

/** Platform-neutral logger. Platform adapters bridge this to JUL, SLF4J, etc. */
public interface BdLogger {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);

    default void error(String message) {
        error(message, null);
    }
}
