# Stash Radar for Paper changelog

## 1.1.0

### New
- **Search by name:** `/find name sword` finds every item with that text in its name, including renamed items and boxes.
- **Search by tag:** `/find #logs` finds a whole group of items, with tab-complete (Minecraft 1.13+).
- **Glow through walls** (Minecraft 1.19.4+): far-away results also glow through walls, in the highlight color. Only the searching player sees it; it's never saved and goes away when you get close.
- **Item frames** and the player's own **ender chest** are searched too.
- **Copper chests** (Minecraft 1.21.9+) are searched by default. Older config.yml files get them added if they search chests.
- `/find help` and `/stashradar help`.

### Improved
- The chat answer lists the 3 nearest containers with coordinates and distance.
- Outlines farther than 28 blocks use LogicLib's long-distance particles, so they don't disappear past 32 blocks.
- If a player asks for more than `max-range`, they're told which range was used. "Nothing found" suggests a bigger range.
- New messages can be changed in config.yml like the others.

### Fixed
- Asking for a range above the server's limit was cut down silently.

### Needs
- **LogicLib Plugin 0.2.0 or newer.** With an older one, Stash Radar says so in the console and turns itself off.

## 0.1.0
- First version: `/find <item> [range]` with per-player particle outlines, item-name tab-complete and "did you mean".
- Searches chests, trapped chests, barrels and shulker boxes (configurable), including shulker box items inside them.
- Configurable range, max range, cooldown, highlight time/color and outline limit. Locked containers are skipped.
- Paper 1.12.2 → 26.3 in one jar.
