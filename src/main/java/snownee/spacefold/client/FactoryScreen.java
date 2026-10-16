package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.FactoryMenu;

public class FactoryScreen extends AbstractContainerScreen<FactoryMenu> {
	private static final Identifier TEXTURE = SpaceFold.id("textures/gui/container/factory.png");
	private static final int NORMAL_SLOT = 0;
	private static final int PROGRESS_SLOT = 33;
	private static final int ERROR_SLOT = 66;

	public FactoryScreen(FactoryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
	}

	private static String errorKey(int code) {
		return switch (code) {
			case FactoryBlockEntity.ERROR_ROOM_SIDE -> "tip.spacefold.factory_room_side";
			case FactoryBlockEntity.ERROR_NO_RESULT -> "tip.spacefold.factory_no_result";
			case FactoryBlockEntity.ERROR_FAILED -> "tip.spacefold.factory_failed";
			case FactoryBlockEntity.ERROR_NO_RECEIVER -> "tip.spacefold.factory_no_receiver";
			case FactoryBlockEntity.ERROR_OBSTRUCTED -> "tip.spacefold.factory_obstructed";
			default -> "tip.spacefold.factory_invalid";
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				TEXTURE,
				leftPos + menu.getSlot(0).x - 8,
				topPos + menu.getSlot(0).y - 8,
				DEFAULT_IMAGE_WIDTH,
				menu.errorCode() == FactoryBlockEntity.ERROR_NONE ? NORMAL_SLOT : ERROR_SLOT,
				32,
				32,
				256,
				256);
		float progress = Mth.clamp(menu.progress() / (float) SpaceFoldConfig.factoryDurationTicks, 0.0F, 1.0F);
		if (progress > 0.0F) {
			int progressHeight = (int) (progress * 32.0F);
			graphics.blit(
					RenderPipelines.GUI_TEXTURED,
					TEXTURE,
					leftPos + menu.getSlot(0).x - 8,
					topPos + menu.getSlot(0).y - 8 + 32 - progressHeight,
					DEFAULT_IMAGE_WIDTH,
					PROGRESS_SLOT + 32 - progressHeight,
					32,
					progressHeight,
					256,
					256);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_SPACE && minecraft.player != null && minecraft.player.isCreative()) {
			Objects.requireNonNull(minecraft.gameMode).handleInventoryButtonClick(menu.containerId, FactoryMenu.BUTTON_CREATE);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
		List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(itemStack));
		if (hoveredSlot != null && hoveredSlot.index == FactoryBlockEntity.TEMPLATE_SLOT &&
				menu.errorCode() != FactoryBlockEntity.ERROR_NONE) {
			lines.add(Component.translatable(errorKey(menu.errorCode())).withStyle(ChatFormatting.RED));
		}
		return lines;
	}
}
