package snownee.spacefold.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import snownee.spacefold.CoreModule;

public record CubeParticleOptions(int color, int life) implements ParticleOptions {
	public static final int DEFAULT_COLOR = 0xFFFFFF;
	public static final int DEFAULT_LIFE = 40;

	public static final MapCodec<CubeParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(CubeParticleOptions::color),
			Codec.INT.optionalFieldOf("life", DEFAULT_LIFE).forGetter(CubeParticleOptions::life)
	).apply(i, CubeParticleOptions::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CubeParticleOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, CubeParticleOptions::color,
			ByteBufCodecs.VAR_INT, CubeParticleOptions::life,
			CubeParticleOptions::new);

	@Override
	public ParticleType<?> getType() {
		return CoreModule.CUBE.getOrCreate();
	}
}
