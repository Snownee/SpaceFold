package snownee.spacefold.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.FactoryMenu;

public class FactoryBlock extends Block implements EntityBlock {
	public static final MapCodec<FactoryBlock> CODEC = simpleCodec(FactoryBlock::new);

	public FactoryBlock(BlockBehaviour.Properties properties) {
		super(properties.mapColor(MapColor.METAL).strength(2.0F));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new FactoryBlockEntity(worldPosition, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (tickLevel, pos, state, blockEntity) -> ((FactoryBlockEntity) blockEntity).tick();
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider(
				(containerId, inventory, player) -> new FactoryMenu(containerId, inventory, pos),
				Component.translatable("gui.spacefold.factory"));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(getMenuProvider(state, level, pos));
		}
		return InteractionResult.SUCCESS;
	}
}
