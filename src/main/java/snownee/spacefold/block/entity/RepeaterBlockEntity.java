package snownee.spacefold.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
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
	private final Object2DoubleMap<ItemVariant> itemInput = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<ItemVariant> itemOutput = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<FluidVariant> fluidInput = new Object2DoubleOpenHashMap<>();
	private final Object2DoubleMap<FluidVariant> fluidOutput = new Object2DoubleOpenHashMap<>();
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
	private volatile Map<Direction, FacePaint> paints = RepeaterPaints.EMPTY;
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

	private static SimulationResult.Rates itemRates(Object2DoubleMap<ItemVariant> items) {
		Map<ItemVariant, Double> map = new LinkedHashMap<>();
		for (Object2DoubleMap.Entry<ItemVariant> entry : items.object2DoubleEntrySet()) {
			map.put(entry.getKey(), entry.getDoubleValue());
		}
		return new SimulationResult.Rates(map, Map.of(), 0);
	}

	private static SimulationResult.Rates fluidRates(Object2DoubleMap<FluidVariant> fluids) {
		Map<FluidVariant, Double> map = new LinkedHashMap<>();
		for (Object2DoubleMap.Entry<FluidVariant> entry : fluids.object2DoubleEntrySet()) {
			map.put(entry.getKey(), entry.getDoubleValue());
		}
		return new SimulationResult.Rates(Map.of(), map, 0);
	}

	public @Nullable SimulationResult getResult() {
		return result;
	}

	public void setResult(SimulationResult result) {
		this.result = result;
		setChanged();
	}

	public Map<Direction, FacePaint> getPaints() {
		return paints;
	}

	public void setPaints(Map<Direction, FacePaint> paints) {
		Map<Direction, FacePaint> updated = RepeaterPaints.copy(paints);
		synchronized (paintsLock) {
			this.paints = updated;
		}
		onPaintsChanged();
	}

	public void setPaint(Direction face, FacePaint paint) {
		Map<Direction, FacePaint> updated;
		synchronized (paintsLock) {
			updated = RepeaterPaints.set(paints, face, paint);
			paints = updated;
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
		push(level);
		SimulationResult current = result;
		if (current == null || current.failed()) {
			return;
		}
		double dt = 1.0 / 20.0;
		SimulationResult.Rates in = current.inputs();
		SimulationResult.Rates out = current.outputs();
		boolean unthrottled = in.kindCount() == 0;
		if (unthrottled && throttle != 1.0) {
			throttle = 1.0;
			setChanged();
		}
		double inputFraction = 1.0;
		for (Map.Entry<ItemVariant, Double> entry : in.items().entrySet()) {
			double need = entry.getValue() * dt;
			if (need <= 0) {
				continue;
			}
			inputFraction = Math.min(inputFraction, itemInput.getDouble(entry.getKey()) / need);
		}
		for (Map.Entry<FluidVariant, Double> entry : in.fluids().entrySet()) {
			double need = entry.getValue() * dt;
			if (need <= 0) {
				continue;
			}
			inputFraction = Math.min(inputFraction, fluidInput.getDouble(entry.getKey()) / need);
		}
		double inEnergy = in.energy() * dt;
		if (inEnergy > 0) {
			inputFraction = Math.min(inputFraction, energyInput / inEnergy);
		}
		double outputFraction = 1.0;
		for (Map.Entry<ItemVariant, Double> entry : out.items().entrySet()) {
			double produce = entry.getValue() * throttle * dt;
			if (produce <= 0) {
				continue;
			}
			outputFraction = Math.min(
					outputFraction,
					(SpaceFoldConfig.repeaterItemBuffer - itemOutput.getDouble(entry.getKey())) / produce);
		}
		for (Map.Entry<FluidVariant, Double> entry : out.fluids().entrySet()) {
			double produce = entry.getValue() * throttle * dt;
			if (produce <= 0) {
				continue;
			}
			outputFraction = Math.min(
					outputFraction,
					(SpaceFoldConfig.repeaterFluidBuffer - fluidOutput.getDouble(entry.getKey())) / produce);
		}
		double outEnergy = out.energy() * throttle * dt;
		if (outEnergy > 0) {
			outputFraction = Math.min(outputFraction, (energyCapacity() - energyOutput) / outEnergy);
		}
		if (inputFraction < 1.0 - 1.0E-9 || outputFraction < 1.0 - 1.0E-9) {
			secondSatisfied = false;
		} else {
			for (Map.Entry<ItemVariant, Double> entry : in.items().entrySet()) {
				add(itemInput, entry.getKey(), -entry.getValue() * dt);
			}
			for (Map.Entry<FluidVariant, Double> entry : in.fluids().entrySet()) {
				add(fluidInput, entry.getKey(), -entry.getValue() * dt);
			}
			energyInput -= in.energy() * dt;
			for (Map.Entry<ItemVariant, Double> entry : out.items().entrySet()) {
				add(itemOutput, entry.getKey(), entry.getValue() * throttle * dt);
			}
			for (Map.Entry<FluidVariant, Double> entry : out.fluids().entrySet()) {
				add(fluidOutput, entry.getKey(), entry.getValue() * throttle * dt);
			}
			energyOutput += out.energy() * throttle * dt;
			setChanged();
		}
		secondTicks++;
		if (secondTicks >= 20) {
			secondTicks = 0;
			if (!unthrottled) {
				double step = (secondSatisfied ? SpaceFoldConfig.repeaterRampUpPercent : -SpaceFoldConfig.repeaterRampDownPercent) / 100.0;
				double updated = Math.max(0.0, Math.min(1.0, throttle + step));
				if (updated != throttle) {
					throttle = updated;
					setChanged();
				}
			}
			secondSatisfied = true;
		}
	}

	private void push(Level level) {
		Direction targetSide = facing();
		BlockPos target = worldPosition.relative(targetSide);
		Direction side = targetSide.getOpposite();
		pushItems(level, target, side);
		pushFluids(level, target, side);
		pushEnergy(level, target, side);
	}

	private void pushItems(Level level, BlockPos target, Direction side) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, side);
		if (storage == null) {
			return;
		}
		List<ItemVariant> keys = new ArrayList<>();
		for (Object2DoubleMap.Entry<ItemVariant> entry : itemOutput.object2DoubleEntrySet()) {
			if (entry.getDoubleValue() >= 1) {
				keys.add(entry.getKey());
			}
		}
		if (keys.isEmpty()) {
			return;
		}
		Map<ItemVariant, Long> moved = new HashMap<>();
		try (Transaction transaction = Transaction.openOuter()) {
			for (ItemVariant variant : keys) {
				long inserted = storage.insert(variant, (long) itemOutput.getDouble(variant), transaction);
				if (inserted > 0) {
					moved.merge(variant, inserted, Long::sum);
				}
			}
			transaction.commit();
		}
		moved.forEach((variant, amount) -> add(itemOutput, variant, -amount));
	}

	private void pushFluids(Level level, BlockPos target, Direction side) {
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, target, side);
		if (storage == null) {
			return;
		}
		List<FluidVariant> keys = new ArrayList<>();
		for (Object2DoubleMap.Entry<FluidVariant> entry : fluidOutput.object2DoubleEntrySet()) {
			if (entry.getDoubleValue() >= 1) {
				keys.add(entry.getKey());
			}
		}
		if (keys.isEmpty()) {
			return;
		}
		Map<FluidVariant, Long> moved = new HashMap<>();
		try (Transaction transaction = Transaction.openOuter()) {
			for (FluidVariant variant : keys) {
				long inserted = storage.insert(variant, (long) fluidOutput.getDouble(variant), transaction);
				if (inserted > 0) {
					moved.merge(variant, inserted, Long::sum);
				}
			}
			transaction.commit();
		}
		moved.forEach((variant, amount) -> add(fluidOutput, variant, -amount));
	}

	private void pushEnergy(Level level, BlockPos target, Direction side) {
		long amount = (long) energyOutput;
		if (amount <= 0) {
			return;
		}
		EnergyStorage storage = EnergyStorage.SIDED.find(level, target, side);
		if (storage == null) {
			return;
		}
		long inserted;
		try (Transaction transaction = Transaction.openOuter()) {
			inserted = storage.insert(amount, transaction);
			transaction.commit();
		}
		if (inserted > 0) {
			energyOutput -= inserted;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, name);
		output.storeNullable("result", SimulationResult.CODEC, result);
		output.store("paints", RepeaterPaints.CODEC, paints);
		output.store("item_input", SimulationResult.Rates.CODEC, itemRates(itemInput));
		output.store("item_output", SimulationResult.Rates.CODEC, itemRates(itemOutput));
		output.store("fluid_input", SimulationResult.Rates.CODEC, fluidRates(fluidInput));
		output.store("fluid_output", SimulationResult.Rates.CODEC, fluidRates(fluidOutput));
		output.store("energy_input", SimulationResult.Rates.CODEC, new SimulationResult.Rates(Map.of(), Map.of(), energyInput));
		output.store("energy_output", SimulationResult.Rates.CODEC, new SimulationResult.Rates(Map.of(), Map.of(), energyOutput));
		output.putDouble("throttle", throttle);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		name = parseCustomNameSafe(input, "CustomName");
		result = input.read("result", SimulationResult.CODEC).orElse(null);
		paints = input.read("paints", RepeaterPaints.CODEC).orElse(RepeaterPaints.EMPTY);
		input.read("item_input", SimulationResult.Rates.CODEC).ifPresent(rates -> itemInput.putAll(rates.items()));
		input.read("item_output", SimulationResult.Rates.CODEC).ifPresent(rates -> itemOutput.putAll(rates.items()));
		input.read("fluid_input", SimulationResult.Rates.CODEC).ifPresent(rates -> fluidInput.putAll(rates.fluids()));
		input.read("fluid_output", SimulationResult.Rates.CODEC).ifPresent(rates -> fluidOutput.putAll(rates.fluids()));
		input.read("energy_input", SimulationResult.Rates.CODEC).ifPresent(rates -> energyInput = rates.energy());
		input.read("energy_output", SimulationResult.Rates.CODEC).ifPresent(rates -> energyOutput = rates.energy());
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
		valueOutput.store("paints", RepeaterPaints.CODEC, paints);
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
