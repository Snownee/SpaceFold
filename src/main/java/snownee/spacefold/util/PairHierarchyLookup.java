package snownee.spacefold.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PairHierarchyLookup<T> implements IHierarchyLookup<T> {
	public final HierarchyLookup<T> first;
	public final HierarchyLookup<T> second;
	private final Map<Key, List<T>> cache = new HashMap<>();

	public PairHierarchyLookup(Class<?> firstClass, Class<?> secondClass) {
		first = new HierarchyLookup<>(firstClass);
		second = new HierarchyLookup<>(secondClass);
	}

	@Override
	public void register(Class<?> clazz, T object) {
		if (first.isClassAcceptable(clazz)) {
			first.register(clazz, object);
		} else if (second.isClassAcceptable(clazz)) {
			second.register(clazz, object);
		} else {
			throw new IllegalArgumentException("Class " + clazz + " is not acceptable");
		}
		invalidate();
	}

	@Override
	public boolean isClassAcceptable(Class<?> clazz) {
		return first.isClassAcceptable(clazz) || second.isClassAcceptable(clazz);
	}

	@Override
	public List<T> get(Class<?> clazz) {
		List<T> result = first.get(clazz);
		return result.isEmpty() ? second.get(clazz) : result;
	}

	public List<T> getMerged(Object first, Object second) {
		return cache.computeIfAbsent(new Key(first.getClass(), second.getClass()), $ -> {
			List<T> firstList = this.first.get(first);
			List<T> secondList = this.second.get(second);
			if (firstList.isEmpty()) {
				return secondList;
			}
			if (secondList.isEmpty()) {
				return firstList;
			}
			List<T> merged = new ArrayList<>(firstList);
			merged.addAll(secondList);
			return List.copyOf(merged);
		});
	}

	@Override
	public void invalidate() {
		first.invalidate();
		second.invalidate();
		cache.clear();
	}

	private record Key(Class<?> first, Class<?> second) {}
}
