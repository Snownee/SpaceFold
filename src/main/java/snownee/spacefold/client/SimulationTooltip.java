package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.dim.SimulationResult;

public record SimulationTooltip(List<Entry> inputs, List<Entry> outputs, int snapshotId) implements TooltipComponent {
	public static @Nullable SimulationTooltip of(SimulationResult result) {
		List<Entry> inputs = toEntries(result.inputs());
		List<Entry> outputs = toEntries(result.outputs());
		if (inputs.isEmpty() && outputs.isEmpty() && !result.hasSnapshot()) {
			return null;
		}
		return new SimulationTooltip(inputs, outputs, result.snapshotId());
	}

	private static List<Entry> toEntries(SimulationResult.Rates rates) {
		List<Entry> entries = new ArrayList<>();
		rates.items().forEach((item, rate) -> entries.add(new Entry.Item(item.toStack(), rate)));
		rates.fluids().forEach((fluid, rate) -> entries.add(new Entry.Fluid(fluid, rate)));
		return entries;
	}

	public sealed interface Entry {
		double rate();

		record Item(ItemStack stack, double rate) implements Entry {
		}

		record Fluid(FluidVariant fluid, double rate) implements Entry {
		}
	}
}
