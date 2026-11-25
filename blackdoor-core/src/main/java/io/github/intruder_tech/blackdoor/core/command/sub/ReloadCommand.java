package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.config.ConfigError;
import io.github.intruder_tech.blackdoor.config.ReloadResult;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloads configuration off-thread. Single-flight (one reload at a time) and rate-limited by a
 * configurable cooldown, so reload spam cannot pile up work (threat model TB4).
 */
public final class ReloadCommand implements Subcommand {

    private static final int MAX_ERRORS_SHOWN = 5;

    private final BlackDoorEngine engine;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong lastStartNanos = new AtomicLong(Long.MIN_VALUE);

    public ReloadCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "blackdoor.command.reload";
    }

    @Override
    public String description() {
        return "Reload config files safely";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        long now = engine.time().nanoTime();
        long cooldown = TimeUnit.SECONDS.toNanos(engine.settings().reloadCooldownSeconds());
        long last = lastStartNanos.get();
        if (last != Long.MIN_VALUE && now - last < cooldown) {
            long waitSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(cooldown - (now - last)));
            sender.sendMessage(Text.PREFIX + "&eReload is on cooldown; try again in " + waitSeconds + "s.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage(Text.PREFIX + "&eA reload is already in progress.");
            return;
        }
        lastStartNanos.set(now);
        String actor = sender.isConsole() ? "console" : sender.name();
        boolean queued = engine.submitWork(() -> {
            ReloadResult result;
            try {
                result = engine.reloadConfiguration(actor);
            } finally {
                // Release before replying, so a sender reacting to the reply never sees "in progress".
                running.set(false);
            }
            report(sender, result);
        });
        if (queued) {
            sender.sendMessage(Text.PREFIX + "Reloading configuration...");
        } else {
            running.set(false);
            sender.sendMessage(Text.PREFIX + "&cBlack Door is busy; reload not started.");
        }
    }

    private static void report(BdSender sender, ReloadResult result) {
        if (result.success()) {
            sender.sendMessage(Text.PREFIX + "&aConfiguration reloaded" + (result.changedFiles().isEmpty()
                    ? " (no changes)." : ": " + String.join(", ", result.changedFiles()) + "."));
        } else {
            sender.sendMessage(Text.PREFIX + "&cReload failed; " + (result.usingDefaults()
                    ? "bundled defaults remain active." : "previous configuration kept."));
            result.errors().stream().limit(MAX_ERRORS_SHOWN).map(ConfigError::toString)
                    .forEach(e -> sender.sendMessage("&8 - &c" + Text.escape(e)));
            if (result.errors().size() > MAX_ERRORS_SHOWN) {
                sender.sendMessage("&8 - &7... " + (result.errors().size() - MAX_ERRORS_SHOWN) + " more in the server log");
            }
        }
        if (!result.warnings().isEmpty()) {
            sender.sendMessage("&8 - &e" + result.warnings().size() + " warning(s); see the server log.");
        }
    }
}
