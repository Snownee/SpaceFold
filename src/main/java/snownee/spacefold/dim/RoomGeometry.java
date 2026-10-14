package snownee.spacefold.dim;

import net.minecraft.world.phys.Vec3;
import snownee.spacefold.SpaceFoldConfig;

public final class RoomGeometry {
	private RoomGeometry() {
	}

	public static int roomAt(double x, double y, double z) {
		int baseX = Math.floorDiv((int) Math.floor(x), Plan.ROOM_GAP);
		int baseZ = Math.floorDiv((int) Math.floor(z), Plan.ROOM_GAP);
		int nearest = -1;
		double nearestDist = Double.MAX_VALUE;
		for (int gx = baseX; gx <= baseX + 1; gx++) {
			for (int gz = baseZ; gz <= baseZ + 1; gz++) {
				int room = gz * Plan.GRID_WIDTH + gx;
				double dx = axisDistance(x, minX(room), maxX(room));
				double dy = axisDistance(y, minY(room), maxY(room));
				double dz = axisDistance(z, minZ(room), maxZ(room));
				double dist = dx * dx + dy * dy + dz * dz;
				if (dist == 0.0D) {
					return room;
				}
				if (dist < nearestDist) {
					nearestDist = dist;
					nearest = room;
				}
			}
		}
		return nearest;
	}

	public static Vec3 clamp(int room, double x, double y, double z) {
		return new Vec3(
				Math.clamp(x, minX(room), maxX(room)),
				Math.clamp(y, minY(room), maxY(room)),
				Math.clamp(z, minZ(room), maxZ(room)));
	}

	public static double minX(int room) {
		return Plan.roomOrigin(room).getX() - Plan.ROOM_MARGIN;
	}

	public static double maxX(int room) {
		return Plan.roomOrigin(room).getX() + SpaceFoldConfig.roomSize - 1 + Plan.ROOM_MARGIN;
	}

	public static double minY(int room) {
		return Plan.FLOOR_Y - Plan.ROOM_MARGIN;
	}

	public static double maxY(int room) {
		return Plan.FLOOR_Y + SpaceFoldConfig.roomSize - 1 + Plan.ROOM_MARGIN;
	}

	public static double minZ(int room) {
		return Plan.roomOrigin(room).getZ() - Plan.ROOM_MARGIN;
	}

	public static double maxZ(int room) {
		return Plan.roomOrigin(room).getZ() + SpaceFoldConfig.roomSize - 1 + Plan.ROOM_MARGIN;
	}

	private static double axisDistance(double value, double min, double max) {
		if (value < min) {
			return min - value;
		}
		if (value > max) {
			return value - max;
		}
		return 0.0D;
	}
}
