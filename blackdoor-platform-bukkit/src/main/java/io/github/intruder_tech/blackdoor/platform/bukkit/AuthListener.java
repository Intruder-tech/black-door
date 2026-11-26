package io.github.intruder_tech.blackdoor.platform.bukkit;

import io.github.intruder_tech.blackdoor.auth.AuthService;
import io.github.intruder_tech.blackdoor.auth.AuthService.Result;
import io.github.intruder_tech.blackdoor.core.BlackDoorEngine;
import io.github.intruder_tech.blackdoor.core.command.Text;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies the login system on a server: /signup and /signin, and nothing else for players who have
 * not authenticated yet. The two commands are handled here, before the server dispatches them, so
 * other plugins that respect cancelled events never see a password.
 */
final class AuthListener implements Listener {

    private static final int REMIND_EVERY_SECONDS = 10;

    private final BlackDoorEngine engine;
    private final AuthService auth;
    private final PlatformScheduler scheduler;
    private ScheduledExecutorService timer;
    private int ticks;

    AuthListener(BlackDoorEngine engine, AuthService auth, PlatformScheduler scheduler) {
        this.engine = engine;
        this.auth = auth;
        this.scheduler = scheduler;
    }

    void start(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerHandSwap(plugin);
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "blackdoor-auth");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    void stop() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }

    /** Hand swapping does not exist on 1.8, so it is registered by name. */
    @SuppressWarnings("unchecked")
    private void registerHandSwap(Plugin plugin) {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(
                    "org.bukkit.event.player.PlayerSwapHandItemsEvent");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.LOWEST, (listener, event) -> {
                if (event instanceof PlayerEvent p && auth.isBlocked(p.getPlayer().getUniqueId())) {
                    ((Cancellable) event).setCancelled(true);
                }
            }, plugin);
        } catch (ClassNotFoundException ignored) {
            // 1.8: no off hand
        }
    }

    private void tick() {
        try {
            for (UUID id : auth.timedOut()) {
                withPlayer(id, p -> p.kickPlayer(Text.toLegacy("&cYou did not sign in in time.")));
            }
            if (++ticks % REMIND_EVERY_SECONDS == 0) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (auth.isBlocked(player.getUniqueId())) {
                        withPlayer(player.getUniqueId(), this::prompt);
                    }
                }
            }
        } catch (RuntimeException e) {
            engine.platform().logger().error("Login timer failed", e);
        }
    }

    private void withPlayer(UUID id, java.util.function.Consumer<Player> action) {
        scheduler.runForPlayer(id, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    private void prompt(Player player) {
        AuthService.State state = auth.state(player.getUniqueId());
        if (state == AuthService.State.NEEDS_SIGNUP) {
            send(player, "&eCreate your account: &f/signup <password> <password>");
        } else if (state == AuthService.State.NEEDS_SIGNIN) {
            send(player, "&ePlease sign in: &f/signin <password>");
        }
    }

    private static void send(CommandSender to, String message) {
        to.sendMessage(Text.toLegacy(Text.PREFIX + message));
    }

    private static String address(Player player) {
        InetSocketAddress socket = player.getAddress();
        return socket == null || socket.getAddress() == null ? null : socket.getAddress().getHostAddress();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (auth.join(player.getUniqueId(), player.getName(), address(player)) != AuthService.State.AUTHENTICATED) {
            player.leaveVehicle();
            prompt(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        auth.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        label = label.substring(label.indexOf(':') + 1);
        Player player = event.getPlayer();
        boolean signup = label.equals("signup");
        if (auth.enabled() && (signup || label.equals("signin"))) {
            event.setCancelled(true);
            authenticate(player, signup, parts);
        } else if (auth.isBlocked(player.getUniqueId())) {
            event.setCancelled(true);
            prompt(player);
        }
    }

    private void authenticate(Player player, boolean signup, String[] parts) {
        UUID id = player.getUniqueId();
        if (!auth.isBlocked(id)) {
            send(player, "&aYou are already signed in.");
            return;
        }
        if (parts.length != (signup ? 3 : 2)) {
            send(player, signup ? "&cUse: &f/signup <password> <password> &7(no spaces in the password)"
                    : "&cUse: &f/signin <password>");
            return;
        }
        String name = player.getName();
        String address = address(player);
        String password = parts[1];
        String repeated = signup ? parts[2] : null;
        boolean queued = engine.submitWork(() -> {
            Result result = signup ? auth.register(id, name, address, password, repeated)
                    : auth.login(id, address, password);
            withPlayer(id, p -> reply(p, signup, result, password));
        });
        if (!queued) {
            send(player, "&cBusy; please try again.");
        }
    }

    private void reply(Player player, boolean signup, Result result, String password) {
        int hours = engine.authSettings().sessionHours();
        if (result == Result.OK) {
            if (signup) {
                send(player, "&aAccount created.");
                send(player, "&7Username: &f" + Text.escape(player.getName()));
                showPassword(player, password);
            } else {
                send(player, "&aSigned in.");
            }
            send(player, "&7For the next &f" + hours + " hours &7you can leave and rejoin from this connection "
                    + "without your password. After that, use &f/signin&7.");
            return;
        }
        send(player, switch (result) {
            case ALREADY_REGISTERED -> "&cYou already have an account. Use &f/signin <password>&c.";
            case NOT_REGISTERED -> "&cNo account yet. Use &f/signup <password> <password>&c.";
            case NAME_TAKEN -> "&cThis name is registered with different capital letters. Ask the staff.";
            case MISMATCH -> "&cThe two passwords are not the same.";
            case TOO_SHORT -> "&cUse at least " + engine.authSettings().minPasswordLength() + " characters.";
            case TOO_LONG -> "&cUse at most " + AuthService.MAX_PASSWORD_LENGTH + " characters.";
            case SAME_AS_NAME -> "&cYour password must not be your name.";
            case WRONG_PASSWORD -> "&cWrong password.";
            case LOCKED_OUT -> "&cToo many wrong passwords. Try again later.";
            case BUSY -> "&eStill checking your last attempt.";
            case STORAGE_ERROR -> "&cThe login system has a problem. Please tell the staff.";
            case NOT_PENDING, OK -> "&aYou are already signed in.";
        });
        if (result == Result.LOCKED_OUT) {
            player.kickPlayer(Text.toLegacy("&cToo many wrong passwords. Try again later."));
        }
    }

    /** The password stays hidden until the player points at it, so streams do not show it. */
    private static void showPassword(Player player, String password) {
        try {
            TextComponent line = new TextComponent(TextComponent.fromLegacyText(
                    Text.toLegacy(Text.PREFIX + "&7Password: &e[point here to see it]")));
            line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new BaseComponent[]{new TextComponent(password)}));
            player.spigot().sendMessage(line);
        } catch (LinkageError | RuntimeException e) {
            send(player, "&7Password: &8(the one you just typed)");
        }
    }

    // Everything below only holds a player still until they have authenticated.

    private void block(Entity who, Cancellable event) {
        if (who instanceof Player && auth.isBlocked(who.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && auth.isBlocked(event.getPlayer().getUniqueId())
                && (from.getX() != to.getX() || from.getY() < to.getY() || from.getZ() != to.getZ())) {
            // looking around and falling to the ground are allowed
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHanging(HangingBreakByEntityEvent event) {
        block(event.getRemover(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSign(SignChangeEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBook(PlayerEditBookEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        block(event.getPlayer(), event);
    }

    @SuppressWarnings("deprecation") // the only pickup event that exists on every supported version
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupItemEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBed(PlayerBedEnterEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        block(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        block(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        block(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamaged(EntityDamageEvent event) {
        block(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        block(event.getDamager(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        block(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicle(VehicleEnterEvent event) {
        block(event.getEntered(), event);
    }
}
