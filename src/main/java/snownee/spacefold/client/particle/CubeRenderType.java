package snownee.spacefold.client.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class CubeRenderType {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withVertexShader("core/position_color")
					.withFragmentShader("core/position_color")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
					.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
					.withLocation("pipeline/spacefold_cube")
					.build());

	public static final RenderType TYPE = RenderType.create(
			"spacefold_cube",
			RenderSetup.builder(PIPELINE)
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.createRenderSetup());

	private CubeRenderType() {
	}

	public static void bootstrap() {
	}
}
