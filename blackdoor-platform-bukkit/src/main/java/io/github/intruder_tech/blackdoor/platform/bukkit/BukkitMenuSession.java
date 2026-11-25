package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.menu.Menu;
import io.github.intruder_tech.blackdoor.core.menu.MenuItem;
import io.github.intruder_tech.blackdoor.core.menu.MenuPage;
import io.github.intruder_tech.blackdoor.core.menu.MenuSession;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** One player's open menu. All rendering hops to the player's owning thread (Folia-safe). */
final class BukkitMenuSession implements MenuSession {

    private final UUID playerId;
    private final BdSender viewer;
    private final PlatformScheduler scheduler;
    private volatile MenuPage page;

    BukkitMenuSession(UUID playerId, BdSender viewer, PlatformScheduler scheduler, MenuPage page) {
        this.playerId = playerId;
        this.viewer = viewer;
        this.scheduler = scheduler;
        this.page = page;
    }

    @Override
    public BdSender viewer() {
        return viewer;
    }

    @Override
    public void show(MenuPage newPage) {
        page = newPage;
        render(true);
    }

    @Override
    public void refresh() {
        render(false);
    }

    @Override
    public void close() {
        scheduler.runForPlayer(playerId, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && isOurs(player.getOpenInventory().getTopInventory().getHolder())) {
                player.closeInventory();
            }
        });
    }

    UUID playerId() {
        return playerId;
    }

    private void render(boolean forceReopen) {
        scheduler.runForPlayer(playerId, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                return;
            }
            Menu menu = page.build(this);
            InventoryHolder openHolder = player.getOpenInventory().getTopInventory().getHolder();
            if (!forceReopen && openHolder instanceof MenuHolder holder && holder.session == this
                    && holder.inventory.getSize() == menu.rows() * 9 && holder.menu.title().equals(menu.title())) {
                holder.menu = menu;
                fill(holder.inventory, menu);
                return;
            }
            if (!forceReopen && !isOurs(openHolder)) {
                return; // the player closed the menu meanwhile; do not pop it open again
            }
            MenuHolder holder = new MenuHolder(this, menu);
            Inventory inventory = Bukkit.createInventory(holder, menu.rows() * 9, Text.toLegacy("&8" + menu.title()));
            holder.inventory = inventory;
            fill(inventory, menu);
            player.openInventory(inventory);
        });
    }

    private boolean isOurs(InventoryHolder holder) {
        return holder instanceof MenuHolder h && h.session == this;
    }

    private static void fill(Inventory inventory, Menu menu) {
        inventory.clear();
        for (MenuItem item : menu.items()) {
            inventory.setItem(item.slot(), MenuItems.render(item));
        }
    }
}
