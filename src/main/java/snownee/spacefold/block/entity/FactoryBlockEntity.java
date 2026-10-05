package snownee.spacefold.block.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import snownee.kiwi.block.entity.ModBlockEntity;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.SFactoryMaterialsPacket;
import snownee.spacefold.paint.FacePaint;
import snownee.spacefold.paint.RepeaterPaints;
import team.reborn.energy.api.EnergyStorage;

public class FactoryBlockEntity extends ModBlockEntity implements Container {
	public static final int TEMPLATE_SLOT = 0;
	public static final int OUTPUT_SLOT = 1;

	public static final int ERROR_NONE = 0;
	public static final int ERROR_INVALID = 1;
	public static final int ERROR_ROOM_SIDE = 2;
	public static final int ERROR_NO_RESULT = 3;
	public static final int ERROR_FAILED = 4;

	private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private final Object2LongMap<ItemVariant> materials = new Object2LongOpenHashMap<>();
	private final Storage<ItemVariant> itemStorage = new FactoryItemStorage();
	private final EnergyStorage energyStorage = new FactoryEnergyStorage();
	private long energy;
	private int progress;
	private int errorCode;
	private @Nullable SimulationResult templateResult;
	private @Nullable Component templateName;
	private Map<Direction, FacePaint> templatePaints = RepeaterPaints.EMPTY;
	private boolean templateDirty = true;

	public FactoryBlockEntity(BlockPos pos, BlockState state) {
		super(CoreModule.FACTORY_BE.get(), pos, state);
	}

	private static Map<Direction, FacePaint> paintsOf(ItemStack stack) {
		Map<Direction, FacePaint> paints = stack.get(CoreModule.REPEATER_PAINT.get());
		return paints == null ? RepeaterPaints.EMPTY : paints;
	}

	public static boolean isTemplateStack(ItemStack stack) {
		return isRepeater(stack) || isSimulator(stack);
	}

	private static boolean isRepeater(ItemStack stack) {
		return stack.getItem() == CoreModule.REPEATER.get().asItem();
	}

	private static boolean isSimulator(ItemStack stack) {
		return stack.getItem() == CoreModule.SIMULATOR.get().asItem();
	}

	public int getErrorCode() {
		return errorCode;
	}

	public ContainerData data() {
		return new ContainerData() {
			@Override
			public int get(int dataId) {
				return dataId == 0 ? progress : errorCode;
			}

			@Override
			public void set(int dataId, int value) {
				if (dataId == 0) {
					progress = value;
				} else {
					errorCode = value;
				}
			}

			@Override
			public int getCount() {
				return 2;
			}
		};
	}

	public @Nullable Storage<ItemVariant> getItemStorage(@Nullable Direction side) {
		return itemStorage;
	}

	public @Nullable EnergyStorage getEnergyStorage(@Nullable Direction side) {
		return energyStorage;
	}

