package snownee.spacefold.dim;

import java.util.List;
import java.util.function.ObjIntConsumer;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.RepeaterBlock;
import snownee.spacefold.util.HierarchyLookup;

public final class BlockMaterials {
	private static final HierarchyLookup<Provider> LOOKUP = new HierarchyLookup<>(Block.class);

	static {
		LOOKUP.register(RepeaterBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getCloneItemStack(level, pos, true)), 1));
		LOOKUP.register(SlabBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getBlock().asItem()), slabAmount(state)));
		LOOKUP.register(CandleBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getBlock().asItem()), state.getValue(BlockStateProperties.CANDLES)));
		LOOKUP.register(TurtleEggBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getBlock().asItem()), state.getValue(BlockStateProperties.EGGS)));
		LOOKUP.register(SeaPickleBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getBlock().asItem()), state.getValue(BlockStateProperties.PICKLES)));
		LOOKUP.register(SnowLayerBlock.class, (level, pos, state, out) ->
				out.accept(ItemVariant.of(state.getBlock().asItem()), state.getValue(BlockStateProperties.LAYERS)));
	}

	private BlockMaterials() {
	}

	public static void register(Class<? extends Block> clazz, Provider provider) {
		LOOKUP.register(clazz, provider);
	}

	public static void collect(ServerLevel level, BlockPos pos, BlockState state, ObjIntConsumer<ItemVariant> out) {
		if (state.isAir()) {
			return;
		}
		Block block = state.getBlock();
		if (block == CoreModule.SIMULATOR.get()) {
			return;
		}
		List<Provider> providers = LOOKUP.get(block);
		if (providers.isEmpty()) {
			collectDefault(level, pos, state, out);
			return;
		}
		for (Provider provider : providers) {
			provider.collect(level, pos, state, out);
		}
	}

	private static void collectDefault(ServerLevel level, BlockPos pos, BlockState state, ObjIntConsumer<ItemVariant> out) {
		Item item = state.getBlock().asItem();
		if (item != Items.AIR) {
			out.accept(ItemVariant.of(new ItemStack(item)), 1);
			return;
		}
		FluidState fluid = state.getFluidState();
		if (fluid.isSource()) {
			Item bucket = fluid.getType().getBucket();
			if (bucket != Items.AIR) {
				out.accept(ItemVariant.of(new ItemStack(bucket)), 1);
			}
		}
	}

	private static int slabAmount(BlockState state) {
		return state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
	}

	@FunctionalInterface
	public interface Provider {
		void collect(ServerLevel level, BlockPos pos, BlockState state, ObjIntConsumer<ItemVariant> out);
	}
}
