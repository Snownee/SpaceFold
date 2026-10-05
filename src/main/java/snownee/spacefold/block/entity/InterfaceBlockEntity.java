package snownee.spacefold.block.entity;

import java.util.Collections;
import java.util.Iterator;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import snownee.kiwi.block.entity.ModBlockEntity;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.block.InterfaceBlock;
import snownee.spacefold.dim.Simulate;
import snownee.spacefold.dim.SimulationManager;
import team.reborn.energy.api.EnergyStorage;

public class InterfaceBlockEntity extends ModBlockEntity {
	private final Object2LongMap<ItemVariant> pendingItems = new Object2LongOpenHashMap<>();
	private final Object2LongMap<FluidVariant> pendingFluids = new Object2LongOpenHashMap<>();
	private final Storage<ItemVariant> itemSink = new Storage<>() {
		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			int room = sessionRoom();
			if (room < 0) {
				return 0L;
			}
			SimulationManager.recordOutputItem(room, resource, maxAmount, transaction);
			return maxAmount;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			return 0L;
		}

		@Override
		public boolean supportsExtraction() {
			return false;
		}

		@Override
		public Iterator<StorageView<ItemVariant>> iterator() {
			return Collections.emptyIterator();
		}
	};
	private final Storage<FluidVariant> fluidSink = new Storage<>() {
		@Override
		public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			int room = sessionRoom();
			if (room < 0) {
				return 0L;
			}
			SimulationManager.recordOutputFluid(room, resource, maxAmount, transaction);
			return maxAmount;
		}

		@Override
		public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			return 0L;
		}

		@Override
		public boolean supportsExtraction() {
			return false;
		}

		@Override
		public Iterator<StorageView<FluidVariant>> iterator() {
			return Collections.emptyIterator();
		}
	};
	private final EnergyStorage energySink = new EnergyStorage() {
		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			int room = sessionRoom();
			if (room < 0) {
				return 0L;
			}
			SimulationManager.recordOutputEnergy(room, maxAmount, transaction);
			return maxAmount;
		}

		@Override
		public boolean supportsExtraction() {
			return false;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			return 0L;
		}

		@Override
		public long getAmount() {
			return 0L;
		}

		@Override
		public long getCapacity() {
			return Long.MAX_VALUE;
		}
	};
	private long pendingEnergy;
	private int replaysLeft;
	private boolean awaitingFirstTick;

	public InterfaceBlockEntity(BlockPos pos, BlockState state) {
		super(CoreModule.INTERFACE_BE.get(), pos, state);
	}

	private static int replayCount() {
		return Math.max(1, SpaceFoldConfig.simulationTickMultiplier) - 1;
	}

	public Storage<ItemVariant> itemSink() {
		return itemSink;
	}

	public Storage<FluidVariant> fluidSink() {
		return fluidSink;
	}

	public EnergyStorage energySink() {
		return energySink;
	}

	public long insertItem(ItemVariant resource, long maxAmount, TransactionContext transaction) {
		int room = sessionRoom();
		if (room < 0) {
			return 0L;
		}
		long moved = insertItemRaw(room, resource, maxAmount, transaction);
		if (moved > 0) {
			cacheItem(resource, moved, transaction);
		}
		return moved;
	}

	public long insertFluid(FluidVariant resource, long maxAmount, TransactionContext transaction) {
		int room = sessionRoom();
		if (room < 0) {
			return 0L;
		}
		long moved = insertFluidRaw(room, resource, maxAmount, transaction);
		if (moved > 0) {
			cacheFluid(resource, moved, transaction);
		}
		return moved;
	}

	public long insertEnergy(long maxAmount, TransactionContext transaction) {
		int room = sessionRoom();
		if (room < 0) {
			return 0L;
		}
		long moved = insertEnergyRaw(room, maxAmount, transaction);
		if (moved > 0) {
			cacheEnergy(moved, transaction);
		}
		return moved;
	}

	private long insertItemRaw(int room, ItemVariant resource, long maxAmount, TransactionContext transaction) {
		Storage<ItemVariant> target = itemTarget();
		long moved = target == null ? 0L : target.insert(resource, maxAmount, transaction);
		if (moved > 0) {
			SimulationManager.recordItem(room, resource, moved, transaction);
		}
		return moved;
	}

	private long insertFluidRaw(int room, FluidVariant resource, long maxAmount, TransactionContext transaction) {
		Storage<FluidVariant> target = fluidTarget();
		long moved = target == null ? 0L : target.insert(resource, maxAmount, transaction);
		if (moved > 0) {
			SimulationManager.recordFluid(room, resource, moved, transaction);
		}
		return moved;
	}

	private long insertEnergyRaw(int room, long maxAmount, TransactionContext transaction) {
		EnergyStorage target = energyTarget();
		long moved = target == null ? 0L : target.insert(maxAmount, transaction);
		if (moved > 0) {
			SimulationManager.recordEnergy(room, moved, transaction);
		}
		return moved;
	}

	public void tick() {
		if (replaysLeft <= 0) {
			return;
		}
		int room = sessionRoom();
		if (room < 0) {
			resetReplays();
			return;
		}
		if (awaitingFirstTick) {
			awaitingFirstTick = false;
			return;
		}
		replaysLeft--;
		replay(room);
		if (replaysLeft <= 0) {
			resetReplays();
		}
	}

	private void replay(int room) {
		try (Transaction transaction = Transaction.openOuter()) {
			for (Object2LongMap.Entry<ItemVariant> entry : pendingItems.object2LongEntrySet()) {
				insertItemRaw(room, entry.getKey(), entry.getLongValue(), transaction);
			}
			for (Object2LongMap.Entry<FluidVariant> entry : pendingFluids.object2LongEntrySet()) {
				insertFluidRaw(room, entry.getKey(), entry.getLongValue(), transaction);
			}
			if (pendingEnergy > 0) {
				insertEnergyRaw(room, pendingEnergy, transaction);
			}
			transaction.commit();
		}
	}

	private void cacheItem(ItemVariant resource, long amount, TransactionContext transaction) {
		int replays = replayCount();
		if (replays <= 0) {
			return;
		}
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				pendingItems.mergeLong(resource, amount, Long::sum);
				replaysLeft = Math.max(replaysLeft, replays);
				awaitingFirstTick = true;
			}
		});
	}

	private void cacheFluid(FluidVariant resource, long amount, TransactionContext transaction) {
		int replays = replayCount();
		if (replays <= 0) {
			return;
		}
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				pendingFluids.mergeLong(resource, amount, Long::sum);
				replaysLeft = Math.max(replaysLeft, replays);
				awaitingFirstTick = true;
			}
		});
	}

	private void cacheEnergy(long amount, TransactionContext transaction) {
		int replays = replayCount();
		if (replays <= 0) {
			return;
		}
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				pendingEnergy += amount;
				replaysLeft = Math.max(replaysLeft, replays);
				awaitingFirstTick = true;
			}
		});
	}

	private void resetReplays() {
		replaysLeft = 0;
		awaitingFirstTick = false;
		pendingItems.clear();
		pendingFluids.clear();
		pendingEnergy = 0;
	}

	private int sessionRoom() {
		if (!(level instanceof ServerLevel serverLevel) || !level.dimension().equals(Simulate.DIMENSION)) {
			return -1;
		}
		return SimulationManager.activeRoomAt(serverLevel, worldPosition);
	}

	private @Nullable Storage<ItemVariant> itemTarget() {
		if (level == null) {
			return null;
		}
		return ItemStorage.SIDED.find(level, worldPosition.relative(innerFacing()), null);
	}

	private @Nullable Storage<FluidVariant> fluidTarget() {
		if (level == null) {
			return null;
		}
		return FluidStorage.SIDED.find(level, worldPosition.relative(innerFacing()), null);
	}

	private @Nullable EnergyStorage energyTarget() {
		if (level == null) {
			return null;
		}
		return EnergyStorage.SIDED.find(level, worldPosition.relative(innerFacing()), null);
	}

	private Direction innerFacing() {
		return getBlockState().getValue(InterfaceBlock.FACING);
	}

	@Override
	protected void readPacketData(ValueInput valueInput) {
	}

	@Override
	protected void writePacketData(ValueOutput valueOutput) {
	}
}
