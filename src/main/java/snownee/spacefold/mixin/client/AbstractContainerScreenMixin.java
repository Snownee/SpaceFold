package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import snownee.spacefold.client.ViewDetailsKey;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void spacefold$viewMaterials(CallbackInfo ci) {
		ViewDetailsKey.tick(hoveredSlot);
	}
}
