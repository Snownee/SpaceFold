package snownee.spacefold.recipe;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.util.FluidAmounts;

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

	@SuppressWarnings("deprecation")
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

	@Override
	public SlotDisplay display() {
		return new SimulationSlotDisplay(base.display(), conditions);
	}

	public Ingredient base() {
		return base;
	}

	public List<Condition> conditions() {
		return conditions;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		SimulationIngredient that = (SimulationIngredient) obj;
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

	public enum Target {
		ENERGY, TIER;

		public static final Codec<Target> CODEC = Codec.STRING.comapFlatMap(
				name -> switch (name) {
					case "energy" -> DataResult.success(ENERGY);
					case "tier" -> DataResult.success(TIER);
					default -> DataResult.error(() -> "Unknown target: " + name);
				},
				target -> target == ENERGY ? "energy" : "tier");
		public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = ByteBufCodecs.BOOL.map(
				tier -> tier ? TIER : ENERGY,
				target -> target == TIER);
	}

	public record Condition(
			Optional<Io> direction,
			Optional<Ingredient> item,
			Optional<FluidVariant> fluid,
			Optional<Target> target,
			Optional<Double> min,
			Optional<Double> max) {
		public static final Codec<Condition> CODEC = RecordCodecBuilder.create(i -> i.group(
						Io.CODEC.optionalFieldOf("direction").forGetter(Condition::direction),
						Ingredient.CODEC.optionalFieldOf("item").forGetter(Condition::item),
						FluidVariant.CODEC.optionalFieldOf("fluid").forGetter(Condition::fluid),
						Target.CODEC.optionalFieldOf("target").forGetter(Condition::target),
						Codec.DOUBLE.optionalFieldOf("min").forGetter(Condition::min),
						Codec.DOUBLE.optionalFieldOf("max").forGetter(Condition::max))
				.apply(i, Condition::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Condition> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(Io.STREAM_CODEC), Condition::direction,
				ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), Condition::item,
				ByteBufCodecs.optional(FluidVariant.PACKET_CODEC), Condition::fluid,
				ByteBufCodecs.optional(Target.STREAM_CODEC), Condition::target,
				ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), Condition::min,
				ByteBufCodecs.optional(ByteBufCodecs.DOUBLE), Condition::max,
				Condition::new);

		public Condition {
			int targets = (item.isPresent() ? 1 : 0) + (fluid.isPresent() ? 1 : 0) + (target.isPresent() ? 1 : 0);
			if (targets != 1) {
				throw new IllegalArgumentException("Condition must target exactly one of item, fluid, energy or tier");
			}
			if (min.isEmpty() && max.isEmpty()) {
				throw new IllegalArgumentException("Condition must define min or max");
			}
			if (target.orElse(Target.ENERGY) != Target.TIER && direction.isEmpty()) {
				throw new IllegalArgumentException("Resource condition must define a direction");
			}
		}

		public boolean test(SimulationResult result) {
			double value;
			if (target.orElse(Target.ENERGY) == Target.TIER) {
				value = result.tier();
			} else {
				SimulationResult.Rates rates = direction.get() == Io.INPUT ? result.inputs() : result.outputs();
				if (item.isPresent()) {
					Ingredient ingredient = item.get();
					value = 0;
					for (Object2DoubleMap.Entry<ItemVariant> entry : rates.items().object2DoubleEntrySet()) {
						if (ingredient.test(entry.getKey().toStack())) {
							value += entry.getDoubleValue();
						}
					}
				} else if (fluid.isPresent()) {
					value = rates.fluids().getDouble(fluid.get());
				} else {
					value = rates.energy();
				}
			}
			if (min.isPresent() && value < min.get()) {
				return false;
			}
			return max.isEmpty() || value <= max.get();
		}

		public Component describe() {
			if (item.isPresent()) {
				Component name = item.get().items().findFirst()
						.map(holder -> new ItemStack(holder).getHoverName())
						.orElse(Component.literal("?"));
				return Component.translatable("tip.spacefold.condition.item", directionLabel(), name, range(value -> value));
			}
			if (fluid.isPresent()) {
				return Component.translatable(
						"tip.spacefold.condition.fluid",
						directionLabel(),
						FluidVariantAttributes.getName(fluid.get()),
						range(FluidAmounts::toMilliBuckets));
			}
			if (target.orElse(Target.ENERGY) == Target.TIER) {
				return Component.translatable("tip.spacefold.condition.tier", range(value -> value));
			}
			return Component.translatable("tip.spacefold.condition.energy", directionLabel(), range(value -> value));
		}

		private Component directionLabel() {
			return Component.translatable(direction.orElse(Io.INPUT) == Io.INPUT ? "gui.spacefold.inputs" : "gui.spacefold.outputs");
		}

		private String range(DoubleUnaryOperator unit) {
			if (min.isPresent() && max.isPresent()) {
				return format(unit.applyAsDouble(min.get())) + "~" + format(unit.applyAsDouble(max.get()));
			}
			if (min.isPresent()) {
				return "≥" + format(unit.applyAsDouble(min.get()));
			}
			return "≤" + format(unit.applyAsDouble(max.get()));
		}

		private static String format(double value) {
			return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
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
