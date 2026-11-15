package com.cosmiclogic.stashradar.paper;

import com.cosmiclogic.logiclib.paper.LogicConfigs;
import com.cosmiclogic.logiclib.paper.LogicText;
import com.cosmiclogic.logiclib.paper.LogicVersion;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stash Radar for Paper: /find <item> highlights nearby containers that have it. */
public final class StashRadarPlugin extends JavaPlugin implements Listener, TabCompleter {

    private final Map<UUID, Long> lastSearch = new HashMap<UUID, Long>();
    private Highlighter highlighter;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        config = LogicConfigs.load(this);
        highlighter = new Highlighter(this);
        PluginCommand find = getCommand("find");
        if (find != null) {
            find.setExecutor(this);
            find.setTabCompleter(this);
        }
        PluginCommand admin = getCommand("stashradar");
        if (admin != null) {
            admin.setExecutor(this);
            admin.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Stash Radar " + getDescription().getVersion() + " running on Minecraft " + LogicVersion.current()
                + " (" + ItemNames.all().size() + " items searchable)");
    }

    @Override
    public void onDisable() {
        if (highlighter != null) {
            highlighter.stopAll();
        }
        lastSearch.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        highlighter.stop(id);
        lastSearch.remove(id);
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("stashradar")) {
            return admin(sender, args);
        }
        return find(sender, args);
    }

    private boolean admin(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        if (sub.equals("reload") && sender.hasPermission("stashradar.admin")) {
            config = LogicConfigs.load(this);
            msg(sender, "reloaded", "&aConfig reloaded.");
            return true;
        }
        if (sub.equals("selftest") && sender instanceof ConsoleCommandSender) {
            SelfTest.run(this, options(true));
            return true;
        }
        LogicText.send(sender, prefix(), "&7Stash Radar " + getDescription().getVersion() + ". Use &f/find <item> [range]&7.");
        return true;
    }

    private boolean find(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            msg(sender, "players-only", "&cOnly players can use /find.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            msg(player, "usage", "&7Usage: &f/find <item> [range]");
            return true;
        }

        // Last argument may be a range number; everything before it is the item name ("oak log").
        int range = config.getInt("range", 16);
        int maxRange = Math.max(1, config.getInt("max-range", 32));
        List<String> words = new ArrayList<String>(Arrays.asList(args));
        if (words.size() > 1) {
            try {
                range = Integer.parseInt(words.get(words.size() - 1));
                words.remove(words.size() - 1);
            } catch (NumberFormatException ignored) {
                // no range given
            }
        }
        range = Math.max(1, Math.min(range, maxRange));
        String input = join(words, "_");

        Material item = ItemNames.find(input);
        if (item == null) {
            msg(player, "unknown-item", "&cNo item called &f{input}&c.", "{input}", input);
            List<String> close = ItemNames.suggest(input, 5);
            if (!close.isEmpty()) {
                msg(player, "did-you-mean", "&7Did you mean: &f{list}", "{list}", join(close, ", "));
            }
            return true;
        }

        if (!player.hasPermission("stashradar.bypass.cooldown")) {
            long cooldownMs = Math.max(0, config.getInt("cooldown-seconds", 5)) * 1000L;
            Long last = lastSearch.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (last != null && now - last < cooldownMs) {
                long left = (cooldownMs - (now - last) + 999) / 1000;
                msg(player, "cooldown", "&7Wait &f{seconds}s &7before searching again.", "{seconds}", String.valueOf(left));
                return true;
            }
            lastSearch.put(player.getUniqueId(), now);
        }

        Location eye = player.getEyeLocation();
        List<ContainerSearch.Hit> hits = ContainerSearch.search(eye, item, range,
                options(player.hasPermission("stashradar.bypass.locked")));
        String itemName = ItemNames.pretty(item);
        if (hits.isEmpty()) {
            msg(player, "none", "&7No &f{item} &7in containers within &f{range} &7blocks.",
                    "{item}", itemName, "{range}", String.valueOf(range));
            return true;
        }
        int total = 0;
        for (ContainerSearch.Hit hit : hits) {
            total += hit.count;
        }
        ContainerSearch.Hit nearest = hits.get(0);
        msg(player, "found", "&aFound &f{count} {item} &ain &f{containers} &acontainer(s). Nearest: &f{distance} &ablocks {direction}.",
                "{count}", String.valueOf(total), "{item}", itemName, "{containers}", String.valueOf(hits.size()),
                "{distance}", String.valueOf(Math.round(nearest.distance)),
                "{direction}", direction(eye, nearest));
        Color color = Highlighter.parseColor(config.getString("highlight-color", "#3CFF7A"), Color.LIME);
        highlighter.show(player, hits, Math.max(1, config.getInt("max-highlights", 24)),
                Math.max(1, config.getInt("highlight-seconds", 12)), color);
        return true;
    }

    ContainerSearch.Options options(boolean includeLocked) {
        List<String> containers = config.getStringList("containers");
        if (containers.isEmpty()) {
            containers = Arrays.asList("CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX");
        }
        return new ContainerSearch.Options(containers, config.getBoolean("search-inside-shulker-items", true), includeLocked);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("stashradar")) {
            if (args.length == 1) {
                List<String> out = new ArrayList<String>();
                for (String s : new String[]{"reload"}) {
                    if (s.startsWith(args[0].toLowerCase())) {
                        out.add(s);
                    }
                }
                return out;
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return ItemNames.suggest(args[0], 40);
        }
        if (args.length == 2 && ItemNames.find(args[0]) != null) {
            int max = Math.max(1, config.getInt("max-range", 32));
            List<String> ranges = new ArrayList<String>();
            for (int r : new int[]{8, 16, 24, 32, 48, 64}) {
                if (r <= max && String.valueOf(r).startsWith(args[1])) {
                    ranges.add(String.valueOf(r));
                }
            }
            return ranges;
        }
        return Collections.emptyList();
    }

    // ---------------------------------------------------------------- helpers

    /** "north-east", "below", ... from the player to the container. */
    static String direction(Location from, ContainerSearch.Hit hit) {
        double dx = hit.x + 0.5 - from.getX();
        double dy = hit.y + 0.5 - from.getY();
        double dz = hit.z + 0.5 - from.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.5) {
            return dy > 0 ? "above you" : "below you";
        }
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        // Minecraft: +z is south, +x is east. atan2 gives 0 for south when using (-dx, dz).
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        String dir = names[index];
        if (dy > 3) {
            dir += ", up";
        } else if (dy < -3) {
            dir += ", down";
        }
        return dir;
    }

    private String prefix() {
        return config.getString("messages.prefix", "&8[&aStash Radar&8]");
    }

    private void msg(CommandSender to, String key, String fallback, String... replacements) {
        String text = config.getString("messages." + key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        LogicText.send(to, prefix(), text);
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
