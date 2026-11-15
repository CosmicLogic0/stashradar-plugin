# Stash Radar for Paper

**Where did I put my diamonds?** `/find diamond` outlines every nearby chest, barrel and shulker box that has it, with particles only you can see. No client mod needed.

- One jar for **Paper 1.12.2 → 26.3** (Java 8 bytecode, compiled against the 1.13.2 API, newer code is guarded).
- Needs **LogicLib for Paper** (`depend: [LogicLib]`), used for config, chat text, version checks and per-player particles.
- The Forge/NeoForge mod is in [stash-radar](https://github.com/CosmicLogic0/stash-radar).

## Commands and permissions
| Command | Permission | Default |
|---|---|---|
| `/find <item> [range]` (aliases `/stashfind`, `/sfind`) | `stashradar.find` | everyone |
| no cooldown | `stashradar.bypass.cooldown` | op |
| also search locked containers | `stashradar.bypass.locked` | op |
| `/stashradar reload` | `stashradar.admin` | op |

Item names: `diamond`, `minecraft:diamond`, `oak log` and partial names (`netherite_ingo`) all work, with tab-complete.

## How it searches
Server side only: it reads the real inventories of containers in **already loaded** chunks within the range (it never loads chunks), skips locked containers, and looks one level into shulker box items. Nothing comes from the player's client. See `config.yml` for range, max range, cooldown, container types, highlight time/color and the max number of outlines.

## Building
Needs Java 17. Put LogicLib's jar in `libs/` (CI downloads it from LogicLib's `dist` branch), then `gradlew build`. The jar is in `build/libs/`.

## Testing (automatic, every push)
`.github/workflows/build.yml` starts a real Paper server for all 10 versions (1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3) with LogicLib + Stash Radar and runs `stashradar selftest` from the console. The self-test places real containers next to spawn, fills them (including a shulker box item inside a chest), runs the real search and checks the results. Any error, or a missing `self-test PASSED` line, fails the build.
