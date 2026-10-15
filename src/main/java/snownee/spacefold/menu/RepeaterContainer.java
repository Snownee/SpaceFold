package snownee.spacefold.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import snownee.spacefold.CoreModule;
import snownee.spacefold.dim.SimulationResult;

public class RepeaterContainer implements Container {
	public static final int BUCKET_AMOUNT = (int) FluidConstants.BUCKET;

	private final NonNullList<ItemStack> items = NonNullList.withSize(RepeaterMenu.SIZE, ItemStack.EMPTY);
	private final @Nullable ItemStack source;
	private final @Nullable SimulationResult base;
	private boolean truncated;

	public RepeaterContainer(@Nullable ItemStack source) {
		this.source = source;
		this.base = source == null ? null : source.get(CoreModule.SIMULATION_RESULT.get());
		if (base != null) {
			fillRates(RepeaterMenu.INPUT_SIGN + 1, base.inputs());
			fillRates(RepeaterMenu.OUTPUT_SIGN + 1, base.outputs());
			fillMaterials(RepeaterMenu.MATERIAL_SIGN + 1, base.materials());
		}
	}

	public static boolean isSign(int slot) {
		return slot == RepeaterMenu.INPUT_SIGN || slot == RepeaterMenu.OUTPUT_SIGN || slot == RepeaterMenu.MATERIAL_SIGN;
	}

	public static ItemStack signStack(int slot) {
		String key = slot == RepeaterMenu.INPUT_SIGN ? "gui.spacefold.inputs"
				: slot == RepeaterMenu.OUTPUT_SIGN ? "gui.spacefold.outputs"
				  : "gui.spacefold.materials";
		ItemStack stack = new ItemStack(Items.OAK_SIGN);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(key));
		return stack;
	}

	public boolean wasTruncated() {
		return truncated;
	}

	private void fillRates(int start, SimulationResult.Rates rates) {
		int end = start + RepeaterMenu.SECTION_SLOTS;
		int slot = start;
		for (Object2DoubleMap.Entry<ItemVariant> entry : rates.items().object2DoubleEntrySet()) {
			slot = fillVariant(end, slot, entry.getKey(), (int) Math.round(entry.getDoubleValue()));
		}
		for (Object2DoubleMap.Entry<FluidVariant> entry : rates.fluids().object2DoubleEntrySet()) {
			double rate = entry.getDoubleValue();
			if (rate <= 0) {
				continue;
			}
			Item bucket = entry.getKey().getFluid().getBucket();
			if (bucket == Items.AIR) {
				continue;
			}
			int buckets = Math.max(1, (int) Math.round(rate / BUCKET_AMOUNT));
			slot = fillVariant(end, slot, ItemVariant.of(bucket), buckets);
		}
	}

	private void fillMaterials(int start, List<SimulationResult.Material> materials) {
		int end = start + RepeaterMenu.SECTION_SLOTS;
		int slot = start;
		for (SimulationResult.Material material : materials) {
			slot = fillVariant(end, slot, material.item(), material.count());
		}
	}

	private int fillVariant(int end, int slot, ItemVariant variant, int count) {
		ItemStack probe = variant.toStack(1);
		int max = probe.getMaxStackSize();
		int remaining = Math.max(1, count);
		while (remaining > 0 && slot < end) {
			int amount = Math.min(remaining, max);
			items.set(slot, probe.copyWithCount(amount));
			remaining -= amount;
			slot++;
		}
		if (remaining > 0) {
			truncated = true;
		}
		return slot;
	}

	private static @Nullable FluidVariant fluidOf(ItemStack stack) {
		if (stack.getItem() instanceof BucketItem bucket) {
			Fluid fluid = bucket.getContent();
			if (fluid != Fluids.EMPTY) {
				return FluidVariant.of(fluid);
			}
		}
		return null;
	}

	private SimulationResult.Rates collectRates(int start) {
		Object2DoubleMap<ItemVariant> itemRates = new Object2DoubleLinkedOpenHashMap<>();
		Object2DoubleMap<FluidVariant> fluidRates = new Object2DoubleLinkedOpenHashMap<>();
		for (int slot = start; slot < start + RepeaterMenu.SECTION_SLOTS; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			FluidVariant fluid = fluidOf(stack);
			if (fluid != null) {
				fluidRates.mergeDouble(fluid, stack.getCount() * (double) BUCKET_AMOUNT, Double::sum);
			} else {
				itemRates.mergeDouble(ItemVariant.of(stack), stack.getCount(), Double::sum);
			}
		}
		return new SimulationResult.Rates(itemRates, fluidRates, 0);
	}

	private List<SimulationResult.Material> collectMaterials(int start) {
		Map<ItemVariant, Integer> map = new LinkedHashMap<>();
		for (int slot = start; slot < start + RepeaterMenu.SECTION_SLOTS; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			map.merge(ItemVariant.of(stack), stack.getCount(), Integer::sum);
		}
		List<SimulationResult.Material> materials = new ArrayList<>();
		for (Map.Entry<ItemVariant, Integer> entry : map.entrySet()) {
			materials.add(new SimulationResult.Material(entry.getKey(), entry.getValue()));
		}
		return materials;
	}

	private void rebuild() {
		setChanged();
		if (source == null) {
			return;
		}
		SimulationResult.Rates inputs = collectRates(RepeaterMenu.INPUT_SIGN + 1);
		SimulationResult.Rates outputs = collectRates(RepeaterMenu.OUTPUT_SIGN + 1);
		source.set(
				CoreModule.SIMULATION_RESULT.get(),
				new SimulationResult(
						new SimulationResult.Rates(inputs.items(), inputs.fluids(), base == null ? 0 : base.inputs().energy()),
						new SimulationResult.Rates(outputs.items(), outputs.fluids(), base == null ? 0 : base.outputs().energy()),
						collectMaterials(RepeaterMenu.MATERIAL_SIGN + 1),
						Optional.empty(),
						base == null ? 1 : base.tier(),
						base == null ? SimulationResult.NO_SNAPSHOT : base.snapshotId()));
	}

	@Override
	public int getContainerSize() {
		return RepeaterMenu.SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (isSign(slot)) {
			return signStack(slot);
		}
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		if (isSign(slot) || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = items.get(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = stack.split(amount);
		rebuild();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (isSign(slot)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = items.get(slot);
		items.set(slot, ItemStack.EMPTY);
		rebuild();
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (isSign(slot)) {
			return;
		}
		items.set(slot, stack);
		rebuild();
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return source == null
				|| player.getInventory().contains(source)
				|| player.getMainHandItem() == source
				|| player.getOffhandItem() == source;
	}

	@Override
	public void clearContent() {
		for (int slot = 0; slot < RepeaterMenu.SIZE; slot++) {
			if (!isSign(slot)) {
				items.set(slot, ItemStack.EMPTY);
			}
		}
		rebuild();
	}
}
