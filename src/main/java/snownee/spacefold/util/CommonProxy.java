package snownee.spacefold.util;

import java.util.Objects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import snownee.kiwi.Mod;
import snownee.kiwi.loader.Platform;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.command.ServerCommand;
import snownee.spacefold.dim.PlanConfinement;
import snownee.spacefold.dim.PlanFlight;
import snownee.spacefold.dim.SimulateMode;
import snownee.spacefold.dim.SimulationManager;
import snownee.spacefold.paint.PaintConversion;
import snownee.spacefold.recipe.SimulationIngredient;

@Mod(SpaceFold.ID)
public class CommonProxy implements ModInitializer {
	@Override
	public void onInitialize() {
		Objects.requireNonNull(CoreModule.CHAMBER_WALL);
		CustomIngredientSerializer.register(SimulationIngredient.SERIALIZER);
		PlanFlight.init();
		PlanConfinement.init();
		SimulateMode.init();
		SimulationManager.init();
		if (Platform.isModLoaded("xercapaint")) {
			PaintConversion.init();
		}
		CommandRegistrationCallback.EVENT.register(ServerCommand::register);
	}
}
