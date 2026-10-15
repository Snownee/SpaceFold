package snownee.spacefold.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import snownee.spacefold.SpaceFoldClientConfig;

public class ChamberWallBlock extends DirectionalBlock {
	public static final MapCodec<ChamberWallBlock> CODEC = simpleCodec(ChamberWallBlock::new);

	public ChamberWallBlock(BlockBehaviour.Properties properties) {
		super(configure(properties));
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	private static BlockBehaviour.Properties configure(BlockBehaviour.Properties properties) {
		return properties.mapColor(MapColor.NONE)
				.strength(-1.0F, 3600000.8F)
				.noOcclusion()
				.noLootTable()
				.dynamicShape()
				.forceSolidOn()
				.isSuffocating(Blocks::never)
				.isViewBlocking(Blocks::never)
				.pushReaction(PushReaction.BLOCK)
				.noTerrainParticles();
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return SpaceFoldClientConfig.roomAmbientOcclusion;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player &&
				(player.getAbilities().flying || state.getValue(FACING) == Direction.DOWN)) {
			return Shapes.empty();
		}
		return Shapes.block();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player) {
			Direction facing = state.getValue(FACING);
			Vec3 toPlayer = player.getEyePosition().subtract(Vec3.atCenterOf(pos).add(facing.getUnitVec3().scale(0.5D)));
			if (toPlayer.dot(facing.getUnitVec3()) < 0.0D) {
				return Shapes.empty();
			}
		}
		return super.getShape(state, level, pos, context);
	}

	@Override
	protected BlockState rotate(final BlockState state, final Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(final BlockState state, final Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}
}
