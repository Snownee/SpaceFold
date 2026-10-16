package snownee.spacefold.client.room;

import java.util.Objects;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.util.Mth;
import snownee.spacefold.client.sky.FogVolume;
import snownee.spacefold.client.sky.ShootingStars;
import snownee.spacefold.client.sky.StarField;

/**
 * 把房间维度（plan / simulate）的空旷夜空渲染成一块 GUI 背景：深空底色 + 星空 + 体积雾 + 流星。
 * <p>
 * 天空的自绘代码（{@link StarField} / {@link FogVolume} / {@link ShootingStars}）原本在世界的天空 pass 里运行，
 * 使用透视投影、模型视图为单位矩阵、相机朝向烘进投影矩阵。这里作为 picture-in-picture 渲染到离屏纹理：
 * 临时把投影换成本类的透视投影（GUI 的正交投影随后会被 {@code GuiRenderer} 重新设置），
 * 天空各元素通过 {@code SkyTarget} 自动落到离屏纹理上。
 * <p>
 * {@link RoomSkyRenderState#progress()}（0~1）驱动强度淡入淡出；{@link RoomSkyRenderState#fovProgress()}
 * 单独驱动"跃迁"的 FOV 收拢（时长更长、减速更强），由 {@link #FOV_ENTER} 收到 {@link #FOV_REST}。
 * 整片夜空额外做缓慢偏航自转（星空自转速度的 1/4），并叠加界面传入的平移偏航，营造空旷虚空感。
 */
public class RoomSkyRenderer extends PictureInPictureRenderer<RoomSkyRenderState> {
	/** 与房间维度数据一致的星空亮度（progress=1 时）。 */
	private static final float STAR_BRIGHTNESS = 0.75F;
	/** 退场/进场两端与稳定态的 FOV：开屏时视野更广，随后收拢，形成"跃迁"感。 */
	private static final float FOV_ENTER = 90.0F;
	private static final float FOV_REST = 70.0F;
	private static final float Z_NEAR = 0.05F;
	private static final float Z_FAR = 4096.0F;
	/** 夜空底色（房间维度 fog_color #0f0f16）的 RGB 分量，清屏时按 progress 预乘 alpha 淡入。 */
	private static final int SKY_RGB = 0x0F0F16;
	/** 星空自转角速度（弧度 / 刻）；缓慢到数分钟才转一圈。 */
	private static final float STAR_DRIFT_RADIANS_PER_TICK = 0.001F;

	private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("spacefold room sky");
	private final Matrix4f projectionMatrix = new Matrix4f();

	public RoomSkyRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<RoomSkyRenderState> getRenderStateClass() {
		return RoomSkyRenderState.class;
	}

	@Override
	protected void renderToTexture(RoomSkyRenderState state, PoseStack poseStack) {
		float progress = Mth.clamp(state.progress(), 0.0F, 1.0F);
		float fovProgress = Mth.clamp(state.fovProgress(), 0.0F, 1.0F);
		int width = state.x1() - state.x0();
		int height = state.y1() - state.y0();
		float fov = Mth.lerp(fovProgress, FOV_ENTER, FOV_REST);
		projectionMatrix.identity().setPerspective(
				(float) Math.toRadians(fov),
				width / (float) height,
				Z_NEAR,
				Z_FAR,
				RenderSystem.getDevice().isZZeroToOne());
		RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE);

		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		modelViewStack.pushMatrix();
		modelViewStack.identity();

		Minecraft minecraft = Minecraft.getInstance();
		double time = Objects.requireNonNull(minecraft.level).getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// 先把时间对自转周期取模，避免 gameTime 很大时 float 精度不足导致角度停滞。
		double driftPeriod = Mth.TWO_PI / STAR_DRIFT_RADIANS_PER_TICK;
		float starDrift = (float) (time % driftPeriod * STAR_DRIFT_RADIANS_PER_TICK);
		float brightness = STAR_BRIGHTNESS * progress;

		// 整片夜空统一偏航：空闲自转为星空自转的 1/4 速度，再叠加平移带来的小幅旋转。
		float skyRotation = starDrift * 0.25F + state.rotation();
		PoseStack starPose = new PoseStack();
		starPose.mulPose(Axis.YP.rotation(skyRotation));
		// 底色预乘 alpha 随 progress 淡入：开屏时天空透明，世界可见，随进度交叉淡入到虚空色。
		int clearColor = (Math.round(progress * 255.0F) << 24)
				| (Math.round((SKY_RGB >> 16 & 0xFF) * progress) << 16)
				| (Math.round((SKY_RGB >> 8 & 0xFF) * progress) << 8)
				| Math.round((SKY_RGB & 0xFF) * progress);
		StarField.render(starPose, brightness, clearColor);

		PoseStack skyPose = new PoseStack();
		skyPose.mulPose(Axis.YP.rotation(skyRotation));
		FogVolume.render(skyPose, progress);
		ShootingStars.render(skyPose, brightness);

		modelViewStack.popMatrix();
	}

	@Override
	protected String getTextureLabel() {
		return "room_sky";
	}

	@Override
	public void close() {
		super.close();
		projectionBuffer.close();
	}
}
