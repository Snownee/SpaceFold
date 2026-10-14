package snownee.spacefold.util;

import java.util.List;

import org.jspecify.annotations.Nullable;

public interface IHierarchyLookup<T> {
	void register(Class<?> clazz, T object);

	boolean isClassAcceptable(Class<?> clazz);

	List<T> get(Class<?> clazz);

	default List<T> get(@Nullable Object object) {
		return object == null ? List.of() : get(object.getClass());
	}

	void invalidate();
}
