package snownee.spacefold.util;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.spacefold.dim.SimulationResult;

public class SpaceFoldUtil {
	public static ItemStack randomItem() {
		List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
		return new ItemStack(items.get((int) (Math.random() * items.size())));
	}

	public static SimulationResult.Rates randomRates() {
		Map<ItemVariant, Double> itemRates = Maps.newHashMap();
		for (int i = 0; i < 2; i++) {
			itemRates.put(ItemVariant.of(randomItem()), (double) (int) (Math.random() * 64));
		}
		return new SimulationResult.Rates(itemRates, Map.of(), 0);
	}
}
