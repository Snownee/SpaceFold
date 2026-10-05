package snownee.spacefold.util;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;

public final class FluidAmounts {
	public static final double DROPLETS_PER_MILLI_BUCKET = FluidConstants.BUCKET / 1000.0;

	private FluidAmounts() {
	}

	public static double toMilliBuckets(double droplets) {
		return droplets / DROPLETS_PER_MILLI_BUCKET;
	}
}
