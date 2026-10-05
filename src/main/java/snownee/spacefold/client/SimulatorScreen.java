package snownee.spacefold.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import snownee.spacefold.dim.SimulationManager;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.menu.SimulatorMenu;

public class SimulatorScreen extends Screen implements MenuAccess<SimulatorMenu> {
	private static final int BUTTON_WIDTH = 120;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int TITLE_COLOR = 0xFFFFFFFF;

	private static final Map<Integer, SimulationResult> PENDING_RESULTS = new HashMap<>();

	private final SimulatorMenu menu;
	private Button teleportButton;
	private Button simulateButton;
	private @Nullable SimulationResult lastResult;
	private int buttonsY;

	public SimulatorScreen(SimulatorMenu menu, Inventory inventory, Component title) {
		super(title);
		this.menu = menu;
	}

	public static void acceptResult(int containerId, @Nullable SimulationResult result) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof SimulatorScreen screen && screen.menu.containerId == containerId) {
			screen.lastResult = result;
			screen.updateButtons();
			return;
		}
		PENDING_RESULTS.put(containerId, result);
	}

	@Override
	protected void init() {
		int x = (this.width - BUTTON_WIDTH) / 2;
		int y = this.height / 2 - (BUTTON_HEIGHT * 2 + GAP) / 2;
		buttonsY = y;
		teleportButton = addRenderableWidget(Button.builder(
						Component.translatable("gui.spacefold.teleport"),
						button -> press(SimulatorMenu.BUTTON_TELEPORT))
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		simulateButton = addRenderableWidget(Button.builder(
						Component.translatable("gui.spacefold.simulate"),
						button -> press(SimulatorMenu.BUTTON_SIMULATE))
				.bounds(x, y + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		lastResult = PENDING_RESULTS.remove(menu.containerId);
		updateButtons();
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int titleWidth = this.font.width(this.title);
		graphics.text(this.font, this.title, (this.width - titleWidth) / 2, buttonsY - 16, TITLE_COLOR, true);
	}

	private void updateButtons() {
		SimulationManager.RoomStatus status = menu.roomStatus();
		teleportButton.active = status != SimulationManager.RoomStatus.RUNNING;
		if (menu.roomIndex() < 0) {
			simulateButton.active = false;
			simulateButton.setTooltip(null);
			return;
		}
		boolean ready = status == SimulationManager.RoomStatus.READY;
		simulateButton.active = ready;
		if (!ready) {
			Tooltip reason = Tooltip.create(reason(status, menu.cooldownSeconds()));
			simulateButton.setTooltip(reason);
			if (!teleportButton.active) {
				teleportButton.setTooltip(reason);
			}
			return;
		}
		if (lastResult == null) {
			simulateButton.setTooltip(null);
			return;
		}
		MutableComponent message = Component.translatable("gui.spacefold.last_result");
		SimulationTooltip tooltip = null;
		if (lastResult.failed()) {
			message.append(Component.literal("\n"))
					.append(Component.translatable(lastResult.failure().orElse("tip.spacefold.simulation_failed")));
		} else {
			tooltip = SimulationTooltip.of(lastResult);
		}
		simulateButton.setTooltip(Tooltip.create(message, Optional.ofNullable(tooltip), null));
	}

	private static Component reason(SimulationManager.RoomStatus status, int cooldownSeconds) {
		if (status == SimulationManager.RoomStatus.RUNNING) {
			return Component.translatable("tip.spacefold.simulation_running");
		}
		return Component.translatable("tip.spacefold.simulation_cooldown", cooldownSeconds);
	}

	private void press(int buttonId) {
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
	}

	@Override
	public SimulatorMenu getMenu() {
		return this.menu;
	}

	@Override
	public void onClose() {
		this.minecraft.player.closeContainer();
		super.onClose();
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
