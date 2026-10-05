package snownee.spacefold;

import snownee.kiwi.config.KiwiConfig;

@KiwiConfig
public final class SpaceFoldConfig {

	@KiwiConfig.Path("debug.enable")
	public static boolean debug;

	@KiwiConfig.Path("containment.placement")
	public static boolean placementEnabled = true;

	@KiwiConfig.Path("containment.confine")
	public static boolean confinementEnabled = true;

	@KiwiConfig.Path("trial.duration")
	public static int simulationDuration = 15;

	@KiwiConfig.Path("trial.cooldown")
	public static int simulationCooldown = 60;

	@KiwiConfig.Path("trial.sourceChunkRadius")
	public static int simulationSourceChunkRadius = 0;

	@KiwiConfig.Path("trial.tickMultiplier")
	public static int simulationTickMultiplier = 20;

	@KiwiConfig.Path("trial.minItemOutputRate")
	public static double simulationMinItemOutputRate = 5.0;

	@KiwiConfig.Path("trial.minFluidOutputRate")
	public static double simulationMinFluidOutputRate = 8100.0;

	@KiwiConfig.Path("trial.minEnergyOutputRate")
	public static double simulationMinEnergyOutputRate = 10000.0;

	@KiwiConfig.Path("trial.maxInputTypes")
	public static int simulationMaxInputTypes = 9;

	@KiwiConfig.Path("trial.maxOutputTypes")
	public static int simulationMaxOutputTypes = 9;

	@KiwiConfig.Path("engine.itemBuffer")
	public static int repeaterItemBuffer = 64;

	@KiwiConfig.Path("engine.fluidBuffer")
	public static int repeaterFluidBuffer = 648000;

	@KiwiConfig.Path("engine.energyBuffer")
	public static int repeaterEnergyBuffer = 100000;

	@KiwiConfig.Path("engine.initialThrottlePercent")
	public static int repeaterInitialThrottlePercent = 0;

	@KiwiConfig.Path("engine.rampUpPercent")
	public static int repeaterRampUpPercent = 10;

	@KiwiConfig.Path("engine.rampDownPercent")
	public static int repeaterRampDownPercent = 15;

	@KiwiConfig.Path("carver.durationTicks")
	public static int factoryDurationTicks = 100;

	@KiwiConfig.Path("carver.energyPerCraft")
	public static int factoryEnergyPerCraft = 1000;

	@KiwiConfig.Path("carver.outputPerCraft")
	public static int factoryOutputPerCraft = 1;

	@KiwiConfig.Path("carver.energyBuffer")
	public static int factoryEnergyBuffer = 100000;

}
