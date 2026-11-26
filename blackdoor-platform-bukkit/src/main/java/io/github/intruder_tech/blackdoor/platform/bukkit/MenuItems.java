package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.menu.Icon;
import io.github.intruder_tech.blackdoor.core.menu.MenuItem;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Icon to item mapping. Each entry lists candidates newest first; the first name the server knows
 * wins, so old versions down to 1.8 show an older stand-in.
 */
final class MenuItems {

    private static final Map<Icon, String[]> MATERIALS = new EnumMap<>(Icon.class);

    private static void put(Icon icon, String... candidates) {
        MATERIALS.put(icon, candidates);
    }

    static {
        put(Icon.MODE_MONITOR, "EMERALD");
        put(Icon.MODE_ENFORCE, "REDSTONE_BLOCK");
        put(Icon.CHECK_DISABLED, "GRAY_DYE", "COAL");
        put(Icon.CATEGORY, "DIAMOND_SWORD");
        put(Icon.LOCKED_COMBAT, "CHARCOAL", "COAL");
        put(Icon.LOCKED_XRAY, "TINTED_GLASS", "GLASS");
        put(Icon.STATUS, "AMETHYST_SHARD", "PAPER");
        put(Icon.INFO, "PAPER");
        put(Icon.RELOAD, "COMPASS");
        put(Icon.BACK, "ARROW");
        put(Icon.CLOSE, "BARRIER");
        put(Icon.SETUP, "NETHER_STAR");
        put(Icon.JOIN_GUARD, "WARD_ARMOR_TRIM_SMITHING_TEMPLATE", "IRON_DOOR");
        put(Icon.LOGIN_SYSTEM, "TRIPWIRE_HOOK");
        put(Icon.POSITION, "ENDER_PEARL");
        put(Icon.ROTATION, "ENDER_EYE", "EYE_OF_ENDER");
        put(Icon.SLOT, "COPPER_INGOT", "IRON_INGOT");
        put(Icon.SELF_INTERACT, "FIRE_CHARGE", "FIREBALL");
        put(Icon.TELEPORT, "ECHO_SHARD", "GHAST_TEAR");
        put(Icon.OVERSIZED, "NETHERITE_INGOT", "IRON_BLOCK");
        put(Icon.TRANSACTION, "HEART_OF_THE_SEA", "EMERALD");
        put(Icon.FLOOD, "NAUTILUS_SHELL", "PRISMARINE_SHARD");
        put(Icon.PAYLOAD, "NETHERITE_SCRAP", "FLINT");
        put(Icon.BOOK, "WRITABLE_BOOK", "BOOK_AND_QUILL");
        put(Icon.SIGN, "OAK_SIGN", "SIGN");
        put(Icon.CREATIVE_ITEM, "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "DIAMOND");
    }

    private MenuItems() {
    }

    private static Material material(Icon icon) {
        for (String name : MATERIALS.getOrDefault(icon, new String[0])) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.PAPER;
    }

    /** Enchanted shimmer: the glint override on 1.20.5+, a hidden enchantment before that. */
    private static void glow(ItemMeta meta) {
        try {
            ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(meta, Boolean.TRUE);
        } catch (ReflectiveOperationException e) {
            Enchantment any = Enchantment.getByName("DURABILITY");
            if (any != null) {
                meta.addEnchant(any, 1, true);
            }
        }
    }

    static ItemStack render(MenuItem item) {
        ItemStack stack = new ItemStack(material(item.icon()));
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.toLegacy(item.name()));
                List<String> lore = new ArrayList<>();
                for (String line : item.lore()) {
                    lore.add(Text.toLegacy(line));
                }
                meta.setLore(lore);
                if (item.glow()) {
                    glow(meta);
                }
                // hide the game's own tooltip lines (enchantments, trim template text)
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
        } catch (LinkageError | RuntimeException e) {
            // Legacy string meta API unavailable: show the bare item rather than fail the menu.
        }
        return stack;
    }
}
