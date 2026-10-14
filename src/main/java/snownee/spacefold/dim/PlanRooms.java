package snownee.spacefold.dim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedData;
import snownee.spacefold.util.Int2ObjectCodec;

public class PlanRooms extends SavedData {
	public static final Codec<PlanRooms> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("next_index")
							.orElse(0)
							.forGetter(r -> r.nextIndex),
					Int2ObjectCodec.of(GlobalPos.CODEC)
							.fieldOf("sources")
							.orElseGet(Int2ObjectOpenHashMap::new)
							.forGetter(r -> r.sources),
					Int2ObjectCodec.of(RoomData.CODEC)
							.fieldOf("rooms")
							.orElseGet(Int2ObjectOpenHashMap::new)
							.forGetter(r -> r.rooms),
					Codec.INT.fieldOf("next_snapshot_id")
							.orElse(0)
							.forGetter(r -> r.nextSnapshotId),
					Int2ObjectCodec.of(Codec.INT.listOf())
							.fieldOf("snapshots")
							.orElseGet(Int2ObjectOpenHashMap::new)
							.forGetter(r -> r.snapshots))
			.apply(i, PlanRooms::new));
	public final Int2ObjectMap<@Nullable GlobalPos> sources;
	public final Int2ObjectMap<@Nullable RoomData> rooms;
	public final Int2ObjectMap<@Nullable List<Integer>> snapshots;
	public int nextIndex;
	public int nextSnapshotId;

	public PlanRooms() {
		this(0, new Int2ObjectOpenHashMap<>(), new Int2ObjectOpenHashMap<>(), 0, new Int2ObjectOpenHashMap<>());
	}

	public PlanRooms(
			int nextIndex,
			Int2ObjectMap<@Nullable GlobalPos> sources,
			Int2ObjectMap<@Nullable RoomData> rooms,
			int nextSnapshotId,
			Int2ObjectMap<@Nullable List<Integer>> snapshots) {
		this.nextIndex = nextIndex;
		this.sources = sources;
		this.rooms = rooms;
		this.nextSnapshotId = nextSnapshotId;
		this.snapshots = snapshots;
	}

	public record RoomData(
			@Nullable UUID creator,
			@Nullable UUID initiator,
			@Nullable SimulationResult result,
			long lastStart) {
		public static final Codec<RoomData> CODEC = RecordCodecBuilder.create(i -> i.group(
						UUIDUtil.CODEC.optionalFieldOf("creator")
								.forGetter(r -> Optional.ofNullable(r.creator())),
						UUIDUtil.CODEC.optionalFieldOf("initiator")
								.forGetter(r -> Optional.ofNullable(r.initiator())),
						SimulationResult.CODEC.optionalFieldOf("result")
								.forGetter(r -> Optional.ofNullable(r.result())),
						Codec.LONG.optionalFieldOf("last_start", 0L)
								.forGetter(RoomData::lastStart))
				.apply(i, (creator, initiator, result, lastStart) -> new RoomData(
						creator.orElse(null),
						initiator.orElse(null),
						result.orElse(null),
						lastStart)));

		public RoomData withCreator(@Nullable UUID creator) {
			return new RoomData(creator, initiator, result, lastStart);
		}

		public RoomData withInitiator(@Nullable UUID initiator) {
			return new RoomData(creator, initiator, result, lastStart);
		}

		public RoomData withResult(@Nullable SimulationResult result) {
			return new RoomData(creator, initiator, result, lastStart);
		}

		public RoomData withLastStart(long lastStart) {
			return new RoomData(creator, initiator, result, lastStart);
		}
	}
}
