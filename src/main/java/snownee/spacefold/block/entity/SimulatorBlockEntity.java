package snownee.spacefold.block.entity;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import snownee.kiwi.block.entity.ModBlockEntity;
import snownee.spacefold.CoreModule;
import snownee.spacefold.dim.Simulate;
import snownee.spacefold.dim.SimulationManager;
import team.reborn.energy.api.EnergyStorage;

public class SimulatorBlockEntity extends ModBlockEntity implements Nameable {
	private final ItemPort[] itemPorts = new ItemPort[6];
	private final FluidPort[] fluidPorts = new FluidPort[6];
	private final EnergyPort[] energyPorts = new EnergyPort[6];

	private int roomIndex = -1;
	private @Nullable Component name;

	public SimulatorBlockEntity(BlockPos pos, BlockState state) {
		super(CoreModule.SIMULATOR_BE.get(), pos, state);
		for (Direction direction : Direction.values()) {
			int index = direction.ordinal();
			itemPorts[index] = new ItemPort(direction);
			fluidPorts[index] = new FluidPort(direction);
			energyPorts[index] = new EnergyPort(direction);
		}
	}

	public @Nullable Storage<ItemVariant> getItemStorage(@Nullable Direction side) {
		return side == null ? null : itemPorts[side.ordinal()];
	}

	public @Nullable Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
		return side == null ? null : fluidPorts[side.ordinal()];
	}

	public @Nullable EnergyStorage getEnergyStorage(@Nullable Direction side) {
		return side == null ? null : energyPorts[side.ordinal()];
	}

	private @Nullable InterfaceBlockEntity interfaceEntity(Direction side) {
		if (level == null || level.isClientSide()) {
			return null;
		}
		ServerLevel simulate = Objects.requireNonNull(level.getServer()).getLevel(Simulate.DIMENSION);
		if (simulate == null) {
			return null;
		}
		BlockPos target = SimulationManager.interfaceTarget(level.dimension(), worldPosition, side);
		if (target == null) {
			return null;
		}
		return simulate.getBlockEntity(target) instanceof InterfaceBlockEntity blockEntity ? blockEntity : null;
	}

	public void setRoom(int room) {
		if (roomIndex >= 0) {
			return;
		}
		roomIndex = room;
		setChanged();
	}

	public int getRoomIndex() {
		return roomIndex;
	}

	@Override
	public Component getName() {
		return name != null ? name : getBlockState().getBlock().getName();
	}

	@Override
	public @Nullable Component getCustomName() {
		return name;
	}

	public void setCustomName(@Nullable Component name) {
		this.name = name;
		setChanged();
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		name = components.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, name);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("room", roomIndex);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, name);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		roomIndex = input.getIntOr("room", -1);
		name = parseCustomNameSafe(input, "CustomName");
	}

	@Override
	protected void readPacketData(ValueInput valueInput) {
	}

	@Override
	protected void writePacketData(ValueOutput valueOutput) {
	}

	private final class ItemPort implements Storage<ItemVariant> {
		private final Direction side;

		private ItemPort(Direction side) {
			this.side = side;
		}

		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			InterfaceBlockEntity target = interfaceEntity(side);
			return target == null ? 0L : target.insertItem(resource, maxAmount, transaction);
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
	}

	private final class FluidPort implements Storage<FluidVariant> {
		private final Direction side;

		private FluidPort(Direction side) {
			this.side = side;
		}

		@Override
		public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
			InterfaceBlockEntity target = interfaceEntity(side);
			return target == null ? 0L : target.insertFluid(resource, maxAmount, transaction);
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
	}

	private final class EnergyPort implements EnergyStorage {
		private final Direction side;

		private EnergyPort(Direction side) {
			this.side = side;
		}

		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			InterfaceBlockEntity target = interfaceEntity(side);
			return target == null ? 0L : target.insertEnergy(maxAmount, transaction);
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
	}
}
