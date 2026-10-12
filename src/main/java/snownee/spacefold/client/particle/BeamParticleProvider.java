package snownee.spacefold.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.util.RandomSource;
import snownee.spacefold.particle.BeamParticleOptions;

public class BeamParticleProvider implements ParticleProvider<BeamParticleOptions> {
	@Override
	public Particle createParticle(
			BeamParticleOptions options,
			ClientLevel level,
			double x,
			double y,
			double z,
			double xAux,
			double yAux,
			double zAux,
			RandomSource random) {
		return new BeamParticle(level, options.from(), options.to(), options.life());
	}
}
