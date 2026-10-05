package snownee.spacefold.dim;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import snownee.spacefold.SpaceFold;

public final class Simulate {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, SpaceFold.id("simulate"));

	private Simulate() {
	}
}
