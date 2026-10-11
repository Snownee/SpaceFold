package snownee.spacefold.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import snownee.kiwi.datagen.KiwiLanguageProvider;

public class SpaceFoldDataGen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
		pack.addProvider(KiwiLanguageProvider::new);
		pack.addProvider(SpaceFoldModels::new);
		pack.addProvider(SpaceFoldRecipes::new);
		pack.addProvider(SpaceFoldBlockTags::new);
	}
}
