package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Clearable;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.CoreModule;
import snownee.spacefold.util.PairHierarchyLookup;
import team.reborn.energy.api.EnergyStorage;

public final class BlockContentChecker {
	private static final List<Resource> RESOURCES = new ArrayList<>();

	public static final Resource ITEMS = new Resource((level, pos, blockEntity) ->
			isEmpty(ItemStorage.SIDED.find(level, pos, null)));
	public static final Resource FLUIDS = new Resource((level, pos, blockEntity) ->
			isEmpty(FluidStorage.SIDED.find(level, pos, null)));
	public static final Resource ENERGY = new Resource((level, pos, blockEntity) -> {
		EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, null);
		return storage == null || storage.getAmount() == 0;
	});

	private BlockContentChecker() {
	}

	public static boolean isEmpty(Level level, BlockPos pos, BlockEntity blockEntity) {
		BlockState state = level.getBlockState(pos);
		if (state.is(CoreModule.MIRROR_NBT_IGNORED) || isClearable(state, level, pos, blockEntity)) {
			return true;
		}
		Block block = state.getBlock();
		for (Resource resource : RESOURCES) {
			if (!resource.isEmpty(level, pos, block, blockEntity)) {
				return false;
			}
		}
		return true;
	}

	public static void clear(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (level.getBlockEntity(pos) instanceof Clearable clearable) {
			clearable.clearContent();
		}
		if (state.getBlock() instanceof WorldlyContainerHolder holder) {
			holder.getContainer(state, level, pos).clearContent();
		}
	}

	private static boolean isClearable(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity) {
		if (blockEntity instanceof Clearable) {
			return true;
		}
		return state.getBlock() instanceof WorldlyContainerHolder;
	}

	private static <T> boolean isEmpty(@Nullable Storage<T> storage) {
		if (storage == null) {
			return true;
		}
		for (StorageView<T> view : storage) {
			if (!view.isResourceBlank() && view.getAmount() > 0) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	public interface Check {
		boolean isEmpty(Level level, BlockPos pos, BlockEntity blockEntity);
	}

	public static final class Resource {
		private final PairHierarchyLookup<Check> lookup = new PairHierarchyLookup<>(Block.class, BlockEntity.class);
		private final Check defaultCheck;

		private Resource(Check defaultCheck) {
			this.defaultCheck = defaultCheck;
			RESOURCES.add(this);
		}

		public void register(Class<?> clazz, Check check) {
			lookup.register(clazz, check);
		}

		boolean isEmpty(Level level, BlockPos pos, Block block, BlockEntity blockEntity) {
			List<Check> checks = lookup.getMerged(block, blockEntity);
			if (checks.isEmpty()) {
				return defaultCheck.isEmpty(level, pos, blockEntity);
			}
			for (Check check : checks) {
				if (!check.isEmpty(level, pos, blockEntity)) {
					return false;
				}
			}
			return true;
		}
	}
}
