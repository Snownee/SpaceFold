package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import snownee.jade.gui.config.SmoothScrollableList;
import snownee.kiwi.loader.Platform;
import snownee.spacefold.compat.jade.JadeCompat;
import snownee.spacefold.network.SViewDetailsPacket;

public class MaterialList extends SmoothScrollableList<MaterialList.MaterialRow> {
	private static final int COLUMNS = 2;
	private static final int CELL_WIDTH = 150;
	private static final int CELL_HEIGHT = 28;
	private static final int ROW_WIDTH = COLUMNS * CELL_WIDTH;
	private static final int MARGIN = 8;
	private static final int CONTENT_PADDING = 6;
	private static final int ICON_GAP = 4;
	private static final int ICON_SIZE = 16;
	private static final int BAR_HEIGHT = 4;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int BAR_COLOR = 0xFF3FB950;
	private static final int BAR_BACKGROUND = 0xFF303030;
	private static final int PANEL_COLOR = 0xC0000000;
	private static final int FOCUS_COLOR = 0xFFFFFFFF;

	private final boolean showProgress;

	public MaterialList(Minecraft minecraft, List<SViewDetailsPacket.Entry> data, boolean showProgress) {
		super(minecraft, ROW_WIDTH, CELL_HEIGHT, 0, CELL_HEIGHT);
		this.showProgress = showProgress;
		if (data.isEmpty()) {
			addEntry(new MaterialRow(List.of()));
			return;
		}
		for (int i = 0; i < data.size(); i += COLUMNS) {
			addEntry(new MaterialRow(data.subList(i, Math.min(i + COLUMNS, data.size()))));
		}
	}

	public void updateLayout(int centerX, int areaTop, int areaBottom) {
		int natural = contentHeight();
		int minTop = areaTop + MARGIN + 12;
		int maxBottom = areaBottom - MARGIN;
		int available = Math.max(CELL_HEIGHT, maxBottom - minTop);
		int height = Math.min(natural, available);
		updateSizeAndPosition(ROW_WIDTH, height, centerX - ROW_WIDTH / 2, minTop + (available - height) / 2);
	}

	@Override
	public int getRowWidth() {
		return ROW_WIDTH;
	}

	@Override
	protected int scrollBarX() {
		return getRight() - 6;
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor graphics) {
		graphics.fill(getX() - 4, getY() - 4, getRight() + 4, getBottom() + 4, withAlpha(PANEL_COLOR, getAlpha()));
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor graphics) {
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		tickSmoothScroll();
		super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.translatable("gui.spacefold.details.materials"));
	}

	private static int withAlpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
		return (color & 0x00FFFFFF) | (a << 24);
	}

	public class MaterialRow extends ContainerObjectSelectionList.Entry<MaterialRow> {
		private final List<MaterialCell> cells = new ArrayList<>();

		public MaterialRow(List<SViewDetailsPacket.Entry> data) {
			for (SViewDetailsPacket.Entry entry : data) {
				cells.add(new MaterialCell(entry));
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return cells;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return cells;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTime) {
			float alpha = MaterialList.this.getAlpha();
			if (cells.isEmpty()) {
				Font font = Minecraft.getInstance().font;
				graphics.centeredText(
						font,
						Component.translatable("gui.spacefold.details.materials_empty"),
						getX() + getWidth() / 2,
						getY() + (getHeight() - font.lineHeight) / 2,
						withAlpha(MUTED_COLOR, alpha));
				return;
			}
			for (int i = 0; i < cells.size(); i++) {
				MaterialCell cell = cells.get(i);
				cell.setX(getX() + i * CELL_WIDTH);
				cell.setY(getY());
				cell.setAlpha(alpha);
				cell.extractRenderState(graphics, mouseX, mouseY, deltaTime);
			}
		}
	}

	public class MaterialCell extends AbstractWidget {
		private final SViewDetailsPacket.Entry data;
		private final ItemStack stack;
		private boolean armed;

		public MaterialCell(SViewDetailsPacket.Entry data) {
			super(0, 0, CELL_WIDTH, CELL_HEIGHT, CommonComponents.EMPTY);
			this.data = data;
			this.stack = data.item().toStack();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			Font font = Minecraft.getInstance().font;
			float alpha = getAlpha();
			int lineHeight = font.lineHeight;
			int contentHeight = lineHeight * 2 + (showProgress ? BAR_HEIGHT : 0);
			int top = y + (CELL_HEIGHT - contentHeight) / 2;
			int iconX = x + CONTENT_PADDING;
			int iconY = y + (CELL_HEIGHT - ICON_SIZE) / 2;
			int textX = iconX + ICON_SIZE + ICON_GAP;
			int barWidth = x + CELL_WIDTH - CONTENT_PADDING - textX;
			float iconScale = Mth.clamp(alpha, 0.0F, 1.0F);
			if (iconScale > 0.01F) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(iconX + ICON_SIZE / 2.0F, iconY + ICON_SIZE / 2.0F);
				graphics.pose().scale(iconScale, iconScale);
				graphics.item(stack, -8, -8);
				graphics.pose().popMatrix();
			}
			graphics.text(font, stack.getHoverName(), textX, top, withAlpha(TEXT_COLOR, alpha), false);
			if (showProgress) {
				int percent = data.total() <= 0 ? 100 : (int) Math.round(100.0 * data.provided() / data.total());
				Component progress = Component.translatable("gui.spacefold.material_progress", data.provided(), data.total(), percent);
				graphics.text(font, progress, textX, top + lineHeight, withAlpha(MUTED_COLOR, alpha), false);
				int barY = top + lineHeight * 2;
				graphics.fill(textX, barY, textX + barWidth, barY + BAR_HEIGHT, withAlpha(BAR_BACKGROUND, alpha));
				int filled = data.total() <= 0 ? barWidth : (int) Math.round(barWidth * (double) data.provided() / data.total());
				if (filled > 0) {
					graphics.fill(textX, barY, textX + filled, barY + BAR_HEIGHT, withAlpha(BAR_COLOR, alpha));
				}
			} else {
				graphics.text(
						font,
						Component.translatable("gui.spacefold.material_quantity", data.total()),
						textX,
						top + lineHeight,
						withAlpha(MUTED_COLOR, alpha),
						false);
			}
			if (isFocused()) {
				graphics.outline(x, y, CELL_WIDTH, CELL_HEIGHT, withAlpha(FOCUS_COLOR, alpha));
			}
			if (isOverIcon(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (!isOverIcon(event.x(), event.y())) {
				return false;
			}
			if (event.button() == 1) {
				lookup(true);
			} else if (event.button() == 0) {
				armed = true;
			} else {
				return false;
			}
			return true;
		}

		@Override
		public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
			armed = false;
			return false;
		}

		@Override
		public boolean mouseReleased(MouseButtonEvent event) {
			boolean fire = armed && event.button() == 0 && isOverIcon(event.x(), event.y());
			armed = false;
			if (fire) {
				lookup(false);
			}
			return fire;
		}

		private void lookup(boolean uses) {
			if (Platform.isModLoaded("jade")) {
				JadeCompat.lookupRecipes(stack, uses);
			}
		}

		private boolean isOverIcon(double mouseX, double mouseY) {
			int iconX = getX() + CONTENT_PADDING;
			int iconY = getY() + (CELL_HEIGHT - ICON_SIZE) / 2;
			return mouseX >= iconX && mouseX < iconX + ICON_SIZE && mouseY >= iconY && mouseY < iconY + ICON_SIZE;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, stack.getHoverName());
		}
	}
}
