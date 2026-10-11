package snownee.spacefold.client.sky;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 在房间维度（plan / simulate）的夜空中绘制划过的流星。
 * <p>
 * 纯视觉装饰，不参与任何玩法逻辑，也不需要服务端同步：所有流星完全由客户端本地时钟
 * {@code level.getGameTime()} 通过确定性伪随机推导出来。因此同一维度里每个客户端、以及
 * plan/simulate 两个维度，在同一时刻看到的流星完全一致（种子不含维度 id）。
 * <p>
 * 轨迹是**有限距离的真实三维直线**：路径最近点离相机 {@code distance ∈ [MIN_DISTANCE, MAX_DISTANCE]}
 * 格，头沿直线飞行、尾迹是头后方的一段真实线段。因此会有正常的近大远小/透视汇聚，
 * 飞远时尾迹收敛成一点。因为含真实深度，尾迹不会像"天球贴纸"那样保持固定屏幕角度。
 */
public final class ShootingStars {
	/** 路径（直线）到相机的最近距离下限，保证流星不会贴脸飞过。 */
	private static final float MIN_DISTANCE = 120.0F;
	/** 路径最近距离上限：随机取值范围 [MIN_DISTANCE, MAX_DISTANCE]。 */
	private static final float MAX_DISTANCE = 600.0F;
	/** 每刻（1/20 秒）生成一颗流星的独立概率。0.1 ≈ 平均每秒 2 颗（当前为测试值）。 */
	private static final float SPAWN_CHANCE_PER_TICK = 0.2F;
	/** 单颗流星的存活时长（刻）。20 刻 = 1 秒。 */
	private static final int LIFETIME_TICKS = 30;
	/** 淡入/淡出各占寿命的比例：开头 FADE_FRACTION 淡入，结尾 FADE_FRACTION 淡出。 */
	private static final float FADE_FRACTION = 0.15F;
	/** 头在寿命内沿直线走过的总路程（格）。 */
	private static final float TRAVEL = 900.0F;
	/** 尾迹长度（格），即头后方那段真实线段的长度。 */
	private static final float TAIL_LENGTH = 300.0F;
	/** 尾迹长度随 fade 变化的指数（透明度仍用 fade 本身）：<1 长度更快到全长，>1 更晚伸长。 */
	private static final float TAIL_LENGTH_EXPONENT = 0.5F;
	/** 头部的半宽（格）。相对较宽、尾部收窄，做出彗星/流星的拖尾形状。 */
	private static final float HEAD_HALF_WIDTH = 0.6F;
	/** 尾部半宽相对头部的比例（向尾部收窄）。 */
	private static final float TAIL_WIDTH_FACTOR = 0.4F;
	/** 运动方向的基准方位角（度）：从正东(+X)向正南(+Z)偏转，正值即"东偏南"。 */
	private static final float MOTION_HEADING_DEG = 25.0F;
	/** 运动方向的基准俯仰角（度）：在水平方向基础上向下倾斜，形成斜向下划过的效果。 */
	private static final float MOTION_PITCH_DEG = 20.0F;
	/** 方位角的小幅随机抖动（度）：每颗流星在基准上 ±该值，避免所有轨迹完全平行。 */
	private static final float MOTION_HEADING_JITTER_DEG = 8.0F;
	/** 俯仰角的小幅随机抖动（度）：每颗流星在基准上 ±该值。 */
	private static final float MOTION_PITCH_JITTER_DEG = 8.0F;
	/** 头部最大不透明度（再乘以星空的 starBrightness）。 */
	private static final float HEAD_ALPHA = 0.9F;
	/** 流星颜色（头尾同色，靠 alpha 做渐隐）。 */
	private static final int HEAD_RGB = 0xCFE4FF;
	/** 每颗流星用一个四边形（2 个三角形 = 4 个顶点）绘制。 */
	private static final int VERTICES_PER_METEOR = 4;
	/** 同一时刻可能存在的最大流星数（等价于可能生成流星的刻数），用于预分配顶点缓冲。 */
	private static final int MAX_METEORS = LIFETIME_TICKS + 1;

	/** 复用型顶点缓冲，按最大用量一次性分配，避免每帧创建/销毁 GPU 缓冲。 */
	private static GpuBuffer vertexBuffer;

	private ShootingStars() {
	}

