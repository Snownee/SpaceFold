package snownee.spacefold;

import snownee.kiwi.config.ConfigUI;
import snownee.kiwi.config.KiwiConfig;

@KiwiConfig(type = KiwiConfig.ConfigType.CLIENT)
public final class SpaceFoldClientConfig {

	@ConfigUI.Hide
	@KiwiConfig.Path("preview.snapshotYaws")
	public static String snapshotYaws = "";

	@KiwiConfig.Path("debug.pip")
	public static boolean debugPip = false;

	@KiwiConfig.Path("debug.graph")
	public static boolean debugGraph = false;

	@KiwiConfig.Path("room.windows")
	public static boolean roomWindows = true;

	@KiwiConfig.Path("room.ambientOcclusion")
	public static float roomAmbientOcclusion = 0.6F;

	@KiwiConfig.Path("room.fogVolume")
	public static boolean roomFogVolume = true;

	@KiwiConfig.Path("room.shootingStars")
	public static boolean roomShootingStars = true;
}
