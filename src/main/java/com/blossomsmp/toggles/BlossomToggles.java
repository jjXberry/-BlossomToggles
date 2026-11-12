package com.blossomsmp.toggles;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import com.blossomsmp.toggles.petals.PetalTools;
import com.blossomsmp.toggles.petals.PetalsCommand;
import com.blossomsmp.toggles.petals.PetalsEarner;
import com.blossomsmp.toggles.petals.PetalsPlaceholders;
import com.blossomsmp.toggles.petals.PetalsShop;
import com.blossomsmp.toggles.petals.ShardsCommand;
import com.blossomsmp.toggles.petals.ShardsManager;
import com.blossomsmp.toggles.petals.ShopItem;
import com.blossomsmp.toggles.petals.SpawnerManager;
import com.blossomsmp.toggles.petals.SpawnerMenu;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BlossomToggles: the Blossom SMP /settings menu, plus everything that used to be in BlossomSettings:
 * Petals (the /ps currency), the Petal Shop, 24-hour Petal tools and DonutSMP-style spawners.
 *
 *   /settings                open the menu (also /toggles, /prefs)
 *   /settings &lt;toggle&gt;       flip one setting, e.g. /settings chat
 *   /petals                  your Petals (staff: give, take, set, check)
 *   /ps                      the Petal Shop
 *   /blossomtoggles reload   staff: re-read the config files
 */
public final class BlossomToggles extends JavaPlugin implements TabExecutor {

    private FileConfiguration messages = new YamlConfiguration();
    private ToggleStore store;
    private MoneyTags moneyTags;
    private WorldListener world;
    private Economy economy;

    // Petals (from BlossomSettings)
    private boolean petalsOn;
    private ShardsManager shards;
    private SpawnerManager spawners;
    private PetalsEarner earner;
    private PetalTools tools;
    private PetalsShop shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Adds new sections (Petals, spawners...) to an older config.yml without touching your changes
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadMessages();
        store = new ToggleStore(this);
        store.load();
        moneyTags = new MoneyTags(this);
        world = new WorldListener(this);
        world.load();

