package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import snownee.spacefold.dim.Simulate;
import snownee.spacefold.dim.SimulationManager;

@Mixin(LevelAccessor.class)
public interface LevelAccessorMixin {
	@ModifyReturnValue(method = "getGameTime", at = @At("RETURN"))
	private long spacefold$gameTime(long original) {
		if (this instanceof ServerLevel level && level.dimension().equals(Simulate.DIMENSION)) {
			return SimulationManager.gameTime();
		}
		return original;
	}
}
