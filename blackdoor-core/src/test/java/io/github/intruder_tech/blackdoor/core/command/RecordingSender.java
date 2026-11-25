package io.github.intruder_tech.blackdoor.core.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingSender implements BdSender {

    public final List<String> messages = new CopyOnWriteArrayList<>();
    private final Set<String> permissions;
    private final String name;

    public RecordingSender(String name, Set<String> permissions) {
        this.name = name;
        this.permissions = permissions;
    }

    public static RecordingSender admin() {
        return new RecordingSender("Admin", Set.of("blackdoor.command.help", "blackdoor.command.status",
                "blackdoor.command.version", "blackdoor.command.reload", "blackdoor.command.diagnostics"));
    }

    public String all() {
        return String.join("\n", messages);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public boolean hasPermission(String node) {
        return permissions.contains(node);
    }

    @Override
    public void sendMessage(String message) {
        messages.add(message);
    }

    @Override
    public Optional<UUID> uniqueId() {
        return Optional.empty();
    }
}
