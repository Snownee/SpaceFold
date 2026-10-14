package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.dim.Plan;
import snownee.spacefold.dim.RoomManager;

@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "isInValidBounds", at = @At("HEAD"), cancellable = true)
	private void spacefold$roomBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		if (!RoomManager.isRoomDimension(level)) {
			return;
		}
		if (level.dimension().equals(Plan.DIMENSION) && !SpaceFoldConfig.placementEnabled) {
			return;
		}
		if (!spacefold$insideRoom(pos)) {
			cir.setReturnValue(false);
		}
	}

	@Unique
	private static boolean spacefold$insideRoom(BlockPos pos) {
		int roomSize = SpaceFoldConfig.roomSize;
		int lx = Math.floorMod(pos.getX(), Plan.ROOM_GAP);
		int lz = Math.floorMod(pos.getZ(), Plan.ROOM_GAP);
		int ly = pos.getY() - Plan.FLOOR_Y;
		if (lx >= roomSize || lz >= roomSize || ly < 0 || ly >= roomSize) {
			return false;
		}
		int edges = 0;
		if (lx == 0 || lx == roomSize - 1) {
			edges++;
		}
		if (lz == 0 || lz == roomSize - 1) {
			edges++;
		}
		if (ly == 0 || ly == roomSize - 1) {
			edges++;
		}
		return edges < 2;
	}
}