	/**
	 * 由 {@code mixin.client.SkyRendererMixin} 在 {@code SkyRenderer.renderSunMoonAndStars} 的 TAIL 调用。
	 * 传入的 poseStack 已经乘上相机的视角旋转（相机在天球原点），因此这里把它的矩阵并入
	 * modelViewStack 后，流星便与星空处于同一坐标系。
	 *
	 * @param starBrightness 香草星星亮度（0~1），为 0 时（白天/强光）不绘制流星
	 */
	public static void render(PoseStack poseStack, float starBrightness) {
		if (starBrightness <= 0.0F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		// 渲染时刻 = 整刻 + 亚刻插值，保证移动平滑；两个维度共用同一 gameTime，故镜像一致。
		double time = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long nowTick = (long) Math.floor(time);

		// —— 运动方向以 world 空间的"东偏南 + 向下俯仰"为基准，每颗流星再小幅抖动 ——
		float baseHeading = MOTION_HEADING_DEG * Mth.DEG_TO_RAD;
		float basePitch = MOTION_PITCH_DEG * Mth.DEG_TO_RAD;
		float headingJitter = MOTION_HEADING_JITTER_DEG * Mth.DEG_TO_RAD;
		float pitchJitter = MOTION_PITCH_JITTER_DEG * Mth.DEG_TO_RAD;
		// pose 把"星空局部坐标"旋转到 world；其逆把 world 方向转回局部坐标。
		Matrix4f skyInverse = new Matrix4f(poseStack.last().pose()).invert();

		VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
		int capacity = format.getVertexSize() * MAX_METEORS * VERTICES_PER_METEOR;

		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(capacity)) {
			BufferBuilder builder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.TRIANGLE_FAN, format);
			int quadCount = 0;
			// 只有寿命窗口内的"刻"才可能有活跃流星，逐刻回看即可（确定性、无状态）。
			for (long bucket = nowTick - LIFETIME_TICKS; bucket <= nowTick; bucket++) {
				float age = (float) (time - bucket);
				if (age < 0.0F || age > LIFETIME_TICKS) {
					continue;
				}
				// 用该刻的哈希播种：同一刻在所有客户端得到相同随机序列。
				RandomSource random = RandomSource.create(mix(bucket));
				if (random.nextFloat() >= SPAWN_CHANCE_PER_TICK) {
					continue;
				}

				// —— 一条三维直线：小幅随机的运动方向 + 轨道锚点（最近点）——
				float heading = baseHeading + (random.nextFloat() * 2.0F - 1.0F) * headingJitter;
				float pitch = basePitch + (random.nextFloat() * 2.0F - 1.0F) * pitchJitter;
				float sinPitch = Mth.sin(pitch);
				float cosPitch = Mth.cos(pitch);
				float sinHeading = Mth.sin(heading);
				float cosHeading = Mth.cos(heading);
				Vector3f direction = skyInverse.transformDirection(new Vector3f(
						cosPitch * cosHeading,
						-sinPitch,
						cosPitch * sinHeading)).normalize();
				// direction 的垂直平面内一组正交基：u1 偏上、u2 水平横切（二者都 ⊥ direction）。
				Vector3f u1 = new Vector3f(sinPitch * cosHeading, cosPitch, sinPitch * sinHeading);
				Vector3f u2 = new Vector3f(-sinHeading, 0.0F, cosHeading);
				// 锚点在该平面内取 n(ψ) = cosψ·u1 + sinψ·u2：其 world 南向分量 u1.z·cosψ + u2.z·sinψ
				// 是一段正弦，零点落在 ψ1 = atan2(u2.z, u1.z) - π/2。把 ψ 的两个符号区间各占 50%，
				// 即可保证玩家南/北两侧的流星数量严格均等；ψ ∈ [-π/2, π/2] 又恒在地平线以上。
				float psi1 = (float) Mth.atan2(cosHeading, sinPitch * sinHeading) - Mth.HALF_PI;
				float psi = random.nextBoolean()
						? Mth.lerp(random.nextFloat(), psi1, Mth.HALF_PI)
						: Mth.lerp(random.nextFloat(), -Mth.HALF_PI, psi1);
				Vector3f anchor = new Vector3f(u1).mul(Mth.cos(psi)).add(new Vector3f(u2).mul(Mth.sin(psi)));
				// 因 anchor ⊥ direction，整条直线到相机的最近距离就是 |nearest|，故 ≥ MIN_DISTANCE。
				float distance = MIN_DISTANCE + random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE);
				Vector3f nearest = skyInverse.transformDirection(anchor).mul(distance);

				// —— 头/尾在直线上的位置 ——
				// s 从 -TRAVEL/2 到 +TRAVEL/2，最近点 s=0 位于路径中点；尾在头后方 TAIL_LENGTH 处。
				float s = (age / LIFETIME_TICKS - 0.5F) * TRAVEL;
				Vector3f head = new Vector3f(nearest).add(new Vector3f(direction).mul(s));
				// 生命周期包络：开场淡入、结尾淡出。尾迹长度与透明度同步变化——
				// fade 小时尾迹短（淡入时由短变长、淡出时由长变短），fade=1 时为全长。
				float fade = fadeAlpha(age / LIFETIME_TICKS);
				// 长度用独立指数：透明度用 fade，长度用 fade^TAIL_LENGTH_EXPONENT。
				float tailFactor = (float) Math.pow(fade, TAIL_LENGTH_EXPONENT);
				Vector3f tail = new Vector3f(head).sub(new Vector3f(direction).mul(TAIL_LENGTH * tailFactor));

				// —— 公告板四边形 ——
				// 侧向取「运动方向 × 指向相机方向」，使四边形正面朝向相机（含真实透视）。
				// 当头几乎沿视线（近/远）时方向退化，此时流星本就缩成一点，随便取一个垂向即可。
				Vector3f radial = new Vector3f(head).add(tail).normalize();
				Vector3f side = new Vector3f(direction).cross(radial);
				if (side.lengthSquared() < 1.0E-6F) {
					side.set(0.0F, 1.0F, 0.0F).cross(direction);
					if (side.lengthSquared() < 1.0E-6F) {
						side.set(1.0F, 0.0F, 0.0F);
					}
				}
				side.normalize();

				float tailWidth = HEAD_HALF_WIDTH * TAIL_WIDTH_FACTOR;
				int headColor = alphaColor(HEAD_ALPHA * starBrightness * fade);
				int tailColor = alphaColor(0.0F);
				// 顶点顺序 A(近头)、B(近尾)、C(近尾另一侧)、D(近头另一侧)，正面朝相机。
				addVertex(builder, new Vector3f(head).add(new Vector3f(side).mul(HEAD_HALF_WIDTH)), headColor);
				addVertex(builder, new Vector3f(tail).add(new Vector3f(side).mul(tailWidth)), tailColor);
				addVertex(builder, new Vector3f(tail).sub(new Vector3f(side).mul(tailWidth)), tailColor);
				addVertex(builder, new Vector3f(head).sub(new Vector3f(side).mul(HEAD_HALF_WIDTH)), headColor);
				quadCount++;
			}
			if (quadCount == 0) {
				return;
			}

