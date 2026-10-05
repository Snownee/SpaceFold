package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.dim.RoomProtection;

@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(
			method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void spacefold$blockPlacement(
			BlockPos pos,
			BlockState blockState,
			int updateFlags,
			int updateLimit,
			CallbackInfoReturnable<Boolean> cir) {
		if (!RoomProtection.canPlace((Level) (Object) this, pos, blockState)) {
			cir.setReturnValue(false);
		}
	}
}
