package snownee.spacefold.client.sky;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 房间维度（plan / simulate）夜空里的静态星空。与原版 {@code SkyRenderer.buildStars} / {@code renderStars}
 * 的星点数据、大小、分布完全一致，只是改用 {@link SkyTarget} 以便在世界天空 pass 与 GUI 的
 * picture-in-picture 渲染中都能落到正确的渲染目标。
 * <p>
 * 星空是一次性构建的静态顶点缓冲；本类同时负责用 {@code clearColor} 给第一趟天空绘制清出底色。
 */
public final class StarField {
	private static final int STAR_COUNT = 1500;
	private static @Nullable GpuBuffer vertexBuffer;
	private static int indexCount;

	private StarField() {
	}

	/**
	 * @param clearColor 本趟绘制前清屏用的 ARGB 底色（深空色），保证目标纹理整体不透明
	 */
	public static void render(PoseStack poseStack, float starBrightness, int clearColor) {
		if (vertexBuffer == null) {
			build();
		}
		Minecraft minecraft = Minecraft.getInstance();
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		modelViewStack.mul(poseStack.last().pose());
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(
						modelViewStack,
						new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness),
						new Vector3f(),
						new Matrix4f());
		GpuTextureView colorTexture = SkyTarget.color(minecraft);
		GpuTextureView depthTexture = SkyTarget.depth(minecraft);
		RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "SpaceFold stars", colorTexture, OptionalInt.of(clearColor), depthTexture, OptionalDouble.of(1.0))) {
			renderPass.setPipeline(RenderPipelines.STARS);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer.getBuffer(indexCount), indexBuffer.type());
			renderPass.drawIndexed(0, 0, indexCount, 1);
		}
		modelViewStack.popMatrix();
	}

	private static void build() {
		RandomSource random = RandomSource.createThreadLocalInstance(10842L);
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * STAR_COUNT * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
			for (int i = 0; i < STAR_COUNT; i++) {
				float x = random.nextFloat() * 2.0F - 1.0F;
				float y = random.nextFloat() * 2.0F - 1.0F;
				float z = random.nextFloat() * 2.0F - 1.0F;
				float starSize = 0.15F + random.nextFloat() * 0.1F;
				float lengthSq = Mth.lengthSquared(x, y, z);
				if (!(lengthSq <= 0.010000001F) && !(lengthSq >= 1.0F)) {
					Vector3f starCenter = new Vector3f(x, y, z).normalize(100.0F);
					float zRot = (float) (random.nextDouble() * (float) Math.PI * 2.0);
					Matrix3f rotation = new Matrix3f()
							.rotateTowards(new Vector3f(starCenter).negate(), new Vector3f(0.0F, 1.0F, 0.0F))
							.rotateZ(-zRot);
					bufferBuilder.addVertex(new Vector3f(starSize, -starSize, 0.0F).mul(rotation).add(starCenter));
					bufferBuilder.addVertex(new Vector3f(starSize, starSize, 0.0F).mul(rotation).add(starCenter));
					bufferBuilder.addVertex(new Vector3f(-starSize, starSize, 0.0F).mul(rotation).add(starCenter));
					bufferBuilder.addVertex(new Vector3f(-starSize, -starSize, 0.0F).mul(rotation).add(starCenter));
				}
			}
			try (MeshData mesh = bufferBuilder.buildOrThrow()) {
				indexCount = mesh.drawState().indexCount();
				vertexBuffer = RenderSystem.getDevice()
						.createBuffer(() -> "SpaceFold star vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
			}
		}
	}
}
