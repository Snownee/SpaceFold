package snownee.spacefold.block.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import snownee.kiwi.block.entity.ModBlockEntity;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.SViewDetailsPacket;
import snownee.spacefold.paint.RepeaterPaints;
import snownee.spacefold.particle.BeamParticleOptions;
import team.reborn.energy.api.EnergyStorage;

public class FactoryBlockEntity extends ModBlockEntity implements Container {
	public static final int TEMPLATE_SLOT = 0;
	public static final int MATERIAL_SLOT = 1;

	public static final int ERROR_NONE = 0;
	public static final int ERROR_INVALID = 1;
	public static final int ERROR_ROOM_SIDE = 2;
	public static final int ERROR_NO_RESULT = 3;
	public static final int ERROR_FAILED = 4;
	public static final int ERROR_NO_RECEIVER = 5;
	public static final int ERROR_OBSTRUCTED = 6;

	private ItemStack item = ItemStack.EMPTY;
	private final Object2LongMap<ItemVariant> materials = new Object2LongOpenHashMap<>();
	private final Storage<ItemVariant> itemStorage = new FactoryItemStorage();
	private final EnergyStorage energyStorage = new FactoryEnergyStorage();
	private long energy;
	private int progress;
	private int cooldown;
	private int errorCode;
	private long shakeTime = -1;
	private long progressTime = -1;
	private @Nullable BlockPos cachedReceiverPos;
	private @Nullable SimulationResult templateResult;
	private ItemStack pendingItem = ItemStack.EMPTY;
	private int pendingTicks;
	private boolean templateDirty = true;

	public FactoryBlockEntity(BlockPos pos, BlockState state) {
		super(CoreModule.FACTORY_BE.get(), pos, state);
	}

	private static RepeaterPaints paintsOf(ItemStack stack) {
		RepeaterPaints paints = stack.get(CoreModule.REPEATER_PAINT.get());
		return paints == null ? RepeaterPaints.EMPTY : paints;
	}

	public static boolean isTemplateStack(ItemStack stack) {
		return CoreModule.REPEATER.is(stack) || CoreModule.SIMULATOR.is(stack);
	}

	public int getErrorCode() {
		return errorCode;
	}

	public int getProgress() {
		return progress;
	}

	public long getShakeTime() {
		return shakeTime;
	}

	public long getProgressTime() {
		return progressTime;
	}

	private void shake() {
		shakeTime = level.getGameTime();
		refresh();
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
		if (!pendingItem.isEmpty()) {
			if (--pendingTicks <= 0) {
				ItemStack stack = pendingItem;
				pendingItem = ItemStack.EMPTY;
				setItem(TEMPLATE_SLOT, stack);
				shake();
				level.playSound(
						null,
						getBlockPos(),
						SoundEvents.AMETHYST_CLUSTER_PLACE,
						SoundSource.BLOCKS,
						1.0F,
						0.9F + level.getRandom().nextFloat() * 0.2F);
			}
			return;
		}
		if (templateDirty) {
			templateDirty = false;
			resolveTemplate();
			refresh();
		}
		if (templateResult == null || (errorCode != ERROR_NONE && errorCode != ERROR_NO_RECEIVER && errorCode != ERROR_OBSTRUCTED)) {
			return;
		}
		if (energy < SpaceFoldConfig.factoryEnergyPerTick) {
			return;
		}
		int duration = Math.max(1, SpaceFoldConfig.factoryDurationTicks);
		if (progress + 1 >= duration) {
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			FactoryBlockEntity receiver = findEmptyFactory();
			if (receiver == null) {
				cooldown = 10;
				return;
			}
			if (!consumeProgress(progress + 1)) {
				return;
			}
			progress = 0;
			produce(receiver);
		} else {
			if (!consumeProgress(progress + 1)) {
				return;
			}
			progress++;
			progressTime = level.getGameTime();
			refresh();
		}
		setChanged();
	}

