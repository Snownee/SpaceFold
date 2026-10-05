package snownee.spacefold.paint;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;

public final class RepeaterPaints {
	public static final Map<Direction, FacePaint> EMPTY = Map.of();

	public static final Codec<Map<Direction, FacePaint>> CODEC = Codec.unboundedMap(Direction.CODEC, FacePaint.CODEC);

	private RepeaterPaints() {
	}

	public static Map<Direction, FacePaint> set(Map<Direction, FacePaint> paints, Direction face, @Nullable FacePaint paint) {
		Map<Direction, FacePaint> result = copy(paints);
		if (paint == null) {
			result.remove(face);
		} else {
			result.put(face, paint);
		}
		return result;
	}

	public static Map<Direction, FacePaint> copy(Map<Direction, FacePaint> paints) {
		Map<Direction, FacePaint> result = new EnumMap<>(Direction.class);
		result.putAll(paints);
		return result;
	}
}
