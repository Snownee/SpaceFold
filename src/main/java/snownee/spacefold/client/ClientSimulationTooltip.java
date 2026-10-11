package snownee.spacefold.client;

import java.util.List;
import java.util.Locale;

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
import snownee.spacefold.client.room.VoxelRoomRenderState;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.util.FluidAmounts;

public class ClientSimulationTooltip implements ClientTooltipComponent {
	private static final int SLOT = 18;
	private static final int ICON = 16;
	private static final int PAD = (SLOT - ICON) / 2;
	private static final int ARROW_HEIGHT = 6;
	private static final int COLOR = -1;
	private static final int PREVIEW = 64;
	private static final int PREVIEW_GAP = 4;

	private final List<Entry> inputs;
	private final List<Entry> outputs;
	private final int snapshotId;

	public ClientSimulationTooltip(SimulationTooltip tooltip) {
		this.inputs = tooltip.inputs();
		this.outputs = tooltip.outputs();
		this.snapshotId = tooltip.snapshotId();
	}

	private boolean hasEntries() {
		return !inputs.isEmpty() || !outputs.isEmpty();
	}

	private boolean hasPreview() {
		return snapshotId >= 0;
	}

	private int columns() {
		return Math.max(inputs.size(), outputs.size());
	}

	private int entriesWidth() {
		return columns() * SLOT;
	}

	private int entriesHeight() {
		return SLOT * (inputs.isEmpty() ? 1 : 2) + ARROW_HEIGHT;
	}

	@Override
	public int getHeight(Font font) {
		return hasPreview() ? Math.max(PREVIEW, entriesHeight()) : entriesHeight();
	}

	@Override
	public int getWidth(Font font) {
		int width = entriesWidth();
		if (hasPreview()) {
			width += PREVIEW;
			if (hasEntries()) {
				width += PREVIEW_GAP;
			}
		}
		return width;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
		int componentHeight = getHeight(font);
		int entriesX = x;
		if (hasPreview()) {
			drawPreview(x, y + (componentHeight - PREVIEW) / 2, graphics);
			entriesX = x + PREVIEW + PREVIEW_GAP;
		}
		if (hasEntries()) {
			drawEntries(font, entriesX, y + (componentHeight - entriesHeight()) / 2, entriesWidth(), graphics);
		}
	}

	private void drawPreview(int x, int y, GuiGraphicsExtractor graphics) {
		RoomSnapshot snapshot = PreviewCache.getSnapshot(snapshotId);
		if (snapshot == null) {
			return;
		}
		VoxelRoomRenderState state = VoxelRoomRenderState.of(
				snapshot,
				snapshotId,
				PreviewCache.getYaw(snapshotId),
				25.0F,
				x,
				y,
				x + PREVIEW,
				y + PREVIEW,
				graphics.scissorStack.peek());
		if (state != null) {
			graphics.guiRenderState.addPicturesInPictureState(state);
			state.submitDebugOutline(graphics);
		}
	}

	private void drawEntries(Font font, int x, int y, int w, GuiGraphicsExtractor graphics) {
		if (!inputs.isEmpty()) {
			drawRow(font, graphics, inputs, x, y, w);
			y += SLOT;
		}
		drawArrow(graphics, x + w / 2, y + 1);
		y += ARROW_HEIGHT;
		drawRow(font, graphics, outputs, x, y, w);
	}

	private void drawRow(Font font, GuiGraphicsExtractor graphics, List<Entry> entries, int x, int y, int w) {
		int startX = x + (w - entries.size() * SLOT) / 2;
		for (int i = 0; i < entries.size(); i++) {
			drawEntry(font, graphics, entries.get(i), startX + i * SLOT, y);
		}
	}

	private void drawEntry(Font font, GuiGraphicsExtractor graphics, Entry entry, int x, int y) {
		double rate = entry.rate();
		int ex = x + PAD;
		int ey = y + PAD;
		switch (entry) {
			case Entry.Item item -> drawItem(graphics, item, ex, ey);
			case Entry.Fluid fluid -> {
				drawFluid(graphics, fluid, ex, ey);
				rate = FluidAmounts.toMilliBuckets(rate);
			}
		}
		drawAmount(graphics, font, ex, ey, formatRate(rate));
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

	private static String formatRate(double rate) {
		if (rate == Math.rint(rate)) {
			return Long.toString((long) rate);
		}
		return String.format(Locale.ROOT, "%.1f", rate);
	}
}
