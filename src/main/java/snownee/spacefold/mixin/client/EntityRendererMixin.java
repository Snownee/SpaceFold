package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import snownee.spacefold.dim.RoomManager;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@WrapOperation(
			method = "extractShadowPiece",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"))
	private int spacefold$decoupleShadowFromLight(Level level, BlockPos pos, Operation<Integer> original) {
		if (RoomManager.isRoomDimension(level)) {
			return 15;
		}
		return original.call(level, pos);
	}
}