	private @Nullable FactoryBlockEntity findEmptyFactory() {
		if (level == null || level.isClientSide()) {
			return null;
		}
		if (cachedReceiverPos != null && level.isLoaded(cachedReceiverPos) &&
				level.getBlockEntity(cachedReceiverPos) instanceof FactoryBlockEntity cached && cached.isEmpty() &&
				hasClearPath(cachedReceiverPos)) {
			errorCode = ERROR_NONE;
			return cached;
		}
		BlockPos origin = getBlockPos();
		FactoryBlockEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		boolean obstructed = false;
		Predicate<Holder<PoiType>> predicate = holder -> CoreModule.FACTORY_POI.is(holder.value());
		for (BlockPos candidate : ((ServerLevel) level).getPoiManager()
				.findAll(predicate, pos -> !pos.equals(origin), origin, 10, PoiManager.Occupancy.ANY)
				.toList()) {
			if (!(level.getBlockEntity(candidate) instanceof FactoryBlockEntity factory) || !factory.isEmpty()) {
				continue;
			}
			if (!hasClearPath(candidate)) {
				obstructed = true;
				continue;
			}
			double distance = candidate.distSqr(origin);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = factory;
			}
		}
		cachedReceiverPos = nearest == null ? null : nearest.getBlockPos();
		if (nearest == null) {
			errorCode = obstructed ? ERROR_OBSTRUCTED : ERROR_NO_RECEIVER;
		} else {
			errorCode = ERROR_NONE;
		}
		return nearest;
	}

	private boolean hasClearPath(BlockPos receiverPos) {
		Vec3 from = Vec3.atCenterOf(getBlockPos().above());
		Vec3 to = Vec3.atCenterOf(receiverPos.above());
		BlockHitResult hit = level.clip(new ClipContext(
				from,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	private long requiredAmount(ItemVariant variant) {
		if (templateResult == null) {
			return 0;
		}
		long total = 0;
		for (SimulationResult.Material material : templateResult.materials()) {
			if (material.item().equals(variant)) {
				total += material.count();
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

	public int templateSnapshotId() {
		return templateResult == null ? SimulationResult.NO_SNAPSHOT : templateResult.snapshotId();
	}

	public List<SViewDetailsPacket.Entry> materialProgresses() {
		if (templateResult == null) {
			return List.of();
		}
		List<SViewDetailsPacket.Entry> entries = new ArrayList<>();
		for (SimulationResult.Material material : templateResult.materials()) {
			long total = material.count();
			long provided = Math.min(total, materials.getLong(material.item()) + consumedAmount(material.item()));
			entries.add(new SViewDetailsPacket.Entry(material.item(), provided, total));
		}
		return entries;
	}

	private boolean consumeProgress(int nextProgress) {
		int duration = Math.max(1, SpaceFoldConfig.factoryDurationTicks);
		Object2LongMap<ItemVariant> deltas = new Object2LongOpenHashMap<>();
		for (SimulationResult.Material material : templateResult.materials()) {
			long total = material.count();
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
		energy -= SpaceFoldConfig.factoryEnergyPerTick;
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
		errorCode = ERROR_NONE;
		progress = 0;
		if (!item.isEmpty()) {
			if (!isTemplateStack(item)) {
				errorCode = ERROR_INVALID;
			} else if (item.getOrDefault(CoreModule.ROOM_SIDE.get(), false)) {
				errorCode = ERROR_ROOM_SIDE;
			} else {
				SimulationResult result = item.get(CoreModule.SIMULATION_RESULT.get());
				if (result == null) {
					errorCode = ERROR_NO_RESULT;
				} else if (result.failed()) {
					errorCode = ERROR_FAILED;
				} else {
					templateResult = result;
				}
			}
		}
		pruneMaterials();
	}

	public boolean creativeInstantProduce() {
		if ((errorCode != ERROR_NONE && errorCode != ERROR_NO_RECEIVER && errorCode != ERROR_OBSTRUCTED) || templateResult == null) {
			return false;
		}
		FactoryBlockEntity receiver = findEmptyFactory();
		if (receiver == null) {
			return false;
		}
		progress = 0;
		produce(receiver);
		return true;
	}

	public void acceptPending(ItemStack stack, int ticks) {
		pendingItem = stack;
		pendingTicks = ticks;
		setChanged();
	}

	private void produce(FactoryBlockEntity receiver) {
		ItemStack made = new ItemStack(CoreModule.REPEATER.get(), 1);
		made.set(CoreModule.SIMULATION_RESULT.get(), templateResult);
		Component name = item.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			made.set(DataComponents.CUSTOM_NAME, name);
		}
		RepeaterPaints paints = paintsOf(item);
		if (!paints.isEmpty()) {
			made.set(CoreModule.REPEATER_PAINT.get(), paints);
		}
		int duration = 10;
		receiver.acceptPending(made, duration);
		shake();
		level.playSound(
				null,
				getBlockPos(),
				SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.BLOCKS,
				1.0F,
				0.8F + level.getRandom().nextFloat() * 0.4F);
		Vec3 from = Vec3.atCenterOf(getBlockPos().above());
		Vec3 to = Vec3.atCenterOf(receiver.getBlockPos().above());
		((ServerLevel) level).sendParticles(new BeamParticleOptions(from, to, duration), from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	@Override
	public int getContainerSize() {
		return 2;
	}

	@Override
	public int getMaxStackSize() {
		return item.isEmpty() ? Container.super.getMaxStackSize() : 1;
	}

	@Override
	public boolean isEmpty() {
		return item.isEmpty() && pendingItem.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == TEMPLATE_SLOT ? item : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		if (slot != TEMPLATE_SLOT || item.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack result;
		if (item.getCount() <= count) {
			result = item;
			item = ItemStack.EMPTY;
		} else {
			result = item.copyWithCount(count);
			item.shrink(count);
		}
		templateDirty = true;
		refresh();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot != TEMPLATE_SLOT) {
			return ItemStack.EMPTY;
		}
		ItemStack result = item;
		item = ItemStack.EMPTY;
		templateDirty = true;
		refresh();
		return result;
	}

	@Override
	public void setItem(int slot, ItemStack itemStack) {
		if (slot == MATERIAL_SLOT) {
			if (!itemStack.isEmpty()) {
				insertMaterial(ItemVariant.of(itemStack), itemStack.getCount());
			}
			return;
		}
		if (!pendingItem.isEmpty()) {
			return;
		}
		this.item = itemStack;
		templateDirty = true;
		refresh();
	}

	@Override
	public void clearContent() {
		item = ItemStack.EMPTY;
		templateDirty = true;
		refresh();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack itemStack) {
		if (slot == TEMPLATE_SLOT) {
			return item.isEmpty() && pendingItem.isEmpty();
		}
		if (slot == MATERIAL_SLOT) {
			return requiredAmount(ItemVariant.of(itemStack)) > 0;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("template", ItemStack.OPTIONAL_CODEC, item);
		output.putLong("energy", energy);
		output.putInt("progress", progress);
		List<SimulationResult.Material> storedMaterials = new ArrayList<>();
		for (Object2LongMap.Entry<ItemVariant> entry : materials.object2LongEntrySet()) {
			storedMaterials.add(new SimulationResult.Material(entry.getKey(), (int) entry.getLongValue()));
		}
		output.store("materials", SimulationResult.Material.CODEC.listOf(), storedMaterials);
		output.store("pendingItem", ItemStack.OPTIONAL_CODEC, pendingItem);
		output.putInt("pendingTicks", pendingTicks);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		item = input.read("template", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		energy = input.getLongOr("energy", 0);
		int savedProgress = input.getIntOr("progress", 0);
		for (SimulationResult.Material material : input.read("materials", SimulationResult.Material.CODEC.listOf()).orElse(List.of())) {
			materials.put(material.item(), material.count());
		}
		templateDirty = false;
		resolveTemplate();
		progress = errorCode == ERROR_NONE ? savedProgress : 0;
		pendingItem = input.read("pendingItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		pendingTicks = input.getIntOr("pendingTicks", 0);
		shakeTime = input.getLongOr("shakeTime", -1);
		progressTime = input.getLongOr("progressTime", -1);
	}

	@Override
	protected void readPacketData(ValueInput valueInput) {
		item = valueInput.read("template", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		progress = valueInput.getIntOr("progress", 0);
		shakeTime = valueInput.getLongOr("shakeTime", -1);
		progressTime = valueInput.getLongOr("progressTime", -1);
		templateDirty = true;
	}

	@Override
	protected void writePacketData(ValueOutput valueOutput) {
		valueOutput.store("template", ItemStack.OPTIONAL_CODEC, item);
		valueOutput.putInt("progress", progress);
		valueOutput.putLong("shakeTime", shakeTime);
		valueOutput.putLong("progressTime", progressTime);
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
			item = incoming;
			templateDirty = true;
			refresh();
			return 1;
		}

		@Override
		public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
			if (item.isEmpty() || !ItemVariant.of(item).equals(resource)) {
				return 0;
			}
			long moved = Math.min(maxAmount, item.getCount());
			if (moved <= 0) {
				return 0;
			}
			item.shrink((int) moved);
			if (item.isEmpty()) {
				item = ItemStack.EMPTY;
			}
			templateDirty = true;
			refresh();
			return moved;
		}

		@Override
		public Iterator<StorageView<ItemVariant>> iterator() {
			List<StorageView<ItemVariant>> views = new ArrayList<>();
			if (!item.isEmpty()) {
				views.add(new StorageView<>() {
					@Override
					public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
						return FactoryItemStorage.this.extract(resource, maxAmount, transaction);
					}

					@Override
					public boolean isResourceBlank() {
						return false;
					}

					@Override
					public ItemVariant getResource() {
						return ItemVariant.of(item);
					}

					@Override
					public long getAmount() {
						return item.getCount();
					}

					@Override
					public long getCapacity() {
						return 1;
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
