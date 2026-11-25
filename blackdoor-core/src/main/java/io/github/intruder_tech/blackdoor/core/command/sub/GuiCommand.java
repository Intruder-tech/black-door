package io.github.intruder_tech.blackdoor.core.command.sub;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Subcommand;
import io.github.intruder_tech.blackdoor.core.command.Text;

import java.util.List;

/** Opens the in-game control panel. Viewing needs this permission; changing needs blackdoor.gui.edit. */
public final class GuiCommand implements Subcommand {

    private final BlackDoorEngine engine;

    public GuiCommand(BlackDoorEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "gui";
    }

    @Override
    public String permission() {
        return "blackdoor.command.gui";
    }

    @Override
    public String description() {
        return "Opens the in-game control panel.";
    }

    @Override
    public void execute(BdSender sender, List<String> args) {
        if (sender.isConsole()) {
            sender.sendMessage(Text.PREFIX + "&cOnly players can open the control panel.");
            return;
        }
        boolean opened = engine.platform().menus()
                .map(presenter -> presenter.open(sender, engine.menus().main()))
                .orElse(false);
        if (!opened) {
            sender.sendMessage(Text.PREFIX + "&cThe control panel is not available on this platform.");
        }
    }
}
