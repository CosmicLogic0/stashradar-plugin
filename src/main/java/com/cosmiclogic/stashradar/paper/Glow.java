package com.cosmiclogic.stashradar.paper;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Glowing outline through walls for far-away results, seen only by the searching player: a block display
 * (Minecraft 1.19.4+) showing the container's own block, glowing in the highlight color, hidden from everyone
 * else before it is even added to the world, and never saved. On older servers {@link #supported()} is false
 * and only the particle outline is used.
 * <p>
 * Everything is looked up by reflection because the plugin is compiled against the 1.13.2 API so one jar runs
 * on every version.
 */
final class Glow {

    private static final Class<?> BLOCK_DISPLAY = find("org.bukkit.entity.BlockDisplay");
    private static final Method SET_VISIBLE_BY_DEFAULT = method(Entity.class, "setVisibleByDefault", boolean.class);
    private static final Method SHOW = method(Player.class, "showEntity", Plugin.class, Entity.class);
    private static final Method HIDE = method(Player.class, "hideEntity", Plugin.class, Entity.class);
    private static final Method SPAWN = spawnMethod();

    private Glow() {
    }

    /** True on servers with block displays and per-player entity visibility (Paper 1.19.4+). */
    static boolean supported() {
        return BLOCK_DISPLAY != null && SET_VISIBLE_BY_DEFAULT != null && SHOW != null && HIDE != null && SPAWN != null;
    }

    /** Spawns the hidden, glowing copy of a container block, or returns null if that isn't possible here. */
    static Entity spawn(Block block, final Color color) {
        if (!supported()) {
            return null;
        }
        try {
            final Object data = Block.class.getMethod("getBlockData").invoke(block);
            World world = block.getWorld();
            Location at = block.getLocation();
            Class<?> consumerType = SPAWN.getParameterTypes()[2];
            Object setup = Proxy.newProxyInstance(Glow.class.getClassLoader(), new Class<?>[]{consumerType}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                    if (m.getDeclaringClass() == Object.class) {
                        return m.getName().equals("equals") ? proxy == args[0] : m.getName().equals("hashCode") ? System.identityHashCode(proxy) : "StashRadarGlow";
                    }
                    Object e = args[0];
                    SET_VISIBLE_BY_DEFAULT.invoke(e, false);   // before it exists for anyone
                    call(Entity.class, e, "setPersistent", new Class<?>[]{boolean.class}, false);
                    call(BLOCK_DISPLAY, e, "setBlock", new Class<?>[]{find("org.bukkit.block.data.BlockData")}, data);
                    call(BLOCK_DISPLAY, e, "setGlowColorOverride", new Class<?>[]{Color.class}, color);
                    // A display sitting inside a block gets light level 0 there and drew as a black box. Shrink it a
                    // little so the real block hides it (only the glow outline shows) and light it fully anyway.
                    hideInside(e);
                    ((Entity) e).setGlowing(true);
                    return null;
                }
            });
            return (Entity) SPAWN.invoke(world, at, BLOCK_DISPLAY, setup);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Scale 0.96 around the block's center, full brightness. Reflection: JOML and Display exist from 1.19.4. */
    private static void hideInside(Object display) {
        try {
            Class<?> v = Class.forName("org.joml.Vector3f");
            Class<?> q = Class.forName("org.joml.Quaternionf");
            Class<?> tr = Class.forName("org.bukkit.util.Transformation");
            Object move = v.getConstructor(float.class, float.class, float.class).newInstance(0.02f, 0.02f, 0.02f);
            Object scale = v.getConstructor(float.class, float.class, float.class).newInstance(0.96f, 0.96f, 0.96f);
            Object transformation = tr.getConstructor(v, q, v, q).newInstance(move, q.getConstructor().newInstance(), scale,
                    q.getConstructor().newInstance());
            Class<?> displayType = Class.forName("org.bukkit.entity.Display");
            displayType.getMethod("setTransformation", tr).invoke(display, transformation);
            Class<?> brightness = Class.forName("org.bukkit.entity.Display$Brightness");
            displayType.getMethod("setBrightness", brightness).invoke(display,
                    brightness.getConstructor(int.class, int.class).newInstance(15, 15));
        } catch (Throwable ignored) {
            // older API: the glow still works
        }
    }

    /** Scale of the glow display (the self-test checks it). */
    static float scaleOf(Entity glow) {
        try {
            Class<?> displayType = Class.forName("org.bukkit.entity.Display");
            Object t = displayType.getMethod("getTransformation").invoke(glow);
            Object scale = t.getClass().getMethod("getScale").invoke(t);
            return (Float) scale.getClass().getField("x").get(scale);
        } catch (Throwable t) {
            return -1;
        }
    }

    static void show(Plugin plugin, Player player, Entity glow) {
        invoke(SHOW, player, plugin, glow);
    }

    static void hide(Plugin plugin, Player player, Entity glow) {
        invoke(HIDE, player, plugin, glow);
    }

    /** True if other players can't see this entity (checked by the self-test). */
    static boolean hiddenByDefault(Entity glow) {
        try {
            return !(Boolean) Entity.class.getMethod("isVisibleByDefault").invoke(glow);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void invoke(Method m, Object target, Object... args) {
        try {
            m.invoke(target, args);
        } catch (Throwable ignored) {
            // the player left or the entity is gone
        }
    }

    private static void call(Class<?> owner, Object target, String name, Class<?>[] types, Object arg) {
        try {
            owner.getMethod(name, types).invoke(target, arg);
        } catch (Throwable ignored) {
            // optional extra
        }
    }

    /** World.spawn(Location, Class, Consumer): java.util.function.Consumer on new servers, Bukkit's own before. */
    private static Method spawnMethod() {
        Method fallback = null;
        for (Method m : World.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals("spawn") && p.length == 3 && p[0] == Location.class && p[1] == Class.class && p[2].isInterface()) {
                if (p[2] == java.util.function.Consumer.class) {
                    return m;
                }
                fallback = m;
            }
        }
        return fallback;
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
