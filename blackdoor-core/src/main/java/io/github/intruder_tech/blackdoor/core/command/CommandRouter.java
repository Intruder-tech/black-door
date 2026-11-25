package io.github.intruder_tech.blackdoor.core.command;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.text.LogSanitizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Dispatches {@code /<label> <sub> [args]} to subcommands, enforcing permissions and input bounds. */
public final class CommandRouter {

    /** Upper bounds on command input (SR-03). */
    static final int MAX_ARGS = 16;
    static final int MAX_ARG_LENGTH = 64;

    private final String label;
    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    private final BdLogger logger;

    public CommandRouter(String label, List<Subcommand> subcommands, BdLogger logger) {
        this.label = Objects.requireNonNull(label, "label");
        this.logger = Objects.requireNonNull(logger, "logger");
        for (Subcommand sub : subcommands) {
            if (this.subcommands.putIfAbsent(sub.name().toLowerCase(Locale.ROOT), sub) != null) {
                throw new IllegalArgumentException("duplicate subcommand " + sub.name());
            }
        }
    }

    public String label() {
        return label;
    }

    public List<Subcommand> subcommands() {
        return List.copyOf(subcommands.values());
    }

    public void dispatch(BdSender sender, String[] rawArgs) {
        if (rawArgs.length > MAX_ARGS || Arrays.stream(rawArgs).anyMatch(a -> a.length() > MAX_ARG_LENGTH)) {
            sender.sendMessage(Text.PREFIX + "&cToo many or too long arguments.");
            return;
        }
        if (rawArgs.length == 0) {
            sendHelp(sender);
            return;
        }
        Subcommand sub = subcommands.get(rawArgs[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            sender.sendMessage(Text.PREFIX + "&cUnknown subcommand. &7Try &f/" + label + " help&7.");
            return;
        }
        if (!sender.hasPermission(sub.permission())) {
            sender.sendMessage(Text.PREFIX + "&cYou do not have permission to use this command.");
            return;
        }
        try {
            sub.execute(sender, List.of(rawArgs).subList(1, rawArgs.length));
        } catch (RuntimeException e) {
            logger.error("Command /" + label + " " + sub.name() + " failed for " + LogSanitizer.sanitize(sender.name()), e);
            sender.sendMessage(Text.PREFIX + "&cInternal error; see the server log.");
        }
    }

    public List<String> complete(BdSender sender, String[] rawArgs) {
        if (rawArgs.length > MAX_ARGS) {
            return List.of();
        }
        if (rawArgs.length <= 1) {
            String prefix = rawArgs.length == 0 ? "" : rawArgs[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Subcommand sub : subcommands.values()) {
                if (sub.name().startsWith(prefix) && sender.hasPermission(sub.permission())) {
                    names.add(sub.name());
                }
            }
            return names;
        }
        Subcommand sub = subcommands.get(rawArgs[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sender.hasPermission(sub.permission())) {
            return List.of();
        }
        return sub.complete(sender, List.of(rawArgs).subList(1, rawArgs.length));
    }

    public void sendHelp(BdSender sender) {
        sender.sendMessage(Text.header("Commands"));
        boolean any = false;
        for (Subcommand sub : subcommands.values()) {
            if (sender.hasPermission(sub.permission())) {
                sender.sendMessage(" &8" + Text.ARROW + " &e/" + label + " " + sub.usage() + " &8- &7" + sub.description());
                any = true;
            }
        }
        if (!any) {
            sender.sendMessage(" &8" + Text.ARROW + " &7No commands available to you.");
        }
        sender.sendMessage(" &8Tip: &7/" + label + " gui &8opens the control panel.");
        sender.sendMessage(Text.footer());
    }
}
