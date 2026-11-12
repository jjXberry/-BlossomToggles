package com.blossomsmp.toggles;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Public chat, mentions, private messages, TPA requests, death messages, and the menu clicks. */
public final class SocialListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BlossomToggles plugin;

    public SocialListener(BlossomToggles plugin) {
        this.plugin = plugin;
    }

    private boolean on(Player player, Toggle toggle) {
        return plugin.store().get(player.getUniqueId(), toggle);
    }

    // ------------------------------------------------------------------ public chat and mentions

    /**
     * Players with Public Chat OFF don't receive other players' chat. Their own messages still show to them,
     * so they can still talk. HIGH so filters (BlossomChat) have already run.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (plugin.isEnabled(Toggle.PUBLIC_CHAT)) {
            event.viewers().removeIf(audience -> audience instanceof Player viewer
                    && !viewer.equals(sender) && !on(viewer, Toggle.PUBLIC_CHAT));
        }
        if (!plugin.isEnabled(Toggle.MENTION_SOUND)) {
            return;
        }
        String text = PLAIN.serialize(event.message()).toLowerCase(Locale.ROOT);
        List<Player> mentioned = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String name = viewer.getName().toLowerCase(Locale.ROOT);
            if (viewer.equals(sender) || name.length() < 3 || !event.viewers().contains(viewer)) {
                continue;
            }
            if (Pattern.compile("(^|[^a-z0-9_])@?" + Pattern.quote(name) + "($|[^a-z0-9_])").matcher(text).find()
                    && on(viewer, Toggle.MENTION_SOUND)) {
                mentioned.add(viewer);
            }
        }
        if (!mentioned.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player viewer : mentioned) {
                    if (viewer.isOnline()) {
                        plugin.sound(viewer, "mention");
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ commands

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String[] parts = message.substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        String bare = colon >= 0 ? label.substring(colon + 1) : label;
        Player player = event.getPlayer();

        // /settings, /toggles, /prefs always open THIS menu, even if BlossomSettings also has /settings.
        if (colon < 0 && plugin.getConfig().getStringList("menu.commands").contains(bare)) {
            String rest = message.substring(1 + parts[0].length());
            boolean oldReload = parts.length > 1 && parts[1].equalsIgnoreCase("reload")
                    && Bukkit.getPluginManager().getPlugin("BlossomSettings") != null;
            // "/settings reload" still reloads BlossomSettings while it is installed
            // (1.5.0 and newer use /blossomsettings reload, older versions /settings reload)
            String target = !oldReload ? "/blossomtoggles:settings"
                    : Bukkit.getPluginCommand("blossomsettings") != null ? "/blossomsettings:blossomsettings"
                    : "/blossomsettings:settings";
            event.setMessage(target + rest);
            return;
        }
        if (parts.length < 2 || player.hasPermission("blossomtoggles.bypass")) {
            return;
        }

        Toggle toggle = null;
        if (plugin.getConfig().getStringList("commands.private-messages").contains(bare)) {
            toggle = Toggle.PRIVATE_MESSAGES;
        } else if (plugin.getConfig().getStringList("commands.tpa").contains(bare)) {
            toggle = Toggle.TPA;
        } else if (plugin.getConfig().getStringList("commands.tpahere").contains(bare)) {
            toggle = Toggle.TPAHERE;
        }
        if (toggle == null || !plugin.isEnabled(toggle)) {
            return;
        }
        // Someone who turned private messages off can't start conversations either.
        if (toggle == Toggle.PRIVATE_MESSAGES && !on(player, Toggle.PRIVATE_MESSAGES)) {
            event.setCancelled(true);
            plugin.send(player, "blocked-own-msgs");
            plugin.sound(player, "denied");
            return;
        }
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null || target.equals(player) || !player.canSee(target) || on(target, toggle)) {
            return;
        }
        event.setCancelled(true);
        plugin.send(player, switch (toggle) {
            case TPA -> "blocked-tpa";
            case TPAHERE -> "blocked-tpahere";
            default -> "blocked-msg";
        }, "{player}", target.getName());
        plugin.sound(player, "denied");
    }

    // ------------------------------------------------------------------ death messages

    /**
     * MONITOR, and DiscordSRV is a soft dependency, so it loads first and reads the death message before it
     * is taken over here. Then the message is sent only to players who want death messages.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isEnabled(Toggle.DEATH_MESSAGES) || event.deathMessage() == null) {
            return;
        }
        boolean someoneOff = false;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!on(viewer, Toggle.DEATH_MESSAGES)) {
                someoneOff = true;
                break;
            }
        }
        if (!someoneOff) {
            return; // nothing to filter: leave the message alone
        }
        net.kyori.adventure.text.Component text = event.deathMessage();
        event.deathMessage(null);
        Bukkit.getConsoleSender().sendMessage(text);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // the player who died always sees how they died
            if (viewer.equals(event.getEntity()) || on(viewer, Toggle.DEATH_MESSAGES)) {
                viewer.sendMessage(text);
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < event.getInventory().getSize()) {
            menu.click(slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SettingsMenu) {
            event.setCancelled(true);
        }
    }
}
