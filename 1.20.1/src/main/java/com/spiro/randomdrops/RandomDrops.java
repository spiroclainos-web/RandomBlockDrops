package com.spiro.randomdrops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * RandomDrops (1.20.1 build)
 *
 * Every time a player breaks a block, this mod drops ONLY a random item —
 * the block's normal drop is suppressed.
 *
 * The drop is DETERMINISTIC per block type WITHIN a world: the same kind of
 * block always gives the same random item in that world, so it feels like a
 * consistent "loot randomizer" rather than pure chaos every swing.
 *
 * The mapping is also UNIQUE PER WORLD: the world's seed is mixed into the RNG,
 * so every new world you create rolls a completely different set of drops.
 *
 * TWO extra rules layered on top:
 *   1. Only items you can actually get in survival are ever rolled. Creative-only
 *      junk (barrier, light, bedrock, command blocks, spawn eggs, etc.) is skipped
 *      so a block never maps to something useless you can't legitimately obtain.
 *   2. RARELY (see CHEST_LOOT_CHANCE) a break instead spits out the full contents
 *      of a random structure chest — end city, desert pyramid, bastion, ancient
 *      city, shipwreck, village... When that happens you get ONLY the chest loot
 *      and nothing else. The rest of the time you get the usual single mapped item.
 *
 * NOTE: This is the 1.20.1 version. Loot tables here are addressed by
 * ResourceLocation and fetched via the server's LootDataManager, which is the
 * pre-1.21 loot system. Trial chambers don't exist in 1.20.1, so they're absent
 * from the structure pool.
 */
public class RandomDrops implements ModInitializer {
	public static final String MOD_ID = "randomdrops";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Chance, per block break, that the block coughs up a structure chest's loot
	 * instead of its normal single mapped item. Kept decently rare so the usual
	 * one-item drop stays the norm. 0.03 = 3% (~1 in 33 blocks). Bump it up or
	 * down to taste.
	 */
	private static final double CHEST_LOOT_CHANCE = 0.03;

	/** RNG for the rare chest-loot roll. Per-break random, NOT tied to the seed. */
	private static final Random LOOT_RNG = new Random();

	/**
	 * Items that exist in the registry but can't be obtained legitimately in
	 * survival. Matched by their registry id path (e.g. "command_block"). All
	 * spawn eggs are filtered separately by the "_spawn_egg" suffix, so they
	 * don't need listing here.
	 */
	private static final Set<String> CREATIVE_ONLY = Set.of(
			"barrier",
			"light",
			"bedrock",
			"command_block",
			"chain_command_block",
			"repeating_command_block",
			"command_block_minecart",
			"structure_block",
			"structure_void",
			"jigsaw",
			"debug_stick",
			"knowledge_book",
			"spawner",
			"end_portal_frame",
			"reinforced_deepslate",
			"budding_amethyst",
			"petrified_oak_slab",
			"infested_stone",
			"infested_cobblestone",
			"infested_stone_bricks",
			"infested_mossy_stone_bricks",
			"infested_cracked_stone_bricks",
			"infested_chiseled_stone_bricks",
			"infested_deepslate"
	);

	/**
	 * The pool of structure chest loot tables a rare break can roll. Covers the
	 * chest loot from basically every generated structure in 1.20.1.
	 */
	private static final List<ResourceLocation> STRUCTURE_CHESTS = List.of(
			BuiltInLootTables.END_CITY_TREASURE,
			BuiltInLootTables.SIMPLE_DUNGEON,
			BuiltInLootTables.ABANDONED_MINESHAFT,
			BuiltInLootTables.NETHER_BRIDGE,
			BuiltInLootTables.STRONGHOLD_LIBRARY,
			BuiltInLootTables.STRONGHOLD_CROSSING,
			BuiltInLootTables.STRONGHOLD_CORRIDOR,
			BuiltInLootTables.DESERT_PYRAMID,
			BuiltInLootTables.JUNGLE_TEMPLE,
			BuiltInLootTables.IGLOO_CHEST,
			BuiltInLootTables.WOODLAND_MANSION,
			BuiltInLootTables.UNDERWATER_RUIN_SMALL,
			BuiltInLootTables.UNDERWATER_RUIN_BIG,
			BuiltInLootTables.BURIED_TREASURE,
			BuiltInLootTables.SHIPWRECK_MAP,
			BuiltInLootTables.SHIPWRECK_SUPPLY,
			BuiltInLootTables.SHIPWRECK_TREASURE,
			BuiltInLootTables.PILLAGER_OUTPOST,
			BuiltInLootTables.BASTION_TREASURE,
			BuiltInLootTables.BASTION_OTHER,
			BuiltInLootTables.BASTION_BRIDGE,
			BuiltInLootTables.BASTION_HOGLIN_STABLE,
			BuiltInLootTables.ANCIENT_CITY,
			BuiltInLootTables.ANCIENT_CITY_ICE_BOX,
			BuiltInLootTables.RUINED_PORTAL,
			BuiltInLootTables.VILLAGE_WEAPONSMITH,
			BuiltInLootTables.VILLAGE_TOOLSMITH,
			BuiltInLootTables.VILLAGE_ARMORER,
			BuiltInLootTables.VILLAGE_CARTOGRAPHER,
			BuiltInLootTables.VILLAGE_MASON,
			BuiltInLootTables.VILLAGE_SHEPHERD,
			BuiltInLootTables.VILLAGE_BUTCHER,
			BuiltInLootTables.VILLAGE_FLETCHER,
			BuiltInLootTables.VILLAGE_FISHER,
			BuiltInLootTables.VILLAGE_TANNERY,
			BuiltInLootTables.VILLAGE_TEMPLE,
			BuiltInLootTables.VILLAGE_DESERT_HOUSE,
			BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
			BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
			BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
			BuiltInLootTables.VILLAGE_SAVANNA_HOUSE
	);

