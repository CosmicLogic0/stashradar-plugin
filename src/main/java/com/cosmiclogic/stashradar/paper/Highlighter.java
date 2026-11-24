package com.cosmiclogic.stashradar.paper;

import com.cosmiclogic.logiclib.paper.LogicParticles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Draws particle outlines around the found containers, for the searching player only (LogicLib per-player
 * particles). Outlines farther than 28 blocks use LogicLib's long-distance particles, so the player's game
 * doesn't drop them. On 1.19.4+ far results also glow through walls ({@link Glow}); the glow is taken away
 * while the player is close, and removed for good when the highlight ends.
 */
public final class Highlighter {

    private static final long PERIOD_TICKS = 10L;
    /** Normal particles disappear past 32 blocks; switch a bit before that. */
    static final double FAR_PARTICLES = 28.0;
    /** Closer than this, the glow is hidden again (looks cleaner next to the chest). */
    static final double GLOW_MIN_DISTANCE = 5.5;

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, List<Entity>> glows = new HashMap<UUID, List<Entity>>();

    public Highlighter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(final Player player, List<ContainerSearch.Hit> hits, int maxHighlights, int seconds, final Color color, boolean glow) {
        stop(player.getUniqueId());
        final List<ContainerSearch.Hit> shown = new ArrayList<ContainerSearch.Hit>(hits.subList(0, Math.min(hits.size(), maxHighlights)));
        if (shown.isEmpty()) {
            return;
        }
        final UUID id = player.getUniqueId();
        final Entity[] ghosts = new Entity[shown.size()];
        final boolean[] visible = new boolean[shown.size()];
        if (glow && Glow.supported()) {
            List<Entity> list = new ArrayList<Entity>();
            for (int i = 0; i < shown.size(); i++) {
                ContainerSearch.Hit hit = shown.get(i);
                if (!hit.blockType.equals("ITEM_FRAME")) {
                    ghosts[i] = Glow.spawn(hit.world.getBlockAt(hit.x, hit.y, hit.z), color);
                    if (ghosts[i] != null) {
                        list.add(ghosts[i]);
                    }
                }
            }
            glows.put(id, list);
        }
        final long runs = Math.max(1, seconds * 20L / PERIOD_TICKS);
        BukkitTask task = new BukkitRunnable() {
            private long count;

            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline() || count++ >= runs) {
                    stop(id);
                    return;
                }
                Location eye = p.getEyeLocation();
                for (int i = 0; i < shown.size(); i++) {
                    ContainerSearch.Hit hit = shown.get(i);
                    if (!hit.world.equals(p.getWorld())) {
                        continue;
                    }
                    double dx = hit.x + 0.5 - eye.getX(), dy = hit.y + 0.5 - eye.getY(), dz = hit.z + 0.5 - eye.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    LogicParticles.box(p, hit.x, hit.y, hit.z, hit.x + 1, hit.y + 1, hit.z + 1, color,
                            dist > FAR_PARTICLES ? LogicParticles.FAR : LogicParticles.NEAR);
                    if (ghosts[i] != null) {
                        boolean wanted = dist > GLOW_MIN_DISTANCE;
                        if (wanted != visible[i]) {
                            if (wanted) {
                                Glow.show(plugin, p, ghosts[i]);
                            } else {
                                Glow.hide(plugin, p, ghosts[i]);
                            }
                            visible[i] = wanted;
                        }
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
        List<Entity> list = glows.remove(id);
        if (list != null) {
            for (Entity e : list) {
                e.remove();
            }
        }
    }

    public void stopAll() {
        for (UUID id : new ArrayList<UUID>(active.keySet())) {
            stop(id);
        }
        for (UUID id : new ArrayList<UUID>(glows.keySet())) {
            stop(id);
        }
    }

    /** How many glow entities exist right now (checked by the self-test after a search ends). */
    int glowCount() {
        int n = 0;
        for (List<Entity> list : glows.values()) {
            n += list.size();
        }
        return n;
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
