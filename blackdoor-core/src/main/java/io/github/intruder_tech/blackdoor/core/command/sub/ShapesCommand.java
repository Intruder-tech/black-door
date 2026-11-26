package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Writes the block shape table to a file, so traces can be replayed without a server. */
public final class ShapesCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public ShapesCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "shapes";
    }

    @Override
    public String permission() {
        return "blackdoor.command.record";
    }

    @Override
    public String description() {
        return "Export block shapes for physics testing";
    }

    @Override
    public String usage() {
        return "shapes dump";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        if (args.isEmpty() || !args.get(0).equalsIgnoreCase("dump")) {
            sender.sendMessage(Text.PREFIX + "&cUsage: &f/" + engine.commands().label() + " " + usage());
            return;
        }
        var shapes = engine.blockShapes();
        if (shapes.isEmpty() || !engine.blockShapesReady()) {
            sender.sendMessage(Text.PREFIX + "&cBlock shapes are not ready (see diagnostics).");
            return;
        }
        String server = engine.platform().info().minecraft().map(Object::toString).orElse("unknown");
        String file = "shapes-" + server + ".txt.gz";
        boolean queued = engine.submitWork(() -> {
            try (Writer out = new OutputStreamWriter(new GZIPOutputStream(
                    Files.newOutputStream(engine.platform().dataDirectory().resolve(file))), StandardCharsets.UTF_8)) {
                shapes.get().write(out, server);
                sender.sendMessage(Text.PREFIX + "&aSaved &f" + file);
            } catch (IOException e) {
                sender.sendMessage(Text.PREFIX + "&cCould not save: " + e.getClass().getSimpleName());
            }
        });
        if (!queued) {
            sender.sendMessage(Text.PREFIX + "&cBlack Door is busy; try again.");
        }
    }
}
