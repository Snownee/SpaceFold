package snownee.spacefold.client.sky;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import snownee.spacefold.SpaceFold;

/**
 * 在房间维度（plan / simulate）的远处绘制体积雾。
 * <p>
 * 用一批面向相机的大尺寸半透明贴图公告板（雾团）铺在地平线上：散布在相机周围不同方位、仰角、
 * 距离上，彼此重叠，叠加成一片有体积感的远景雾气。雾团位于世界固定方向上，只随视角旋转（类似
 * 星空），并且同色，多次 alpha 叠加与顺序无关，因此不需要排序。
 * <p>
 * 性能要点：雾团的方向/尺寸/颜色与时间无关，所以顶点只在首次渲染时构建并上传一次；之后每帧
 * 只更新一个 model-view 矩阵（相机视角 × 缓慢漂移的绕 Y 旋转），不再重建网格、不再写缓冲，
 * 也不产生每帧的临时对象分配。绘制发生在天空 pass，写入主渲染目标；深度测试开启但不写深度：
 * 近处地形会遮住雾，而雾能盖住它后方的空洞。GPU 侧的开销主要来自大雾团的 overdraw，通过
 * {@link #SPRITE_COUNT} / 尺寸 / 贴图 alpha 的具体取值来权衡。
 */
public final class FogVolume {
	/** 自定义管线：POSITION_TEX_COLOR + 半透明混合、只测深度不写深度、不剔除 + 一张贴图。 */
	public static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
					.withLocation(SpaceFold.id("pipeline/spacefold_fog"))
					.withVertexShader("core/position_tex_color")
					.withFragmentShader("core/position_tex_color")
					.withSampler("Sampler0")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withCull(false)
					.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
					.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
					.build()
	);
	private static final Identifier TEXTURE = SpaceFold.id("textures/environment/fog.png");
	private static final Vector3f WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);
	/** 雾团数量。GPU 的 overdraw 与之正相关，是首要的性能旋钮。 */
	private static final int SPRITE_COUNT = 96;
	/** 雾团中心到相机的距离范围（格），保证雾只出现在"远处"。 */
	private static final float MIN_DISTANCE = 160.0F;
	private static final float MAX_DISTANCE = 520.0F;
	/** 雾团世界尺寸范围（格，取宽度）。 */
	private static final float MIN_SIZE = 140.0F;
	private static final float MAX_SIZE = 360.0F;
	/** 高度 / 宽度，越大越接近正方形。 */
	private static final float ASPECT = 0.55F;
	/** 雾团中心仰角范围（弧度）：主要贴着地平线。 */
	private static final float MIN_ELEVATION = -0.40F;
	private static final float MAX_ELEVATION = 0.18F;
	/** 单个雾团的顶点色不透明度上限（再乘以贴图自身的 alpha）。 */
	private static final float BASE_ALPHA = 0.18F;
	/** 雾的颜色（不含 alpha），比维度雾色略亮，才能在漆黑虚空中被看见。 */
	private static final int MIST_RGB = 0x464676;
	/** 整体缓慢漂移（弧度 / 刻），让雾团横过天空。 */
	private static final float DRIFT_RADIANS_PER_TICK = 0.0001F;
	/** 每个雾团 4 个顶点。 */
	private static final int VERTICES_PER_SPRITE = 4;

	/** 一次性构建的静态顶点缓冲（世界方向上的雾团）。 */
	private static GpuBuffer vertexBuffer;
	private static GpuBuffer indexBuffer;
	private static boolean built;

	private FogVolume() {
	}

	/** 由 {@code ClientProxy} 在客户端初始化时调用，确保管线随其它管线一起预编译。 */
	public static void init() {
	}

	/**
	 * 由 {@code mixin.client.SkyRendererMixin} 在 {@code SkyRenderer.renderSunMoonAndStars} 的 TAIL 调用。
	 * 传入的 poseStack 此时是单位矩阵，因此只需并入 model-view 的相机视角旋转即可。
	 */
	public static void render(PoseStack poseStack) {
		if (!built) {
			build();
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		float drift = (level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)) * DRIFT_RADIANS_PER_TICK;

		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		modelViewStack.mul(poseStack.last().pose());
		modelViewStack.rotateY(drift);
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

		AbstractTexture texture = minecraft.getTextureManager().getTexture(TEXTURE);
		GpuTextureView colorTexture = minecraft.getMainRenderTarget().getColorTextureView();
		GpuTextureView depthTexture = minecraft.getMainRenderTarget().getDepthTextureView();
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "SpaceFold fog volume", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
			renderPass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type());
			renderPass.drawIndexed(0, 0, SPRITE_COUNT * 6, 1);
		}
		modelViewStack.popMatrix();
	}

	/** 一次性构建并上传静态雾团网格与索引缓冲。 */
	private static void build() {
		VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
		int capacity = format.getVertexSize() * SPRITE_COUNT * VERTICES_PER_SPRITE;
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(capacity)) {
			BufferBuilder builder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);
			for (int i = 0; i < SPRITE_COUNT; i++) {
				RandomSource random = RandomSource.create(mix(i));
				float azimuth = random.nextFloat() * Mth.TWO_PI;
				float elevation = Mth.lerp(random.nextFloat(), MIN_ELEVATION, MAX_ELEVATION);
				float distance = Mth.lerp(random.nextFloat(), MIN_DISTANCE, MAX_DISTANCE);
				float size = Mth.lerp(random.nextFloat(), MIN_SIZE, MAX_SIZE);
				int color = alphaColor(BASE_ALPHA * (0.5F + random.nextFloat() * 0.5F));

				float cosElevation = Mth.cos(elevation);
				Vector3f direction = new Vector3f(cosElevation * Mth.cos(azimuth), Mth.sin(elevation), cosElevation * Mth.sin(azimuth));
				Vector3f center = new Vector3f(direction).mul(distance);
				Vector3f right = new Vector3f(direction).cross(WORLD_UP);
				if (right.lengthSquared() < 1.0E-6F) {
					right.set(1.0F, 0.0F, 0.0F);
				}
				right.normalize();
				Vector3f up = new Vector3f(right).cross(direction).normalize();
				Vector3f rightHalf = right.mul(size * 0.5F);
				Vector3f upHalf = up.mul(size * 0.5F * ASPECT);

				addVertex(builder, new Vector3f(center).sub(rightHalf).sub(upHalf), 0.0F, 1.0F, color);
				addVertex(builder, new Vector3f(center).add(rightHalf).sub(upHalf), 1.0F, 1.0F, color);
				addVertex(builder, new Vector3f(center).add(rightHalf).add(upHalf), 1.0F, 0.0F, color);
				addVertex(builder, new Vector3f(center).sub(rightHalf).add(upHalf), 0.0F, 0.0F, color);
			}

			try (MeshData mesh = builder.buildOrThrow()) {
				vertexBuffer = RenderSystem.getDevice()
						.createBuffer(() -> "SpaceFold fog volume vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
			}
		}
		indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(SPRITE_COUNT * 6);
		built = true;
	}

	private static void addVertex(BufferBuilder builder, Vector3f position, float u, float v, int color) {
		builder.addVertex(position.x, position.y, position.z).setUv(u, v).setColor(color);
	}

	/** 用给定 alpha 合成雾色（颜色通道固定，透明度决定浓淡）。 */
	private static int alphaColor(float alpha) {
		return ((int) (alpha * 255.0F) << 24) | MIST_RGB;
	}

	/** SplitMix64 哈希：把雾团序号映射为稳定的 64 位种子。 */
	private static long mix(long value) {
		long x = value + 0x9E3779B97F4A7C15L;
		x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
		x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
		return x ^ (x >>> 31);
	}
}
