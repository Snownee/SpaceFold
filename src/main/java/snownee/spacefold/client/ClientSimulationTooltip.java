package snownee.spacefold.client;

import java.util.List;
import java.util.Locale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import snownee.spacefold.client.SimulationTooltip.Entry;
import snownee.spacefold.util.FluidAmounts;

@Environment(EnvType.CLIENT)
public class ClientSimulationTooltip implements ClientTooltipComponent {
	private static final int SLOT = 18;
	private static final int ARROW_HEIGHT = 6;
	private static final int ARROW_WIDTH = 9;
	private static final int SINGLE_ROW_LIMIT = 8;
	private static final int COLOR = -1;

	private final List<Entry> inputs;
	private final List<Entry> outputs;

	public ClientSimulationTooltip(SimulationTooltip tooltip) {
		this.inputs = tooltip.inputs();
		this.outputs = tooltip.outputs();
	}

	private boolean singleRow() {
		return inputs.size() + outputs.size() <= SINGLE_ROW_LIMIT;
	}

	private boolean hasArrow() {
		return singleRow() && !inputs.isEmpty() && !outputs.isEmpty();
	}

	private int columns() {
		return Math.max(inputs.size(), outputs.size());
	}

	@Override
	public int getHeight(Font font) {
		return singleRow() ? SLOT : SLOT * 2 + ARROW_HEIGHT;
	}

	@Override
	public int getWidth(Font font) {
		if (singleRow()) {
			return (inputs.size() + outputs.size()) * SLOT + (hasArrow() ? ARROW_WIDTH : 0);
		}
		return columns() * SLOT;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		if (singleRow()) {
			int slotX = x;
			for (Entry entry : inputs) {
				drawEntry(font, graphics, entry, slotX, y);
				slotX += SLOT;
			}
			if (hasArrow()) {
				drawArrowRight(graphics, slotX, y);
				slotX += ARROW_WIDTH;
			}
			for (Entry entry : outputs) {
				drawEntry(font, graphics, entry, slotX, y);
				slotX += SLOT;
			}
			return;
		}
		drawRow(font, graphics, inputs, x, y, w);
		drawArrow(graphics, x + w / 2, y + SLOT + 1);
		drawRow(font, graphics, outputs, x, y + SLOT + ARROW_HEIGHT, w);
	}

	private void drawRow(Font font, GuiGraphicsExtractor graphics, List<Entry> entries, int x, int y, int w) {
		int startX = x + (w - entries.size() * SLOT) / 2;
		for (int i = 0; i < entries.size(); i++) {
			drawEntry(font, graphics, entries.get(i), startX + i * SLOT, y);
		}
	}

	private void drawEntry(Font font, GuiGraphicsExtractor graphics, Entry entry, int x, int y) {
		double rate = entry.rate();
		switch (entry) {
			case Entry.Item item -> drawItem(graphics, item, x, y);
			case Entry.Fluid fluid -> {
				drawFluid(graphics, fluid, x, y);
				rate = FluidAmounts.toMilliBuckets(rate);
			}
		}
		drawAmount(graphics, font, x, y, formatRate(rate));
	}

	private static void drawItem(GuiGraphicsExtractor graphics, Entry.Item entry, int x, int y) {
		graphics.item(entry.stack(), x, y);
	}

	private static void drawFluid(GuiGraphicsExtractor graphics, Entry.Fluid entry, int x, int y) {
		FluidVariant fluid = entry.fluid();
		FluidModel model = Minecraft.getInstance()
				.getModelManager()
				.getFluidStateModelSet()
				.get(fluid.getFluid().defaultFluidState());
		TextureAtlasSprite sprite = model.stillMaterial().sprite();
		int color = FluidVariantRendering.getColor(fluid);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, color);
	}

	private static void drawAmount(GuiGraphicsExtractor graphics, Font font, int x, int y, String text) {
		boolean smaller = text.length() > 3;
		float scale = smaller ? 0.5F : 0.75F;
		int dx = smaller ? 32 : 22;
		int dy = smaller ? 23 : 13;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		graphics.text(font, text, dx - font.width(text), dy, COLOR, true);
		graphics.pose().popMatrix();
	}

	private static void drawArrow(GuiGraphicsExtractor graphics, int cx, int y) {
		for (int i = 0; i < 4; i++) {
			graphics.fill(cx - 3 + i, y + i, cx + 4 - i, y + i + 1, COLOR);
		}
	}

	private static void drawArrowRight(GuiGraphicsExtractor graphics, int x, int y) {
		int cy = y + SLOT / 2;
		int cx = x + ARROW_WIDTH / 2 - 2;
		for (int i = 0; i < 4; i++) {
			graphics.fill(cx + i, cy - 3 + i, cx + i + 1, cy + 4 - i, COLOR);
		}
	}

	private static String formatRate(double rate) {
		if (rate == Math.rint(rate)) {
			return Long.toString((long) rate);
		}
		return String.format(Locale.ROOT, "%.1f", rate);
	}
}
