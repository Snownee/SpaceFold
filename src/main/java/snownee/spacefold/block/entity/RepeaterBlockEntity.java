package snownee.spacefold.block.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import snownee.kiwi.block.entity.ModBlockEntity;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.client.paint.PaintClientHooks;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.paint.FacePaint;
import snownee.spacefold.paint.RepeaterPaints;
import team.reborn.energy.api.EnergyStorage;

public class RepeaterBlockEntity extends ModBlockEntity implements Nameable {
	private final Object2DoubleMap<ItemVariant> itemInput = new Object2DoubleLinkedOpenHashMap<>();
	private final Object2DoubleMap<ItemVariant> itemOutput = new Object2DoubleLinkedOpenHashMap<>();
	private final Object2DoubleMap<FluidVariant> fluidInput = new Object2DoubleLinkedOpenHashMap<>();
	private final Object2DoubleMap<FluidVariant> fluidOutput = new Object2DoubleLinkedOpenHashMap<>();
	private final Object paintsLock = new Object();
	private final Storage<ItemVariant> itemInputBuffer;
	private final Storage<ItemVariant> itemOutputBuffer;
	private final Storage<ItemVariant> itemCombinedBuffer;
	private final Storage<FluidVariant> fluidInputBuffer;
	private final Storage<FluidVariant> fluidOutputBuffer;
	private final Storage<FluidVariant> fluidCombinedBuffer;
	private final EnergyStorage energyInputBuffer;
	private final EnergyStorage energyOutputBuffer;
	private final EnergyStorage energyCombinedBuffer;
	private double energyInput;
	private double energyOutput;
	private @Nullable SimulationResult result;
	private @Nullable Component name;
	private volatile RepeaterPaints paints = RepeaterPaints.EMPTY;
	private double throttle;
	private int secondTicks;
	private boolean secondSatisfied = true;

	public RepeaterBlockEntity(BlockPos pos, BlockState state) {
		super(CoreModule.REPEATER_BE.get(), pos, state);
		throttle = SpaceFoldConfig.repeaterInitialThrottlePercent / 100.0;
		itemInputBuffer = new VariantBuffer<>(itemInput, () -> SpaceFoldConfig.repeaterItemBuffer, false, this::acceptsItemInput);
		itemOutputBuffer = new VariantBuffer<>(itemOutput, () -> SpaceFoldConfig.repeaterItemBuffer, true, variant -> true);
		itemCombinedBuffer = new CombinedStorage<>(List.of(itemInputBuffer, itemOutputBuffer));
		fluidInputBuffer = new VariantBuffer<>(fluidInput, () -> SpaceFoldConfig.repeaterFluidBuffer, false, this::acceptsFluidInput);
		fluidOutputBuffer = new VariantBuffer<>(fluidOutput, () -> SpaceFoldConfig.repeaterFluidBuffer, true, variant -> true);
		fluidCombinedBuffer = new CombinedStorage<>(List.of(fluidInputBuffer, fluidOutputBuffer));
		energyInputBuffer = new EnergyBuffer(false, this::acceptsEnergyInput);
		energyOutputBuffer = new EnergyBuffer(true, () -> true);
		energyCombinedBuffer = new EnergyStorage() {
			@Override
			public long insert(long maxAmount, TransactionContext transaction) {
				return energyInputBuffer.insert(maxAmount, transaction);
			}

			@Override
			public long extract(long maxAmount, TransactionContext transaction) {
				return energyOutputBuffer.extract(maxAmount, transaction);
			}

			@Override
			public long getAmount() {
				return energyInputBuffer.getAmount() + energyOutputBuffer.getAmount();
			}

			@Override
			public long getCapacity() {
				return energyInputBuffer.getCapacity() + energyOutputBuffer.getCapacity();
			}
		};
	}

	private static <T> void add(Object2DoubleMap<T> map, T key, double delta) {
		map.put(key, map.getDouble(key) + delta);
	}

	public @Nullable SimulationResult getResult() {
		return result;
	}

	public void setResult(SimulationResult result) {
		this.result = result;
		setChanged();
	}

