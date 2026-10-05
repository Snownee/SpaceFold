package snownee.spacefold.compat.jade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.JadeClient;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.compat.RecipeLookupPlugin;
import snownee.jade.compat.RecipeLookupResult;
import snownee.jade.util.ModIdentification;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.block.RepeaterBlock;
import snownee.spacefold.block.SimulatorBlock;
import snownee.spacefold.block.entity.RepeaterBlockEntity;

@WailaPlugin
public class JadeCompat implements IWailaPlugin {
	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(RepeaterProvider.INSTANCE, RepeaterBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(FacingProvider.INSTANCE, ChamberWallBlock.class);
		registration.registerBlockComponent(FacingProvider.INSTANCE, SimulatorBlock.class);
		registration.registerBlockComponent(RepeaterProvider.Client.INSTANCE, RepeaterBlock.class);
	}

	public static void lookupRecipes(ItemStack itemStack, boolean uses) {
		if (itemStack.isEmpty() || JadeClient.recipeLookupPlugins.isEmpty()) {
			return;
		}
		Identifier specialId = ModIdentification.getSpecialId(itemStack).orElse(null);
		RecipeLookupResult selected = null;
		List<RecipeLookupResult> results = new ArrayList<>();
		for (RecipeLookupPlugin plugin : JadeClient.recipeLookupPlugins) {
			try {
				RecipeLookupResult result = plugin.lookup(itemStack, specialId, uses);
				if (result.isFail()) {
					continue;
				}
				results.add(result);
				if (selected == null || result.score() > selected.score()) {
					selected = result;
				}
			} catch (Throwable e) {
				SpaceFold.LOGGER.error("Failed to lookup recipes", e);
			}
		}
		if (selected != null) {
			selected.action().accept(null, results);
		}
	}
}
