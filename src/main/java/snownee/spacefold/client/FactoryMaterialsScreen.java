package snownee.spacefold.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import snownee.kiwi.loader.Platform;
import snownee.spacefold.compat.jade.JadeCompat;
import snownee.spacefold.network.SFactoryMaterialsPacket.Entry;

public class FactoryMaterialsScreen extends Screen {
	private static final int COLUMNS = 2;
	private static final int CELL_WIDTH = 150;
	private static final int CELL_HEIGHT = 28;
	private static final int PADDING = 8;
	private static final int ICON_SIZE = 16;
	private static final int BAR_HEIGHT = 4;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int BAR_COLOR = 0xFF3FB950;
	private static final int BAR_BACKGROUND = 0xFF303030;
	private static final int PANEL_COLOR = 0xC0000000;

	private final List<Entry> entries;
	private final boolean showProgress;
	private int left;
	private int top;
	private int rows;

	public FactoryMaterialsScreen(List<Entry> entries, boolean showProgress) {
		super(Component.translatable("gui.spacefold.factory_materials"));
		this.entries = entries;
		this.showProgress = showProgress;
	}

	@Override
	protected void init() {
		rows = Math.max(1, (entries.size() + COLUMNS - 1) / COLUMNS);
		int gridWidth = COLUMNS * CELL_WIDTH;
		int gridHeight = rows * CELL_HEIGHT;
		left = (width - gridWidth) / 2;
		top = Math.max(PADDING + 12, (height - gridHeight) / 2);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.fill(
				left - PADDING,
				top - PADDING - 10,
				left + COLUMNS * CELL_WIDTH + PADDING,
				top + rows * CELL_HEIGHT + PADDING,
				PANEL_COLOR);
		graphics.text(font, title, left, top - 10, TEXT_COLOR, false);
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			int x = left + i % COLUMNS * CELL_WIDTH;
			int y = top + i / COLUMNS * CELL_HEIGHT;
			renderEntry(graphics, entry, x, y, mouseX, mouseY);
		}
	}

	private void renderEntry(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int mouseX, int mouseY) {
		ItemStack stack = entry.item().toStack();
		graphics.item(stack, x, y);
		graphics.text(font, stack.getHoverName(), x + ICON_SIZE + 4, y, TEXT_COLOR, false);
		if (!showProgress) {
			graphics.text(
					font,
					Component.translatable("gui.spacefold.material_quantity", entry.total()),
					x + ICON_SIZE + 4,
					y + 9,
					MUTED_COLOR,
					false);
			if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
			return;
		}
		int percent = entry.total() <= 0 ? 100 : (int) Math.round(100.0 * entry.provided() / entry.total());
		Component progress = Component.translatable("gui.spacefold.material_progress", entry.provided(), entry.total(), percent);
		graphics.text(font, progress, x + ICON_SIZE + 4, y + 9, MUTED_COLOR, false);
		int barX = x + ICON_SIZE + 4;
		int barY = y + 18;
		int barWidth = CELL_WIDTH - ICON_SIZE - 12;
		graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, BAR_BACKGROUND);
		int filled = entry.total() <= 0 ? barWidth : (int) Math.round(barWidth * (double) entry.provided() / entry.total());
		if (filled > 0) {
			graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, BAR_COLOR);
		}
		if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
			graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 || event.button() == 1) {
			for (int i = 0; i < entries.size(); i++) {
				int x = left + i % COLUMNS * CELL_WIDTH;
				int y = top + i / COLUMNS * CELL_HEIGHT;
				if (event.x() >= x && event.x() < x + ICON_SIZE && event.y() >= y && event.y() < y + ICON_SIZE) {
					if (Platform.isModLoaded("jade")) {
						JadeCompat.lookupRecipes(entries.get(i).item().toStack(), event.button() == 1);
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
