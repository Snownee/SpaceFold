package snownee.spacefold.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.block.entity.InterfaceBlockEntity;
import snownee.spacefold.dim.Simulate;

public class InterfaceBlock extends ChamberWallBlock implements EntityBlock {
	public static final MapCodec<InterfaceBlock> CODEC = simpleCodec(InterfaceBlock::new);

	public InterfaceBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends InterfaceBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new InterfaceBlockEntity(worldPosition, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
		if (level.isClientSide() || !level.dimension().equals(Simulate.DIMENSION)) {
			return null;
		}
		return (tickLevel, pos, state, blockEntity) -> ((InterfaceBlockEntity) blockEntity).tick();
	}
}
