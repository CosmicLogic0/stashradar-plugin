package com.cosmiclogic.stashradar.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Item tags for /find #logs (Minecraft 1.13+). Looked up by reflection: the plugin is compiled against the
 * 1.13.2 API and also runs on 1.12.2, which has no tags (there {@link #supported()} is false).
 */
final class Tags {

    private static final Method GET_TAG = method(Bukkit.class, "getTag", String.class, NamespacedKey.class, Class.class);
    private static final Method GET_TAGS = method(Bukkit.class, "getTags", String.class, Class.class);
    private static final Class<?> TAG = find("org.bukkit.Tag");

    private Tags() {
    }

    static boolean supported() {
        return GET_TAG != null && TAG != null;
    }

    /** "#logs" or "#minecraft:logs" -> the matcher, or null if there's no such item tag. */
    static ContainerSearch.Matcher matcher(String typed) {
        if (!supported()) {
            return null;
        }
        try {
            String id = typed.startsWith("#") ? typed.substring(1) : typed;
            id = id.toLowerCase(Locale.ROOT);
            String ns = "minecraft";
            int colon = id.indexOf(':');
            if (colon >= 0) {
                ns = id.substring(0, colon);
                id = id.substring(colon + 1);
            }
            @SuppressWarnings("deprecation")
            NamespacedKey key = new NamespacedKey(ns, id);
            final Object tag = GET_TAG.invoke(null, "items", key, Material.class);
            if (tag == null || ((java.util.Collection<?>) TAG.getMethod("getValues").invoke(tag)).isEmpty()) {
                return null;   // some versions hand back an empty tag for a name that doesn't exist
            }
            final Method isTagged = TAG.getMethod("isTagged", org.bukkit.Keyed.class);
            return new ContainerSearch.Matcher() {
                @Override
                public boolean test(ItemStack stack) {
                    try {
                        return (Boolean) isTagged.invoke(tag, stack.getType());
                    } catch (Throwable t) {
                        return false;
                    }
                }
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /** "#minecraft:logs" shows as "#logs". */
    static String label(String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return t.startsWith("#minecraft:") ? "#" + t.substring(11) : t;
    }

    /** Tab completion: "#lo" -> "#logs", "#logs_that_burn", ... */
    static List<String> suggest(String typed, int limit) {
        if (GET_TAGS == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        String q = typed.toLowerCase(Locale.ROOT).replace("#", "").replace("minecraft:", "");
        try {
            for (Object tag : (Iterable<?>) GET_TAGS.invoke(null, "items", Material.class)) {
                NamespacedKey key = ((org.bukkit.Keyed) tag).getKey();
                String name = key.getNamespace().equals("minecraft") ? key.getKey() : key.toString();
                if (name.startsWith(q)) {
                    out.add("#" + name);
                }
            }
        } catch (Throwable ignored) {
            // no tags here
        }
        Collections.sort(out);
        return out.size() > limit ? new ArrayList<String>(out.subList(0, limit)) : out;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... types) {
        try {
            return owner.getMethod(name, types);
        } catch (Throwable t) {
            return null;
        }
    }
}
