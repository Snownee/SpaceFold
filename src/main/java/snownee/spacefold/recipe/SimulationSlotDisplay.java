package snownee.spacefold.recipe;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.display.DisplayContentsFactory;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import snownee.spacefold.recipe.SimulationIngredient.Condition;

public record SimulationSlotDisplay(SlotDisplay base, List<Condition> conditions) implements SlotDisplay {
	public static final MapCodec<SimulationSlotDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
					SlotDisplay.CODEC.fieldOf("base").forGetter(SimulationSlotDisplay::base),
					Condition.CODEC.listOf().fieldOf("conditions").forGetter(SimulationSlotDisplay::conditions))
			.apply(i, SimulationSlotDisplay::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SimulationSlotDisplay> STREAM_CODEC = StreamCodec.composite(
			SlotDisplay.STREAM_CODEC, SimulationSlotDisplay::base,
			Condition.STREAM_CODEC.apply(ByteBufCodecs.list()), SimulationSlotDisplay::conditions,
			SimulationSlotDisplay::new);

	public static final SlotDisplay.Type<SimulationSlotDisplay> TYPE = new SlotDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public SlotDisplay.Type<SimulationSlotDisplay> type() {
		return TYPE;
	}

	@Override
	public <T> Stream<T> resolve(ContextMap context, DisplayContentsFactory<T> factory) {
		if (!(factory instanceof DisplayContentsFactory.ForStacks<T> stacks)
				|| factory instanceof DisplayContentsFactory.ForRemainders<T>
				|| conditions.isEmpty()) {
			return base.resolve(context, factory);
		}
		List<Component> lines = conditions.stream().map(Condition::describe).toList();
		return base.resolve(context, SlotDisplay.ItemStackContentsFactory.INSTANCE).map(stack -> {
			ItemStack copy = stack.copy();
			copy.set(DataComponents.LORE, new ItemLore(lines));
			return stacks.forStack(copy);
		});
	}
}
