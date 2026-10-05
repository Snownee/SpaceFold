package snownee.spacefold.dim;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import snownee.spacefold.SpaceFold;

public final class SimulateMode {
	public static final AttachmentType<GameType> RETURN_MODE =
			AttachmentRegistry.createPersistent(SpaceFold.id("return_mode"), GameType.CODEC);

	private SimulateMode() {
	}

	public static void init() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			boolean fromSimulate = origin.dimension().equals(Simulate.DIMENSION);
			boolean toSimulate = destination.dimension().equals(Simulate.DIMENSION);
			if (toSimulate && !fromSimulate) {
				enter(player);
			} else if (fromSimulate && !toSimulate) {
				exit(player);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (player.level().dimension().equals(Simulate.DIMENSION)) {
				player.getAttachedOrSet(RETURN_MODE, player.level().getServer().getDefaultGameType());
				player.setGameMode(GameType.SPECTATOR);
			}
		});
	}

	private static void enter(ServerPlayer player) {
		player.setAttached(RETURN_MODE, player.gameMode());
		player.setGameMode(GameType.SPECTATOR);
	}

	private static void exit(ServerPlayer player) {
		GameType mode = player.removeAttached(RETURN_MODE);
		if (mode != null) {
			player.setGameMode(mode);
		}
	}
}
