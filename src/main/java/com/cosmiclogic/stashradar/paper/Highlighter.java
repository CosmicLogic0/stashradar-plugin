package com.cosmiclogic.stashradar.paper;

import com.cosmiclogic.logiclib.paper.LogicParticles;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Draws particle outlines around the found containers, for the searching player only (LogicLib per-player particles). */
public final class Highlighter {

    private static final long PERIOD_TICKS = 10L;

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<UUID, BukkitTask>();

    public Highlighter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(final Player player, List<ContainerSearch.Hit> hits, int maxHighlights, int seconds, final Color color) {
        stop(player.getUniqueId());
        final List<ContainerSearch.Hit> shown = new ArrayList<ContainerSearch.Hit>(hits.subList(0, Math.min(hits.size(), maxHighlights)));
        if (shown.isEmpty()) {
            return;
        }
        final long runs = Math.max(1, seconds * 20L / PERIOD_TICKS);
        final UUID id = player.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            private long count;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline() || count++ >= runs) {
                    cancel();
                    active.remove(id);
                    return;
                }
                for (ContainerSearch.Hit hit : shown) {
                    if (hit.world.equals(p.getWorld())) {
                        LogicParticles.box(p, hit.x, hit.y, hit.z, hit.x + 1, hit.y + 1, hit.z + 1, color);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, PERIOD_TICKS);
        active.put(id, task);
    }

    public void stop(UUID id) {
        BukkitTask old = active.remove(id);
        if (old != null) {
            old.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : active.values()) {
            task.cancel();
        }
        active.clear();
    }

    static Color parseColor(String hex, Color fallback) {
        try {
            String h = hex.trim();
            if (h.startsWith("#")) {
                h = h.substring(1);
            }
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return fallback;
        }
    }
}
