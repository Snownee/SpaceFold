package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import snownee.spacefold.CoreModule;
import snownee.spacefold.client.BlockOutlineColors;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private ClientLevel level;
	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Inject(method = "extractBlockOutline", at = @At("TAIL"))
	private void spacefold$customOutlineColor(CallbackInfo ci) {
		BlockOutlineRenderState state = levelRenderState.blockOutlineRenderState;
		if (state == null || minecraft.options.highContrastBlockOutline().get()) {
			return;
		}
		if (level.getBlockState(state.pos()).is(CoreModule.CHAMBER_WINDOWS)) {
			BlockOutlineColors.setColor(state, ARGB.white(102));
		}
	}

	@WrapOperation(method = "renderBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(I)I"))
	private int spacefold$applyCustomOutlineColor(
			int alpha,
			Operation<Integer> original,
			@Local(name = "state") BlockOutlineRenderState state) {
		return BlockOutlineColors.getColor(state, original.call(alpha));
	}
}
