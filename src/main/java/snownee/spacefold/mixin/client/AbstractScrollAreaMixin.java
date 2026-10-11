package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

@Mixin(AbstractScrollArea.class)
public abstract class AbstractScrollAreaMixin {
	@WrapOperation(
			method = "extractScrollbar",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void spacefold$fadeScrollbar(
			GuiGraphicsExtractor graphics,
			RenderPipeline renderPipeline,
			Identifier location,
			int x,
			int y,
			int width,
			int height,
			Operation<Void> original) {
		graphics.blitSprite(
				renderPipeline,
				location,
				x,
				y,
				width,
				height,
				ARGB.white(Mth.clamp(((AbstractWidget) (Object) this).getAlpha(), 0.0F, 1.0F)));
	}
}
