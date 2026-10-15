package snownee.spacefold.paint;

import net.minecraft.core.Direction;

public final class FacingRotation {
	private FacingRotation() {
	}

	public static Direction toWorld(Direction facing, Direction modelFace) {
		return switch (facing) {
			case UP -> modelFace;
			case DOWN -> modelFace.getOpposite();
			case NORTH -> switch (modelFace) {
				case UP -> Direction.NORTH;
				case DOWN -> Direction.SOUTH;
				case NORTH -> Direction.DOWN;
				case SOUTH -> Direction.UP;
				default -> modelFace;
			};
			case SOUTH -> switch (modelFace) {
				case UP -> Direction.SOUTH;
				case DOWN -> Direction.NORTH;
				case NORTH -> Direction.DOWN;
				case SOUTH -> Direction.UP;
				case EAST -> Direction.WEST;
				case WEST -> Direction.EAST;
			};
			case EAST -> switch (modelFace) {
				case UP -> Direction.EAST;
				case DOWN -> Direction.WEST;
				case NORTH -> Direction.DOWN;
				case SOUTH -> Direction.UP;
				case EAST -> Direction.SOUTH;
				case WEST -> Direction.NORTH;
			};
			case WEST -> switch (modelFace) {
				case UP -> Direction.WEST;
				case DOWN -> Direction.EAST;
				case NORTH -> Direction.DOWN;
				case SOUTH -> Direction.UP;
				case EAST -> Direction.NORTH;
				case WEST -> Direction.SOUTH;
			};
		};
	}

	public static Direction toModel(Direction facing, Direction worldFace) {
		return switch (facing) {
			case UP -> worldFace;
			case DOWN -> worldFace.getOpposite();
			case NORTH -> switch (worldFace) {
				case NORTH -> Direction.UP;
				case SOUTH -> Direction.DOWN;
				case DOWN -> Direction.NORTH;
				case UP -> Direction.SOUTH;
				default -> worldFace;
			};
			case SOUTH -> switch (worldFace) {
				case SOUTH -> Direction.UP;
				case NORTH -> Direction.DOWN;
				case DOWN -> Direction.NORTH;
				case UP -> Direction.SOUTH;
				case EAST -> Direction.WEST;
				case WEST -> Direction.EAST;
			};
			case EAST -> switch (worldFace) {
				case EAST -> Direction.UP;
				case WEST -> Direction.DOWN;
				case DOWN -> Direction.NORTH;
				case UP -> Direction.SOUTH;
				case SOUTH -> Direction.EAST;
				case NORTH -> Direction.WEST;
			};
			case WEST -> switch (worldFace) {
				case WEST -> Direction.UP;
				case EAST -> Direction.DOWN;
				case DOWN -> Direction.NORTH;
				case UP -> Direction.SOUTH;
				case NORTH -> Direction.EAST;
				case SOUTH -> Direction.WEST;
			};
		};
	}

	public static int rotationOffset(Direction facing, Direction worldFace) {
		Direction right = toWorld(facing, baseRight(toModel(facing, worldFace)));
		Direction reference = baseRight(worldFace);
		if (right == reference) {
			return 0;
		}
		if (right == baseDown(worldFace)) {
			return 3;
		}
		if (right == reference.getOpposite()) {
			return 2;
		}
		return 1;
	}

	private static Direction baseRight(Direction face) {
		return switch (face) {
			case NORTH -> Direction.WEST;
			case SOUTH -> Direction.EAST;
			case WEST -> Direction.SOUTH;
			case EAST -> Direction.NORTH;
			case UP, DOWN -> Direction.EAST;
		};
	}

	private static Direction baseDown(Direction face) {
		return switch (face) {
			case UP -> Direction.SOUTH;
			case DOWN -> Direction.NORTH;
			case NORTH, SOUTH, WEST, EAST -> Direction.DOWN;
		};
	}

	public static void rotatePosition(Direction facing, float x, float y, float z, float[] out) {
		switch (facing) {
			case UP -> {
				out[0] = x;
				out[1] = y;
				out[2] = z;
			}
			case DOWN -> {
				out[0] = x;
				out[1] = 1.0F - y;
				out[2] = 1.0F - z;
			}
			case NORTH -> {
				out[0] = x;
				out[1] = z;
				out[2] = 1.0F - y;
			}
			case SOUTH -> {
				out[0] = 1.0F - x;
				out[1] = z;
				out[2] = y;
			}
			case EAST -> {
				out[0] = y;
				out[1] = z;
				out[2] = x;
			}
			case WEST -> {
				out[0] = 1.0F - y;
				out[1] = z;
				out[2] = 1.0F - x;
			}
		}
	}
}
