package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.menu.Icon;
import io.github.intruder_tech.blackdoor.core.menu.MenuItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Icon to item mapping using materials that exist from 1.8 to current versions. */
final class MenuItems {

    private static final Map<Icon, String> MATERIALS = new EnumMap<>(Icon.class);

    static {
        MATERIALS.put(Icon.MODE_MONITOR, "EMERALD");
        MATERIALS.put(Icon.MODE_ENFORCE, "REDSTONE_BLOCK");
        MATERIALS.put(Icon.CHECK_DISABLED, "COAL");
        MATERIALS.put(Icon.CHECK_MONITOR, "GOLD_INGOT");
        MATERIALS.put(Icon.CHECK_MITIGATE, "REDSTONE");
        MATERIALS.put(Icon.CATEGORY, "BOOK");
        MATERIALS.put(Icon.LOCKED, "BEDROCK");
        MATERIALS.put(Icon.INFO, "PAPER");
        MATERIALS.put(Icon.RELOAD, "COMPASS");
        MATERIALS.put(Icon.BACK, "ARROW");
        MATERIALS.put(Icon.CLOSE, "BARRIER");
        MATERIALS.put(Icon.SETUP, "NETHER_STAR");
    }

    private MenuItems() {
    }

    static ItemStack render(MenuItem item) {
        Material material = Material.matchMaterial(MATERIALS.getOrDefault(item.icon(), "PAPER"));
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.toLegacy(item.name()));
                List<String> lore = new ArrayList<>();
                for (String line : item.lore()) {
                    lore.add(Text.toLegacy(line));
                }
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
        } catch (LinkageError | RuntimeException e) {
            // Legacy string meta API unavailable: show the bare item rather than fail the menu.
        }
        return stack;
    }
}
