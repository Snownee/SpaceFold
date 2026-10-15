package snownee.spacefold.paint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;

public record RepeaterPaints(Map<Direction, FacePaint> paints) {
	public static final RepeaterPaints EMPTY = new RepeaterPaints(Map.of());

	public static final Codec<RepeaterPaints> CODEC = Entry.CODEC.listOf()
			.xmap(RepeaterPaints::fromEntries, RepeaterPaints::toEntries);

	public RepeaterPaints {
		paints = Map.copyOf(paints);
	}

	public static RepeaterPaints of(Map<Direction, FacePaint> paints) {
		return paints.isEmpty() ? EMPTY : new RepeaterPaints(paints);
	}

	public boolean isEmpty() {
		return paints.isEmpty();
	}

	public int size() {
		return paints.size();
	}

	public @Nullable FacePaint get(Direction face) {
		return paints.get(face);
	}

	public Iterable<Map.Entry<Direction, FacePaint>> entries() {
		return paints.entrySet();
	}

	public RepeaterPaints set(Direction face, @Nullable FacePaint paint) {
		Map<Direction, FacePaint> result = new HashMap<>(paints);
		if (paint == null) {
			result.remove(face);
		} else {
			result.put(face, paint);
		}
		return of(result);
	}

	private static RepeaterPaints fromEntries(List<Entry> entries) {
		Map<Direction, FacePaint> result = new EnumMap<>(Direction.class);
		for (Entry entry : entries) {
			for (Direction face : entry.faces()) {
				result.put(face, entry.paint());
			}
		}
		return of(result);
	}

	private static List<Entry> toEntries(RepeaterPaints paints) {
		if (paints.isEmpty()) {
			return List.of();
		}
		Map<FacePaint, List<Direction>> grouped = new LinkedHashMap<>();
		for (Direction face : Direction.values()) {
			FacePaint paint = paints.get(face);
			if (paint != null) {
				grouped.computeIfAbsent(paint, $ -> new ArrayList<>()).add(face);
			}
		}
		List<Entry> entries = new ArrayList<>(grouped.size());
		grouped.forEach((paint, faces) -> entries.add(new Entry(List.copyOf(faces), paint)));
		return entries;
	}

	private record Entry(List<Direction> faces, FacePaint paint) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
						Direction.CODEC.listOf()
								.fieldOf("faces")
								.forGetter(Entry::faces),
						FacePaint.CODEC
								.fieldOf("paint")
								.forGetter(Entry::paint))
				.apply(i, Entry::new));
	}
}
