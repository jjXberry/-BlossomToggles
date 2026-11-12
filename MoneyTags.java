package com.blossomsmp.toggles;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A "$3.9K" line floating above (or just under) each player's name.
 *
 * A player's tag is NOT made at all when:
 *   - they have the hide permission (staff / admins), or
 *   - they turned "Show My Money" off, or
 *   - they are vanished, invisible, dead or in spectator.
 * A viewer only sees tags if "See Money Tags" is on, and never sees their own.
 *
 * The text displays follow the player every couple of ticks instead of riding them,
 * because riding entities break teleports.
 */
public final class MoneyTags {

    private final BlossomToggles plugin;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<UUID, String> balances = new HashMap<>();
    private final Map<UUID, String> shown = new HashMap<>();

    private BukkitTask task;
    private long runs;
    private int interval = 2;
    private int refreshRuns = 10;
    private double offset = 0.75;
    private String format = "&a$&f{balance}";
    private String hidePermission = "blossomtoggles.money.hidden";
    private boolean enabled;

    public MoneyTags(BlossomToggles plugin) {
        this.plugin = plugin;
    }

    public void start() {
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("money-tags.enabled", true);
        interval = Math.max(1, cfg.getInt("money-tags.update-ticks", 2));
        refreshRuns = Math.max(1, Math.max(interval, cfg.getInt("money-tags.balance-refresh-ticks", 20)) / interval);
        String position = cfg.getString("money-tags.position", "above");
        offset = "below".equalsIgnoreCase(position)
                ? cfg.getDouble("money-tags.below-offset", 0.05)
                : cfg.getDouble("money-tags.above-offset", 0.75);
        format = cfg.getString("money-tags.format", "&a$&f{balance}");
        hidePermission = cfg.getString("money-tags.hide-permission", "blossomtoggles.money.hidden");
        if (enabled) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (TextDisplay display : displays.values()) {
            display.remove();
        }
        displays.clear();
        balances.clear();
        shown.clear();
    }

    /** Should this player have a money tag at all? */
    private boolean hasTag(Player player) {
        return !player.isDead() && !player.isInvisible() && !vanished(player)
                && player.getGameMode() != GameMode.SPECTATOR
                && !player.hasPermission(hidePermission)
                && plugin.store().get(player.getUniqueId(), Toggle.SHOW_MONEY);
    }

    /** EssentialsX, SuperVanish and PremiumVanish all set the "vanished" metadata. */
    private static boolean vanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean wants(Player viewer, Player owner) {
        return !viewer.getUniqueId().equals(owner.getUniqueId())
                && plugin.store().get(viewer.getUniqueId(), Toggle.SEE_MONEY)
                && viewer.canSee(owner);
    }

    private void tick() {
        Economy economy = plugin.economy();
        if (economy == null) {
            return;
        }
        boolean refresh = runs++ % refreshRuns == 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!hasTag(player)) {
                remove(id);
                continue;
            }
            TextDisplay display = displays.get(id);
            if (display != null && (!display.isValid() || !display.getWorld().equals(player.getWorld()))) {
                remove(id);
                display = null;
            }
            String balance = balances.get(id);
            if (balance == null || refresh) {
                balance = Text.shortMoney(economy.getBalance(player));
                balances.put(id, balance);
            }
            if (display == null) {
                create(player, balance);
                continue;
            }
            if (!balance.equals(shown.get(id))) {
                display.text(Text.c(Text.fill(format, "{balance}", balance)));
                shown.put(id, balance);
            }
            display.teleport(anchor(player));
            if (refresh) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    show(viewer, player, display);
                }
            }
        }
    }

    private void show(Player viewer, Player owner, TextDisplay display) {
        if (wants(viewer, owner)) {
            viewer.showEntity(plugin, display);
        } else {
            viewer.hideEntity(plugin, display);
        }
    }

    private Location anchor(Player player) {
        // getHeight() follows the pose, so the tag drops when sneaking or swimming
        return player.getLocation().add(0, player.getHeight() + offset, 0);
    }

    private void create(Player owner, String balance) {
        TextDisplay display = owner.getWorld().spawn(anchor(owner), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false); // shown per viewer below
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(0.3f);
            d.setTeleportDuration(Math.min(59, interval));
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.text(Text.c(Text.fill(format, "{balance}", balance)));
        });
        UUID id = owner.getUniqueId();
        displays.put(id, display);
        shown.put(id, balance);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (wants(viewer, owner)) {
                viewer.showEntity(plugin, display);
            }
        }
    }

    /** A viewer changed "See Money Tags": update every tag for them right away. */
    public void refreshViewer(Player viewer) {
        for (Map.Entry<UUID, TextDisplay> entry : displays.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null) {
                viewer.hideEntity(plugin, entry.getValue());
            } else {
                show(viewer, owner, entry.getValue());
            }
        }
    }

    public void remove(UUID id) {
        TextDisplay display = displays.remove(id);
        if (display != null) {
            display.remove();
        }
        balances.remove(id);
        shown.remove(id);
    }
}
