package snownee.spacefold.client;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.util.Mth;
import snownee.kiwi.config.KiwiConfigManager;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.network.CSnapshotRequestPacket;

public final class PreviewCache {
	public static final float DEFAULT_YAW = 45.0F;

	private static final Int2ObjectMap<@Nullable RoomSnapshot> snapshots = new Int2ObjectOpenHashMap<>();
	private static final IntSet pendingSnapshots = new IntOpenHashSet();
	private static String snapshotYaws = "";
	private static Int2FloatMap parsedYaws = new Int2FloatOpenHashMap();
	private static int version;

	private PreviewCache() {
	}

	public static int version() {
		return version;
	}

	public static @Nullable RoomSnapshot getSnapshot(int id) {
		if (id < 0) {
			return null;
		}
		RoomSnapshot snapshot = snapshots.get(id);
		if (snapshot == null && pendingSnapshots.add(id)) {
			KPacketSender.sendToServer(new CSnapshotRequestPacket(id));
		}
		return snapshot;
	}

	public static void accept(int id, @Nullable RoomSnapshot snapshot) {
		pendingSnapshots.remove(id);
		if (snapshot != null) {
			snapshots.put(id, snapshot);
			if (RoomSnapshot.isPreviewId(id)) {
				version++;
			}
		}
	}

	public static void invalidate(int id) {
		snapshots.remove(id);
		pendingSnapshots.remove(id);
		if (RoomSnapshot.isPreviewId(id)) {
			version++;
		}
	}

	public static float getYaw(int snapshotId) {
		return parsedYaws().getOrDefault(snapshotId, DEFAULT_YAW);
	}

	public static void setYaw(int snapshotId, float yaw) {
		parsedYaws().put(snapshotId, Mth.wrapDegrees(yaw));
		SpaceFoldClientConfig.snapshotYaws = encodeYaws(parsedYaws);
		KiwiConfigManager.getHandler(SpaceFoldClientConfig.class).save();
	}

	private static Int2FloatMap parsedYaws() {
		String raw = SpaceFoldClientConfig.snapshotYaws;
		if (!Objects.equals(raw, snapshotYaws)) {
			snapshotYaws = raw;
			parsedYaws = decodeYaws(raw);
		}
		return parsedYaws;
	}

	private static Int2FloatMap decodeYaws(String raw) {
		Int2FloatMap map = new Int2FloatOpenHashMap();
		for (String token : raw.split(";")) {
			int eq = token.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			map.put(Integer.parseInt(token.substring(0, eq)), Float.parseFloat(token.substring(eq + 1)));
		}
		return map;
	}

	private static String encodeYaws(Int2FloatMap map) {
		int[] keys = map.keySet().toIntArray();
		Arrays.sort(keys);
		StringBuilder sb = new StringBuilder();
		for (int key : keys) {
			if (!sb.isEmpty()) {
				sb.append(';');
			}
			sb.append(key).append('=').append(String.format(Locale.ENGLISH, "%.1f", map.get(key)));
		}
		return sb.toString();
	}
}
