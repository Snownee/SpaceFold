package snownee.spacefold.dim;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.SpaceFoldConfig;

public final class RoomProtection {
	private static final ThreadLocal<Deque<ServerPlayer>> PLACING = ThreadLocal.withInitial(ArrayDeque::new);
	private static final ThreadLocal<Boolean> INTERNAL = ThreadLocal.withInitial(() -> false);

	private RoomProtection() {
	}

	public static void enter(ServerPlayer player) {
		PLACING.get().push(player);
	}

	public static void exit() {
		Deque<ServerPlayer> stack = PLACING.get();
		stack.pop();
		if (stack.isEmpty()) {
			PLACING.remove();
		}
	}

	public static void runInternal(Runnable action) {
		Boolean previous = INTERNAL.get();
		INTERNAL.set(true);
		try {
			action.run();
		} finally {
			INTERNAL.set(previous);
		}
	}

	public static boolean canPlace(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return true;
		}
		if (INTERNAL.get()) {
			return true;
		}
		if (level.dimension().equals(Simulate.DIMENSION)) {
			if (PLACING.get().peek() != null) {
				return false;
			}
			if (state.isAir()) {
				return true;
			}
			int room = RoomManager.roomAt(serverLevel, pos);
			if (room < 0) {
				return false;
			}
			return !RoomManager.isShell(room, pos);
		}
		if (!level.dimension().equals(Plan.DIMENSION)) {
			return true;
		}
		if (!SpaceFoldConfig.placementEnabled) {
			return true;
		}
		if (state.isAir()) {
			return true;
		}

		int room = RoomManager.roomAt(serverLevel, pos);
		if (room < 0) {
			return false;
		}
		ServerPlayer player = PLACING.get().peek();
		boolean creative = player != null && player.isCreative();
		if (RoomManager.isShell(room, pos) && !creative) {
			return false;
		}
		if (player != null && !player.isCreative() && !player.isSpectator()) {
			return RoomManager.currentRoom(serverLevel, player) == room;
		}
		return true;
	}
}
