# Random Drops

Break a block, get something completely different.

Random Drops turns every block in the world into a mystery box. Each block type is
secretly mapped to its own random item, and that mapping is **tied to the world seed** —
so every new world is a completely different loot randomizer to figure out. Drops are
filtered to items you can actually obtain in survival, and on rare breaks a block will
spill the full contents of a real structure chest (end cities, bastions, ancient cities,
shipwrecks, villages and more).

A Fabric mod by **Spiro**. Released under **CC0-1.0** — do whatever you want with it.

## How it works

- Breaking any block cancels its normal drop and gives the randomized one instead.
- The drop is **deterministic per block type within a world** (same block always gives
  the same item in that world), but **unique per world** because the world seed is mixed
  into the RNG.
- Creative-only items (barriers, light, bedrock, command blocks, spawn eggs, etc.) are
  filtered out so you never roll something un-obtainable.
- A small per-break chance (`CHEST_LOOT_CHANCE`, default 3%) drops a full structure
  chest's loot instead of the single item. When that happens you get **only** the chest
  loot.

## Versions

This repo holds one buildable Fabric project per supported Minecraft version:

| Folder | Minecraft | Java |
|--------|-----------|------|
| `1.20.1/` | 1.20.1 | 17 |
| `1.21.1/` | 1.21.1 | 21 |
| `1.21.5/` | 1.21.5 | 21 |
| `26.1.2/` | 26.1.2 | 25 |

The `1.21.x` and `26.1.2` builds share identical source (modern registry-based loot API).
The `1.20.1` build uses a tweaked variant because pre-1.21 Minecraft addresses loot tables
by `ResourceLocation` via the server's `LootDataManager`, and trial chambers don't exist
in 1.20.1.

## Building

From inside a version folder:

```
./gradlew build
```

The finished jar lands in `build/libs/`. Requires **Fabric API** at runtime.

## Tweaking

All the knobs are constants at the top of `RandomDrops.java`:

- `CHEST_LOOT_CHANCE` — how often a break drops chest loot.
- `CREATIVE_ONLY` — item IDs to keep out of the drop pool.
- `STRUCTURE_CHESTS` — which structure loot tables can drop.
