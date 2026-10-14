package snownee.spacefold.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FactorySlot extends Slot {
	public FactorySlot(Container container, int slot, int x, int y) {
		super(container, slot, x, y);
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return container.canPlaceItem(getContainerSlot(), itemStack);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}
}
