package snownee.spacefold.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FactorySlot extends Slot {
	private final int maxStackSize;

	public FactorySlot(Container container, int slot, int x, int y, int maxStackSize) {
		super(container, slot, x, y);
		this.maxStackSize = maxStackSize;
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return container.canPlaceItem(getContainerSlot(), itemStack);
	}

	@Override
	public int getMaxStackSize() {
		return maxStackSize;
	}
}