	public void tick() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (templateDirty) {
			templateDirty = false;
			resolveTemplate();
		}
		if (errorCode != ERROR_NONE || templateResult == null) {
			return;
		}
		if (!canProduce()) {
			return;
		}
		if (energy < SpaceFoldConfig.factoryEnergyPerCraft) {
			return;
		}
		if (!consumeProgress(progress + 1)) {
			return;
		}
		progress++;
		int duration = Math.max(1, SpaceFoldConfig.factoryDurationTicks);
		if (progress >= duration) {
			progress = 0;
			energy -= SpaceFoldConfig.factoryEnergyPerCraft;
			produce();
		}
		setChanged();
	}

	private long requiredAmount(ItemVariant variant) {
		if (templateResult == null) {
			return 0;
		}
		long total = 0;
		int amount = Math.max(1, SpaceFoldConfig.factoryOutputPerCraft);
		for (SimulationResult.Material material : templateResult.materials()) {
			if (material.item().equals(variant)) {
				total += (long) material.count() * amount;
			}
		}
		return total;
	}

	private long consumedAmount(ItemVariant variant) {
		long total = requiredAmount(variant);
		if (total <= 0) {
			return 0;
		}
		int duration = Math.max(1, SpaceFoldConfig.factoryDurationTicks);
		return total * progress / duration;
	}

	public List<SFactoryMaterialsPacket.Entry> materialProgresses() {
		if (templateResult == null) {
			return List.of();
		}
		int amount = Math.max(1, SpaceFoldConfig.factoryOutputPerCraft);
		List<SFactoryMaterialsPacket.Entry> entries = new ArrayList<>();
		for (SimulationResult.Material material : templateResult.materials()) {
			long total = (long) material.count() * amount;
			long provided = Math.min(total, materials.getLong(material.item()) + consumedAmount(material.item()));
			entries.add(new SFactoryMaterialsPacket.Entry(material.item(), provided, total));
		}
		return entries;
	}

	private boolean consumeProgress(int nextProgress) {
		int duration = Math.max(1, SpaceFoldConfig.factoryDurationTicks);
		int amount = Math.max(1, SpaceFoldConfig.factoryOutputPerCraft);
		Object2LongMap<ItemVariant> deltas = new Object2LongOpenHashMap<>();
		for (SimulationResult.Material material : templateResult.materials()) {
			long total = (long) material.count() * amount;
			long delta = total * nextProgress / duration - total * (nextProgress - 1) / duration;
			if (delta > 0) {
				deltas.mergeLong(material.item(), delta, Long::sum);
			}
		}
		for (Object2LongMap.Entry<ItemVariant> entry : deltas.object2LongEntrySet()) {
			if (materials.getLong(entry.getKey()) < entry.getLongValue()) {
				return false;
			}
		}
		for (Object2LongMap.Entry<ItemVariant> entry : deltas.object2LongEntrySet()) {
			materials.mergeLong(entry.getKey(), -entry.getLongValue(), Long::sum);
		}
		return true;
	}

	private void pruneMaterials() {
		Object2LongMap<ItemVariant> kept = new Object2LongOpenHashMap<>();
		for (Object2LongMap.Entry<ItemVariant> entry : materials.object2LongEntrySet()) {
			long cap = requiredAmount(entry.getKey());
			if (cap > 0) {
				kept.put(entry.getKey(), Math.min(entry.getLongValue(), cap));
			}
		}
		materials.clear();
		for (Object2LongMap.Entry<ItemVariant> entry : kept.object2LongEntrySet()) {
			materials.put(entry.getKey(), entry.getLongValue());
		}
	}

	private long insertMaterial(ItemVariant resource, long maxAmount) {
		long cap = requiredAmount(resource) - consumedAmount(resource);
		if (cap <= 0) {
			return 0;
		}
		long space = cap - materials.getLong(resource);
		if (space <= 0) {
			return 0;
		}
		long moved = Math.min(maxAmount, space);
		if (moved <= 0) {
			return 0;
		}
		materials.mergeLong(resource, moved, Long::sum);
		setChanged();
		return moved;
	}

	private void resolveTemplate() {
		templateResult = null;
		templateName = null;
		templatePaints = RepeaterPaints.EMPTY;
		errorCode = ERROR_NONE;
		progress = 0;
		ItemStack stack = items.get(TEMPLATE_SLOT);
		if (!stack.isEmpty()) {
			if (!isTemplateStack(stack)) {
				errorCode = ERROR_INVALID;
			} else if (stack.getOrDefault(CoreModule.ROOM_SIDE.get(), false)) {
				errorCode = ERROR_ROOM_SIDE;
			} else {
				SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
				if (result == null) {
					errorCode = ERROR_NO_RESULT;
				} else if (result.failed()) {
					errorCode = ERROR_FAILED;
				} else {
					templateResult = result;
					templateName = stack.get(DataComponents.CUSTOM_NAME);
					templatePaints = paintsOf(stack);
				}
			}
		}
		pruneMaterials();
	}

	private boolean canProduce() {
		ItemStack output = items.get(OUTPUT_SLOT);
		int amount = Math.max(1, SpaceFoldConfig.factoryOutputPerCraft);
		int max = new ItemStack(CoreModule.REPEATER.get()).getMaxStackSize();
		if (output.isEmpty()) {
			return amount <= max;
		}
		if (output.getItem() != CoreModule.REPEATER.get().asItem()) {
			return false;
		}
		if (!Objects.equals(output.get(CoreModule.SIMULATION_RESULT.get()), templateResult)) {
			return false;
		}
		if (!Objects.equals(output.get(DataComponents.CUSTOM_NAME), templateName)) {
			return false;
		}
		if (!Objects.equals(paintsOf(output), templatePaints)) {
			return false;
		}
		return output.getCount() + amount <= max;
	}

	private void produce() {
		int amount = Math.max(1, SpaceFoldConfig.factoryOutputPerCraft);
		ItemStack output = items.get(OUTPUT_SLOT);
		if (output.isEmpty()) {
			ItemStack made = new ItemStack(CoreModule.REPEATER.get(), amount);
			made.set(CoreModule.SIMULATION_RESULT.get(), templateResult);
			if (templateName != null) {
				made.set(DataComponents.CUSTOM_NAME, templateName);
			}
			if (!templatePaints.isEmpty()) {
				made.set(CoreModule.REPEATER_PAINT.get(), templatePaints);
			}
			items.set(OUTPUT_SLOT, made);
		} else {
			output.grow(amount);
		}
		setChanged();
	}

	@Override
	public int getContainerSize() {
		return 2;
	}

	@Override
	public boolean isEmpty() {
		return items.get(0).isEmpty() && items.get(1).isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack stack = items.get(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack result;
		if (stack.getCount() <= count) {
			result = stack;
			items.set(slot, ItemStack.EMPTY);
		} else {
			result = stack.copyWithCount(count);
			stack.shrink(count);
		}
		if (slot == TEMPLATE_SLOT) {
			templateDirty = true;
		}
		setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack stack = items.get(slot);
		items.set(slot, ItemStack.EMPTY);
		if (slot == TEMPLATE_SLOT) {
			templateDirty = true;
		}
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (slot == TEMPLATE_SLOT) {
			templateDirty = true;
		}
		setChanged();
	}

	@Override
	public void clearContent() {
		items.set(0, ItemStack.EMPTY);
		items.set(1, ItemStack.EMPTY);
		templateDirty = true;
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == TEMPLATE_SLOT && isTemplateStack(stack);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("template", ItemStack.OPTIONAL_CODEC, items.get(TEMPLATE_SLOT));
		output.store("output", ItemStack.OPTIONAL_CODEC, items.get(OUTPUT_SLOT));
		output.putLong("energy", energy);
		output.putInt("progress", progress);
		List<SimulationResult.Material> storedMaterials = new ArrayList<>();
		for (Object2LongMap.Entry<ItemVariant> entry : materials.object2LongEntrySet()) {
			storedMaterials.add(new SimulationResult.Material(entry.getKey(), (int) entry.getLongValue()));
		}
		output.store("materials", SimulationResult.Material.CODEC.listOf(), storedMaterials);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.set(TEMPLATE_SLOT, input.read("template", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		items.set(OUTPUT_SLOT, input.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		energy = input.getLongOr("energy", 0);
		progress = input.getIntOr("progress", 0);
		for (SimulationResult.Material material : input.read("materials", SimulationResult.Material.CODEC.listOf()).orElse(List.of())) {
			materials.put(material.item(), material.count());
		}
		templateDirty = true;
	}

	@Override
	protected void readPacketData(ValueInput valueInput) {
	}

	@Override
	protected void writePacketData(ValueOutput valueOutput) {
	}

	private final class FactoryItemStorage implements Storage<ItemVariant> {
		@Override
		public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			if (maxAmount <= 0) {
				return 0;
			}
			long materialMoved = insertMaterial(resource, maxAmount);
			if (materialMoved > 0) {
				return materialMoved;
			}
			ItemStack incoming = resource.toStack(1);
			if (!canPlaceItem(TEMPLATE_SLOT, incoming)) {
				return 0;
			}
			ItemStack existing = items.get(TEMPLATE_SLOT);
			long space;
			if (existing.isEmpty()) {
				space = Math.min(maxAmount, incoming.getMaxStackSize());
			} else if (ItemStack.isSameItemSameComponents(existing, incoming)) {
				space = Math.min(maxAmount, existing.getMaxStackSize() - existing.getCount());
			} else {
				return 0;
			}
			if (space <= 0) {
				return 0;
			}
			if (existing.isEmpty()) {
				items.set(TEMPLATE_SLOT, incoming.copyWithCount((int) space));
			} else {
				existing.grow((int) space);
			}
			templateDirty = true;
			setChanged();
			return space;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			ItemStack output = items.get(OUTPUT_SLOT);
			if (output.isEmpty() || !ItemVariant.of(output).equals(resource)) {
				return 0;
			}
			long moved = Math.min(maxAmount, output.getCount());
			if (moved <= 0) {
				return 0;
			}
			output.shrink((int) moved);
			if (output.isEmpty()) {
				items.set(OUTPUT_SLOT, ItemStack.EMPTY);
			}
			setChanged();
			return moved;
		}

		@Override
		public Iterator<StorageView<ItemVariant>> iterator() {
			List<StorageView<ItemVariant>> views = new ArrayList<>();
			for (int slot = 0; slot < items.size(); slot++) {
				ItemStack stack = items.get(slot);
				if (stack.isEmpty()) {
					continue;
				}
				int index = slot;
				views.add(new StorageView<>() {
					@Override
					public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
						if (index == TEMPLATE_SLOT) {
							return 0;
						}
						return FactoryItemStorage.this.extract(resource, maxAmount, transaction);
					}

					@Override
					public boolean isResourceBlank() {
						return false;
					}

					@Override
					public ItemVariant getResource() {
						return ItemVariant.of(stack);
					}

					@Override
					public long getAmount() {
						return stack.getCount();
					}

					@Override
					public long getCapacity() {
						return stack.getMaxStackSize();
					}
				});
			}
			return views.iterator();
		}
	}

	private final class FactoryEnergyStorage implements EnergyStorage {
		@Override
		public long insert(long maxAmount, TransactionContext transaction) {
			long space = SpaceFoldConfig.factoryEnergyBuffer - energy;
			if (space <= 0) {
				return 0;
			}
			long moved = Math.min(maxAmount, space);
			if (moved <= 0) {
				return 0;
			}
			energy += moved;
			setChanged();
			return moved;
		}

		@Override
		public boolean supportsExtraction() {
			return false;
		}

		@Override
		public long extract(long maxAmount, TransactionContext transaction) {
			return 0;
		}

		@Override
		public long getAmount() {
			return energy;
		}

		@Override
		public long getCapacity() {
			return SpaceFoldConfig.factoryEnergyBuffer;
		}
	}
}
