package snownee.spacefold.util;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;

public final class Object2DoubleCodec {
	private Object2DoubleCodec() {
	}

	public static <K> Codec<Object2DoubleMap<K>> of(Codec<K> keyCodec) {
		return of(keyCodec, "key", "value");
	}

	public static <K> Codec<Object2DoubleMap<K>> of(Codec<K> keyCodec, String keyField, String valueField) {
		Codec<Entry<K>> entryCodec = RecordCodecBuilder.create(i -> i.group(
						keyCodec.fieldOf(keyField).forGetter(Entry::key),
						Codec.DOUBLE.fieldOf(valueField).forGetter(Entry::value))
				.apply(i, Entry::new));
		return entryCodec.listOf().xmap(Object2DoubleCodec::fromList, Object2DoubleCodec::toList);
	}

	private static <K> Object2DoubleMap<K> fromList(List<Entry<K>> entries) {
		Object2DoubleMap<K> result = new Object2DoubleLinkedOpenHashMap<>();
		for (Entry<K> entry : entries) {
			result.put(entry.key(), entry.value());
		}
		return result;
	}

	private static <K> List<Entry<K>> toList(Object2DoubleMap<K> map) {
		List<Entry<K>> entries = new ArrayList<>();
		for (Object2DoubleMap.Entry<K> entry : map.object2DoubleEntrySet()) {
			entries.add(new Entry<>(entry.getKey(), entry.getDoubleValue()));
		}
		return entries;
	}

	private record Entry<K>(K key, double value) {
	}
}
