package io.github.intruder_tech.blackdoor.platform.bukkit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.regex.Pattern;

/**
 * Keeps passwords out of the console and log files: the server records every command a player
 * types ("issued server command: ..."), so those lines are dropped for /signup and /signin.
 */
final class PasswordLogFilter extends AbstractFilter {

    private static final Pattern SECRET = Pattern.compile(
            "issued server command: /(?:[\\w-]+:)?(?:signup|signin)\\b", Pattern.CASE_INSENSITIVE);

    /** @return false if the server's logger could not be filtered */
    static boolean install() {
        try {
            ((Logger) LogManager.getRootLogger()).addFilter(new PasswordLogFilter());
            return true;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    @Override
    public Result filter(LogEvent event) {
        String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
        return message != null && SECRET.matcher(message).find() ? Result.DENY : Result.NEUTRAL;
    }
}
