package io.github.intruder_tech.blackdoor.proxy.bungee;

import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.CommandRouter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

final class BungeeCommand extends Command implements TabExecutor {

    private final CommandRouter router;

    BungeeCommand(CommandRouter router) {
        // No root permission: per-subcommand permissions are enforced by the router.
        super(BlackDoorEngine.PROXY_LABEL, null, "bdproxy");
        this.router = router;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        router.dispatch(new BungeeSender(sender), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return router.complete(new BungeeSender(sender), args);
    }
}
