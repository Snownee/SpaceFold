package snownee.spacefold.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.FactoryMenu;
import snownee.spacefold.menu.RepeaterMenu;
import snownee.spacefold.network.CRepeaterConfigPacket;
import snownee.spacefold.network.SViewDetailsPacket;

public final class ViewDetailsKey {
	private static final int HOLD_MILLIS = 450;

	public static KeyMapping KEY;

	private static long holdStart = -1;

	private ViewDetailsKey() {
	}

	public static void register() {
		KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.spacefold.view_details",
				InputConstants.KEY_V,
				KeyMapping.Category.register(SpaceFold.id("main"))));
	}

	public static boolean isDown() {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(KEY);
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
		}
		return KEY.isDown();
	}

	public static float progress() {
		if (holdStart < 0 || !isDown()) {
			return 0F;
		}
		return Mth.clamp((Util.getMillis() - holdStart) / (float) HOLD_MILLIS, 0F, 1F);
	}

	public static void tick(@Nullable Slot hoveredSlot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (hoveredSlot != null && minecraft.screen instanceof FactoryScreen factoryScreen &&
				hoveredSlot.index == FactoryBlockEntity.TEMPLATE_SLOT) {
			if (hoveredSlot.getItem().isEmpty()) {
				holdStart = -1;
				return;
			}
			if (consumeHold()) {
				minecraft.gameMode.handleInventoryButtonClick(factoryScreen.getMenu().containerId, FactoryMenu.BUTTON_MATERIALS);
			}
			return;
		}
		ItemStack stack = hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem();
		if (stack.isEmpty()) {
			holdStart = -1;
			return;
		}
		boolean inPlayerInventory = hoveredSlot != null
				&& minecraft.player != null
				&& hoveredSlot.container == minecraft.player.getInventory();
		if (!stack.has(CoreModule.SIMULATION_RESULT.get())) {
			if (!inPlayerInventory || !canConfigure(stack)) {
				holdStart = -1;
				return;
			}
			if (consumeHold()) {
				minecraft.player.closeContainer();
				KPacketSender.sendToServer(new CRepeaterConfigPacket(hoveredSlot.getContainerSlot()));
			}
			return;
		}
		if (consumeHold()) {
			int configSlot = inPlayerInventory && RepeaterMenu.canConfigure(minecraft.player, stack)
					? hoveredSlot.getContainerSlot()
					: -1;
			minecraft.player.closeContainer();
			minecraft.setScreen(new DetailsScreen(
					SViewDetailsPacket.entriesFrom(stack),
					false,
					SViewDetailsPacket.snapshotIdFrom(stack),
					configSlot,
					stack));
		}
	}

	public static boolean canConfigure(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null
				&& FactoryBlockEntity.isTemplateStack(stack)
				&& RepeaterMenu.canConfigure(minecraft.player, stack);
	}

	private static boolean consumeHold() {
		if (!isDown()) {
			holdStart = -1;
			return false;
		}
		long now = Util.getMillis();
		if (holdStart < 0) {
			holdStart = now;
		}
		if (now - holdStart >= HOLD_MILLIS) {
			holdStart = -1;
			return true;
		}
		return false;
	}
}
