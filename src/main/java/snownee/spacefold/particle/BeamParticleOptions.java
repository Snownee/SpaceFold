package snownee.spacefold.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.CoreModule;

public record BeamParticleOptions(Vec3 from, Vec3 to, int life) implements ParticleOptions {
	public static final int DEFAULT_LIFE = 100;

	public static final MapCodec<BeamParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Vec3.CODEC.fieldOf("from").forGetter(BeamParticleOptions::from),
			Vec3.CODEC.fieldOf("to").forGetter(BeamParticleOptions::to),
			Codec.INT.optionalFieldOf("life", DEFAULT_LIFE).forGetter(BeamParticleOptions::life)
	).apply(i, BeamParticleOptions::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeamParticleOptions> STREAM_CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, BeamParticleOptions::from,
			Vec3.STREAM_CODEC, BeamParticleOptions::to,
			ByteBufCodecs.VAR_INT, BeamParticleOptions::life,
			BeamParticleOptions::new);

	@Override
	public ParticleType<?> getType() {
		return CoreModule.BEAM.getOrCreate();
	}
}
