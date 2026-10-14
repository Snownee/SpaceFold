package snownee.spacefold.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.dim.Simulate;
import snownee.spacefold.dim.SimulationManager;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@WrapOperation(
			method = "tickChildren(Ljava/util/function/BooleanSupplier;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
	private void spacefold$tickLevel(ServerLevel level, BooleanSupplier haveTime, Operation<Void> original) {
		if (!level.dimension().equals(Simulate.DIMENSION) || !SimulationManager.hasActive()) {
			original.call(level, haveTime);
			return;
		}
		int times = Math.max(1, SpaceFoldConfig.simulationTickMultiplier);
		for (int i = 0; i < times; i++) {
			SimulationManager.advanceGameTime();
			original.call(level, haveTime);
		}
	}
}
