package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.menu.RepeaterMenu;

public class RepeaterScreen extends AbstractContainerScreen<RepeaterMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	public RepeaterScreen(RepeaterMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 222);
		this.inventoryLabelY = 128;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
		List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(itemStack));
		if (hoveredSlot != null) {
			String key = switch (hoveredSlot.index) {
				case RepeaterMenu.INPUT_SIGN -> "gui.spacefold.repeater_inputs_tip";
				case RepeaterMenu.OUTPUT_SIGN -> "gui.spacefold.repeater_outputs_tip";
				case RepeaterMenu.MATERIAL_SIGN -> "gui.spacefold.repeater_materials_tip";
				default -> null;
			};
			if (key != null) {
				lines.add(Component.translatable(key));
			}
		}
		return lines;
	}
}
