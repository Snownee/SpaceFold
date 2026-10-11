package snownee.spacefold.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.block.InterfaceBlock;
import snownee.spacefold.dim.RoomProtection;
import snownee.spacefold.dim.Simulate;

public class OpenerItem extends Item {
	public OpenerItem(Properties properties) {
		super(properties);
	}

	private static @Nullable BlockState convert(BlockState state) {
		Block block = state.getBlock();
		if (block == CoreModule.INTERFACE.get()) {
			return CoreModule.CHAMBER_WALL.get()
					.defaultBlockState()
					.setValue(ChamberWallBlock.FACING, state.getValue(InterfaceBlock.FACING));
		}
		if (block == CoreModule.INTERFACE_WINDOW.get()) {
			return CoreModule.CHAMBER_WINDOW.get()
					.defaultBlockState()
					.setValue(ChamberWallBlock.FACING, state.getValue(InterfaceBlock.FACING));
		}
		if (block == CoreModule.CHAMBER_WALL.get()) {
			return CoreModule.INTERFACE.get()
					.defaultBlockState()
					.setValue(InterfaceBlock.FACING, state.getValue(ChamberWallBlock.FACING));
		}
		if (block == CoreModule.CHAMBER_WINDOW.get()) {
			return CoreModule.INTERFACE_WINDOW.get()
					.defaultBlockState()
					.setValue(InterfaceBlock.FACING, state.getValue(ChamberWallBlock.FACING));
		}
		return null;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState converted = convert(level.getBlockState(pos));
		if (converted == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.dimension().equals(Simulate.DIMENSION)) {
			return InteractionResult.PASS;
		}
		RoomProtection.runInternal(() -> level.setBlock(pos, converted, Block.UPDATE_ALL));
		ServerLevel serverLevel = (ServerLevel) level;
		serverLevel.sendParticles(
				ParticleTypes.END_ROD,
				pos.getX() + 0.5D,
				pos.getY() + 0.5D,
				pos.getZ() + 0.5D,
				16,
				0.35D,
				0.35D,
				0.35D,
				0.02D);
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}
}
