package com.blossomsmp.toggles;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Every setting a player can switch. The id is the name used in config.yml, messages.yml and /settings &lt;id&gt;.
 * Names, descriptions, icons, defaults and on/off are all set in the config files.
 */
public enum Toggle {
    // ---- social
    PUBLIC_CHAT("public-chat", true, Material.OAK_SIGN),
    PRIVATE_MESSAGES("private-messages", true, Material.WRITABLE_BOOK),
    MENTION_SOUND("mention-sound", true, Material.BELL),
    TPA("tpa", true, Material.ENDER_PEARL),
    TPAHERE("tpahere", true, Material.ENDER_EYE),
    DEATH_MESSAGES("death-messages", true, Material.SKELETON_SKULL),
    // ---- world
    MOB_SPAWNS("mob-spawns", true, Material.ZOMBIE_HEAD),
    PHANTOMS("phantoms", true, Material.PHANTOM_MEMBRANE),
    NIGHT_VISION("night-vision", false, Material.GOLDEN_CARROT),
    // ---- money
    SEE_MONEY("see-money", true, Material.GOLD_INGOT),
    SHOW_MONEY("show-money", true, Material.EMERALD);

    public final String id;
    public final boolean builtInDefault;
    public final Material builtInIcon;

    Toggle(String id, boolean builtInDefault, Material builtInIcon) {
        this.id = id;
        this.builtInDefault = builtInDefault;
        this.builtInIcon = builtInIcon;
    }

    /** "chat", "public-chat", "PUBLIC_CHAT" all work. */
    public static Toggle find(String text) {
        String t = text.toLowerCase(Locale.ROOT).replace('_', '-');
        for (Toggle toggle : values()) {
            if (toggle.id.equals(t)) {
                return toggle;
            }
        }
        return switch (t) {
            case "chat" -> PUBLIC_CHAT;
            case "msg", "msgs", "pm", "pms", "dm", "dms", "messages" -> PRIVATE_MESSAGES;
            case "mention", "mentions", "ping" -> MENTION_SOUND;
            case "tpask" -> TPA;
            case "deaths" -> DEATH_MESSAGES;
            case "mobs" -> MOB_SPAWNS;
            case "phantom" -> PHANTOMS;
            case "nv", "nightvision" -> NIGHT_VISION;
            case "money", "moneytags", "nametags" -> SEE_MONEY;
            case "mymoney", "privacy" -> SHOW_MONEY;
            default -> null;
        };
    }
}
