package com.cosmiclogic.stashradar.paper;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Turns what the player typed ("diamond", "minecraft:oak_log", "Oak Log") into a Material, and suggests names. */
public final class ItemNames {

    private static List<String> names;

    private ItemNames() {
    }

    /** Every item name in lowercase ("diamond", "oak_log"), without legacy names. Cached. */
    public static synchronized List<String> all() {
        if (names == null) {
            List<String> list = new ArrayList<String>();
            for (Material material : Material.values()) {
                if (isUsable(material)) {
                    list.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
            Collections.sort(list);
            names = list;
        }
        return names;
    }

    private static boolean isUsable(Material material) {
        String name = material.name();
        if (name.startsWith("LEGACY_") || name.equals("AIR") || name.endsWith("_AIR")) {
            return false;
        }
        try {
            if (material.isLegacy()) {
                return false;
            }
        } catch (Throwable ignored) {
            // 1.12.2 has no legacy materials
        }
        try {
            return material.isItem();
        } catch (Throwable ignored) {
            return true;   // 1.12.2 has no isItem()
        }
    }

    /** Normalised form: lowercase, namespace removed, spaces/dashes as underscores. */
    public static String normalise(String input) {
        String s = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return s.replace(' ', '_').replace('-', '_');
    }

    /** The exact item, or null. */
    public static Material find(String input) {
        String key = normalise(input);
        if (key.isEmpty()) {
            return null;
        }
        if (all().contains(key)) {
            Material m = Material.getMaterial(key.toUpperCase(Locale.ROOT));
            if (m != null) {
                return m;
            }
        }
        // Only one item contains what was typed (e.g. "netherite_ingo" -> netherite_ingot): take it.
        List<String> close = suggest(key, 2);
        if (close.size() == 1) {
            return Material.getMaterial(close.get(0).toUpperCase(Locale.ROOT));
        }
        return null;
    }

    /** Names that start with, then contain, what was typed. */
    public static List<String> suggest(String input, int limit) {
        String key = normalise(input);
        List<String> starts = new ArrayList<String>();
        List<String> contains = new ArrayList<String>();
        for (String name : all()) {
            if (name.startsWith(key)) {
                starts.add(name);
            } else if (name.contains(key)) {
                contains.add(name);
            }
        }
        starts.addAll(contains);
        return starts.size() > limit ? new ArrayList<String>(starts.subList(0, limit)) : starts;
    }

    /** "oak_log" -> "Oak Log" */
    public static String pretty(Material material) {
        return prettyName(material.name());
    }

    /** "TRAPPED_CHEST" -> "Trapped Chest" */
    public static String prettyName(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
