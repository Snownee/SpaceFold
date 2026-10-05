package snownee.spacefold.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.block.entity.FactoryBlockEntity;

public class FactorySlot extends Slot {
	private final boolean template;

	public FactorySlot(Container container, int slot, int x, int y, boolean template) {
		super(container, slot, x, y);
		this.template = template;
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return template && FactoryBlockEntity.isTemplateStack(itemStack);
	}
}
