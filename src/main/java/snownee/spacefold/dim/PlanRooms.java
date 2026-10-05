package snownee.spacefold.dim;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.saveddata.SavedData;

public class PlanRooms extends SavedData {
	public static final Codec<PlanRooms> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.fieldOf("next_index")
							.orElse(0)
							.forGetter(r -> r.nextIndex),
					Codec.unboundedMap(Codec.STRING.xmap(Integer::valueOf, String::valueOf), GlobalPos.CODEC)
							.fieldOf("sources")
							.orElseGet(HashMap::new)
							.forGetter(r -> r.sources),
					Codec.unboundedMap(Codec.STRING.xmap(Integer::valueOf, String::valueOf), SimulationResult.CODEC)
							.fieldOf("results")
							.orElseGet(HashMap::new)
							.forGetter(r -> r.results),
					Codec.unboundedMap(Codec.STRING.xmap(Integer::valueOf, String::valueOf), Codec.LONG)
							.fieldOf("last_starts")
							.orElseGet(HashMap::new)
							.forGetter(r -> r.lastStarts))
			.apply(i, PlanRooms::new));
	public final Map<Integer, GlobalPos> sources;
	public final Map<Integer, SimulationResult> results;
	public final Map<Integer, Long> lastStarts;
	public int nextIndex;

	public PlanRooms() {
		this(0, Map.of(), Map.of(), Map.of());
	}

	public PlanRooms(int nextIndex, Map<Integer, GlobalPos> sources, Map<Integer, SimulationResult> results, Map<Integer, Long> lastStarts) {
		this.nextIndex = nextIndex;
		this.sources = new HashMap<>(sources);
		this.results = new HashMap<>(results);
		this.lastStarts = new HashMap<>(lastStarts);
	}
}