	public RepeaterPaints getPaints() {
		return paints;
	}

	public void setPaints(RepeaterPaints paints) {
		synchronized (paintsLock) {
			this.paints = paints;
		}
		onPaintsChanged();
	}

	public void setPaint(Direction face, @Nullable FacePaint paint) {
		synchronized (paintsLock) {
			paints = paints.set(face, paint);
		}
		onPaintsChanged();
	}

	private void onPaintsChanged() {
		setChanged();
		if (level == null) {
			return;
		}
		if (level.isClientSide()) {
			PaintClientHooks.markDirty(worldPosition);
		} else {
			refresh();
		}
	}

	public @Nullable Storage<ItemVariant> getItemStorage(@Nullable Direction side) {
		if (side == null) {
			return itemCombinedBuffer;
		}
		return side == facing() ? itemOutputBuffer : itemInputBuffer;
	}

	public @Nullable Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
		if (side == null) {
			return fluidCombinedBuffer;
		}
		return side == facing() ? fluidOutputBuffer : fluidInputBuffer;
	}

	public @Nullable EnergyStorage getEnergyStorage(@Nullable Direction side) {
		if (side == null) {
			return energyCombinedBuffer;
		}
		return side == facing() ? energyOutputBuffer : energyInputBuffer;
	}

	private Direction facing() {
		return getBlockState().getValue(DirectionalBlock.FACING);
	}

	private boolean acceptsItemInput(ItemVariant variant) {
		SimulationResult current = result;
		return current != null && !current.failed() && current.inputs().items().containsKey(variant);
	}

	private boolean acceptsFluidInput(FluidVariant variant) {
		SimulationResult current = result;
		return current != null && !current.failed() && current.inputs().fluids().containsKey(variant);
	}

	private boolean acceptsEnergyInput() {
		SimulationResult current = result;
		return current != null && !current.failed() && current.inputs().energy() > 0;
	}

	private long energyCapacity() {
		SimulationResult current = result;
		if (current == null || current.failed()) {
			return 0;
		}
		return current.inputs().energy() > 0 || current.outputs().energy() > 0 ? SpaceFoldConfig.repeaterEnergyBuffer : 0;
	}

	public double getThrottle() {
		return throttle;
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
		result = components.get(CoreModule.SIMULATION_RESULT.get());
		setPaints(components.getOrDefault(CoreModule.REPEATER_PAINT.get(), RepeaterPaints.EMPTY));
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, name);
		components.set(CoreModule.SIMULATION_RESULT.get(), result);
		components.set(CoreModule.REPEATER_PAINT.get(), paints.isEmpty() ? null : paints);
	}

	public void tick() {
		if (level == null || level.isClientSide()) {
			return;
		}
		SimulationResult current = result;
		if (current == null || current.failed()) {
			return;
		}
		SimulationResult.Rates in = current.inputs();
		SimulationResult.Rates out = current.outputs();
		if (in.kindCount() == 0 && out.kindCount() == 0
				&& itemInput.isEmpty() && fluidInput.isEmpty() && energyInput == 0
				&& itemOutput.isEmpty() && fluidOutput.isEmpty() && energyOutput == 0) {
			if (throttle != 1.0) {
				throttle = 1.0;
				setChanged();
			}
			return;
		}
		double dt = 1.0 / 20.0;
		boolean unthrottled = in.kindCount() == 0;
		if (unthrottled && throttle != 1.0) {
			throttle = 1.0;
		}
		boolean consumed = false;
		double inputFraction = 1.0;
		if (!in.items().isEmpty()) {
			for (Object2DoubleMap.Entry<ItemVariant> entry : in.items().object2DoubleEntrySet()) {
				double need = entry.getDoubleValue() * dt;
				if (need <= 0) {
					continue;
				}
				inputFraction = Math.min(inputFraction, itemInput.getDouble(entry.getKey()) / need);
			}
		}
		if (!in.fluids().isEmpty()) {
			for (Object2DoubleMap.Entry<FluidVariant> entry : in.fluids().object2DoubleEntrySet()) {
				double need = entry.getDoubleValue() * dt;
				if (need <= 0) {
					continue;
				}
				inputFraction = Math.min(inputFraction, fluidInput.getDouble(entry.getKey()) / need);
			}
		}
		double inEnergy = in.energy() * dt;
		if (inEnergy > 0) {
			inputFraction = Math.min(inputFraction, energyInput / inEnergy);
		}
		double outputFraction = 1.0;
		if (!out.items().isEmpty()) {
			for (Object2DoubleMap.Entry<ItemVariant> entry : out.items().object2DoubleEntrySet()) {
				double produce = entry.getDoubleValue() * throttle * dt;
				if (produce <= 0) {
					continue;
				}
				outputFraction = Math.min(
						outputFraction,
						(SpaceFoldConfig.repeaterItemBuffer - itemOutput.getDouble(entry.getKey())) / produce);
			}
		}
		if (!out.fluids().isEmpty()) {
			for (Object2DoubleMap.Entry<FluidVariant> entry : out.fluids().object2DoubleEntrySet()) {
				double produce = entry.getDoubleValue() * throttle * dt;
				if (produce <= 0) {
					continue;
				}
				outputFraction = Math.min(
						outputFraction,
						(SpaceFoldConfig.repeaterFluidBuffer - fluidOutput.getDouble(entry.getKey())) / produce);
			}
		}
		double outEnergy = out.energy() * throttle * dt;
		if (outEnergy > 0) {
			outputFraction = Math.min(outputFraction, (energyCapacity() - energyOutput) / outEnergy);
		}
		if (inputFraction < 1.0 - 1.0E-9 || outputFraction < 1.0 - 1.0E-9) {
			secondSatisfied = false;
		} else {
			for (Object2DoubleMap.Entry<ItemVariant> entry : in.items().object2DoubleEntrySet()) {
				add(itemInput, entry.getKey(), -entry.getDoubleValue() * dt);
			}
			for (Object2DoubleMap.Entry<FluidVariant> entry : in.fluids().object2DoubleEntrySet()) {
				add(fluidInput, entry.getKey(), -entry.getDoubleValue() * dt);
			}
			energyInput -= in.energy() * dt;
			for (Object2DoubleMap.Entry<ItemVariant> entry : out.items().object2DoubleEntrySet()) {
				add(itemOutput, entry.getKey(), entry.getDoubleValue() * throttle * dt);
			}
			for (Object2DoubleMap.Entry<FluidVariant> entry : out.fluids().object2DoubleEntrySet()) {
				add(fluidOutput, entry.getKey(), entry.getDoubleValue() * throttle * dt);
			}
			energyOutput += out.energy() * throttle * dt;
			consumed = true;
		}
		secondTicks++;
		if (secondTicks >= 20) {
			secondTicks = 0;
			if (!unthrottled) {
				double step = (secondSatisfied ? SpaceFoldConfig.repeaterRampUpPercent : -SpaceFoldConfig.repeaterRampDownPercent) / 100.0;
				double updated = Math.clamp(throttle + step, 0.0, 1.0);
				if (updated != throttle) {
					throttle = updated;
					consumed = true;
				}
			}
			secondSatisfied = true;
		}
		if (push(level)) {
			consumed = true;
		}
		if (consumed) {
			setChanged();
		}
	}

	private boolean push(Level level) {
		if (itemOutput.isEmpty() && fluidOutput.isEmpty() && energyOutput < 1) {
			return false;
		}
		Direction targetSide = facing();
		BlockPos target = worldPosition.relative(targetSide);
		Direction side = targetSide.getOpposite();
		BlockState state = level.getBlockState(target);
		if (state.isAir()) {
			return false;
		}
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
		boolean moved = pushItems(level, target, state, blockEntity, side);
		moved |= pushFluids(level, target, state, blockEntity, side);
		moved |= pushEnergy(level, target, state, blockEntity, side);
		return moved;
	}

	private boolean pushItems(Level level, BlockPos target, BlockState state, @Nullable BlockEntity blockEntity, Direction side) {
		if (itemOutput.isEmpty()) {
			return false;
		}
		Storage<ItemVariant> storage = null;
		boolean moved = false;
		try (Transaction transaction = Transaction.openOuter()) {
			for (Object2DoubleMap.Entry<ItemVariant> entry : itemOutput.object2DoubleEntrySet()) {
				long amount = (long) entry.getDoubleValue();
				if (amount < 1) {
					continue;
				}
				if (storage == null) {
					storage = ItemStorage.SIDED.find(level, target, state, blockEntity, side);
					if (storage == null) {
						return false;
					}
				}
				ItemVariant variant = entry.getKey();
				long inserted = storage.insert(variant, amount, transaction);
				if (inserted > 0) {
					transaction.addOuterCloseCallback(result -> {
						if (result.wasCommitted()) {
							add(itemOutput, variant, -inserted);
						}
					});
					moved = true;
				}
			}
			transaction.commit();
		}
		return moved;
	}

	private boolean pushFluids(Level level, BlockPos target, BlockState state, @Nullable BlockEntity blockEntity, Direction side) {
		if (fluidOutput.isEmpty()) {
			return false;
		}
		Storage<FluidVariant> storage = null;
		boolean moved = false;
		try (Transaction transaction = Transaction.openOuter()) {
			for (Object2DoubleMap.Entry<FluidVariant> entry : fluidOutput.object2DoubleEntrySet()) {
				long amount = (long) entry.getDoubleValue();
				if (amount < 1) {
					continue;
				}
				if (storage == null) {
					storage = FluidStorage.SIDED.find(level, target, state, blockEntity, side);
					if (storage == null) {
						return false;
					}
				}
				FluidVariant variant = entry.getKey();
				long inserted = storage.insert(variant, amount, transaction);
				if (inserted > 0) {
					transaction.addOuterCloseCallback(result -> {
						if (result.wasCommitted()) {
							add(fluidOutput, variant, -inserted);
						}
					});
					moved = true;
				}
			}
			transaction.commit();
		}
		return moved;
	}

	private boolean pushEnergy(Level level, BlockPos target, BlockState state, @Nullable BlockEntity blockEntity, Direction side) {
		long amount = (long) energyOutput;
		if (amount <= 0) {
			return false;
		}
		EnergyStorage storage = EnergyStorage.SIDED.find(level, target, state, blockEntity, side);
		if (storage == null) {
			return false;
		}
		long inserted;
		try (Transaction transaction = Transaction.openOuter()) {
			inserted = storage.insert(amount, transaction);
			transaction.commit();
		}
		if (inserted > 0) {
			energyOutput -= inserted;
			return true;
		}
		return false;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, name);
		output.storeNullable("result", SimulationResult.CODEC, result);
		output.storeNullable("paints", RepeaterPaints.CODEC, paints.isEmpty() ? null : paints);
		output.store("inputs", SimulationResult.Rates.CODEC, new SimulationResult.Rates(itemInput, fluidInput, energyInput));
		output.store("outputs", SimulationResult.Rates.CODEC, new SimulationResult.Rates(itemOutput, fluidOutput, energyOutput));
		output.putDouble("throttle", throttle);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		name = parseCustomNameSafe(input, "CustomName");
		result = input.read("result", SimulationResult.CODEC).orElse(null);
		paints = input.read("paints", RepeaterPaints.CODEC).orElse(RepeaterPaints.EMPTY);
		input.read("inputs", SimulationResult.Rates.CODEC).ifPresent(rates -> {
			itemInput.putAll(rates.items());
			fluidInput.putAll(rates.fluids());
			energyInput = rates.energy();
		});
		input.read("outputs", SimulationResult.Rates.CODEC).ifPresent(rates -> {
			itemOutput.putAll(rates.items());
			fluidOutput.putAll(rates.fluids());
			energyOutput = rates.energy();
		});
		throttle = input.getDoubleOr("throttle", SpaceFoldConfig.repeaterInitialThrottlePercent / 100.0);
		secondTicks = 0;
		secondSatisfied = true;
	}

	@Override
	protected void readPacketData(ValueInput valueInput) {
		paints = valueInput.read("paints", RepeaterPaints.CODEC).orElse(RepeaterPaints.EMPTY);
		if (level != null && level.isClientSide()) {
			PaintClientHooks.markDirty(worldPosition);
		}
	}

	@Override
	protected void writePacketData(ValueOutput valueOutput) {
		valueOutput.storeNullable("paints", RepeaterPaints.CODEC, paints.isEmpty() ? null : paints);
	}

	private record VariantBuffer<T>(
			Object2DoubleMap<T> amounts,
			LongSupplier capacity,
			boolean output,
			Predicate<T> filter) implements Storage<T> {

		@Override
		public long insert(T resource, long maxAmount, TransactionContext transaction) {
			if (output || !filter.test(resource)) {
				return 0;
			}
			long space = capacity.getAsLong() - (long) amounts.getDouble(resource);
			if (space <= 0) {
				return 0;
			}
			long moved = Math.min(maxAmount, space);
			if (moved <= 0) {
				return 0;
			}
			transaction.addOuterCloseCallback(result -> {
				if (result.wasCommitted()) {
					add(amounts, resource, moved);
				}
			});
			return moved;
		}

		@Override
		public boolean supportsInsertion() {
			return !output;
		}

		@Override
		public long extract(T resource, long maxAmount, TransactionContext transaction) {
			if (!output) {
				return 0;
			}
			long moved = Math.min(maxAmount, (long) amounts.getDouble(resource));
			if (moved <= 0) {
				return 0;
			}
			transaction.addOuterCloseCallback(result -> {
				if (result.wasCommitted()) {
					add(amounts, resource, -moved);
				}
			});
			return moved;
		}

		@Override
		public boolean supportsExtraction() {
			return output;
		}

		@Override
		public Iterator<StorageView<T>> iterator() {
			List<StorageView<T>> views = new ArrayList<>();
			for (Object2DoubleMap.Entry<T> entry : amounts.object2DoubleEntrySet()) {
				double amount = entry.getDoubleValue();
				if (amount < 1) {
					continue;
				}
				T resource = entry.getKey();
				views.add(new StorageView<T>() {
					@Override
					public long extract(T extracted, long maxAmount, TransactionContext transaction) {
						if (!output) {
							return 0;
						}
						long moved = Math.min(maxAmount, (long) amount);
						if (moved <= 0) {
							return 0;
						}
						transaction.addOuterCloseCallback(result -> {
							if (result.wasCommitted()) {
								add(amounts, resource, -moved);
							}
						});
						return moved;
					}

					@Override
					public boolean isResourceBlank() {
						return false;
					}

					@Override
					public T getResource() {
						return resource;
					}

					@Override
					public long getAmount() {
						return (long) amount;
					}

					@Override
					public long getCapacity() {
						return capacity.getAsLong();
					}
				});
			}
			return views.iterator();
		}
	}

	private final class EnergyBuffer implements EnergyStorage {
		private final boolean output;
		private final BooleanSupplier inputAllowed;

		private EnergyBuffer(boolean output, BooleanSupplier inputAllowed) {
			this.output = output;
			this.inputAllowed = inputAllowed;
		}

		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			if (output || !inputAllowed.getAsBoolean()) {
				return 0;
			}
			long space = energyCapacity() - (long) energyInput;
			if (space <= 0) {
				return 0;
			}
			long moved = Math.min(maxAmount, space);
			if (moved <= 0) {
				return 0;
			}
			transaction.addOuterCloseCallback(result -> {
				if (result.wasCommitted()) {
					energyInput += moved;
				}
			});
			return moved;
		}

		@Override
		public boolean supportsInsertion() {
			return !output;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			if (!output) {
				return 0;
			}
			long moved = Math.min(maxAmount, (long) energyOutput);
			if (moved <= 0) {
				return 0;
			}
			transaction.addOuterCloseCallback(result -> {
				if (result.wasCommitted()) {
					energyOutput -= moved;
				}
			});
			return moved;
		}

		@Override
		public boolean supportsExtraction() {
			return output;
		}

		@Override
		public long getAmount() {
			return (long) (output ? energyOutput : energyInput);
		}

		@Override
		public long getCapacity() {
			return energyCapacity();
		}
	}
}