	@Override
	public void onInitialize() {
		// BEFORE fires while the block is still in the world, so we can cancel the
		// vanilla break and take full control of what drops.
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			// On the client, let the normal prediction run — the server is in charge.
			if (world.isClientSide()) {
				return true;
			}

			ServerLevel serverLevel = (ServerLevel) world;

			// RARE: instead of the usual mapped item, dump a whole structure chest's
			// worth of loot. Rolled per break (not tied to the seed) so it's a genuine
			// surprise rather than "this block type always does it".
			if (LOOT_RNG.nextDouble() < CHEST_LOOT_CHANCE) {
				List<ItemStack> loot = rollStructureChest(serverLevel, pos);
				if (!loot.isEmpty()) {
					// Break with no normal drops, then pop ONLY the chest loot.
					world.destroyBlock(pos, false);
					for (ItemStack stack : loot) {
						Block.popResource(world, pos, stack);
					}
					return false;
				}
				// If the rolled table happened to come up empty, fall through to the
				// normal single-item drop so the break still gives something.
			}

			// Mix the world's seed together with the block's registry id so that:
			//   - same block type in the SAME world -> always the same item, but
			//   - a different world (different world seed) -> a different mapping.
			// That's why every new world you create now has its own unique drops.
			int blockId = BuiltInRegistries.BLOCK.getId(state.getBlock());
			long worldSeed = serverLevel.getSeed();
			Random random = new Random(worldSeed * 31L + blockId);

			int itemCount = BuiltInRegistries.ITEM.size();

			// Pick a random item, skipping AIR and anything you can't get in survival
			// so every break gives a legitimately obtainable item.
			Item drop;
			do {
				drop = BuiltInRegistries.ITEM.byId(random.nextInt(itemCount));
			} while (!isSurvivalObtainable(drop));

			// Remove the block WITHOUT its normal drops (dropBlock = false), keeping
			// the break particles and sound, then pop out only our random item.
			world.destroyBlock(pos, false);
			Block.popResource(world, pos, new ItemStack(drop));

			// Returning false cancels the vanilla break so it can't also drop the
			// block's usual item.
			return false;
		});

		LOGGER.info("RandomDrops loaded — every block now drops a random item (with a rare chance of full structure chest loot)!");
	}

	/** True only for items a player can legitimately obtain in survival. */
	private static boolean isSurvivalObtainable(Item item) {
		if (item == Items.AIR) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (path.endsWith("_spawn_egg")) {
			return false;
		}
		return !CREATIVE_ONLY.contains(path);
	}

	/**
	 * Rolls the loot of one random structure chest at the given position.
	 * Retries a few different tables if one comes up empty, so a rare proc
	 * rarely feels like a total dud. Returns an empty list only if every
	 * attempt was empty.
	 */
	private static List<ItemStack> rollStructureChest(ServerLevel level, net.minecraft.core.BlockPos pos) {
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.create(LootContextParamSets.CHEST);

		for (int attempt = 0; attempt < 6; attempt++) {
			ResourceLocation key = STRUCTURE_CHESTS.get(LOOT_RNG.nextInt(STRUCTURE_CHESTS.size()));
			LootTable table = level.getServer().getLootData().getLootTable(key);
			List<ItemStack> loot = table.getRandomItems(params);
			if (!loot.isEmpty()) {
				return loot;
			}
		}
		return List.of();
	}
}
