package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.FactoryMenu;

public class FactoryScreen extends AbstractContainerScreen<FactoryMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

	public FactoryScreen(FactoryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.titleLabelX = 29;
	}

	private static String errorKey(int code) {
		return switch (code) {
			case FactoryBlockEntity.ERROR_ROOM_SIDE -> "tip.spacefold.factory_room_side";
			case FactoryBlockEntity.ERROR_NO_RESULT -> "tip.spacefold.factory_no_result";
			case FactoryBlockEntity.ERROR_FAILED -> "tip.spacefold.factory_failed";
			default -> "tip.spacefold.factory_invalid";
		};
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(
						Component.translatable("gui.spacefold.factory_materials"),
						button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, FactoryMenu.BUTTON_MATERIALS))
				.bounds(this.leftPos + 8, this.topPos + 58, 160, 20)
				.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
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
