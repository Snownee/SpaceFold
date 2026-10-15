package snownee.spacefold.paint;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import snownee.spacefold.block.RepeaterBlock;
import snownee.spacefold.block.entity.RepeaterBlockEntity;
import xerca.xercapaint.CanvasType;
import xerca.xercapaint.entity.EntityCanvas;

public final class PaintConversion {
	private PaintConversion() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register(PaintConversion::onUseEntity);
	}

	private static InteractionResult onUseEntity(
			Player player,
			Level level,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult) {
		if (level.isClientSide() || !(entity instanceof EntityCanvas canvas)) {
			return InteractionResult.PASS;
		}
		if (canvas.getCanvasType() != CanvasType.SMALL) {
			return InteractionResult.PASS;
		}
		FacePaint paint = facePaint(canvas);
		if (paint == null) {
			return InteractionResult.PASS;
		}
		Direction face = canvas.getDirection();
		BlockPos supportPos = canvas.blockPosition().relative(face.getOpposite());
		BlockState state = level.getBlockState(supportPos);
		if (!(state.getBlock() instanceof RepeaterBlock) || !(level.getBlockEntity(supportPos) instanceof RepeaterBlockEntity repeater)) {
			return InteractionResult.PASS;
		}
		Direction facing = state.getValue(DirectionalBlock.FACING);
		repeater.setPaint(
				FacingRotation.toModel(facing, face),
				new FacePaint(paint.pixels(), paint.rotation() + FacingRotation.rotationOffset(facing, face)));
		canvas.discard();
		level.playSound(null, supportPos, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	private static @Nullable FacePaint facePaint(EntityCanvas canvas) {
		EntityCanvas.Picture picture = EntityCanvas.PICTURES.get(canvas.getCanvasID());
		if (picture == null) {
			return null;
		}
		int[] pixels = picture.pixels();
		if (pixels.length < FacePaint.PIXEL_COUNT) {
			return null;
		}
		return new FacePaint(pixels, canvas.getRotation());
	}
}
