package snownee.spacefold.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HierarchyLookup<T> implements IHierarchyLookup<T> {
	private final Class<?> baseClass;
	private final Map<Class<?>, List<T>> objects = new HashMap<>();
	private final Map<Class<?>, List<T>> cache = new HashMap<>();

	public HierarchyLookup(Class<?> baseClass) {
		this.baseClass = baseClass;
	}

	@Override
	public void register(Class<?> clazz, T object) {
		if (!isClassAcceptable(clazz)) {
			throw new IllegalArgumentException("Class " + clazz + " is not acceptable");
		}
		objects.computeIfAbsent(clazz, $ -> new ArrayList<>()).add(object);
		invalidate();
	}

	@Override
	public boolean isClassAcceptable(Class<?> clazz) {
		return baseClass.isAssignableFrom(clazz);
	}

	@Override
	public List<T> get(Class<?> clazz) {
		return cache.computeIfAbsent(clazz, this::collect);
	}

	private List<T> collect(Class<?> clazz) {
		List<T> list = new ArrayList<>();
		collectHierarchy(clazz, list);
		return List.copyOf(list);
	}

	private void collectHierarchy(Class<?> clazz, List<T> list) {
		if (clazz != baseClass && clazz != Object.class) {
			collectHierarchy(clazz.getSuperclass(), list);
		}
		list.addAll(objects.getOrDefault(clazz, List.of()));
	}

	@Override
	public void invalidate() {
		cache.clear();
	}
}
