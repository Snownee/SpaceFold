package snownee.spacefold.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;

public class SpaceFoldModels extends FabricModelProvider {
	public SpaceFoldModels(FabricPackOutput output) {
		super(output);
	}

	private static void generateCubeItemModel(ItemModelGenerators itemModelGenerators, Item item, Identifier texture) {
		Identifier model = ModelTemplates.CUBE_ALL.create(
				ModelLocationUtils.getModelLocation(item),
				TextureMapping.cube(new Material(texture)),
				itemModelGenerators.modelOutput);
		itemModelGenerators.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators blockModelGenerators) {

	}

	@Override
	public void generateItemModels(ItemModelGenerators itemModelGenerators) {
		generateCubeItemModel(itemModelGenerators, CoreModule.CHAMBER_WALL.get().asItem(), Identifier.withDefaultNamespace("block/stone"));
		generateCubeItemModel(itemModelGenerators, CoreModule.INTERFACE.get().asItem(), SpaceFold.id("block/interface"));
		itemModelGenerators.itemModelOutput.accept(
				CoreModule.SIMULATOR.get().asItem(),
				ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(CoreModule.SIMULATOR.get())));
		itemModelGenerators.itemModelOutput.accept(
				CoreModule.REPEATER.get().asItem(),
				ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(CoreModule.REPEATER.get())));
		itemModelGenerators.itemModelOutput.accept(
				CoreModule.FACTORY.get().asItem(),
				ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(CoreModule.FACTORY.get())));
		itemModelGenerators.generateFlatItem(CoreModule.OPENER.get(), ModelTemplates.FLAT_ITEM);
	}
}
