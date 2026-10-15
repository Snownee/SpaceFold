package snownee.spacefold.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import snownee.spacefold.CoreModule;

public class SpaceFoldRecipes extends FabricRecipeProvider {
	public SpaceFoldRecipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(output, registriesFuture);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
		return new RecipeProvider(registries, output) {
			@Override
			public void buildRecipes() {
				shaped(RecipeCategory.TOOLS, CoreModule.OPENER.get())
						.define('A', Items.AMETHYST_SHARD)
						.define('E', Items.END_ROD)
						.pattern("A")
						.pattern("E")
						.unlockedBy("has_item", has(CoreModule.SIMULATOR.get()))
						.save(output);

				shaped(RecipeCategory.MISC, CoreModule.SIMULATOR.get())
						.define('O', Blocks.CRYING_OBSIDIAN)
						.define('E', Items.ENDER_EYE)
						.define('I', Items.IRON_INGOT)
						.pattern("OOO")
						.pattern("IEI")
						.pattern("OOO")
						.unlockedBy("has_item", has(Blocks.CRYING_OBSIDIAN))
						.save(output);

				shaped(RecipeCategory.MISC, CoreModule.FACTORY.get(), 2)
						.define('O', Blocks.CRYING_OBSIDIAN)
						.define('A', Items.AMETHYST_SHARD)
						.define('I', Items.IRON_INGOT)
						.pattern("AOA")
						.pattern(" I ")
						.pattern(" O ")
						.unlockedBy("has_item", has(Blocks.CRYING_OBSIDIAN))
						.save(output);

				shaped(RecipeCategory.REDSTONE, CoreModule.SIMULATION_REDSTONE.get())
						.define('R', Blocks.REDSTONE_BLOCK)
						.define('A', Items.AMETHYST_SHARD)
						.pattern(" A ")
						.pattern("ARA")
						.pattern(" A ")
						.unlockedBy("has_item", has(Blocks.REDSTONE_BLOCK))
						.save(output);
			}
		};
	}

	@Override
	public String getName() {
		return "Space Fold - Recipes";
	}
}
