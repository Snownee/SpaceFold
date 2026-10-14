package snownee.spacefold.dim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.SpaceFoldConfig;

public final class Plan {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, SpaceFold.id("plan"));

	public static final int ROOM_GAP = 1024;
	public static final int GRID_WIDTH = 64;
	public static final int FLOOR_Y = 64;
	public static final int ROOM_MARGIN = 10;

	private Plan() {
	}

	public static Vec3i innerPosOffset() {
		return new Vec3i(SpaceFoldConfig.roomSize / 2, 1, SpaceFoldConfig.roomSize / 2);
	}

	public static BlockPos roomOrigin(int room) {
		int gridX = room % GRID_WIDTH;
		int gridZ = room / GRID_WIDTH;
		return new BlockPos(gridX * ROOM_GAP, FLOOR_Y, gridZ * ROOM_GAP);
	}
}
