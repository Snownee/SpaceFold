package snownee.spacefold.util;

import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.RecordBuilder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class Int2ObjectCodec {
	private Int2ObjectCodec() {
	}

	public static <V> Codec<Int2ObjectMap<V>> of(Codec<V> elementCodec) {
		return new Codec<>() {
			@Override
			public <T> DataResult<Pair<Int2ObjectMap<V>, T>> decode(DynamicOps<T> ops, T input) {
				return ops.getMap(input).flatMap(map -> {
					Int2ObjectMap<V> result = new Int2ObjectOpenHashMap<>();
					Stream.Builder<Pair<T, T>> failed = Stream.builder();
					DataResult<Unit> accumulated = map.entries().reduce(
							DataResult.success(Unit.INSTANCE, Lifecycle.stable()),
							(acc, pair) -> {
								DataResult<Integer> key = parseKey(ops, pair.getFirst());
								DataResult<V> value = elementCodec.parse(ops, pair.getSecond());
								DataResult<Pair<Integer, V>> entry = key.apply2stable(Pair::of, value);
								Optional<Pair<Integer, V>> parsed = entry.resultOrPartial();
								if (parsed.isPresent()) {
									result.put(parsed.get().getFirst().intValue(), parsed.get().getSecond());
								} else {
									failed.add(pair);
								}
								return acc.apply2stable((u, p) -> u, entry);
							},
							(acc1, acc2) -> acc1.apply2stable((u1, u2) -> u1, acc2));
					T errors = ops.createMap(failed.build());
					return accumulated.map(unit -> Pair.of(result, input))
							.setPartial(Pair.of(result, input))
							.mapError(e -> e + " missed input: " + errors);
				});
			}

			@Override
			public <T> DataResult<T> encode(Int2ObjectMap<V> input, DynamicOps<T> ops, T prefix) {
				RecordBuilder<T> builder = ops.mapBuilder();
				for (Int2ObjectMap.Entry<V> entry : input.int2ObjectEntrySet()) {
					builder.add(
							ops.createString(Integer.toString(entry.getIntKey())),
							elementCodec.encodeStart(ops, entry.getValue()));
				}
				return builder.build(prefix);
			}

			@Override
			public String toString() {
				return "Int2ObjectCodec[" + elementCodec + "]";
			}
		};
	}

	private static <T> DataResult<Integer> parseKey(DynamicOps<T> ops, T input) {
		return Codec.STRING.parse(ops, input).flatMap(key -> {
			try {
				return DataResult.success(Integer.valueOf(key));
			} catch (NumberFormatException e) {
				return DataResult.error(() -> "Not a valid integer key: '" + key + "'");
			}
		});
	}
}
