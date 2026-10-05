package snownee.spacefold.paint;

import net.minecraft.core.Direction;

public final class FacingRotation {
	private FacingRotation() {
	}

	public static Direction toWorld(Direction facing, Direction modelFace) {
		return rotateFace(facing, modelFace);
	}

	public static Direction toModel(Direction facing, Direction worldFace) {
		return rotateFace(opposite(facing), worldFace);
	}

	private static Direction opposite(Direction facing) {
		return switch (facing) {
			case EAST -> Direction.WEST;
			case WEST -> Direction.EAST;
			case UP -> Direction.DOWN;
			case DOWN -> Direction.UP;
			default -> facing;
		};
	}

	private static Direction rotateFace(Direction facing, Direction face) {
		return switch (facing) {
			case NORTH -> face;
			case SOUTH -> switch (face) {
				case NORTH -> Direction.SOUTH;
				case SOUTH -> Direction.NORTH;
				case EAST -> Direction.WEST;
				case WEST -> Direction.EAST;
				default -> face;
			};
			case EAST -> switch (face) {
				case NORTH -> Direction.EAST;
				case EAST -> Direction.SOUTH;
				case SOUTH -> Direction.WEST;
				case WEST -> Direction.NORTH;
				default -> face;
			};
			case WEST -> switch (face) {
				case NORTH -> Direction.WEST;
				case WEST -> Direction.SOUTH;
				case SOUTH -> Direction.EAST;
				case EAST -> Direction.NORTH;
				default -> face;
			};
			case DOWN -> switch (face) {
				case NORTH -> Direction.DOWN;
				case DOWN -> Direction.SOUTH;
				case SOUTH -> Direction.UP;
				case UP -> Direction.NORTH;
				default -> face;
			};
			case UP -> switch (face) {
				case NORTH -> Direction.UP;
				case UP -> Direction.SOUTH;
				case SOUTH -> Direction.DOWN;
				case DOWN -> Direction.NORTH;
				default -> face;
			};
		};
	}

	public static void rotatePosition(Direction facing, float x, float y, float z, float[] out) {
		switch (facing) {
			case NORTH -> {
				out[0] = x;
				out[1] = y;
				out[2] = z;
			}
			case SOUTH -> {
				out[0] = 1.0F - x;
				out[1] = y;
				out[2] = 1.0F - z;
			}
			case EAST -> {
				out[0] = 1.0F - z;
				out[1] = y;
				out[2] = x;
			}
			case WEST -> {
				out[0] = z;
				out[1] = y;
				out[2] = 1.0F - x;
			}
			case DOWN -> {
				out[0] = x;
				out[1] = z;
				out[2] = 1.0F - y;
			}
			case UP -> {
				out[0] = x;
				out[1] = 1.0F - z;
				out[2] = y;
			}
		}
	}
}
