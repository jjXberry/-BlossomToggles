package com.blossomsmp.toggles;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Every player's choices, in plugins/BlossomToggles/data.yml. Only choices that differ from the
 * default are stored, so changing a default in config.yml changes it for everyone who never picked.
 * Saving happens in the background, written to a .tmp file and swapped in, so a crash can't corrupt it.
 */
public final class ToggleStore {

    private final BlossomToggles plugin;
    private final File file;
    private final Map<UUID, EnumMap<Toggle, Boolean>> data = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BlossomToggles-save");
        t.setDaemon(true);
        return t;
    });
    private boolean loadFailed;

    public ToggleStore(BlossomToggles plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        data.clear();
        if (!file.exists()) {
            importFromBlossomSettings();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            loadFailed = true;
            plugin.getLogger().log(Level.SEVERE, "data.yml could not be read, so players' choices are NOT saved "
                    + "until it is fixed (the file is left untouched).", e);
            return;
        }
        for (String key : yaml.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            EnumMap<Toggle, Boolean> map = new EnumMap<>(Toggle.class);
            for (Toggle toggle : Toggle.values()) {
                if (section.contains(toggle.id)) {
                    map.put(toggle, section.getBoolean(toggle.id));
                }
            }
            if (!map.isEmpty()) {
                data.put(id, map);
            }
        }
    }

    /** First start: keep the choices players made in the old BlossomSettings menu. */
    private void importFromBlossomSettings() {
        File old = new File(plugin.getDataFolder().getParentFile(), "BlossomSettings/data.yml");
        if (!old.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(old);
        Map<String, Toggle> names = Map.of(
                "MOB_SPAWNS", Toggle.MOB_SPAWNS,
                "PHANTOM_SPAWNING", Toggle.PHANTOMS,
                "NIGHT_VISION", Toggle.NIGHT_VISION,
                "MONEY_NAMETAGS", Toggle.SEE_MONEY);
        int players = 0;
        for (String key : yaml.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            EnumMap<Toggle, Boolean> map = new EnumMap<>(Toggle.class);
            for (Map.Entry<String, Toggle> entry : names.entrySet()) {
                if (section.contains(entry.getKey())) {
                    map.put(entry.getValue(), section.getBoolean(entry.getKey()));
                }
            }
            if (!map.isEmpty()) {
                data.put(id, map);
                players++;
            }
        }
        if (players > 0) {
            plugin.getLogger().info("Copied the old /settings choices of " + players + " players from BlossomSettings.");
            save();
        }
    }

    public boolean get(UUID id, Toggle toggle) {
        if (!plugin.isEnabled(toggle)) {
            return plugin.defaultOf(toggle);
        }
        EnumMap<Toggle, Boolean> map = data.get(id);
        Boolean value = map == null ? null : map.get(toggle);
        return value != null ? value : plugin.defaultOf(toggle);
    }

    public void set(UUID id, Toggle toggle, boolean value) {
        EnumMap<Toggle, Boolean> map = data.computeIfAbsent(id, k -> new EnumMap<>(Toggle.class));
        if (value == plugin.defaultOf(toggle)) {
            map.remove(toggle);
            if (map.isEmpty()) {
                data.remove(id);
            }
        } else {
            map.put(toggle, value);
        }
        save();
    }

    public void reset(UUID id) {
        if (data.remove(id) != null) {
            save();
        }
    }

    /** Save in the background (call from the main thread). */
    public void save() {
        if (loadFailed) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, EnumMap<Toggle, Boolean>> entry : data.entrySet()) {
            for (Map.Entry<Toggle, Boolean> t : entry.getValue().entrySet()) {
                yaml.set(entry.getKey() + "." + t.getKey().id.toLowerCase(Locale.ROOT), t.getValue());
            }
        }
        String text = yaml.saveToString();
        try {
            io.execute(() -> write(text));
        } catch (RuntimeException e) {
            write(text); // shutting down
        }
    }

    private void write(String text) {
        Path target = file.toPath();
        Path temp = target.resolveSibling("data.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    public void shutdown() {
        save();
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
