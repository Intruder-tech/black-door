package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.trace.TraceRecorder;
import io.github.intruder_tech.blackdoor.state.PlayerSession;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Starts or stops a golden-trace recording of one player (milestone 2.4b test data). */
public final class RecordCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public RecordCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "record";
    }

    @Override
    public String permission() {
        return "blackdoor.command.record";
    }

    @Override
    public String description() {
        return "Record movement for physics testing";
    }

    @Override
    public String usage() {
        return "record <player> [seconds] | record stop <player>";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        TraceRecorder recorder = engine.traceRecorder();
        boolean stopping = !args.isEmpty() && args.get(0).equalsIgnoreCase("stop");
        String target = stopping ? (args.size() > 1 ? args.get(1) : null) : (args.isEmpty() ? null : args.get(0));
        if (target == null) {
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " " + usage());
            return;
        }
        Optional<PlayerSession> session = engine.sessions().flatMap(s -> s.findSession(target));
        if (session.isEmpty() || session.get().uniqueId() == null) {
            sender.sendMessage(Text.PREFIX + "&c" + Text.CROSS + " No watched player named '&f" + Text.escape(target) + "&c'.");
            return;
        }
        if (stopping) {
            if (!recorder.stop(session.get().uniqueId())) {
                sender.sendMessage(Text.PREFIX + "&e" + Text.escape(target) + " is not being recorded.");
            }
            return;
        }
        int seconds = 60;
        if (args.size() > 1) {
            try {
                seconds = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                seconds = -1;
            }
        }
        if (seconds < 5 || seconds > TraceRecorder.MAX_SECONDS) {
            sender.sendMessage(Text.PREFIX + "&cSeconds must be between 5 and " + TraceRecorder.MAX_SECONDS + ".");
            return;
        }
        if (!recorder.start(session.get(), seconds, sender)) {
            sender.sendMessage(Text.PREFIX + "&e" + Text.escape(target) + " is already being recorded.");
            return;
        }
        sender.sendMessage(Text.PREFIX + "&a" + Text.CHECK + " Recording &f" + Text.escape(target) + " &afor &f" + seconds
                + "s&a. Play normally: walk, sprint, jump, swim, climb, take knockback.");
    }

    @Override
    public List<String> complete(BdSender sender, List<String> args) {
        if (args.size() != 1) {
            return List.of();
        }
        String prefix = args.get(0).toLowerCase(Locale.ROOT);
        List<String> names = new java.util.ArrayList<>();
        if ("stop".startsWith(prefix)) {
            names.add("stop");
        }
        engine.sessions().ifPresent(s -> s.names().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .limit(50).forEach(names::add));
        return names;
    }
}
