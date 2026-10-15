package snownee.spacefold.util;

import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
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
		Object2DoubleMap<ItemVariant> itemRates = new Object2DoubleOpenHashMap<>();
		for (int i = 0; i < 2; i++) {
			itemRates.put(ItemVariant.of(randomItem()), (int) (Math.random() * 64));
		}
		return new SimulationResult.Rates(itemRates, Object2DoubleMaps.emptyMap(), 0);
	}
}
