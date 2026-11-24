package com.cosmiclogic.stashradar.paper;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Lockable;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The actual search. Runs on the server thread and only looks at chunks that are already loaded,
 * reading the real container contents (nothing comes from the player's client).
 */
public final class ContainerSearch {

    /** One container that has the item. */
    public static final class Hit {
        public final World world;
        public final int x;
        public final int y;
        public final int z;
        public final String blockType;
        public final int count;
        public final double distance;

        Hit(World world, int x, int y, int z, String blockType, int count, double distance) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
            this.count = count;
            this.distance = distance;
        }
    }

    /** Settings for one search. */
    public static final class Options {
        public final List<String> containers;
        public final boolean insideShulkerItems;
        public final boolean includeLocked;
        public final boolean itemFrames;

        public Options(List<String> containers, boolean insideShulkerItems, boolean includeLocked, boolean itemFrames) {
            List<String> upper = new ArrayList<String>();
            for (String c : containers) {
                upper.add(c.trim().toUpperCase(Locale.ROOT));
            }
            this.containers = upper;
            this.insideShulkerItems = insideShulkerItems;
            this.includeLocked = includeLocked;
            this.itemFrames = itemFrames;
        }
    }

    /** What we look for: one item, a tag ("#logs") or a name ("sword"). */
    public interface Matcher {
        boolean test(ItemStack stack);
    }

    public static Matcher of(final Material item) {
        return new Matcher() {
            @Override
            public boolean test(ItemStack stack) {
                return stack.getType() == item;
            }
        };
    }

    /** Items whose shown name (a custom name too) or id contains the text, ignoring case and colors. */
    public static Matcher named(String text) {
        final String q = text.trim().toLowerCase(Locale.ROOT);
        return new Matcher() {
            @Override
            public boolean test(ItemStack stack) {
                if (q.isEmpty()) {
                    return false;
                }
                if (ItemNames.pretty(stack.getType()).toLowerCase(Locale.ROOT).contains(q)
                        || stack.getType().name().toLowerCase(Locale.ROOT).contains(q.replace(' ', '_'))) {
                    return true;
                }
                try {
                    if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                        return org.bukkit.ChatColor.stripColor(stack.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT).contains(q);
                    }
                } catch (Throwable ignored) {
                    // odd item data
                }
                return false;
            }
        };
    }

    /** How many matching items are in a player's own inventory-like container (the ender chest). */
    public static int count(Inventory inventory, Matcher match, boolean insideShulkerItems) {
        return inventory == null ? 0 : count(inventory.getContents(), match, insideShulkerItems, 0);
    }

    private ContainerSearch() {
    }

    /** All containers (and item frames) within {@code range} blocks of {@code center} with matching items, nearest first. */
    public static List<Hit> search(Location center, Matcher item, int range, Options options) {
        List<Hit> hits = new ArrayList<Hit>();
        World world = center.getWorld();
        if (world == null || item == null || range < 1) {
            return hits;
        }
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();
        double rangeSq = (double) range * range;
        int minChunkX = floor(cx - range) >> 4;
        int maxChunkX = floor(cx + range) >> 4;
        int minChunkZ = floor(cz - range) >> 4;
        int maxChunkZ = floor(cz + range) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;   // never load chunks just to search them
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                BlockState[] states;
                try {
                    states = chunk.getTileEntities();
                } catch (Throwable t) {
                    continue;
                }
                for (BlockState state : states) {
                    double dx = state.getX() + 0.5 - cx;
                    double dy = state.getY() + 0.5 - cy;
                    double dz = state.getZ() + 0.5 - cz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > rangeSq) {
                        continue;
                    }
                    String type = state.getType().name();
                    if (!isSearched(type, options.containers)) {
                        continue;
                    }
                    if (!options.includeLocked && isLocked(state)) {
                        continue;
                    }
                    Inventory inventory = inventoryOf(state);
                    if (inventory == null) {
                        continue;
                    }
                    int count = count(inventory.getContents(), item, options.insideShulkerItems, 0);
                    if (count > 0) {
                        hits.add(new Hit(world, state.getX(), state.getY(), state.getZ(), type, count, Math.sqrt(distSq)));
                    }
                }
            }
        }
        if (options.itemFrames) {
            for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
                if (!(entity instanceof ItemFrame) || !entity.isValid()) {
                    continue;
                }
                Location at = entity.getLocation();
                double dx = at.getX() - cx, dy = at.getY() - cy, dz = at.getZ() - cz;
                double distSq = dx * dx + dy * dy + dz * dz;
                ItemStack shown = ((ItemFrame) entity).getItem();
                int count = shown == null ? 0 : count(new ItemStack[]{shown}, item, options.insideShulkerItems, 0);
                if (count > 0 && distSq <= rangeSq) {
                    hits.add(new Hit(world, at.getBlockX(), at.getBlockY(), at.getBlockZ(), "ITEM_FRAME", count, Math.sqrt(distSq)));
                }
            }
        }
        Collections.sort(hits, new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Double.compare(a.distance, b.distance);
            }
        });
        return hits;
    }

    static boolean isSearched(String blockType, List<String> containers) {
        for (String c : containers) {
            if (c.equals(blockType)) {
                return true;
            }
            if (c.equals("SHULKER_BOX") && blockType.endsWith("SHULKER_BOX")) {
                return true;
            }
            if (c.equals("COPPER_CHEST") && blockType.endsWith("COPPER_CHEST")) {
                return true;   // every oxidation stage and waxed kind (Minecraft 1.21.9+)
            }
        }
        return false;
    }

    private static boolean isLocked(BlockState state) {
        try {
            return state instanceof Lockable && ((Lockable) state).isLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    /** For a double chest: only this half, so the two halves aren't counted twice. */
    private static Inventory inventoryOf(BlockState state) {
        try {
            if (state instanceof Chest) {
                return ((Chest) state).getBlockInventory();
            }
            if (state instanceof InventoryHolder) {
                return ((InventoryHolder) state).getInventory();
            }
        } catch (Throwable ignored) {
            // A block that changed while we looked at it: skip it.
        }
        return null;
    }

    /** How many matching items are in these stacks, including inside shulker box items (one level deep). */
    static int count(ItemStack[] contents, Matcher item, boolean insideShulkerItems, int depth) {
        int total = 0;
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (item.test(stack)) {
                total += stack.getAmount();
            }
            if (insideShulkerItems && depth == 0 && stack.getType().name().endsWith("SHULKER_BOX")) {
                total += countInsideShulkerItem(stack, item) * stack.getAmount();
            }
        }
        return total;
    }

    private static int countInsideShulkerItem(ItemStack stack, Matcher item) {
        try {
            if (!stack.hasItemMeta()) {
                return 0;
            }
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof BlockStateMeta)) {
                return 0;
            }
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            if (!blockMeta.hasBlockState()) {
                return 0;
            }
            BlockState inner = blockMeta.getBlockState();
            if (inner instanceof ShulkerBox) {
                return count(((ShulkerBox) inner).getInventory().getContents(), item, true, 1);
            }
        } catch (Throwable ignored) {
            // Odd item data: just don't look inside.
        }
        return 0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
