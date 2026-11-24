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

    /** LogicLib Plugin features this plugin uses (LogicParticles.FAR, safe config loading) need 0.2.0. */
    static final String NEEDS_LOGICLIB = "0.2.0";
    /** config.yml layout this version writes. 2 = Stash Radar 1.1.0. */
    static final int CONFIG_VERSION = 2;

    @Override
    public void onEnable() {
        String have = logicLibVersion();
        if (!logicLibIsNewEnough(have)) {
            getLogger().severe("Stash Radar needs LogicLib Plugin " + NEEDS_LOGICLIB + " or newer, but this server has " + have
                    + ". Download the newest LogicLib Plugin from CurseForge, then restart. Stash Radar is turned off until then.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = LogicConfigs.load(this);
        upgradeConfig();
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
                + " (" + ItemNames.all().size() + " items searchable, tags " + (Tags.supported() ? "on" : "off")
                + ", glow through walls " + (Glow.supported() ? "on" : "off: needs 1.19.4+") + ")");
    }

    String logicLibVersion() {
        org.bukkit.plugin.Plugin ll = getServer().getPluginManager().getPlugin("LogicLib");
        return ll == null ? "none" : ll.getDescription().getVersion();
    }

    static boolean logicLibIsNewEnough(String version) {
        LogicVersion have = LogicVersion.parse(version);
        LogicVersion need = LogicVersion.parse(NEEDS_LOGICLIB);
        return have.compareTo(need) >= 0;
    }

    /**
     * Adds what 1.1.0 needs to an older config.yml without touching the owner's choices. "config-version" is
     * written here (not in the bundled config.yml) so an old file is recognised even after LogicLib fills in
     * the new default keys.
     */
    private void upgradeConfig() {
        if (upgrade(config)) {
            saveConfig();
            getLogger().info("config.yml updated to version " + CONFIG_VERSION + " (your settings were kept)");
        }
    }

    static boolean upgrade(org.bukkit.configuration.Configuration c) {
        if (c.getInt("config-version", 0) >= CONFIG_VERSION) {
            return false;
        }
        List<String> containers = new ArrayList<String>(c.getStringList("containers"));
        if (containers.contains("CHEST") && !containers.contains("COPPER_CHEST")) {
            containers.add("COPPER_CHEST");
            c.set("containers", containers);
        }
        c.set("config-version", CONFIG_VERSION);
        return true;
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

    Highlighter highlighter() {
        return highlighter;
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
            upgradeConfig();
            msg(sender, "reloaded", "&aConfig reloaded.");
            return true;
        }
        if (sub.equals("selftest") && sender instanceof ConsoleCommandSender) {
            // The bundled default container list, not the server's own (an owner may have changed it).
            List<String> defaults = getConfig().getDefaults() != null ? getConfig().getDefaults().getStringList("containers")
                    : Arrays.asList("CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX", "COPPER_CHEST");
            SelfTest.run(this, new ContainerSearch.Options(defaults, true, true, true));
            return true;
        }
        LogicText.send(sender, prefix(), "&7Stash Radar " + getDescription().getVersion() + " server commands:");
        LogicText.send(sender, " &f/find help  &7how players search");
        LogicText.send(sender, " &f/stashradar reload  &7reload config.yml (stashradar.admin)");
        LogicText.send(sender, " &f/stashradar help  &7this list");
        return true;
    }

    private void help(CommandSender to) {
        int range = config.getInt("range", 16);
        int max = Math.max(1, config.getInt("max-range", 32));
        int cooldown = config.getInt("cooldown-seconds", 5);
        LogicText.send(to, prefix(), "&7finds items in nearby chests, barrels and shulker boxes.");
        LogicText.send(to, " &f/find <item> [range]  &7e.g. /find diamond or /find oak log 24");
        if (Tags.supported()) {
            LogicText.send(to, " &f/find #<tag> [range]  &7a group of items, e.g. /find #logs");
        }
        LogicText.send(to, " &f/find name <text> [range]  &7names contain the text, e.g. /find name sword");
        LogicText.send(to, "&8Range " + range + " by default, up to " + max + " blocks. "
                + (cooldown > 0 ? "Wait " + cooldown + "s between searches. " : "")
                + "Results glow for " + config.getInt("highlight-seconds", 12) + "s.");
    }

    private boolean find(CommandSender sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            msg(sender, "players-only", "&cOnly players can use /find.");
            return true;
        }
        Player player = (Player) sender;

        // Last argument may be a range number; everything before it is the item name ("oak log").
        int maxRange = Math.max(1, config.getInt("max-range", 32));
        int requested = -1;
        List<String> words = new ArrayList<String>(Arrays.asList(args));
        if (words.size() > 1) {
            try {
                requested = Integer.parseInt(words.get(words.size() - 1));
                words.remove(words.size() - 1);
            } catch (NumberFormatException ignored) {
                // no range given
            }
        }
        int range = Math.max(1, Math.min(requested > 0 ? requested : config.getInt("range", 16), maxRange));

        ContainerSearch.Matcher match;
        String what;
        if (words.get(0).equalsIgnoreCase("name") && words.size() > 1) {
            String text = join(words.subList(1, words.size()), " ");
            match = ContainerSearch.named(text);
            what = "items named \"" + text + "\"";
        } else if (words.get(0).startsWith("#")) {
            if (!Tags.supported()) {
                msg(player, "no-tags", "&cItem tags need Minecraft 1.13 or newer.");
                return true;
            }
            match = Tags.matcher(words.get(0));
            if (match == null) {
                msg(player, "unknown-tag", "&cNo item tag called &f{input}&c.", "{input}", words.get(0));
                return true;
            }
            what = Tags.label(words.get(0));
        } else {
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
            match = ContainerSearch.of(item);
            what = ItemNames.pretty(item);
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

        if (requested > maxRange) {
            msg(player, "range-limited", "&7This server searches at most &f{range} &7blocks, so that's what I used.", "{range}", String.valueOf(range));
        }
        Location eye = player.getEyeLocation();
        boolean inside = config.getBoolean("search-inside-shulker-items", true);
        List<ContainerSearch.Hit> hits = ContainerSearch.search(eye, match, range,
                options(player.hasPermission("stashradar.bypass.locked")));
        int ender = config.getBoolean("search-ender-chest", true) ? ContainerSearch.count(player.getEnderChest(), match, inside) : 0;
        if (hits.isEmpty()) {
            highlighter.stop(player.getUniqueId());
            msg(player, "none", "&7No &f{item} &7in containers within &f{range} &7blocks.",
                    "{item}", what, "{range}", String.valueOf(range));
            if (range < maxRange) {
                msg(player, "try-bigger", "&8Try a bigger range (up to {max}).", "{max}", String.valueOf(maxRange));
            }
            if (ender > 0) {
                msg(player, "ender-only", "&dYou have {count} in your ender chest.", "{count}", String.valueOf(ender));
            }
            return true;
        }
        int total = 0;
        for (ContainerSearch.Hit hit : hits) {
            total += hit.count;
        }
        ContainerSearch.Hit nearest = hits.get(0);
        msg(player, "found", "&aFound &f{count} {item} &ain &f{containers} &acontainer(s). Nearest: &f{distance} &ablocks {direction}.",
                "{count}", String.valueOf(total), "{item}", what, "{containers}", String.valueOf(hits.size()),
                "{distance}", String.valueOf(Math.round(nearest.distance)),
                "{direction}", direction(eye, nearest));
        for (int i = 0; i < Math.min(3, hits.size()); i++) {
            ContainerSearch.Hit hit = hits.get(i);
            line(player, "listed", " &f{count} in {container} &7at {x}, {y}, {z} ({distance} blocks {direction})",
                    "{count}", String.valueOf(hit.count), "{container}", ItemNames.prettyName(hit.blockType),
                    "{x}", String.valueOf(hit.x), "{y}", String.valueOf(hit.y), "{z}", String.valueOf(hit.z),
                    "{distance}", String.valueOf(Math.round(hit.distance)), "{direction}", direction(eye, hit));
        }
        if (hits.size() > 3) {
            line(player, "more", " &7...and {more} more, all outlined.", "{more}", String.valueOf(hits.size() - 3));
        }
        if (ender > 0) {
            line(player, "ender", " &dAlso {count} in your ender chest.", "{count}", String.valueOf(ender));
        }
        Color color = Highlighter.parseColor(config.getString("highlight-color", "#3CFF7A"), Color.LIME);
        highlighter.show(player, hits, Math.max(1, config.getInt("max-highlights", 24)),
                Math.max(1, config.getInt("highlight-seconds", 12)), color, config.getBoolean("glow-through-walls", true));
        return true;
    }

    ContainerSearch.Options options(boolean includeLocked) {
        List<String> containers = config.getStringList("containers");
        if (containers.isEmpty()) {
            containers = Arrays.asList("CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX");
        }
        return new ContainerSearch.Options(containers, config.getBoolean("search-inside-shulker-items", true), includeLocked,
                config.getBoolean("search-item-frames", true));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("stashradar")) {
            if (args.length == 1) {
                List<String> out = new ArrayList<String>();
                for (String s : new String[]{"help", "reload"}) {
                    if (s.startsWith(args[0].toLowerCase())) {
                        out.add(s);
                    }
                }
                return out;
            }
            return Collections.emptyList();
        }
        if (args.length == 1) {
            if (args[0].startsWith("#")) {
                return Tags.suggest(args[0], 40);
            }
            List<String> out = new ArrayList<String>();
            for (String s : new String[]{"help", "name"}) {
                if (!args[0].isEmpty() && s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            out.addAll(ItemNames.suggest(args[0], 40));
            return out;
        }
        if (args.length == 2 && (ItemNames.find(args[0]) != null || (args[0].startsWith("#") && Tags.matcher(args[0]) != null))) {
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

    /** Like msg, without the [Stash Radar] prefix (for the lines under the answer). */
    private void line(CommandSender to, String key, String fallback, String... replacements) {
        String text = config.getString("messages." + key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        LogicText.send(to, text);
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
