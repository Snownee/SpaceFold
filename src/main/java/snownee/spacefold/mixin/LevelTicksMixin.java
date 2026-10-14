package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import snownee.spacefold.dim.SimulationManager;

@Mixin(LevelTicks.class)
public class LevelTicksMixin {
	@ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
	private ScheduledTick<?> spacefold$rebaseTriggerTick(ScheduledTick<?> tick) {
		long offset = SimulationManager.copyTriggerOffset();
		if (offset == 0) {
			return tick;
		}
		return new ScheduledTick<>(tick.type(), tick.pos(), tick.triggerTick() + offset, tick.priority(), tick.subTickOrder());
	}
}
