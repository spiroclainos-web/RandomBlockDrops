package com.spiro.randomdrops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
 * Every time a player breaks a block, this mod drops ONLY a random outcome —
 * the block's normal drop is suppressed.
 *
 * The outcome is DETERMINISTIC per block type WITHIN a world: the same kind of
 * block always gives the same thing in that world, so it feels like a consistent
 * "loot randomizer" rather than pure chaos every swing. The mapping is also
 * UNIQUE PER WORLD because the world seed is mixed into the RNG, so every new
 * world rolls a completely different set of drops.
 *
 * An "outcome" is usually a single item, but some block types are instead mapped
 * to a whole STRUCTURE CHEST loot table (end city, bastion, ancient city, etc.).
 * That mapping is decided by the seed exactly the way an item mapping is — there
 * is NO random chance. If the seed assigns a chest table to a block, that block
 * ALWAYS drops that chest's loot (and nothing else); the contents themselves roll
 * fresh each break, just like opening a real chest.
 *
 * Rules that keep it clean:
 *   - Only survival-obtainable items are ever rolled (no barriers, command blocks,
 *     spawn eggs, etc.).
 *   - A block NEVER drops its own item, so you can't set up an infinite
 *     break/replace duplication loop.
 *   - Blocks that naturally drop nothing even with the right tool (fire, etc.)
 *     drop nothing here too.
 *   - Multi-part blocks (doors, beds, tall plants) no longer drop themselves
 *     alongside the random outcome.
 *
 * NOTE: This is the 1.20.1 version. Loot tables here are addressed by
 * ResourceLocation and fetched via the server's LootDataManager (the pre-1.21
 * loot system). Trial chambers don't exist in 1.20.1, so they're absent from the
 * structure pool.
 */
public class RandomDrops implements ModInitializer {
	public static final String MOD_ID = "randomdrops";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Items that exist in the registry but can't be obtained legitimately in
	 * survival. Matched by their registry id path (e.g. "command_block"). All
	 * spawn eggs are filtered separately by the "_spawn_egg" suffix.
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
	 * Structure chest loot tables. The seed can map a block to any of these, in
	 * which case that block always drops that chest's loot.
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

			// Blocks that naturally drop nothing even with the right tool (fire,
			// portals, etc.) should still drop nothing. Letting vanilla handle the
			// break gives exactly that — no item.
			if (dropsNothing(serverLevel, state)) {
				return true;
			}

			Block brokenBlock = state.getBlock();

			// Mix the world seed with the block's registry id so the same block type
			// always maps to the same outcome in this world, but a different world
			// (different seed) maps differently.
			int blockId = BuiltInRegistries.BLOCK.getId(brokenBlock);
			Random random = new Random(serverLevel.getSeed() * 31L + blockId);

			int chestCount = STRUCTURE_CHESTS.size();
			int itemCount = BuiltInRegistries.ITEM.size();
			int total = itemCount + chestCount;

			// Pick the deterministic outcome. Chest loot tables live in the SAME
			// selection space as items, so being mapped to chest loot is just another
			// possible "drop" decided by the seed — never a random per-break chance.
			while (true) {
				int roll = random.nextInt(total);

				if (roll < chestCount) {
					// This block type is the one that drops this chest's loot. Always.
					List<ItemStack> loot = rollChest(serverLevel, pos, STRUCTURE_CHESTS.get(roll));
					breakWithoutDrops(world, pos, state);
					for (ItemStack stack : loot) {
						Block.popResource(world, pos, stack);
					}
					return false;
				}

				Item drop = BuiltInRegistries.ITEM.byId(roll - chestCount);
				if (!isSurvivalObtainable(drop)) {
					continue;
				}
				// Never drop the block's own item — that would let you break/replace
				// the same block forever for an infinite supply.
				if (drop == brokenBlock.asItem()) {
					continue;
				}

				breakWithoutDrops(world, pos, state);
				Block.popResource(world, pos, new ItemStack(drop));
				return false;
			}
		});

		LOGGER.info("RandomDrops loaded — every block now drops a seed-mapped random item or structure chest loot!");
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
	 * True if this block yields no loot at all even with the right tool (e.g. fire).
	 * We resolve the block's own loot table and check whether it's the empty table,
	 * which catches both "no loot table assigned" and "loot table resolves to empty".
	 */
	private static boolean dropsNothing(ServerLevel level, BlockState state) {
		ResourceLocation key = state.getBlock().getLootTable();
		return level.getServer().getLootData().getLootTable(key) == LootTable.EMPTY;
	}

	/**
	 * Clears the block (and any attached parts like door/bed halves and tall
	 * plants) WITHOUT any natural drops, keeping the break particles and sound.
	 * UPDATE_SUPPRESS_DROPS propagates through the neighbour updates, so the
	 * partner half can't sneak a drop in either.
	 */
	private static void breakWithoutDrops(Level world, BlockPos pos, BlockState state) {
		world.levelEvent(2001, pos, Block.getId(state));
		world.setBlock(pos, world.getFluidState(pos).createLegacyBlock(),
				Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
	}

	/**
	 * Rolls the loot of a structure chest at the given position. Retries a few
	 * times on the same table if it comes up empty so the mapped block rarely
	 * feels like a dud. Returns empty only if every roll was empty.
	 */
	private static List<ItemStack> rollChest(ServerLevel level, BlockPos pos, ResourceLocation key) {
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.create(LootContextParamSets.CHEST);
		LootTable table = level.getServer().getLootData().getLootTable(key);
		for (int attempt = 0; attempt < 6; attempt++) {
			List<ItemStack> loot = table.getRandomItems(params);
			if (!loot.isEmpty()) {
				return loot;
			}
		}
		return List.of();
	}
}
