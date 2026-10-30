package br.com.survivalplus.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

final class PortalListener implements Listener {
    private record Attempt(Location from, int tick) {}
    private final SurvivalPlusCore plugin;
    private final Map<UUID, Attempt> attempts = new HashMap<>();

    PortalListener(SurvivalPlusCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void playerPortal(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || !plugin.guard().active()) return;
        Location from = e.getPlayer().getLocation();
        if (!plugin.guard().touchesManagedWorld(from) && !plugin.guard().touchesManagedWorld(e.getTo())) return;
        // Never create or clear terrain for automatic exit placement.
        e.setCanCreatePortal(false);
        PortalGuard.Decision d = plugin.guard().initial(e.getPlayer(), from, e.getTo(), e.getSearchRadius());
        if (!d.allowed()) { e.setCancelled(true); blocked(e.getPlayer(), d); return; }
        e.setSearchRadius(d.radius());
        remember(e.getPlayer(), from);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent e) {
        if (!plugin.guard().active()) return;
        Location from = e.getEntity().getLocation();
        if (e.getPortalType() != PortalType.NETHER
                || !plugin.guard().entityPortalInScope(from, e.getTo())) return;
        e.setCanCreatePortal(false);
        PortalGuard.Decision d = plugin.guard().initial(e.getEntity(), from, e.getTo(), e.getSearchRadius());
        if (!d.allowed()) { e.setCancelled(true); blocked(e.getEntity(), d); return; }
        e.setSearchRadius(d.radius());
        remember(e.getEntity(), from);
    }

    /** Paper 26.2 emits this after it selects the existing exit and computes its exact position. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void finalPlayerTeleport(PlayerTeleportEvent e) {
        if (e instanceof PlayerPortalEvent || e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || !plugin.guard().active()) return;
        if (!plugin.guard().touchesManagedWorld(e.getFrom()) && !plugin.guard().touchesManagedWorld(e.getTo())) return;
        Attempt attempt = consume(e.getPlayer());
        Location source = attempt == null ? e.getFrom() : attempt.from();
        PortalGuard.Decision d = plugin.guard().finalExit(e.getPlayer(), source, e.getTo());
        if (!d.allowed()) { e.setCancelled(true); blocked(e.getPlayer(), d); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void finalEntityTeleport(EntityTeleportEvent e) {
        if (!plugin.guard().active()) return;
        if (isEnd(e.getFrom()) || isEnd(e.getTo())) {
            attempts.remove(e.getEntity().getUniqueId());
            return;
        }
        // EntityTeleportEvent has no cause. Only associate a final event with the
        // Nether attempt from this tick, not commands or natural teleports later.
        Attempt attempt = consume(e.getEntity());
        if (attempt == null) return;
        PortalGuard.Decision d = plugin.guard().finalExit(e.getEntity(), attempt.from(), e.getTo());
        if (!d.allowed()) { e.setCancelled(true); blocked(e.getEntity(), d); }
    }

    // Do NOT cancel EntityPortalExitEvent: in Paper 26.2 cancellation only resets
    // position/velocity while retaining the destination world. EntityTeleportEvent
    // above is the real, cancellable final guard for non-player entities.

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerPortalResult(PlayerPortalEvent e) {
        if (e.isCancelled()) attempts.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entityPortalResult(EntityPortalEvent e) {
        if (e.isCancelled()) attempts.remove(e.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portalCreation(PortalCreateEvent e) {
        if (!plugin.guard().active() || e.getReason() != PortalCreateEvent.CreateReason.NETHER_PAIR) return;
        if (plugin.guard().touchesManagedWorld(e.getWorld().getSpawnLocation())) e.setCancelled(true);
    }

    private void remember(Entity entity, Location from) {
        UUID id = entity.getUniqueId();
        Attempt attempt = new Attempt(from.clone(), Bukkit.getCurrentTick());
        attempts.put(id, attempt);
        // Paper 26.2 portal search and the final transition are synchronous in
        // this tick. An unsuccessful search must not mark a later admin command.
        Bukkit.getScheduler().runTask(plugin, () -> attempts.remove(id, attempt));
    }

    private Attempt consume(Entity entity) {
        Attempt attempt = attempts.remove(entity.getUniqueId());
        return attempt != null && attempt.tick() == Bukkit.getCurrentTick() ? attempt : null;
    }

    private static boolean isEnd(Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getEnvironment() == World.Environment.THE_END;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void finalEntityResult(EntityTeleportEvent e) {
        // Also discard an attempt when another plugin already canceled the
        // final transition before our HIGHEST guard was invoked.
        attempts.remove(e.getEntity().getUniqueId());
    }

    private void blocked(Entity entity, PortalGuard.Decision decision) {
        attempts.remove(entity.getUniqueId());
        if (entity instanceof Player player) plugin.notifications().blocked(player, decision.message());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent || e.getTo() == null) return;
        if (e.getFrom().getX() == e.getTo().getX() && e.getFrom().getZ() == e.getTo().getZ()) return;
        plugin.notifications().refresh(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleportNotification(PlayerTeleportEvent e) {
        // Any teleport can cross the connection zone; only notifications apply to commands.
        // Discard an old portal attempt before an unrelated teleport can trigger an End exit.
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) attempts.remove(e.getPlayer().getUniqueId());
        refreshNextTick(e.getPlayer());
    }

    @EventHandler public void join(PlayerJoinEvent e) { refreshNextTick(e.getPlayer()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent e) { refreshNextTick(e.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent e) {
        plugin.notifications().forget(e.getPlayer().getUniqueId());
        attempts.remove(e.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void worldLoad(WorldLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guard().status());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void worldUnload(WorldUnloadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.guard().status());
    }

    private void refreshNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.notifications().refresh(player, player.getLocation());
        });
    }
    void clear() { attempts.clear(); }
}
