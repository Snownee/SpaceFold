package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import snownee.spacefold.network.SSimulationRatesPacket;
import snownee.spacefold.network.SSimulationRatesPacket.Entry;
import snownee.spacefold.network.SSimulationRatesPacket.Section;
import snownee.spacefold.util.FluidAmounts;

public class SimulationHud implements HudElement {
	private static final long TIMEOUT_MS = 2000L;

	private static volatile @Nullable SSimulationRatesPacket snapshot;
	private static volatile long updatedAt;

	public static void accept(SSimulationRatesPacket packet) {
		snapshot = packet;
		updatedAt = System.currentTimeMillis();
	}

	private static List<Component> lines(SSimulationRatesPacket packet) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.spacefold.rates"));
		lines.add(Component.translatable("gui.spacefold.progress", packet.progress() + "%"));
		addSection(lines, "gui.spacefold.inputs", packet.inputs());
		addSection(lines, "gui.spacefold.outputs", packet.outputs());
		return lines;
	}

	private static void addSection(List<Component> lines, String header, Section section) {
		lines.add(Component.translatable(header));
		for (Entry entry : section.entries()) {
			if (entry.item() != null) {
				lines.add(Component.translatable("gui.spacefold.item_rate", entry.item().toStack().getHoverName(), format(entry.rate())));
			} else if (entry.fluid() != null) {
				lines.add(Component.translatable(
						"gui.spacefold.fluid_rate",
						FluidVariantAttributes.getName(entry.fluid()),
						format(FluidAmounts.toMilliBuckets(entry.rate()))));
			}
		}
		if (section.energy() > 0) {
			lines.add(Component.translatable("gui.spacefold.energy_rate", format(section.energy())));
		}
		if (section.hidden() > 0) {
			lines.add(Component.translatable("gui.spacefold.hidden", section.hidden()));
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		SSimulationRatesPacket packet = snapshot;
		if (packet == null || packet.progress() < 0) {
			return;
		}
		if (System.currentTimeMillis() - updatedAt > TIMEOUT_MS) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		List<Component> lines = lines(packet);
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int count = lines.size();
		int bottom = graphics.guiHeight() / 2 + count * 9 / 3;
		int headerY = bottom - count * 9;
		int left = graphics.guiWidth() - width - 3;
		int right = graphics.guiWidth() - 3 + 2;
		graphics.fill(left - 2, headerY - 9 - 1, right, headerY - 1, minecraft.options.getBackgroundColor(0.4F));
		graphics.fill(left - 2, headerY - 1, right, bottom, minecraft.options.getBackgroundColor(0.3F));
		Component title = lines.get(0);
		graphics.text(font, title, left + width / 2 - font.width(title) / 2, headerY - 9, -1, false);
		for (int i = 1; i < count; i++) {
			Component line = lines.get(i);
			graphics.text(font, line, right - 2 - font.width(line), headerY + (i - 1) * 9, -1, false);
		}
	}
}
