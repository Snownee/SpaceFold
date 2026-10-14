package snownee.spacefold.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.network.SViewDetailsPacket;

public class FactoryMenu extends AbstractContainerMenu {
	public static final int BUTTON_MATERIALS = 0;
	public static final int BUTTON_CREATE = 1;

	private final Container container;
	private final ContainerData data;

	public FactoryMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, (FactoryBlockEntity) inventory.player.level().getBlockEntity(pos));
	}

	public FactoryMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(2), new SimpleContainerData(2));
	}

	private FactoryMenu(int containerId, Inventory inventory, FactoryBlockEntity blockEntity) {
		this(containerId, inventory, blockEntity, blockEntity.data());
	}

	private FactoryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(CoreModule.FACTORY_MENU.get(), containerId);
		this.container = container;
		this.data = data;
		addSlot(new FactorySlot(container, FactoryBlockEntity.TEMPLATE_SLOT, 80, 35));
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(data);
	}

	public int progress() {
		return data.get(0);
	}

	public int errorCode() {
		return data.get(1);
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (buttonId == BUTTON_MATERIALS && player instanceof ServerPlayer serverPlayer &&
				container instanceof FactoryBlockEntity factory) {
			KPacketSender.send(
					new SViewDetailsPacket(
							factory.materialProgresses(),
							true,
							factory.templateSnapshotId(),
							factory.getItem(FactoryBlockEntity.TEMPLATE_SLOT)), serverPlayer);
			return true;
		}
		if (buttonId == BUTTON_CREATE && player.isCreative() && container instanceof FactoryBlockEntity factory) {
			return factory.creativeInstantProduce();
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (slotIndex < 1) {
			if (!moveItemStackTo(stack, 1, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (!moveItemStackTo(stack, FactoryBlockEntity.TEMPLATE_SLOT, FactoryBlockEntity.TEMPLATE_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
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
}
