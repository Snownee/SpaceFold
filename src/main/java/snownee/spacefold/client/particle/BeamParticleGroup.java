package snownee.spacefold.client.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

public class BeamParticleGroup extends ParticleGroup<BeamParticle> {
	private final BeamParticleRenderState renderState = new BeamParticleRenderState();

	public BeamParticleGroup(ParticleEngine engine) {
		super(engine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTickTime) {
		this.renderState.clear();
		for (BeamParticle particle : this.particles) {
			if (frustum.isVisible(particle.aabb())) {
				particle.extract(this.renderState, partialTickTime);
			}
		}
		return this.renderState;
	}
}
