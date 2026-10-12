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
						.define('E', Items.ENDER_PEARL)
						.define('A', Items.AMETHYST_SHARD)
						.define('I', Items.IRON_INGOT)
						.pattern("  A")
						.pattern(" E ")
						.pattern("I  ")
						.unlockedBy("has_amethyst_shard", has(Items.AMETHYST_SHARD))
						.save(output);

				shaped(RecipeCategory.MISC, CoreModule.SIMULATOR.get())
						.define('O', Blocks.CRYING_OBSIDIAN)
						.define('E', Items.ENDER_EYE)
						.define('M', CoreModule.CHAMBER_WALL.get())
						.pattern("MOM")
						.pattern("OEO")
						.pattern("MOM")
						.unlockedBy("has_ender_eye", has(Items.ENDER_EYE))
						.save(output);

				shaped(RecipeCategory.MISC, CoreModule.REPEATER.get())
						.define('S', CoreModule.SIMULATOR.get())
						.define('A', Items.AMETHYST_BLOCK)
						.define('D', Items.DIAMOND)
						.pattern("DAD")
						.pattern("ASA")
						.pattern("DAD")
						.unlockedBy("has_spacetime_anchor", has(CoreModule.SIMULATOR.get()))
						.save(output);

				shaped(RecipeCategory.MISC, CoreModule.FACTORY.get(), 2)
						.define('R', CoreModule.REPEATER.get())
						.define('A', Items.AMETHYST_SHARD)
						.define('I', Items.IRON_INGOT)
						.pattern("IAI")
						.pattern("ARA")
						.pattern("IAI")
						.unlockedBy("has_shard_engine", has(CoreModule.REPEATER.get()))
						.save(output);
			}
		};
	}

	@Override
	public String getName() {
		return "Space Fold - Recipes";
	}
}
