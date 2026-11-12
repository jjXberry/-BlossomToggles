package com.blossomsmp.toggles;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The /settings menu: the social toggles in the top half, world and money toggles in the bottom half.
 * Each toggle is an icon with an ON/OFF switch underneath; clicking either one flips it.
 */
public final class SettingsMenu implements InventoryHolder {

    private static final Toggle[] TOP = {Toggle.PUBLIC_CHAT, Toggle.PRIVATE_MESSAGES, Toggle.MENTION_SOUND,
            Toggle.TPA, Toggle.TPAHERE, Toggle.DEATH_MESSAGES};
    private static final Toggle[] BOTTOM = {Toggle.MOB_SPAWNS, Toggle.PHANTOMS, Toggle.NIGHT_VISION,
            Toggle.SEE_MONEY, Toggle.SHOW_MONEY};
    /** Slots for 1 to 7 things spread over a row (add the row's first slot). */
    private static final int[][] SPREAD = {
            {}, {4}, {3, 5}, {2, 4, 6}, {1, 3, 5, 7}, {2, 3, 4, 5, 6}, {1, 2, 3, 5, 6, 7}, {1, 2, 3, 4, 5, 6, 7}
    };
    private static final int HEAD = 4;
    private static final int RESET = 45;
    private static final int CLOSE = 49;

    private final BlossomToggles plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Toggle> slots = new HashMap<>();

    public SettingsMenu(BlossomToggles plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, Text.c(plugin.raw("menu-title")));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        draw();
        viewer.openInventory(inventory);
    }

    /** A texture from the Blossom resource pack, only when the server sends it (otherwise players see "missing"). */
    private ItemStack model(ItemStack item, String model) {
        if (!plugin.customIcons()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(model);
        if (meta != null && key != null) {
            meta.setItemModel(key);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.c(name));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(Text.c(lore));
        }
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String path, Material fallback) {
        String name = plugin.getConfig().getString(path);
        Material found = name == null ? null : Material.matchMaterial(name);
        return found == null || !found.isItem() ? fallback : found;
    }

    private void draw() {
        inventory.clear();
        slots.clear();
        ItemStack border = item(material("menu.border", Material.PINK_STAINED_GLASS_PANE), " ", List.of(), false);
        ItemStack filler = item(material("menu.filler", Material.GRAY_STAINED_GLASS_PANE), " ", List.of(), false);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, i < 9 || i >= 45 ? border : filler);
        }

        ItemStack head = item(Material.PLAYER_HEAD, Text.fill(plugin.raw("menu-head-name"), "{player}", viewer.getName()),
                plugin.rawList("menu-head-lore"), false);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(viewer);
            head.setItemMeta(skull);
        }
        inventory.setItem(HEAD, head);

        row(TOP, 9);
        row(BOTTOM, 27);

        inventory.setItem(RESET, item(Material.WATER_BUCKET, plugin.raw("menu-reset-name"), plugin.rawList("menu-reset-lore"), false));
        inventory.setItem(CLOSE, model(item(Material.BARRIER, plugin.raw("menu-close"), List.of(), false), "blossom:icon_close"));
    }

    /** One row of icons (at rowStart) with their switches on the row below. */
    private void row(Toggle[] toggles, int rowStart) {
        List<Toggle> shown = new ArrayList<>();
        for (Toggle toggle : toggles) {
            if (plugin.isEnabled(toggle)) {
                shown.add(toggle);
            }
        }
        int[] positions = SPREAD[Math.min(7, shown.size())];
        for (int i = 0; i < positions.length; i++) {
            Toggle toggle = shown.get(i);
            boolean on = plugin.store().get(viewer.getUniqueId(), toggle);
            String state = plugin.raw(on ? "state-on" : "state-off");

            List<String> lore = new ArrayList<>();
            for (String line : plugin.rawList("toggles." + toggle.id + ".description")) {
                lore.add(line);
            }
            lore.add("");
            lore.add(Text.fill(plugin.raw("menu-status"), "{state}", state));
            lore.add(plugin.raw("menu-click"));
            String name = plugin.raw("toggles." + toggle.id + ".name");

            int iconSlot = rowStart + positions[i];
            int switchSlot = iconSlot + 9;
            inventory.setItem(iconSlot, item(material("toggles." + toggle.id + ".icon", toggle.builtInIcon),
                    (on ? "&a" : "&c") + name, lore, on));
            inventory.setItem(switchSlot, model(item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    Text.fill(plugin.raw(on ? "switch-on" : "switch-off"), "{name}", name), lore, false),
                    on ? "blossom:icon_switch_on" : "blossom:icon_switch_off"));
            slots.put(iconSlot, toggle);
            slots.put(switchSlot, toggle);
        }
    }

    public void click(int slot) {
        if (slot == CLOSE) {
            plugin.sound(viewer, "click");
            Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
            return;
        }
        if (slot == RESET) {
            plugin.store().reset(viewer.getUniqueId());
            plugin.applyAll(viewer);
            plugin.send(viewer, "reset-done");
            plugin.sound(viewer, "toggle-off");
            draw();
            return;
        }
        Toggle toggle = slots.get(slot);
        if (toggle == null) {
            return;
        }
        plugin.flip(viewer, toggle);
        draw();
    }
}
