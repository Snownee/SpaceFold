package snownee.spacefold.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.BlockTags;
import snownee.spacefold.CoreModule;

public class SpaceFoldBlockTags extends FabricTagsProvider.BlockTagsProvider {

	public SpaceFoldBlockTags(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
		super(output, registryLookupFuture);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		valueLookupBuilder(CoreModule.CHAMBER_WINDOWS).add(CoreModule.CHAMBER_WINDOW.get(), CoreModule.INTERFACE_WINDOW.get());

		valueLookupBuilder(BlockTags.DRAGON_IMMUNE).add(
				CoreModule.CHAMBER_WALL.get(),
				CoreModule.CHAMBER_WINDOW.get(),
				CoreModule.INTERFACE.get(),
				CoreModule.INTERFACE_WINDOW.get());
		valueLookupBuilder(BlockTags.WITHER_IMMUNE).add(
				CoreModule.CHAMBER_WALL.get(),
				CoreModule.CHAMBER_WINDOW.get(),
				CoreModule.INTERFACE.get(),
				CoreModule.INTERFACE_WINDOW.get());
	}
}
