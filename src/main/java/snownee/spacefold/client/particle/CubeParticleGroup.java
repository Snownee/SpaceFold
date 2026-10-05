package snownee.spacefold.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

public class CubeParticleGroup extends ParticleGroup<CubeParticle> {
	private final CubeParticleRenderState renderState = new CubeParticleRenderState();

	public CubeParticleGroup(ParticleEngine engine) {
		super(engine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
		this.renderState.clear();
		for (CubeParticle particle : this.particles) {
			if (frustum.pointInFrustum(particle.getX(), particle.getY(), particle.getZ())) {
				particle.extract(this.renderState, partialTickTime);
			}
		}
		return this.renderState;
	}
}
