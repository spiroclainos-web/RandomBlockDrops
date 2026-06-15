package com.spiro.randomdrops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 * RandomDrops
 *
 * Every time a player breaks a block, this mod drops ONLY a random outcome —
 * the block's normal drop is suppressed.
 *
 * The outcome is DETERMINISTIC per block type WITHIN a world: the same kind of
 * block always gives the same thing in that world, so it feels like a consistent
 * "loot randomizer" rather than pure chaos every swing. The mapping is also
 * UNIQUE PER WORLD because the world seed is mixed into the RNG.
 *
 * An "outcome" is usually a single item, but some block types are instead mapped
 * to a whole STRUCTURE CHEST loot table (end city, bastion, ancient city, etc.).
 * That mapping is decided by the seed exactly the way an item mapping is — there
 * is NO random chance. If the seed assigns a chest table to a block, that block
 * ALWAYS drops that chest's loot (and nothing else); the contents roll fresh each
 * break, like opening a real chest.
 *
 * Rules that keep it clean:
 *   - Only survival-obtainable items are ever rolled.
 *   - A block NEVER drops its own item (no infinite break/replace dupes).
 *   - Blocks that naturally drop nothing (fire, etc.) drop nothing here too.
 *   - Multi-part blocks (doors, beds, tall plants) don't drop themselves alongside
 *     the random outcome — we clear the whole structure without any natural drops.
 */
public class RandomDrops implements ModInitializer {
	public static final String MOD_ID = "randomdrops";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Items that exist in the registry but can't be obtained legitimately in
	 * survival. Matched by registry id path. Spawn eggs are filtered separately.
	 */
	private static final Set<String> CREATIVE_ONLY = Set.of(
			"barrier", "light", "bedrock", "command_block", "chain_command_block",
			"repeating_command_block", "command_block_minecart", "structure_block",
			"structure_void", "jigsaw", "debug_stick", "knowledge_book", "spawner",
			"trial_spawner", "vault", "end_portal_frame", "reinforced_deepslate",
			"budding_amethyst", "petrified_oak_slab", "infested_stone",
			"infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
			"infested_cracked_stone_bricks", "infested_chiseled_stone_bricks",
			"infested_deepslate", "test_block", "test_instance_block"
	);

	/**
	 * Blocks whose loot table has no pools — they yield nothing even with the right
	 * tool, so they should drop nothing here. (The empty-loot-table case, e.g. air,
	 * is handled separately by checking for an absent loot table.)
	 */
	private static final Set<String> NO_DROP_BLOCKS = Set.of(
			"fire", "soul_fire", "cake", "powder_snow", "frosted_ice", "frogspawn",
			"nether_portal", "budding_amethyst", "reinforced_deepslate", "spawner",
			"trial_spawner", "vault", "suspicious_sand", "suspicious_gravel"
	);

	/**
	 * Structure chest loot tables. The seed can map a block to any of these, in
	 * which case that block always drops that chest's loot.
	 */
	private static final List<ResourceKey<LootTable>> STRUCTURE_CHESTS = List.of(
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
			BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
			BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON,
			BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE,
			BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE,
			BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS,
			BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY,
			BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR,
			BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION,
			BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION_BARREL,
			BuiltInLootTables.TRIAL_CHAMBERS_ENTRANCE,
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
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			// On the client, let the normal prediction run — the server is in charge.
			if (world.isClientSide()) {
				return true;
			}

			// Blocks that naturally drop nothing (fire, portals, etc.) should still
			// drop nothing. Let vanilla handle the break — it gives no item.
			if (dropsNothing(state)) {
				return true;
			}

			ServerLevel serverLevel = (ServerLevel) world;
			Block brokenBlock = state.getBlock();

			// Mix the world seed with the block's registry id so the same block type
			// always maps to the same outcome in this world, but a different world maps
			// differently.
			int blockId = BuiltInRegistries.BLOCK.getId(brokenBlock);
			Random random = new Random(serverLevel.getSeed() * 31L + blockId);

			int chestCount = STRUCTURE_CHESTS.size();
			int total = BuiltInRegistries.ITEM.size() + chestCount;

			// Pick the deterministic outcome. Chest loot tables live in the SAME
			// selection space as items — being mapped to chest loot is just another
			// seed-decided "drop", never a random per-break chance.
			while (true) {
				int roll = random.nextInt(total);

				if (roll < chestCount) {
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
				// Never drop the block's own item — that would allow infinite
				// break/replace duplication.
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

	/** True if this block yields no loot at all even with the right tool (e.g. fire). */
	private static boolean dropsNothing(BlockState state) {
		// No loot table assigned at all (air, barrier, etc.).
		if (state.getBlock().getLootTable().isEmpty()) {
			return true;
		}
		// Has a loot table, but it has no pools so it can never produce an item.
		return NO_DROP_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
	}

	/**
	 * Removes the block — and, for multi-part blocks (doors, beds, tall plants),
	 * its partner half — WITHOUT any natural drops, keeping the break effect.
	 *
	 * We can't rely on UPDATE_SUPPRESS_DROPS for the partner: the engine strips that
	 * flag during neighbour-shape updates, so the partner would still drop. Instead
	 * we clear each part with UPDATE_KNOWN_SHAPE, which skips the drop-cascade entirely.
	 */
	private static void breakWithoutDrops(Level world, BlockPos pos, BlockState state) {
		world.levelEvent(2001, pos, Block.getId(state));
		BlockPos partner = partnerPos(world, pos, state);
		if (partner != null) {
			clearSilently(world, partner);
		}
		clearSilently(world, pos);
	}

	/** Position of the other half of a door/bed/tall-plant, or null if not multi-part. */
	private static BlockPos partnerPos(Level world, BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
					? pos.above() : pos.below();
			if (world.getBlockState(other).is(state.getBlock())) {
				return other;
			}
		} else if (state.getBlock() instanceof BedBlock) {
			BlockPos other = pos.relative(BedBlock.getConnectedDirection(state));
			if (world.getBlockState(other).is(state.getBlock())) {
				return other;
			}
		}
		return null;
	}

	/** Sets a position to air (or its fluid) with no drops and no neighbour cascade. */
	private static void clearSilently(Level world, BlockPos pos) {
		world.setBlock(pos, world.getFluidState(pos).createLegacyBlock(),
				Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/**
	 * Rolls the loot of a structure chest at the given position. Retries a few times
	 * on the same table if it comes up empty so the mapped block rarely feels like a
	 * dud. Returns empty only if every roll was empty.
	 */
	private static List<ItemStack> rollChest(ServerLevel level, BlockPos pos, ResourceKey<LootTable> key) {
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.create(LootContextParamSets.CHEST);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		for (int attempt = 0; attempt < 6; attempt++) {
			List<ItemStack> loot = table.getRandomItems(params);
			if (!loot.isEmpty()) {
				return loot;
			}
		}
		return List.of();
	}
}