        getServer().getPluginManager().registerEvents(new SocialListener(this), this);
        getServer().getPluginManager().registerEvents(world, this);
        for (String name : List.of("settings", "blossomtoggles")) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault is not installed, so money tags are off.");
        }
        moneyTags.start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            nightVision(player);
        }
        startPetals();
        getLogger().info("BlossomToggles is ready" + (petalsOn ? " (settings, Petals, Petal Shop, tools and spawners)." : "."));
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsMenu) {
                player.closeInventory();
            }
        }
        if (moneyTags != null) {
            moneyTags.stop();
        }
        if (earner != null) {
            earner.stop();
        }
        if (tools != null) {
            tools.stop();
        }
        if (spawners != null) {
            spawners.stop();
        }
        if (shards != null) {
            shards.save();
        }
        if (store != null) {
            store.shutdown();
        }
    }

    // ------------------------------------------------------------------ files

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            getLogger().severe("messages.yml has a mistake in it, so the built-in messages are used: "
                    + String.valueOf(e.getMessage()).split("\n")[0]);
        }
        InputStream in = getResource("messages.yml");
        if (in != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        messages = yaml;
    }

    public void reloadEverything() {
        reloadConfig();
        loadMessages();
        world.load();
        moneyTags.stop();
        moneyTags.start();
        if (spawners != null) {
            spawners.reload();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            nightVision(player);
        }
    }

    // ------------------------------------------------------------------ Petals (from BlossomSettings)

    private void startPetals() {
        if (getServer().getPluginManager().getPlugin("BlossomSettings") != null) {
            getLogger().severe("BlossomSettings is still installed, so Petals, /ps, Petal tools and spawners are OFF "
                    + "in BlossomToggles (running both would pay Petals twice). Stop the server, delete "
                    + "BlossomSettings.jar from plugins (keep its folder), and start again: everything moves over.");
            return;
        }
        importFromBlossomSettings();

        shards = new ShardsManager(this);
        shards.load();
        spawners = new SpawnerManager(this);
        getServer().getPluginManager().registerEvents(spawners, this);
        getServer().getPluginManager().registerEvents(new SpawnerMenu(this), this);
        earner = new PetalsEarner(this);
        tools = new PetalTools(this);
        shop = new PetalsShop(this);
        getServer().getPluginManager().registerEvents(earner, this);
        getServer().getPluginManager().registerEvents(tools, this);
        getServer().getPluginManager().registerEvents(shop, this);

        ShardsCommand petalsCommand = new ShardsCommand(this);
        PluginCommand petals = getCommand("petals");
        if (petals != null) {
            petals.setExecutor(petalsCommand);
            petals.setTabCompleter(petalsCommand);
        }
        PetalsCommand shopCommand = new PetalsCommand(this, shop);
        PluginCommand ps = getCommand("petalsshop");
        if (ps != null) {
            ps.setExecutor(shopCommand);
            ps.setTabCompleter(shopCommand);
        }
        earner.start();
        tools.start();
        spawners.start();
        // Save balances every 5 minutes as a safety net
        getServer().getScheduler().runTaskTimer(this, () -> shards.save(), 6000L, 6000L);

        // Same placeholder names as before: %blossomsettings_petals% keeps working in TAB and holograms
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PetalsPlaceholders(this).register();
        }
        petalsOn = true;
    }

    /**
     * Once: copy Petals balances (shards.yml), spawners (spawners.yml) and the Petals parts of config.yml
     * from plugins/BlossomSettings. The old folder is left as it is, as a backup.
     */
    private void importFromBlossomSettings() {
        File old = new File(getDataFolder().getParentFile(), "BlossomSettings");
        File marker = new File(getDataFolder(), "imported-from-blossomsettings.txt");
        if (!old.isDirectory() || marker.exists()) {
            return;
        }
        List<String> done = new ArrayList<>();
        try {
            Files.createDirectories(getDataFolder().toPath());
            for (String name : List.of("shards.yml", "spawners.yml")) {
                File from = new File(old, name);
                File to = new File(getDataFolder(), name);
                if (from.exists() && !to.exists()) {
                    Files.copy(from.toPath(), to.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    done.add(name);
                }
            }
            File oldConfig = new File(old, "config.yml");
            if (oldConfig.exists()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(oldConfig);
                for (String path : List.of("earn-petals", "shard-prices", "tools", "spawners")) {
                    ConfigurationSection section = yaml.getConfigurationSection(path);
                    if (section == null) {
                        continue;
                    }
                    for (String key : section.getKeys(true)) {
                        if (!section.isConfigurationSection(key)) {
                            getConfig().set(path + "." + key, section.get(key));
                        }
                    }
                    done.add("config: " + path);
                }
                saveConfig();
            }
            Files.writeString(marker.toPath(), "Copied from plugins/BlossomSettings: " + done
                    + "\nDelete this file to copy again (only files that don't exist here yet are copied).\n");
        } catch (IOException e) {
            getLogger().severe("Could not copy everything from plugins/BlossomSettings: " + e.getMessage());
            return;
        }
        if (!done.isEmpty()) {
            getLogger().info("Moved over from BlossomSettings: " + String.join(", ", done) + ".");
        }
    }

    /** Tag names stay "blossomsettings:..." so tools and spawners players already own keep working. */
    public NamespacedKey legacyKey(String key) {
        return NamespacedKey.fromString("blossomsettings:" + key);
    }

    public ShardsManager shards() {
        return shards;
    }

    public SpawnerManager spawners() {
        return spawners;
    }

    public PetalTools tools() {
        return tools;
    }

    /** Petal Shop price, from config.yml shard-prices (falls back to the built-in default). */
    public long price(ShopItem item) {
        return getConfig().getLong("shard-prices." + item.id, item.defaultPrice);
    }

    // ------------------------------------------------------------------ toggles

    public ToggleStore store() {
        return store;
    }

    public MoneyTags moneyTags() {
        return moneyTags;
    }

    /** A toggle switched off in config.yml is hidden from the menu and does nothing. */
    public boolean isEnabled(Toggle toggle) {
        return getConfig().getBoolean("toggles." + toggle.id + ".enabled", true);
    }

    public boolean defaultOf(Toggle toggle) {
        return getConfig().getBoolean("toggles." + toggle.id + ".default", toggle.builtInDefault);
    }

    /** Flip one toggle for a player, apply it, and tell them. */
    public void flip(Player player, Toggle toggle) {
        if (!isEnabled(toggle)) {
            send(player, "unknown-toggle", "{toggle}", toggle.id);
            return;
        }
        boolean now = !store.get(player.getUniqueId(), toggle);
        store.set(player.getUniqueId(), toggle, now);
        apply(player, toggle);
        send(player, now ? "toggled-on" : "toggled-off", "{name}", raw("toggles." + toggle.id + ".name"));
        sound(player, now ? "toggle-on" : "toggle-off");
    }

    private void apply(Player player, Toggle toggle) {
        switch (toggle) {
            case NIGHT_VISION -> nightVision(player);
            case SEE_MONEY -> moneyTags.refreshViewer(player);
            default -> { } // the money tag task picks up SHOW_MONEY by itself within a tick
        }
    }

    public void applyAll(Player player) {
        nightVision(player);
        moneyTags.refreshViewer(player);
    }

    /** Gives or removes the endless night vision. Night vision from potions is never touched. */
    public void nightVision(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        boolean ours = current != null && current.isInfinite();
        if (isEnabled(Toggle.NIGHT_VISION) && store.get(player.getUniqueId(), Toggle.NIGHT_VISION)) {
            if (!ours) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        PotionEffect.INFINITE_DURATION, 0, true, false, false));
            }
        } else if (ours) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    public Economy economy() {
        if (economy == null && getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                economy = provider.getProvider();
            }
        }
        return economy;
    }

    // ------------------------------------------------------------------ messages and sounds

    public String raw(String key) {
        String text = messages.getString(key);
        return text == null ? "&cMissing message: " + key : text;
    }

    public List<String> rawList(String key) {
        return messages.getStringList(key);
    }

    public void send(CommandSender to, String key, String... replacements) {
        String text = raw(key);
        if (!text.isEmpty()) {
            to.sendMessage(Text.c(raw("prefix") + Text.fill(text, replacements)));
        }
    }

    public void sound(Player player, String key) {
        String name = getConfig().getString("sounds." + key, "");
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(name), Sound.Source.MASTER, 1f, 1f));
        } catch (RuntimeException e) {
            // a typo in a sound name should never break a click
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("blossomtoggles")
                || (args.length > 0 && args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission("blossomtoggles.admin")) {
                send(sender, "no-permission");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadEverything();
                send(sender, "reloaded");
            } else {
                send(sender, "admin-usage");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            new SettingsMenu(this, player).open();
            return true;
        }
        Toggle toggle = Toggle.find(args[0]);
        if (toggle == null || !isEnabled(toggle)) {
            send(player, "unknown-toggle", "{toggle}", args[0]);
            return true;
        }
        flip(player, toggle);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) {
            return out;
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("settings")) {
            for (Toggle toggle : Toggle.values()) {
                if (isEnabled(toggle)) {
                    options.add(toggle.id);
                }
            }
        }
        if (sender.hasPermission("blossomtoggles.admin")) {
            options.add("reload");
        }
        for (String option : options) {
            if (option.startsWith(typed)) {
                out.add(option);
            }
        }
        return out;
    }
}
