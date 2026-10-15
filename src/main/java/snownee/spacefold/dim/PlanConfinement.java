package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.SpaceFoldConfig;

public final class PlanConfinement {
	private static final Map<UUID, Integer> LOCKS = new HashMap<>();
	private static final double FALL_MARGIN = 8.0D;

	private PlanConfinement() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(PlanConfinement::onLevelTick);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (!RoomManager.isRoomDimension(destination)) {
				LOCKS.remove(player.getUUID());
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (!RoomManager.isRoomDimension(player.level())) {
				LOCKS.remove(player.getUUID());
			}
		});
	}

	public static void lock(ServerPlayer player, int room) {
		LOCKS.put(player.getUUID(), room);
	}

	private static void onLevelTick(ServerLevel level) {
		if (!RoomManager.isRoomDimension(level) || !SpaceFoldConfig.confinementEnabled) {
			return;
		}
		if (RoomManager.roomCount(level) <= 0) {
			return;
		}
		List<LivingEntity> entities = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof LivingEntity living && !living.isRemoved()) {
				entities.add(living);
			}
		}
		for (LivingEntity entity : entities) {
			confine(level, entity);
		}
	}

	private static void confine(ServerLevel level, LivingEntity entity) {
		int room = anchorRoom(level, entity);
		if (room < 0) {
			return;
		}
		if (entity instanceof ServerPlayer player && !player.isCreative() && !LOCKS.containsKey(player.getUUID())) {
			LOCKS.put(player.getUUID(), room);
		}
		if (!needsRescue(level, entity, room)) {
			return;
		}
		RoomManager.rescue(level, entity, room);
	}

	private static int anchorRoom(ServerLevel level, LivingEntity entity) {
		Integer locked = entity instanceof ServerPlayer player ? LOCKS.get(player.getUUID()) : null;
		if (locked != null && locked >= 0 && locked < RoomManager.roomCount(level)) {
			return locked;
		}
		int room = RoomGeometry.roomAt(entity.getX(), entity.getY(), entity.getZ());
		if (room >= 0 && room < RoomManager.roomCount(level)) {
			return room;
		}
		return RoomManager.nearestRoom(level, entity.blockPosition());
	}

	private static boolean needsRescue(ServerLevel level, LivingEntity entity, int room) {
		if (entity.getY() < level.getMinY() + FALL_MARGIN) {
			return true;
		}
		if (entity instanceof ServerPlayer player && player.isCreative()) {
			return false;
		}
		Vec3 clamped = RoomGeometry.clamp(room, entity.getX(), entity.getY(), entity.getZ());
		return clamped.x != entity.getX() || clamped.y != entity.getY() || clamped.z != entity.getZ();
	}
}
