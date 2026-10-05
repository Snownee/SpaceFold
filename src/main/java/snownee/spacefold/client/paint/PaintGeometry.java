package snownee.spacefold.client.paint;

import net.minecraft.core.Direction;

public final class PaintGeometry {
	private static final float[][] UV = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};

	private static final float[][][] POSITIONS = new float[6][4][3];

	static {
		positions(Direction.DOWN, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1);
		positions(Direction.UP, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0);
		positions(Direction.NORTH, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0);
		positions(Direction.SOUTH, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1);
		positions(Direction.WEST, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1);
		positions(Direction.EAST, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0);
	}

	private PaintGeometry() {
	}

	private static void positions(Direction face, float... coords) {
		float[][] facePositions = POSITIONS[face.get3DDataValue()];
		for (int i = 0; i < 4; i++) {
			facePositions[i][0] = coords[i * 3];
			facePositions[i][1] = coords[i * 3 + 1];
			facePositions[i][2] = coords[i * 3 + 2];
		}
	}

	public static float x(Direction face, int vertex) {
		return POSITIONS[face.get3DDataValue()][vertex][0];
	}

	public static float y(Direction face, int vertex) {
		return POSITIONS[face.get3DDataValue()][vertex][1];
	}

	public static float z(Direction face, int vertex) {
		return POSITIONS[face.get3DDataValue()][vertex][2];
	}

	public static float u(int rotation, int vertex) {
		float u = UV[vertex][0];
		float v = UV[vertex][1];
		return switch (rotation) {
			case 1 -> v;
			case 2 -> 1.0F - u;
			case 3 -> 1.0F - v;
			default -> u;
		};
	}

	public static float v(int rotation, int vertex) {
		float u = UV[vertex][0];
		float v = UV[vertex][1];
		return switch (rotation) {
			case 1 -> 1.0F - u;
			case 2 -> 1.0F - v;
			case 3 -> u;
			default -> v;
		};
	}
}
