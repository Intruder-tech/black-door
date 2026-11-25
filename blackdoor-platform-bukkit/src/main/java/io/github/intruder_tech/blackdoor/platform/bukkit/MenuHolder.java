package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.menu.Menu;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as a Black Door menu; identity-based, never title-based. */
final class MenuHolder implements InventoryHolder {

    final BukkitMenuSession session;
    volatile Menu menu;
    Inventory inventory;

    MenuHolder(BukkitMenuSession session, Menu menu) {
        this.session = session;
        this.menu = menu;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
