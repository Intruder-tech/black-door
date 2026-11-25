package io.github.intruder_tech.blackdoor.core.menu;

import java.util.List;
import java.util.Objects;

/**
 * One clickable slot. Text uses {@code &}-colour codes.
 *
 * @param action null for display-only items
 */
public record MenuItem(int slot, Icon icon, String name, List<String> lore, MenuAction action) {

    public MenuItem {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }
}
