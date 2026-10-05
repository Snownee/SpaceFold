package snownee.spacefold.recipe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.SimulationResult;

public class SimulationIngredient implements CustomIngredient {
	public static final CustomIngredientSerializer<SimulationIngredient> SERIALIZER = new Serializer();

	private final Ingredient base;
	private final List<Condition> conditions;

	public SimulationIngredient(Ingredient base, List<Condition> conditions) {
		this.base = base;
		this.conditions = List.copyOf(conditions);
	}

	@Override
	public boolean test(ItemStack stack) {
		if (!base.test(stack)) {
			return false;
		}
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result == null) {
			return false;
		}
		for (Condition condition : conditions) {
			if (!condition.test(result)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Stream<Holder<Item>> items() {
		return base.items();
	}

	@Override
	public boolean requiresTesting() {
		return true;
	}

	@Override
	public CustomIngredientSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	public Ingredient base() {
		return base;
	}

	public List<Condition> conditions() {
		return conditions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SimulationIngredient that = (SimulationIngredient) o;
		return base.equals(that.base) && conditions.equals(that.conditions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(base, conditions);
	}

	public enum Io {
		INPUT, OUTPUT;

		public static final Codec<Io> CODEC = Codec.STRING.comapFlatMap(
				name -> switch (name) {
					case "input" -> DataResult.success(INPUT);
					case "output" -> DataResult.success(OUTPUT);
					default -> DataResult.error(() -> "Unknown direction: " + name);
				},
				io -> io == INPUT ? "input" : "output");
		public static final StreamCodec<ByteBuf, Io> STREAM_CODEC = ByteBufCodecs.BOOL.map(
				output -> output ? OUTPUT : INPUT,
				io -> io == OUTPUT);
	}

	public record Condition(
			Io direction,
			Optional<Ingredient> item,
			Optional<FluidVariant> fluid,
			boolean energy,
			Optional<Double> min,
			Optional<Double> max) {
		public static final Codec<Condition> CODEC = RecordCodecBuilder.create(i -> i.group(
						Io.CODEC.fieldOf("direction").forGetter(Condition::direction),
						Ingredient.CODEC.optionalFieldOf("item").forGetter(Condition::item),
						FluidVariant.CODEC.optionalFieldOf("fluid").forGetter(Condition::fluid),
						Codec.BOOL.optionalFieldOf("energy", false).forGetter(Condition::energy),
						Codec.DOUBLE.optionalFieldOf("min").forGetter(Condition::min),
						Codec.DOUBLE.optionalFieldOf("max").forGetter(Condition::max))
				.apply(i, Condition::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Condition> STREAM_CODEC = StreamCodec.composite(
				Io.STREAM_CODEC, Condition::direction,
				ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), Condition::item,
				ByteBufCodecs.optional(FluidVariant.PACKET_CODEC), Condition::fluid,
				ByteBufCodecs.BOOL, Condition::energy,
				ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), Condition::min,
				ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), Condition::max,
				Condition::new);

		public Condition {
			int targets = (item.isPresent() ? 1 : 0) + (fluid.isPresent() ? 1 : 0) + (energy ? 1 : 0);
			if (targets != 1) {
				throw new IllegalArgumentException("Condition must target exactly one of item, fluid or energy");
			}
			if (min.isEmpty() && max.isEmpty()) {
				throw new IllegalArgumentException("Condition must define min or max");
			}
		}

		public boolean test(SimulationResult result) {
			SimulationResult.Rates rates = direction == Io.INPUT ? result.inputs() : result.outputs();
			double rate;
			if (item.isPresent()) {
				Ingredient ingredient = item.get();
				rate = 0;
				for (Map.Entry<ItemVariant, Double> entry : rates.items().entrySet()) {
					if (ingredient.test(entry.getKey().toStack())) {
						rate += entry.getValue();
					}
				}
			} else if (fluid.isPresent()) {
				rate = rates.fluids().getOrDefault(fluid.get(), 0.0);
			} else {
				rate = rates.energy();
			}
			if (min.isPresent() && rate < min.get()) {
				return false;
			}
			return max.isEmpty() || rate <= max.get();
		}
	}

	private static class Serializer implements CustomIngredientSerializer<SimulationIngredient> {
		private static final Identifier ID = SpaceFold.id("simulation");
		private static final MapCodec<SimulationIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
						Ingredient.CODEC.fieldOf("base").forGetter(SimulationIngredient::base),
						Condition.CODEC.listOf().fieldOf("conditions").forGetter(SimulationIngredient::conditions))
				.apply(i, SimulationIngredient::new));
		private static final StreamCodec<RegistryFriendlyByteBuf, SimulationIngredient> PACKET_CODEC = StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, SimulationIngredient::base,
				Condition.STREAM_CODEC.apply(ByteBufCodecs.list()), SimulationIngredient::conditions,
				SimulationIngredient::new);

		@Override
		public Identifier getIdentifier() {
			return ID;
		}

		@Override
		public MapCodec<SimulationIngredient> getCodec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SimulationIngredient> getStreamCodec() {
			return PACKET_CODEC;
		}
	}
}
