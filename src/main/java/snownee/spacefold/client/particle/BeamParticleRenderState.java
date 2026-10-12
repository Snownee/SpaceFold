package snownee.spacefold.client.particle;

import java.util.Arrays;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.world.phys.Vec3;

public class BeamParticleRenderState implements ParticleGroupRenderState {
	private static final float BASE_HALF = 0.03F;
	private static final float OUTER_HALF = 0.06F;
	private static final float OUTER_ALPHA = 0.35F;
	private static final float ROLL_SPEED = 0.1F;

	private double[] tailX = new double[16];
	private double[] tailY = new double[16];
	private double[] tailZ = new double[16];
	private double[] headX = new double[16];
	private double[] headY = new double[16];
	private double[] headZ = new double[16];
	private int[] colors = new int[16];
	private float[] alphas = new float[16];
	private int count;
	double animationTime;

	public void add(Vec3 tail, Vec3 head, int color, float alpha) {
		if (this.count == this.tailX.length) {
			this.grow();
		}
		this.tailX[this.count] = tail.x;
		this.tailY[this.count] = tail.y;
		this.tailZ[this.count] = tail.z;
		this.headX[this.count] = head.x;
		this.headY[this.count] = head.y;
		this.headZ[this.count] = head.z;
		this.colors[this.count] = color;
		this.alphas[this.count] = alpha;
		this.count++;
	}

	@Override
	public void clear() {
		this.count = 0;
	}

	@Override
	public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (this.count == 0) {
			return;
		}
		float roll = (float) (this.animationTime * ROLL_SPEED);
		double camX = camera.pos.x;
		double camY = camera.pos.y;
		double camZ = camera.pos.z;
		PoseStack poseStack = new PoseStack();
		for (int i = 0; i < this.count; i++) {
			double tx = this.tailX[i] - camX;
			double ty = this.tailY[i] - camY;
			double tz = this.tailZ[i] - camZ;
			double dx = this.headX[i] - this.tailX[i];
			double dy = this.headY[i] - this.tailY[i];
			double dz = this.headZ[i] - this.tailZ[i];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (length < 1.0E-4) {
				continue;
			}
			Quaternionf align = new Quaternionf().rotationTo(
					new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float) dx, (float) dy, (float) dz));
			int argb = ((int) (this.alphas[i] * 255.0F) << 24) | (this.colors[i] & 0xFFFFFF);
			int outerArgb = ((int) (this.alphas[i] * OUTER_ALPHA * 255.0F) << 24) | (this.colors[i] & 0xFFFFFF);
			float len = (float) length;
			poseStack.pushPose();
			poseStack.translate((float) tx, (float) ty, (float) tz);
			poseStack.mulPose(align);
			poseStack.mulPose(Axis.YP.rotation(roll));
			submitNodeCollector.submitCustomGeometry(
					poseStack, BeamRenderType.TYPE, (pose, buffer) -> pyramid(pose, buffer, outerArgb, len, OUTER_HALF));
			submitNodeCollector.submitCustomGeometry(
					poseStack, BeamRenderType.TYPE, (pose, buffer) -> pyramid(pose, buffer, argb, len, BASE_HALF));
			poseStack.popPose();
		}
	}

	private static void pyramid(PoseStack.Pose pose, VertexConsumer buffer, int argb, float length, float half) {
		float ax = -half;
		float az = -half;
		float bx = half;
		float bz = -half;
		float cx = half;
		float cz = half;
		float dx = -half;
		float dz = half;
		triangle(buffer, pose, argb, 0.0F, 0.0F, 0.0F, ax, length, az, bx, length, bz);
		triangle(buffer, pose, argb, 0.0F, 0.0F, 0.0F, bx, length, bz, cx, length, cz);
		triangle(buffer, pose, argb, 0.0F, 0.0F, 0.0F, cx, length, cz, dx, length, dz);
		triangle(buffer, pose, argb, 0.0F, 0.0F, 0.0F, dx, length, dz, ax, length, az);
		triangle(buffer, pose, argb, ax, length, az, bx, length, bz, cx, length, cz);
		triangle(buffer, pose, argb, ax, length, az, cx, length, cz, dx, length, dz);
	}

	private static void triangle(
			VertexConsumer buffer,
			PoseStack.Pose pose,
			int argb,
			float x0,
			float y0,
			float z0,
			float x1,
			float y1,
			float z1,
			float x2,
			float y2,
			float z2) {
		buffer.addVertex(pose, x0, y0, z0).setColor(argb);
		buffer.addVertex(pose, x1, y1, z1).setColor(argb);
		buffer.addVertex(pose, x2, y2, z2).setColor(argb);
	}

	private void grow() {
		int capacity = this.tailX.length * 2;
		this.tailX = Arrays.copyOf(this.tailX, capacity);
		this.tailY = Arrays.copyOf(this.tailY, capacity);
		this.tailZ = Arrays.copyOf(this.tailZ, capacity);
		this.headX = Arrays.copyOf(this.headX, capacity);
		this.headY = Arrays.copyOf(this.headY, capacity);
		this.headZ = Arrays.copyOf(this.headZ, capacity);
		this.colors = Arrays.copyOf(this.colors, capacity);
		this.alphas = Arrays.copyOf(this.alphas, capacity);
	}
}
