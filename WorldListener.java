package com.blossomsmp.toggles;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Mob spawns, phantoms and night vision, plus join/quit housekeeping. */
public final class WorldListener implements Listener {

    private final BlossomToggles plugin;
    private final Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
    private double radiusSq = 128 * 128;

    public WorldListener(BlossomToggles plugin) {
        this.plugin = plugin;
    }

    public void load() {
        reasons.clear();
        for (String name : plugin.getConfig().getStringList("mob-spawns.reasons")) {
            try {
                reasons.add(CreatureSpawnEvent.SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("config.yml: '" + name + "' in mob-spawns.reasons is not a spawn reason.");
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(CreatureSpawnEvent.SpawnReason.NATURAL);
        }
        double radius = plugin.getConfig().getDouble("mob-spawns.radius", 128);
        radiusSq = radius * radius;
    }

    /**
     * A hostile mob only stops spawning if EVERY player near it has Mob Spawns off.
     * One player can't switch spawns off for everybody, and mob farms keep working for whoever wants them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.isEnabled(Toggle.MOB_SPAWNS) || !reasons.contains(event.getSpawnReason())
                || !(event.getEntity() instanceof Enemy) || event.getEntity() instanceof Phantom) {
            return;
        }
        Location at = event.getLocation();
        boolean anyone = false;
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) > radiusSq) {
                continue;
            }
            anyone = true;
            if (plugin.store().get(player.getUniqueId(), Toggle.MOB_SPAWNS)) {
                return;
            }
        }
        if (anyone) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhantom(PhantomPreSpawnEvent event) {
        if (plugin.isEnabled(Toggle.PHANTOMS) && event.getSpawningEntity() instanceof Player player
                && !plugin.store().get(player.getUniqueId(), Toggle.PHANTOMS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.nightVision(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.moneyTags().refreshViewer(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.nightVision(player));
    }

    /** Milk and totems clear effects: put night vision back. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectCleared(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityPotionEffectEvent.Cause cause = event.getCause();
        if (cause == EntityPotionEffectEvent.Cause.MILK || cause == EntityPotionEffectEvent.Cause.TOTEM) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.nightVision(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.moneyTags().remove(event.getPlayer().getUniqueId());
    }
}
