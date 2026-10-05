package snownee.spacefold.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.util.RandomSource;
import snownee.spacefold.particle.CubeParticleOptions;

public class CubeParticleProvider implements ParticleProvider<CubeParticleOptions> {
	@Override
	public Particle createParticle(
			CubeParticleOptions options,
			ClientLevel level,
			double x,
			double y,
			double z,
			double xAux,
			double yAux,
			double zAux,
			RandomSource random) {
		return new CubeParticle(level, x, y, z, options.color(), options.life());
	}
}
