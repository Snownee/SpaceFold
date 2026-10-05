package snownee.spacefold.client.particle;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import snownee.spacefold.particle.CubeParticleOptions;

public final class CubeParticleHighlighter {
	private CubeParticleHighlighter() {
	}

	public static void highlight(List<BlockPos> positions, int color, int life) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		CubeParticleOptions options = new CubeParticleOptions(color, life);
		for (BlockPos pos : positions) {
			level.addParticle(options, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
		}
	}
}
