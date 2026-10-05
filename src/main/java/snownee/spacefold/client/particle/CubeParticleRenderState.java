package snownee.spacefold.client.particle;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

public class CubeParticleRenderState implements ParticleGroupRenderState {
	private static final double HALF_THICKNESS = 0.05;

	private double[] xs = new double[64];
	private double[] ys = new double[64];
	private double[] zs = new double[64];
	private float[] halves = new float[64];
	private float[] alphas = new float[64];
	private int[] colors = new int[64];
	private int count;

	private static void renderCube(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, float h, int argb) {
		double x0 = x - h;
		double x1 = x + h;
		double y0 = y - h;
		double y1 = y + h;
		double z0 = z - h;
		double z1 = z + h;

		edge(buffer, pose, x0, y0, z0, x1, y0, z0, argb);
		edge(buffer, pose, x0, y1, z0, x1, y1, z0, argb);
		edge(buffer, pose, x0, y0, z1, x1, y0, z1, argb);
		edge(buffer, pose, x0, y1, z1, x1, y1, z1, argb);

		edge(buffer, pose, x0, y0, z0, x0, y1, z0, argb);
		edge(buffer, pose, x1, y0, z0, x1, y1, z0, argb);
		edge(buffer, pose, x0, y0, z1, x0, y1, z1, argb);
		edge(buffer, pose, x1, y0, z1, x1, y1, z1, argb);

		edge(buffer, pose, x0, y0, z0, x0, y0, z1, argb);
		edge(buffer, pose, x1, y0, z0, x1, y0, z1, argb);
		edge(buffer, pose, x0, y1, z0, x0, y1, z1, argb);
		edge(buffer, pose, x1, y1, z0, x1, y1, z1, argb);
	}

	private static void edge(
			VertexConsumer buffer,
			PoseStack.Pose pose,
			double x0,
			double y0,
			double z0,
			double x1,
			double y1,
			double z1,
			int argb) {
		double t = HALF_THICKNESS;
		double minX;
		double minY;
		double minZ;
		double maxX;
		double maxY;
		double maxZ;
		if (x0 != x1) {
			minX = x0;
			maxX = x1;
			minY = y0 - t;
			maxY = y0 + t;
			minZ = z0 - t;
			maxZ = z0 + t;
		} else if (y0 != y1) {
			minX = x0 - t;
			maxX = x0 + t;
			minY = y0;
			maxY = y1;
			minZ = z0 - t;
			maxZ = z0 + t;
		} else {
			minX = x0 - t;
			maxX = x0 + t;
			minY = y0 - t;
			maxY = y0 + t;
			minZ = z0;
			maxZ = z1;
		}
		box(buffer, pose, minX, minY, minZ, maxX, maxY, maxZ, argb);
	}

	private static void box(
			VertexConsumer buffer,
			PoseStack.Pose pose,
			double x0,
			double y0,
			double z0,
			double x1,
			double y1,
			double z1,
			int argb) {
		quad(buffer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb);
		quad(buffer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, argb);
		quad(buffer, pose, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, argb);
		quad(buffer, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, argb);
		quad(buffer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, argb);
		quad(buffer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb);
	}

	private static void quad(
			VertexConsumer buffer,
			PoseStack.Pose pose,
			double ax,
			double ay,
			double az,
			double bx,
			double by,
			double bz,
			double cx,
			double cy,
			double cz,
			double dx,
			double dy,
			double dz,
			int argb) {
		buffer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(argb);
		buffer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(argb);
		buffer.addVertex(pose, (float) cx, (float) cy, (float) cz).setColor(argb);
		buffer.addVertex(pose, (float) dx, (float) dy, (float) dz).setColor(argb);
	}

	public void add(double x, double y, double z, float half, int color, float alpha) {
		if (this.count == this.xs.length) {
			this.grow();
		}
		this.xs[this.count] = x;
		this.ys[this.count] = y;
		this.zs[this.count] = z;
		this.halves[this.count] = half;
		this.colors[this.count] = color;
		this.alphas[this.count] = alpha;
		this.count++;
	}

	@Override
	public void clear() {
		this.count = 0;
	}

	@Override
	public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
		if (this.count == 0) {
			return;
		}
		double camX = camera.pos.x;
		double camY = camera.pos.y;
		double camZ = camera.pos.z;
		collector.submitCustomGeometry(
				new PoseStack(), CubeRenderType.TYPE, (pose, buffer) -> {
					for (int i = 0; i < this.count; i++) {
						int argb = ((int) (this.alphas[i] * 255.0F) << 24) | (this.colors[i] & 0xFFFFFF);
						renderCube(buffer, pose, this.xs[i] - camX, this.ys[i] - camY, this.zs[i] - camZ, this.halves[i], argb);
					}
				});
	}

	private void grow() {
		int capacity = this.xs.length * 2;
		this.xs = Arrays.copyOf(this.xs, capacity);
		this.ys = Arrays.copyOf(this.ys, capacity);
		this.zs = Arrays.copyOf(this.zs, capacity);
		this.halves = Arrays.copyOf(this.halves, capacity);
		this.alphas = Arrays.copyOf(this.alphas, capacity);
		this.colors = Arrays.copyOf(this.colors, capacity);
	}
}
