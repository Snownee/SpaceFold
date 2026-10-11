package snownee.spacefold.dim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;

public record SimulationResult(
		Rates inputs,
		Rates outputs,
		List<Material> materials,
		boolean failed,
		Optional<String> failure,
		int tier,
		int snapshotId) {
	public static final int NO_SNAPSHOT = -1;

	public static final Codec<SimulationResult> CODEC = RecordCodecBuilder.create(i -> i.group(
					Rates.CODEC.optionalFieldOf("inputs", Rates.EMPTY).forGetter(SimulationResult::inputs),
					Rates.CODEC.optionalFieldOf("outputs", Rates.EMPTY).forGetter(SimulationResult::outputs),
					Material.CODEC.listOf()
							.optionalFieldOf("materials", List.of())
							.forGetter(SimulationResult::materials),
					Codec.BOOL.optionalFieldOf("failed", false).forGetter(SimulationResult::failed),
					Codec.STRING.optionalFieldOf("failure").forGetter(SimulationResult::failure),
					Codec.INT.optionalFieldOf("tier", 1).forGetter(SimulationResult::tier),
					Codec.INT.optionalFieldOf("snapshot", NO_SNAPSHOT).forGetter(SimulationResult::snapshotId))
			.apply(i, SimulationResult::new));

	public SimulationResult(Rates inputs, Rates outputs, List<Material> materials, boolean failed) {
		this(inputs, outputs, materials, failed, Optional.empty(), 1, NO_SNAPSHOT);
	}

	public SimulationResult(Rates inputs, Rates outputs, List<Material> materials, boolean failed, Optional<String> failure, int tier) {
		this(inputs, outputs, materials, failed, failure, tier, NO_SNAPSHOT);
	}

	public boolean hasSnapshot() {
		return snapshotId != NO_SNAPSHOT;
	}

	public SimulationResult withSnapshotId(int snapshotId) {
		return new SimulationResult(inputs, outputs, materials, failed, failure, tier, snapshotId);
	}

	public static SimulationResult previewOnly(int snapshotId) {
		return new SimulationResult(Rates.EMPTY, Rates.EMPTY, List.of(), false, Optional.empty(), 1, snapshotId);
	}

	public record Material(ItemVariant item, int count) {
		public static final Codec<Material> CODEC = RecordCodecBuilder.create(i -> i.group(
						ItemVariant.CODEC.fieldOf("item")
								.forGetter(Material::item),
						Codec.INT.fieldOf("count")
								.forGetter(Material::count))
				.apply(i, Material::new));
	}

	public record Rates(Map<ItemVariant, Double> items, Map<FluidVariant, Double> fluids, double energy) {
		public static final Rates EMPTY = new Rates(Map.of(), Map.of(), 0);
		public static final Codec<Rates> CODEC = RecordCodecBuilder.create(i -> i.group(
						ItemRate.CODEC.listOf()
								.optionalFieldOf("items", List.of())
								.forGetter(Rates::itemList),
						FluidRate.CODEC.listOf()
								.optionalFieldOf("fluids", List.of())
								.forGetter(Rates::fluidList),
						Codec.DOUBLE.optionalFieldOf("energy", 0.0).forGetter(Rates::energy))
				.apply(i, Rates::fromLists));

		private static Rates fromLists(List<ItemRate> items, List<FluidRate> fluids, double energy) {
			Map<ItemVariant, Double> itemRates = new LinkedHashMap<>();
			for (ItemRate rate : items) {
				itemRates.put(rate.item(), rate.rate());
			}
			Map<FluidVariant, Double> fluidRates = new LinkedHashMap<>();
			for (FluidRate rate : fluids) {
				fluidRates.put(rate.fluid(), rate.rate());
			}
			return new Rates(itemRates, fluidRates, energy);
		}

		public int kindCount() {
			return items.size() + fluids.size() + (energy > 0 ? 1 : 0);
		}

		private List<ItemRate> itemList() {
			return items.entrySet().stream().map(entry -> new ItemRate(entry.getKey(), entry.getValue())).toList();
		}

		private List<FluidRate> fluidList() {
			return fluids.entrySet().stream().map(entry -> new FluidRate(entry.getKey(), entry.getValue())).toList();
		}

		private record ItemRate(ItemVariant item, double rate) {
			static final Codec<ItemRate> CODEC = RecordCodecBuilder.create(i -> i.group(
							ItemVariant.CODEC.fieldOf("item").forGetter(ItemRate::item),
							Codec.DOUBLE.fieldOf("rate").forGetter(ItemRate::rate))
					.apply(i, ItemRate::new));
		}

		private record FluidRate(FluidVariant fluid, double rate) {
			static final Codec<FluidRate> CODEC = RecordCodecBuilder.create(i -> i.group(
							FluidVariant.CODEC.fieldOf("fluid").forGetter(FluidRate::fluid),
							Codec.DOUBLE.fieldOf("rate").forGetter(FluidRate::rate))
					.apply(i, FluidRate::new));
		}
	}
}
