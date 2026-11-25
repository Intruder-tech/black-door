package io.github.intruder_tech.blackdoor.core.command;

import java.util.List;

/** One {@code /blackdoor <name>} subcommand. Each has its own permission node (SR-70). */
public interface Subcommand {

    String name();

    /** Permission node, e.g. {@code blackdoor.command.status}. */
    String permission();

    String description();

    default String usage() {
        return name();
    }

    void execute(BdSender sender, List<String> args);

    default List<String> complete(BdSender sender, List<String> args) {
        return List.of();
    }
}
