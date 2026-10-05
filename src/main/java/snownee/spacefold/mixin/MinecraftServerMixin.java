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
		int times = level.dimension().equals(Simulate.DIMENSION) && SimulationManager.hasActive()
				? Math.max(1, SpaceFoldConfig.simulationTickMultiplier)
				: 1;
		for (int i = 0; i < times; i++) {
			original.call(level, haveTime);
		}
	}
}
