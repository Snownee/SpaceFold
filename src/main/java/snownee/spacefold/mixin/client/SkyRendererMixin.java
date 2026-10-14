package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import snownee.spacefold.client.sky.FogVolume;
import snownee.spacefold.client.sky.ShootingStars;
import snownee.spacefold.dim.RoomManager;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void spacefold$hideSun(float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (spacefold$isRoomDimension()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void spacefold$hideMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
		if (spacefold$isRoomDimension()) {
			ci.cancel();
		}
	}

	@Inject(method = "shouldRenderDarkDisc", at = @At("HEAD"), cancellable = true)
	private void spacefold$hideDarkDisc(float deltaPartialTick, ClientLevel level, CallbackInfoReturnable<Boolean> cir) {
		if (RoomManager.isRoomDimension(level)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void spacefold$renderShootingStars(
			PoseStack poseStack,
			float sunAngle,
			float moonAngle,
			float starAngle,
			MoonPhase moonPhase,
			float rainBrightness,
			float starBrightness,
			CallbackInfo ci) {
		if (!spacefold$isRoomDimension()) {
			return;
		}
		FogVolume.render(poseStack, 1.0F);
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.XP.rotation(starAngle));
		ShootingStars.render(poseStack, starBrightness);
		poseStack.popPose();
	}

	@Unique
	private static boolean spacefold$isRoomDimension() {
		ClientLevel level = Minecraft.getInstance().level;
		return level != null && RoomManager.isRoomDimension(level);
	}
}
