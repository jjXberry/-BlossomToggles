package com.blossomsmp.toggles.petals;

import com.blossomsmp.toggles.BlossomToggles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /petalsshop           opens the shop
 * /petals               shows your balance
 * /petals check <player>, give|take|set <player> <amount>   (admins)
 */
public final class PetalsCommand implements CommandExecutor, TabCompleter {

    private static final TextColor PINK = TextColor.color(0xFF69B4);
    private static final String ADMIN = "blossomsettings.petals.admin";

    private final BlossomToggles plugin;
    private final PetalsShop shop;

    public PetalsCommand(BlossomToggles plugin, PetalsShop shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the shop.");
            return true;
        }
        shop.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
