package snownee.spacefold.dim;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

public final class PlanFlight {
	private PlanFlight() {
	}

	public static void init() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> update(player));
		ServerPlayerEvents.JOIN.register(PlanFlight::update);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> update(newPlayer));
	}

	public static void update(ServerPlayer player) {
		if (RoomManager.isRoomDimension(player.level())) {
			enable(player);
		} else if (!player.isCreative() && !player.isSpectator()) {
			Abilities abilities = player.getAbilities();
			if (abilities.mayfly) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
	}

	public static void enable(ServerPlayer player) {
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		player.onUpdateAbilities();
	}
}
