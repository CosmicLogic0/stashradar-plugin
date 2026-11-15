package com.cosmiclogic.stashradar.paper;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Console-only "/stashradar selftest", used by CI on every Minecraft version: builds a few real
 * containers next to spawn, runs the real search, checks the answers and removes everything again.
 */
final class SelfTest {

    private SelfTest() {
    }

    static void run(JavaPlugin plugin, ContainerSearch.Options options) {
        List<String> problems = new ArrayList<String>();
        List<Block> placed = new ArrayList<Block>();
        int checks = 0;
        try {
            World world = Bukkit.getWorlds().get(0);
            Location spawn = world.getSpawnLocation();
            int x = spawn.getBlockX() + 3;
            int z = spawn.getBlockZ() + 3;
            int y = Math.min(world.getMaxHeight() - 10, 200);
            world.getChunkAt(x >> 4, z >> 4).load(true);
            world.getChunkAt((x + 40) >> 4, z >> 4).load(true);

            // A chest with 7 diamonds and a purple shulker box item holding 3 emeralds.
            Block chest = place(world, x, y, z, Material.CHEST, placed);
            Inventory chestInv = inventory(chest);
            chestInv.addItem(new ItemStack(Material.DIAMOND, 7));
            chestInv.addItem(shulkerWith(Material.EMERALD, 3));

            // A barrel (1.14+) with 2 diamonds.
            Material barrelType = Material.getMaterial("BARREL");
            int expectedDiamondContainers = 1;
            int expectedDiamonds = 7;
            if (barrelType != null) {
                Block barrel = place(world, x + 2, y, z, barrelType, placed);
                inventory(barrel).addItem(new ItemStack(Material.DIAMOND, 2));
                expectedDiamondContainers++;
                expectedDiamonds += 2;
            }

            // A placed shulker box with 4 diamonds.
            Block box = place(world, x - 2, y, z, Material.getMaterial("PURPLE_SHULKER_BOX"), placed);
            inventory(box).addItem(new ItemStack(Material.DIAMOND, 4));
            expectedDiamondContainers++;
            expectedDiamonds += 4;

            // A chest far outside the search range: must NOT be found.
            Block far = place(world, x + 40, y, z, Material.CHEST, placed);
            inventory(far).addItem(new ItemStack(Material.DIAMOND, 64));

            Location from = new Location(world, x + 0.5, y + 0.5, z - 3.5);

            List<ContainerSearch.Hit> diamonds = ContainerSearch.search(from, Material.DIAMOND, 8, options);
            checks++;
            expect(problems, "diamond containers", expectedDiamondContainers, diamonds.size());
            checks++;
            expect(problems, "diamond count", expectedDiamonds, total(diamonds));

            List<ContainerSearch.Hit> emeralds = ContainerSearch.search(from, Material.EMERALD, 8, options);
            checks++;
            expect(problems, "emeralds inside a shulker box item", 3, total(emeralds));

            ContainerSearch.Options noNested = new ContainerSearch.Options(options.containers, false, options.includeLocked);
            checks++;
            expect(problems, "emeralds with nested search off", 0,
                    ContainerSearch.search(from, Material.EMERALD, 8, noNested).size());

            checks++;
            expect(problems, "gold (not stored anywhere)", 0,
                    ContainerSearch.search(from, Material.GOLD_INGOT, 8, options).size());

            ContainerSearch.Options chestsOnly = new ContainerSearch.Options(Arrays.asList("CHEST"), true, options.includeLocked);
            checks++;
            expect(problems, "diamond containers when only chests are searched", 1,
                    ContainerSearch.search(from, Material.DIAMOND, 8, chestsOnly).size());

            checks++;
            expect(problems, "far chest found with a big range", expectedDiamondContainers + 1,
                    ContainerSearch.search(from, Material.DIAMOND, 48, options).size());

            checks++;
            expectTrue(problems, "item name 'minecraft:diamond'", ItemNames.find("minecraft:diamond") == Material.DIAMOND);
            checks++;
            expectTrue(problems, "item name 'Diamond Block'", ItemNames.find("Diamond Block") == Material.DIAMOND_BLOCK);
            checks++;
            expectTrue(problems, "suggestions for 'diam'", ItemNames.suggest("diam", 10).contains("diamond"));
        } catch (Throwable t) {
            problems.add("exception " + t);
        } finally {
            for (Block block : placed) {
                try {
                    Inventory inv = inventory(block);
                    if (inv != null) {
                        inv.clear();
                    }
                    block.setType(Material.AIR);
                } catch (Throwable ignored) {
                    // best effort cleanup
                }
            }
        }
        if (problems.isEmpty()) {
            plugin.getLogger().info("Stash Radar self-test PASSED (" + checks + " checks)");
        } else {
            for (String p : problems) {
                plugin.getLogger().warning("Stash Radar self-test check FAILED: " + p);
            }
            plugin.getLogger().warning("Stash Radar self-test FAILED (" + problems.size() + " of " + checks + ")");
        }
    }

    private static Block place(World world, int x, int y, int z, Material type, List<Block> placed) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(type);
        placed.add(block);
        return block;
    }

    private static Inventory inventory(Block block) {
        BlockState state = block.getState();
        if (state instanceof org.bukkit.block.Chest) {
            return ((org.bukkit.block.Chest) state).getBlockInventory();
        }
        if (state instanceof InventoryHolder) {
            return ((InventoryHolder) state).getInventory();
        }
        return null;
    }

    private static ItemStack shulkerWith(Material content, int amount) {
        ItemStack item = new ItemStack(Material.getMaterial("PURPLE_SHULKER_BOX"));
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().addItem(new ItemStack(content, amount));
        meta.setBlockState(box);
        item.setItemMeta(meta);
        return item;
    }

    private static int total(List<ContainerSearch.Hit> hits) {
        int t = 0;
        for (ContainerSearch.Hit h : hits) {
            t += h.count;
        }
        return t;
    }

    private static void expect(List<String> problems, String what, int expected, int actual) {
        if (expected != actual) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void expectTrue(List<String> problems, String what, boolean ok) {
        if (!ok) {
            problems.add(what);
        }
    }
}
