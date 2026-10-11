package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueOutput;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.RepeaterBlock;

public class RoomSnapshot extends SavedData {
	public static final int PREVIEW_ID_BASE = 1 << 24;

	public static final Codec<RoomSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("room")
							.forGetter(RoomSnapshot::room),
					Codec.INT.fieldOf("size")
							.forGetter(RoomSnapshot::size),
					BlockState.CODEC.listOf()
							.fieldOf("palette")
							.forGetter(RoomSnapshot::palette),
					Entry.CODEC.listOf()
							.fieldOf("blocks")
							.forGetter(RoomSnapshot::blocks))
			.apply(i, RoomSnapshot::new));

	private final int room;
	private final int size;
	private final List<BlockState> palette;
	private final List<Entry> blocks;

	private RoomSnapshot(int room, int size, List<BlockState> palette, List<Entry> blocks) {
		this.room = room;
		this.size = size;
		this.palette = palette;
		this.blocks = blocks;
	}

	public static RoomSnapshot create(int room, int size, List<BlockState> palette, List<Entry> blocks) {
		return new RoomSnapshot(room, size, palette, blocks);
	}

	public static boolean isPreviewId(int id) {
		return id >= PREVIEW_ID_BASE;
	}

	public static int previewId(int room) {
		return PREVIEW_ID_BASE + room;
	}

	public static SavedDataType<RoomSnapshot> type(int id) {
		return new SavedDataType<>(
				SpaceFold.id("snapshot/" + id),
				() -> {
					throw new IllegalStateException("Should never create an empty room snapshot");
				},
				CODEC,
				DataFixTypes.LEVEL);
	}

	public static RoomSnapshot capture(Level level, int room, BlockPos origin, int size) {
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> indices = new HashMap<>();
		List<Entry> blocks = new ArrayList<>();
		for (int dx = 0; dx < size; dx++) {
			for (int dy = 0; dy < size; dy++) {
				for (int dz = 0; dz < size; dz++) {
					BlockPos at = origin.offset(dx, dy, dz);
					BlockState state = level.getBlockState(at);
					if (state.isAir()) {
						continue;
					}
					int index = indices.computeIfAbsent(state, s -> {
						palette.add(s);
						return palette.size() - 1;
					});
					blocks.add(new Entry(dx, dy, dz, index, captureBlockEntity(level, at)));
				}
			}
		}
		return new RoomSnapshot(room, size, palette, blocks);
	}

	private static Optional<CompoundTag> captureBlockEntity(Level level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return Optional.empty();
		}
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		blockEntity.saveWithId(output);
		return Optional.of(output.buildResult());
	}

	public int room() {
		return room;
	}

	public int size() {
		return size;
	}

	public List<BlockState> palette() {
		return palette;
	}

	public List<Entry> blocks() {
		return blocks;
	}

	/** 收集方块中 tier 最高的至多十个碎片引擎的复制物品。 */
	public List<ItemStack> topRepeaterResults(HolderLookup.Provider registries) {
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		List<ItemStack> results = new ArrayList<>();
		for (Entry entry : blocks) {
			BlockState state = palette.get(entry.state());
			if (!CoreModule.REPEATER.is(state.getBlock())) {
				continue;
			}
			CompoundTag tag = entry.nbt().orElse(null);
			if (tag == null) {
				continue;
			}
			ItemStack stack = RepeaterBlock.createCloneStack(state, tag, ops);
			if (stack.get(CoreModule.SIMULATION_RESULT.get()) == null) {
				continue;
			}
			results.add(stack);
		}
		results.sort(Comparator.comparingInt((ItemStack stack) -> stack.get(CoreModule.SIMULATION_RESULT.get()).tier()).reversed());
		if (results.size() > 100) {
			return new ArrayList<>(results.subList(0, 100));
		}
		return results;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		return obj instanceof RoomSnapshot that
				&& room == that.room
				&& size == that.size
				&& palette.equals(that.palette)
				&& blocks.equals(that.blocks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(room, size, palette, blocks);
	}

	public record Entry(int x, int y, int z, int state, Optional<CompoundTag> nbt) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
						Codec.INT.fieldOf("x")
								.forGetter(Entry::x),
						Codec.INT.fieldOf("y")
								.forGetter(Entry::y),
						Codec.INT.fieldOf("z")
								.forGetter(Entry::z),
						Codec.INT.fieldOf("state")
								.forGetter(Entry::state),
						CompoundTag.CODEC.optionalFieldOf("nbt")
								.forGetter(Entry::nbt))
				.apply(i, Entry::new));
	}
}
