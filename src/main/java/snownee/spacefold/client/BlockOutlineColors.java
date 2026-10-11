package snownee.spacefold.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;

public final class BlockOutlineColors {
	private static final RenderStateDataKey<Integer> COLOR = RenderStateDataKey.create(() -> "spacefold:block_outline_color");

	private BlockOutlineColors() {
	}

	public static void setColor(BlockOutlineRenderState state, int color) {
		state.setData(COLOR, color);
	}

	public static int getColor(BlockOutlineRenderState state, int fallback) {
		return state.getDataOrDefault(COLOR, fallback);
	}
}
