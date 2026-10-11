package snownee.spacefold.client.room;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public record RoomSkyRenderState(
		int x0,
		int y0,
		int x1,
		int y1,
		float scale,
		float progress,
		float fovProgress,
		float rotation,
		@Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
	public RoomSkyRenderState(int x0, int y0, int x1, int y1, float scale, float progress, float fovProgress, float rotation, @Nullable ScreenRectangle scissorArea) {
		this(x0, y0, x1, y1, scale, progress, fovProgress, rotation, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}
}
