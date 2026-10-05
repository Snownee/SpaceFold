package snownee.spacefold.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class CubeParticleType extends ParticleType<CubeParticleOptions> {
	public CubeParticleType() {
		super(false);
	}

	@Override
	public MapCodec<CubeParticleOptions> codec() {
		return CubeParticleOptions.CODEC;
	}

	@Override
	public StreamCodec<? super RegistryFriendlyByteBuf, CubeParticleOptions> streamCodec() {
		return CubeParticleOptions.STREAM_CODEC;
	}
}
