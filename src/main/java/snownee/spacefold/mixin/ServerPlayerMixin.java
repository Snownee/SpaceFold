package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import snownee.spacefold.dim.PlanFlight;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "setGameMode", at = @At("TAIL"))
	private void spacefold$updateFlightOnGameModeChange(GameType mode, CallbackInfoReturnable<Boolean> cir) {
		PlanFlight.update((ServerPlayer) (Object) this);
	}
}
