package com.blossomsmp.toggles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Colours (&a and &#F4A9C0) and short money (3.9K, 1.2M). */
public final class Text {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();

    private Text() {
    }

    /** Coloured text that is never italic unless you write &o. */
    public static Component c(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> c(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(c(line));
        }
        return out;
    }

    public static String fill(String text, String... replacements) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return out;
    }

    /** 950 -> "950", 3900 -> "3.9K", 1250000 -> "1.25M", 2000000000 -> "2B" */
    public static String shortMoney(double value) {
        double v = Math.max(0, value);
        if (v >= 1_000_000_000_000d) {
            return trim(v / 1_000_000_000_000d) + "T";
        }
        if (v >= 1_000_000_000d) {
            return trim(v / 1_000_000_000d) + "B";
        }
        if (v >= 1_000_000d) {
            return trim(v / 1_000_000d) + "M";
        }
        if (v >= 1_000d) {
            return trim(v / 1_000d) + "K";
        }
        return String.valueOf((long) Math.floor(v));
    }

    /** 3.94 -> "3.9", 1.25 -> "1.25", 3.0 -> "3". Always rounds down, so nobody looks richer than they are. */
    private static String trim(double x) {
        double floored = Math.floor(x * 100.0) / 100.0;
        String s = x >= 100 ? String.format(Locale.US, "%.0f", Math.floor(x))
                : x >= 10 ? String.format(Locale.US, "%.1f", Math.floor(x * 10.0) / 10.0)
                : String.format(Locale.US, "%.2f", floored);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
