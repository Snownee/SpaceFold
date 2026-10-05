package snownee.spacefold.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;

public class CubeParticle extends Particle {
	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType("spacefold:cube");

	private static final float BASE_HALF_SIZE = 0.55F;
	private static final float BREATH_AMPLITUDE = 0.15F;
	private static final float BREATH_SPEED = 0.25F;
	private static final float BASE_ALPHA = 0.6F;
	private static final int FADE_TICKS = 15;

	private final int color;

	public CubeParticle(ClientLevel level, double x, double y, double z, int color, int lifetime) {
		super(level, x, y, z);
		this.color = color;
		this.lifetime = Math.max(1, lifetime);
		this.hasPhysics = false;
		this.gravity = 0.0F;
		this.friction = 1.0F;
	}

	@Override
	public void tick() {
		this.age++;
		if (this.age >= this.lifetime) {
			this.remove();
		}
	}

	@Override
	public ParticleRenderType getGroup() {
		return RENDER_TYPE;
	}

	public void extract(CubeParticleRenderState state, float partialTickTime) {
		float t = this.age + partialTickTime;
		float half = BASE_HALF_SIZE * (1.0F + BREATH_AMPLITUDE * Mth.sin(t * BREATH_SPEED));
		float fade = Mth.clamp((this.lifetime - t) / FADE_TICKS, 0.0F, 1.0F);
		state.add(
				Mth.lerp(partialTickTime, this.xo, this.x),
				Mth.lerp(partialTickTime, this.yo, this.y),
				Mth.lerp(partialTickTime, this.zo, this.z),
				half,
				this.color,
				BASE_ALPHA * fade);
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}

	public double getZ() {
		return this.z;
	}
}
