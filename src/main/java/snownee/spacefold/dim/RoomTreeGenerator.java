package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.util.SpaceFoldUtil;

public final class RoomTreeGenerator {
	private static final float WINDOW_CHANCE = 0.9F;
	private static final float LEAF_CHANCE = 0.75F;
	private static final float HUB_CHANCE = 0.3F;
	private static final double HUB_WEIGHT = 4.0;

	private RoomTreeGenerator() {
	}

	public static Result generate(ServerLevel level, int room, int n, RandomSource random) {
		List<List<Integer>> children = buildTree(n, random);
		int[] tier = new int[n];
		computeTier(children, 0, tier);
		int[] ids = new int[n];
		for (int i = 0; i < n; i++) {
			ids[i] = RoomManager.createSnapshotId(level);
		}
		ids[0] = RoomSnapshot.previewId(room);
		SimulationResult[] results = new SimulationResult[n];
		for (int i = 0; i < n; i++) {
			results[i] = new SimulationResult(
					SpaceFoldUtil.randomRates(),
					SpaceFoldUtil.randomRates(),
					List.of(),
					Optional.empty(),
					tier[i],
					ids[i]);
		}
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		RoomSnapshot rootSnapshot = null;
		for (int i = 0; i < n; i++) {
			RoomSnapshot snapshot = buildSnapshot(room, children.get(i), results, ops, random);
			RoomManager.putSnapshot(level, ids[i], snapshot);
			if (i == 0) {
				rootSnapshot = snapshot;
			}
		}
		return new Result(ids[0], rootSnapshot, results[0]);
	}

	private static List<List<Integer>> buildTree(int n, RandomSource random) {
		List<List<Integer>> children = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			children.add(new ArrayList<>());
		}
		if (n <= 1) {
			return children;
		}
		List<Integer> branches = new ArrayList<>(n);
		List<Double> weights = new ArrayList<>(n);
		branches.add(0);
		weights.add(initialWeight(random));
		for (int node = 1; node < n; node++) {
			int index = pick(branches.size(), weights, random);
			int parent = branches.get(index);
			children.get(parent).add(node);
			if (random.nextFloat() >= LEAF_CHANCE) {
				branches.add(node);
				weights.add(initialWeight(random));
			}
		}
		return children;
	}

	private static int pick(int count, List<Double> weights, RandomSource random) {
		double total = 0.0;
		for (int i = 0; i < count; i++) {
			total += weights.get(i);
		}
		double target = random.nextDouble() * total;
		for (int i = 0; i < count; i++) {
			target -= weights.get(i);
			if (target <= 0.0) {
				return i;
			}
		}
		return count - 1;
	}

	private static double initialWeight(RandomSource random) {
		return random.nextFloat() < HUB_CHANCE ? HUB_WEIGHT : 1.0;
	}

	private static int computeTier(List<List<Integer>> children, int node, int[] tier) {
		int max = 0;
		for (int child : children.get(node)) {
			max = Math.max(max, computeTier(children, child, tier));
		}
		tier[node] = max + 1;
		return tier[node];
	}

	private static RoomSnapshot buildSnapshot(
			int room,
			List<Integer> childNodes,
			SimulationResult[] results,
			RegistryOps<Tag> ops,
			RandomSource random) {
		int size = SpaceFoldConfig.roomSize;
		BlockState wall = CoreModule.CHAMBER_WALL.get().defaultBlockState();
		BlockState window = random.nextFloat() < WINDOW_CHANCE ?
				CoreModule.CHAMBER_WINDOW.get().defaultBlockState() :
				wall;
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> indices = new HashMap<>();
		List<RoomSnapshot.Entry> blocks = new ArrayList<>();
		for (int dx = 0; dx < size; dx++) {
			for (int dy = 0; dy < size; dy++) {
				for (int dz = 0; dz < size; dz++) {
					Direction facing = RoomManager.inwardDirection(dx, dy, dz);
					if (facing == null) {
						continue;
					}
					BlockState state = (facing == Direction.UP ? wall : window).setValue(ChamberWallBlock.FACING, facing);
					addBlock(palette, indices, blocks, dx, dy, dz, state, Optional.empty());
				}
			}
		}
		BlockPos center = new BlockPos(Plan.innerPosOffset());
		addBlock(
				palette,
				indices,
				blocks,
				center.getX(),
				center.getY(),
				center.getZ(),
				CoreModule.SIMULATOR.get().defaultBlockState(),
				Optional.empty());
		List<BlockPos> spots = interiorSpots(center, childNodes.size());
		for (int i = 0; i < childNodes.size(); i++) {
			BlockPos pos = spots.get(i);
			CompoundTag nbt = new CompoundTag();
			nbt.putString(
					"id",
					BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(CoreModule.REPEATER_BE.get()).toString());
			nbt.put("result", SimulationResult.CODEC.encodeStart(ops, results[childNodes.get(i)]).getOrThrow());
			addBlock(
					palette,
					indices,
					blocks,
					pos.getX(),
					pos.getY(),
					pos.getZ(),
					CoreModule.REPEATER.get().defaultBlockState(),
					Optional.of(nbt));
		}
		return RoomSnapshot.create(room, size, palette, blocks);
	}

	private static List<BlockPos> interiorSpots(BlockPos center, int count) {
		List<BlockPos> spots = new ArrayList<>(count);
		for (int dy = 1; dy < SpaceFoldConfig.roomSize - 1 && spots.size() < count; dy++) {
			for (int dx = 1; dx < SpaceFoldConfig.roomSize - 1 && spots.size() < count; dx++) {
				for (int dz = 1; dz < SpaceFoldConfig.roomSize - 1 && spots.size() < count; dz++) {
					BlockPos pos = new BlockPos(dx, dy, dz);
					if (!pos.equals(center)) {
						spots.add(pos);
					}
				}
			}
		}
		return spots;
	}

	private static void addBlock(
			List<BlockState> palette,
			Map<BlockState, Integer> indices,
			List<RoomSnapshot.Entry> blocks,
			int x,
			int y,
			int z,
			BlockState state,
			Optional<CompoundTag> nbt) {
		int index = indices.computeIfAbsent(
				state, s -> {
					palette.add(s);
					return palette.size() - 1;
				});
		blocks.add(new RoomSnapshot.Entry(x, y, z, index, nbt));
	}

	public record Result(int rootId, RoomSnapshot rootSnapshot, SimulationResult rootResult) {
	}
}
