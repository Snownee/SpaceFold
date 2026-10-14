package snownee.spacefold.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.FactoryMenu;

public class FactoryBlock extends Block implements EntityBlock {
	public static final MapCodec<FactoryBlock> CODEC = simpleCodec(FactoryBlock::new);
	public static final VoxelShape SHAPE = Shapes.or(
			Block.column(16, 12, 16),
			Block.column(8, 2, 12),
			Block.column(12, 0, 2)
	);

	public FactoryBlock(BlockBehaviour.Properties properties) {
		super(properties.strength(1.5F));
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

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
		if (!level.isClientSide() && entity instanceof ItemEntity itemEntity && itemEntity.hasPickUpDelay() &&
				level.getBlockEntity(pos) instanceof Container container) {
			if (insertItem(container, itemEntity.getItem())) {
				itemEntity.setItem(itemEntity.getItem()); // send update packet
			}
		}
	}

	public boolean insertItem(Container container, ItemStack itemStack) {
		if (!container.canPlaceItem(0, itemStack)) {
			return false;
		}
		ItemStack displayed = container.getItem(0);
		if (displayed.isEmpty() || ItemStack.isSameItemSameComponents(displayed, itemStack)) {
			int maxSize = Math.min(itemStack.getMaxStackSize(), container.getMaxStackSize());
			int transferAmount = Math.min(itemStack.getCount(), maxSize - displayed.getCount());
			if (transferAmount > 0) {
				ItemStack split = itemStack.split(transferAmount);
				split.grow(displayed.getCount());
				container.setItem(0, split);
				return true;
			}
		}
		return false;
	}
}
