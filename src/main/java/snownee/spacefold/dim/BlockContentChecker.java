package snownee.spacefold.dim;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import team.reborn.energy.api.EnergyStorage;

public final class BlockContentChecker {
	public static final Check ITEMS = (level, pos, blockEntity) ->
			isEmpty(ItemStorage.SIDED.find(level, pos, null));
	public static final Check FLUIDS = (level, pos, blockEntity) ->
			isEmpty(FluidStorage.SIDED.find(level, pos, null));
	public static final Check ENERGY = (level, pos, blockEntity) -> {
		EnergyStorage storage = EnergyStorage.SIDED.find(level, pos, null);
		return storage == null || storage.getAmount() == 0;
	};
	private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();

	static {
		register(ITEMS);
		register(FLUIDS);
		register(ENERGY);
	}

	private BlockContentChecker() {
	}

	public static void register(Check check) {
		CHECKS.add(check);
	}

	public static boolean isEmpty(Level level, BlockPos pos, BlockEntity blockEntity) {
		for (Check check : CHECKS) {
			if (!check.isEmpty(level, pos, blockEntity)) {
				return false;
			}
		}
		return true;
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
}