			try (MeshData mesh = builder.buildOrThrow()) {
				GpuBuffer buffer = vertexBuffer();
				RenderSystem.getDevice()
						.createCommandEncoder()
						.writeToBuffer(buffer.slice(0L, mesh.vertexBuffer().remaining()), mesh.vertexBuffer());

				// 把 poseStack 对应的相机旋转并入 model-view；天空 pass 内没有 SubmitNodeCollector，
				// 只能用原始 RenderPass 绘制。
				Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
				modelViewStack.pushMatrix();
				modelViewStack.mul(poseStack.last().pose());
				GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
						.writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
				GpuTextureView colorTexture = SkyTarget.color(minecraft);
				GpuTextureView depthTexture = SkyTarget.depth(minecraft);
				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(() -> "SpaceFold shooting stars", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
					// SUNRISE_SUNSET：POSITION_COLOR + TRIANGLE_FAN + 半透明混合、无深度状态，
					// 正好用于顶点色渐隐的流星；TRIANGLE_FAN 每颗独立 draw。
					renderPass.setPipeline(RenderPipelines.SUNRISE_SUNSET);
					RenderSystem.bindDefaultUniforms(renderPass);
					renderPass.setUniform("DynamicTransforms", dynamicTransforms);
					renderPass.setVertexBuffer(0, buffer);
					for (int i = 0; i < quadCount; i++) {
						renderPass.draw(i * VERTICES_PER_METEOR, VERTICES_PER_METEOR);
					}
				}
				modelViewStack.popMatrix();
			}
		}
	}

	/** 生命周期包络：开头 FADE_FRACTION 淡入、结尾 FADE_FRACTION 淡出，中间保持 1；用 smoothstep 平滑。 */
	private static float fadeAlpha(float life) {
		float fadeIn = Mth.clamp(life / FADE_FRACTION, 0.0F, 1.0F);
		float fadeOut = Mth.clamp((1.0F - life) / FADE_FRACTION, 0.0F, 1.0F);
		float t = Math.min(fadeIn, fadeOut);
		return t * t * (3.0F - 2.0F * t);
	}

	/** 用给定 alpha 合成头部颜色（颜色通道固定，透明度决定渐隐）。 */
	private static int alphaColor(float alpha) {
		return ((int) (alpha * 255.0F) << 24) | HEAD_RGB;
	}

	private static void addVertex(BufferBuilder builder, Vector3f position, int color) {
		builder.addVertex(position.x, position.y, position.z).setColor(color);
	}

	/** 懒加载复用顶点缓冲：usage = VERTEX | COPY_DST，容量按最大流星数预留。 */
	private static GpuBuffer vertexBuffer() {
		if (vertexBuffer == null) {
			int size = DefaultVertexFormat.POSITION_COLOR.getVertexSize() * MAX_METEORS * VERTICES_PER_METEOR;
			vertexBuffer = RenderSystem.getDevice()
					.createBuffer(() -> "SpaceFold shooting star vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
		}
		return vertexBuffer;
	}

	/** SplitMix64 哈希：把"刻"映射为一个稳定的 64 位种子。 */
	private static long mix(long value) {
		long x = value + 0x9E3779B97F4A7C15L;
		x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
		x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
		return x ^ (x >>> 31);
	}
}
