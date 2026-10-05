package snownee.spacefold.client.paint;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class PaintClientHooks {
	private PaintClientHooks() {
	}

	public static void markDirty(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null) {
			minecraft.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
		}
	}

	public static void markSectionDirty(int sectionX, int sectionY, int sectionZ) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null) {
			minecraft.levelRenderer.setSectionDirty(sectionX, sectionY, sectionZ);
		}
	}

	public static void markAllDirty() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null) {
			minecraft.levelRenderer.allChanged();
		}
	}
}
