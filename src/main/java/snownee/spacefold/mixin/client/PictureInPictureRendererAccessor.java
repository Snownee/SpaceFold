package snownee.spacefold.mixin.client;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;

@Mixin(PictureInPictureRenderer.class)
public interface PictureInPictureRendererAccessor {
	@Accessor("textureView")
	@Nullable GpuTextureView spacefold$textureView();
}
