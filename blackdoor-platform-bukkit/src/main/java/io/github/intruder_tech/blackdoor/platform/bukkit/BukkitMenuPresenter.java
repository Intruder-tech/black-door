package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.core.command.BdSender;
import io.github.intruder_tech.blackdoor.core.menu.MenuItem;
import io.github.intruder_tech.blackdoor.core.menu.MenuPage;
import io.github.intruder_tech.blackdoor.core.menu.MenuPresenter;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Renders Black Door menus as chest inventories. Every click and drag inside a Black Door menu
 * is cancelled (at LOWEST and again at HIGHEST priority) so menu items can never be taken, even
 * if another plugin un-cancels the event.
 */
final class BukkitMenuPresenter implements MenuPresenter, Listener {

    private final PlatformScheduler scheduler;
    private final BdLogger logger;

    BukkitMenuPresenter(PlatformScheduler scheduler, BdLogger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public boolean open(BdSender viewer, MenuPage page) {
        Optional<UUID> id = viewer.uniqueId();
        if (id.isEmpty()) {
            return false;
        }
        new BukkitMenuSession(id.get(), viewer, scheduler, page).show(page);
        return true;
    }

    @Override
    public void closeAll() {
        // On Folia, inventories may only be touched from the player's region thread, which is not
        // available during disable; there we rely on the click guard being unregistered last.
        if (!Bukkit.isPrimaryThread()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                    player.closeInventory();
                }
            } catch (RuntimeException e) {
                logger.warn("Could not close a Black Door menu: " + e.getClass().getSimpleName());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder top = event.getView().getTopInventory().getHolder();
        if (!(top instanceof MenuHolder holder)) {
            return;
        }
        deny(event);
        if (event.getClickedInventory() == null || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.session.playerId())) {
            return;
        }
        Optional<MenuItem> item = holder.menu.itemAt(event.getRawSlot());
        if (item.isPresent() && item.get().action() != null) {
            try {
                item.get().action().onClick(holder.session);
            } catch (RuntimeException e) {
                logger.error("Menu action failed", e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickGuard(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            deny(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragGuard(InventoryDragEvent event) {
        onDrag(event);
    }

    private static void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }
}
