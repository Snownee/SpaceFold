package snownee.spacefold.dim;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import snownee.spacefold.util.Object2DoubleCodec;

public record SimulationResult(
		Rates inputs,
		Rates outputs,
		List<Material> materials,
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
					Codec.STRING.optionalFieldOf("failure").forGetter(SimulationResult::failure),
					Codec.INT.optionalFieldOf("tier", 1).forGetter(SimulationResult::tier),
					Codec.INT.optionalFieldOf("snapshot", NO_SNAPSHOT).forGetter(SimulationResult::snapshotId))
			.apply(i, SimulationResult::new));

	public SimulationResult(Rates inputs, Rates outputs, List<Material> materials) {
		this(inputs, outputs, materials, Optional.empty(), 1, NO_SNAPSHOT);
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public SimulationResult(Rates inputs, Rates outputs, List<Material> materials, Optional<String> failure, int tier) {
		this(inputs, outputs, materials, failure, tier, NO_SNAPSHOT);
	}

	public boolean hasSnapshot() {
		return snapshotId != NO_SNAPSHOT;
	}

	public boolean failed() {
		return failure.isPresent();
	}

	public SimulationResult withSnapshotId(int snapshotId) {
		return new SimulationResult(inputs, outputs, materials, failure, tier, snapshotId);
	}

	public static SimulationResult previewOnly(int snapshotId) {
		return new SimulationResult(Rates.EMPTY, Rates.EMPTY, List.of(), Optional.empty(), 1, snapshotId);
	}

	public record Material(ItemVariant item, int count) {
		public static final Codec<Material> CODEC = RecordCodecBuilder.create(i -> i.group(
						ItemVariant.CODEC.fieldOf("item")
								.forGetter(Material::item),
						Codec.INT.fieldOf("count")
								.forGetter(Material::count))
				.apply(i, Material::new));
	}

	public record Rates(Object2DoubleMap<ItemVariant> items, Object2DoubleMap<FluidVariant> fluids, double energy) {
		public static final Codec<Object2DoubleMap<ItemVariant>> ITEM_AMOUNTS_CODEC = Object2DoubleCodec.of(ItemVariant.CODEC, "item", "rate");
		public static final Codec<Object2DoubleMap<FluidVariant>> FLUID_AMOUNTS_CODEC = Object2DoubleCodec.of(FluidVariant.CODEC, "fluid", "rate");
		public static final Rates EMPTY = new Rates(Object2DoubleMaps.emptyMap(), Object2DoubleMaps.emptyMap(), 0);
		public static final Codec<Rates> CODEC = RecordCodecBuilder.create(i -> i.group(
						ITEM_AMOUNTS_CODEC
								.optionalFieldOf("items", Object2DoubleMaps.emptyMap())
								.forGetter(Rates::items),
						FLUID_AMOUNTS_CODEC
								.optionalFieldOf("fluids", Object2DoubleMaps.emptyMap())
								.forGetter(Rates::fluids),
						Codec.DOUBLE.optionalFieldOf("energy", 0.0).forGetter(Rates::energy))
				.apply(i, Rates::new));

		public int kindCount() {
			return items.size() + fluids.size() + (energy > 0 ? 1 : 0);
		}
	}
}
