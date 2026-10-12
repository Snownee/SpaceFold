package snownee.spacefold.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BeamParticle extends Particle {
	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType("spacefold:beam");

	public static final int COLOR = 0xFFFFFF;
	private static final float BASE_ALPHA = 0.5F;
	private static final float FADE_FRACTION = 0.2F;
	private static final float TRAIL_LENGTH_FRACTION = 0.4F;

	private final Vec3 from;
	private final Vec3 to;

	public BeamParticle(ClientLevel level, Vec3 from, Vec3 to, int life) {
		super(level, from.x, from.y, from.z);
		this.from = from;
		this.to = to;
		this.lifetime = Math.max(1, life);
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

	public AABB aabb() {
		return new AABB(this.from, this.to).inflate(0.5);
	}

	public void extract(BeamParticleRenderState state, float partialTickTime) {
		float life = Mth.clamp((this.age + partialTickTime) / this.lifetime, 0.0F, 1.0F);
		float alpha = BASE_ALPHA * fadeAlpha(life);
		Vec3 delta = this.to.subtract(this.from);
		float total = (float) delta.length();
		Vec3 dir = total < 1.0E-4 ? Vec3.ZERO : delta.scale(1.0 / total);
		float tailDist = total * easeInCubic(life);
		float length = total * TRAIL_LENGTH_FRACTION * Mth.sin(Mth.PI * life * 0.75F);
		length = Mth.clamp(length, 0.0F, total - tailDist);
		Vec3 tail = this.from.add(dir.scale(tailDist));
		Vec3 head = this.from.add(dir.scale(tailDist + length));
		state.animationTime = this.level.getGameTime() + partialTickTime;
		state.add(tail, head, COLOR, alpha);
	}

	private static float easeInCubic(float x) {
		return x * x * x;
	}

	private static float fadeAlpha(float life) {
		float fadeIn = Mth.clamp(life / FADE_FRACTION, 0.0F, 1.0F);
		float fadeOut = Mth.clamp((1.0F - life) / FADE_FRACTION, 0.8F, 1.0F);
		float t = Math.min(fadeIn, fadeOut);
		return t * t * (3.0F - 2.0F * t);
	}
}
