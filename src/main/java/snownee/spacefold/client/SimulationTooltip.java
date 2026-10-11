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
		return of(result, true, true);
	}

	public static @Nullable SimulationTooltip of(SimulationResult result, boolean showEntries, boolean showSnapshot) {
		List<Entry> inputs = showEntries ? toEntries(result.inputs()) : List.of();
		List<Entry> outputs = showEntries ? toEntries(result.outputs()) : List.of();
		int snapshotId = showSnapshot ? result.snapshotId() : SimulationResult.NO_SNAPSHOT;
		if (inputs.isEmpty() && outputs.isEmpty() && snapshotId == SimulationResult.NO_SNAPSHOT) {
			return null;
		}
		return new SimulationTooltip(inputs, outputs, snapshotId);
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
