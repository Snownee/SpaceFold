package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.dim.RoomGeometry;
import snownee.spacefold.dim.RoomManager;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void spacefold$confine(CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		if (!SpaceFoldConfig.confinementEnabled) {
			return;
		}
		if (!RoomManager.isRoomDimension(player.level().dimension())) {
			return;
		}
		if (player.isCreative()) {
			return;
		}
		int room = RoomGeometry.roomAt(player.getX(), player.getY(), player.getZ());
		if (room < 0) {
			return;
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Vec3 clamped = RoomGeometry.clamp(room, x, y, z);
		if (clamped.x == x && clamped.y == y && clamped.z == z) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		double vx = clamped.x == x ? velocity.x : 0.0D;
		double vy = clamped.y == y ? velocity.y : 0.0D;
		double vz = clamped.z == z ? velocity.z : 0.0D;
		player.setPos(clamped.x, clamped.y, clamped.z);
		player.setDeltaMovement(vx, vy, vz);
	}
}
