package snownee.spacefold.client.sky;

import java.util.Objects;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.Minecraft;

/**
 * 房间天空自绘的渲染目标。世界天空 pass 里 override 为 null，直接写主渲染目标；
 * 在 GUI 的 picture-in-picture 渲染中 override 指向离屏纹理，天空便画进该纹理。
 */
public final class SkyTarget {
	private SkyTarget() {
	}

	public static GpuTextureView color(Minecraft minecraft) {
		return RenderSystem.outputColorTextureOverride != null
				? RenderSystem.outputColorTextureOverride
				: Objects.requireNonNull(minecraft.getMainRenderTarget().getColorTextureView());
	}

	public static GpuTextureView depth(Minecraft minecraft) {
		return RenderSystem.outputDepthTextureOverride != null
				? RenderSystem.outputDepthTextureOverride
				: Objects.requireNonNull(minecraft.getMainRenderTarget().getDepthTextureView());
	}
}
