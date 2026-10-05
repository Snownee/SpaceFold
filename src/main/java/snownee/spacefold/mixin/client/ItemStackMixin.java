package snownee.spacefold.mixin.client;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.CoreModule;
import snownee.spacefold.client.SimulationTooltip;
import snownee.spacefold.dim.SimulationResult;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void spacefold$simulationTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (Minecraft.getInstance().hasShiftDown()) {
			return;
		}
		ItemStack stack = (ItemStack) (Object) this;
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result == null) {
			return;
		}
		SimulationTooltip tooltip = SimulationTooltip.of(result);
		if (tooltip != null) {
			cir.setReturnValue(Optional.of(tooltip));
		}
	}
}
