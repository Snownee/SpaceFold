package snownee.spacefold.dim;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.SpaceFoldConfig;

public final class PlanConfinement {
	private static final Map<UUID, Integer> LOCKS = new HashMap<>();

	private PlanConfinement() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(PlanConfinement::onLevelTick);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (!RoomManager.isRoomDimension(destination.dimension())) {
				LOCKS.remove(player.getUUID());
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (!RoomManager.isRoomDimension(player.level().dimension())) {
				LOCKS.remove(player.getUUID());
			}
		});
	}

	public static void lock(ServerPlayer player, int room) {
		LOCKS.put(player.getUUID(), room);
	}

	private static void onLevelTick(ServerLevel level) {
		if (!RoomManager.isRoomDimension(level.dimension()) || !SpaceFoldConfig.confinementEnabled) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			confine(level, player);
		}
	}

	private static void confine(ServerLevel level, ServerPlayer player) {
		if (player.isCreative()) {
			return;
		}
		int room = anchorRoom(level, player);
		if (room < 0) {
			room = RoomManager.nearestRoom(level, player.blockPosition());
			if (room < 0) {
				return;
			}
			LOCKS.put(player.getUUID(), room);
		}
		clamp(player, room);
	}

	private static int anchorRoom(ServerLevel level, ServerPlayer player) {
		Integer locked = LOCKS.get(player.getUUID());
		if (locked != null && locked >= 0 && locked < RoomManager.roomCount(level)) {
			return locked;
		}
		return RoomManager.currentRoom(level, player);
	}

	private static void clamp(ServerPlayer player, int room) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Vec3 clamped = RoomGeometry.clamp(room, x, y, z);
		if (clamped.x == x && clamped.y == y && clamped.z == z) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		double vx = clamped.x == x ? velocity.x : 0.0D;
		double vy = clamped.y == y ? velocity.y : 0.0D;
		double vz = clamped.z == z ? velocity.z : 0.0D;
		player.teleportTo(clamped.x, clamped.y, clamped.z);
		player.setDeltaMovement(vx, vy, vz);
		player.resetFallDistance();
	}
}
