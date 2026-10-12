package snownee.spacefold.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class BeamParticleType extends ParticleType<BeamParticleOptions> {
	public BeamParticleType() {
		super(false);
	}

	@Override
	public MapCodec<BeamParticleOptions> codec() {
		return BeamParticleOptions.CODEC;
	}

	@Override
	public StreamCodec<? super RegistryFriendlyByteBuf, BeamParticleOptions> streamCodec() {
		return BeamParticleOptions.STREAM_CODEC;
	}
}
