package snownee.spacefold.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.CoreModule;

public class RepeaterMenu extends AbstractContainerMenu {
	public static final int COLUMNS = 9;
	public static final int ROWS = 6;
	public static final int SIZE = COLUMNS * ROWS;
	public static final int INPUT_SIGN = 0;
	public static final int OUTPUT_SIGN = 18;
	public static final int MATERIAL_SIGN = 36;
	public static final int SECTION_SLOTS = COLUMNS * 2 - 1;

	private final RepeaterContainer container;

	public RepeaterMenu(int containerId, Inventory inventory, ItemStack stack) {
		this(containerId, inventory, new RepeaterContainer(stack));
	}

	public RepeaterMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new RepeaterContainer(null));
	}

	private RepeaterMenu(int containerId, Inventory inventory, RepeaterContainer container) {
		super(CoreModule.REPEATER_MENU.get(), containerId);
		this.container = container;
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				int index = row * COLUMNS + col;
				Slot slot = RepeaterContainer.isSign(index)
						? new ReadOnlySlot(container, index, 8 + col * 18, 18 + row * 18)
						: new Slot(container, index, 8 + col * 18, 18 + row * 18);
				addSlot(slot);
			}
		}
		addStandardInventorySlots(inventory, 8, 139);
		if (container.wasTruncated() && inventory.player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("gui.spacefold.repeater_truncated"));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < SIZE) {
			if (!moveItemStackTo(stack, SIZE, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, SIZE, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	private static class ReadOnlySlot extends Slot {
		private ReadOnlySlot(RepeaterContainer container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack itemStack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}
	}
}
