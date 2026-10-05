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
		int tier) {
	public static final Codec<SimulationResult> CODEC = RecordCodecBuilder.create(i -> i.group(
					Rates.CODEC.fieldOf("inputs")
							.orElse(Rates.EMPTY)
							.forGetter(SimulationResult::inputs),
					Rates.CODEC.fieldOf("outputs")
							.orElse(Rates.EMPTY)
							.forGetter(SimulationResult::outputs),
					Material.CODEC.listOf()
							.fieldOf("materials")
							.orElse(List.of())
							.forGetter(SimulationResult::materials),
					Codec.BOOL.fieldOf("failed")
							.orElse(false)
							.forGetter(SimulationResult::failed),
					Codec.STRING.optionalFieldOf("failure")
							.forGetter(SimulationResult::failure),
					Codec.INT.optionalFieldOf("tier", 1)
							.forGetter(SimulationResult::tier))
			.apply(i, SimulationResult::new));

	public SimulationResult(Rates inputs, Rates outputs, List<Material> materials, boolean failed) {
		this(inputs, outputs, materials, failed, Optional.empty(), 1);
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
								.fieldOf("items")
								.orElse(List.of())
								.forGetter(Rates::itemList),
						FluidRate.CODEC.listOf()
								.fieldOf("fluids")
								.orElse(List.of())
								.forGetter(Rates::fluidList),
						Codec.DOUBLE.fieldOf("energy")
								.orElse(0.0)
								.forGetter(Rates::energy))
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
							ItemVariant.CODEC.fieldOf("item")
									.forGetter(ItemRate::item),
							Codec.DOUBLE.fieldOf("rate")
									.forGetter(ItemRate::rate))
					.apply(i, ItemRate::new));
		}

		private record FluidRate(FluidVariant fluid, double rate) {
			static final Codec<FluidRate> CODEC = RecordCodecBuilder.create(i -> i.group(
							FluidVariant.CODEC.fieldOf("fluid")
									.forGetter(FluidRate::fluid),
							Codec.DOUBLE.fieldOf("rate")
									.forGetter(FluidRate::rate))
					.apply(i, FluidRate::new));
		}
	}
}
