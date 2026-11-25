package io.github.intruder_tech.blackdoor.proxy.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import io.github.intruder_tech.blackdoor.core.command.CommandRouter;

import java.util.List;

record VelocityCommand(CommandRouter router) implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        router.dispatch(new VelocitySender(invocation.source()), invocation.arguments());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return router.complete(new VelocitySender(invocation.source()), invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Per-subcommand permissions are enforced by the router; this only hides the root command.
        return invocation.source().hasPermission("blackdoor.command.help");
    }
}
