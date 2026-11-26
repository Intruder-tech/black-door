package io.github.intruder_tech.blackdoor.core.menu;

import java.util.List;
import java.util.Objects;

/**
 * One clickable slot. Text uses {@code &}-colour codes.
 *
 * @param action null for display-only items
 * @param glow   show the enchanted shimmer (used for settings that actively block)
 */
public record MenuItem(int slot, Icon icon, String name, List<String> lore, MenuAction action, boolean glow) {

    public MenuItem {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    public MenuItem(int slot, Icon icon, String name, List<String> lore, MenuAction action) {
        this(slot, icon, name, lore, action, false);
    }
}
