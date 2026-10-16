package snownee.spacefold.client;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.client.room.VoxelRoomRenderState;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.dim.SimulationManager;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.menu.SimulatorMenu;

public class SimulatorScreen extends Screen implements MenuAccess<SimulatorMenu> {
	private static final int BUTTON_WIDTH = 120;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int PREVIEW = 128;
	private static final float PREVIEW_PITCH = 25.0F;

	private static final Int2ObjectMap<@Nullable SimulationResult> PENDING_RESULTS = new Int2ObjectOpenHashMap<>();

	private final SimulatorMenu menu;
	private @Nullable Button teleportButton;
	private @Nullable Button simulateButton;
	private @Nullable Button pinButton;
	private @Nullable SimulationResult lastResult;
	private int buttonsY;
	private float previewYaw = PreviewCache.DEFAULT_YAW;
	private int previewYawSnapshotId = Integer.MIN_VALUE;
	private boolean rotating;

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
						_ -> press(SpaceFoldClientConfig.roomWindows ?
								SimulatorMenu.BUTTON_TELEPORT :
								SimulatorMenu.BUTTON_TELEPORT_NO_WINDOWS))
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		simulateButton = addRenderableWidget(Button.builder(
						Component.translatable("gui.spacefold.simulate"),
						_ -> press(SimulatorMenu.BUTTON_SIMULATE))
				.bounds(x, y + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		pinButton = addRenderableWidget(Button.builder(
						Component.translatable("gui.spacefold.pin_rotation"),
						_ -> pinRotation())
				.bounds(0, 0, 56, 16)
				.build());
		pinButton.visible = false;
		if (lastResult == null) {
			lastResult = PENDING_RESULTS.remove(menu.containerId);
		}
		updateButtons();
		updatePinButton();
	}

	@Override
	public void tick() {
		super.tick();
		updateButtons();
		updatePinButton();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		drawPreview(graphics);
		int titleWidth = this.font.width(this.title);
		graphics.text(this.font, this.title, (this.width - titleWidth) / 2, buttonsY - 16, TITLE_COLOR, true);
	}

	private void drawPreview(GuiGraphicsExtractor graphics) {
		if (lastResult == null || !lastResult.hasSnapshot()) {
			return;
		}
		RoomSnapshot snapshot = PreviewCache.getSnapshot(lastResult.snapshotId());
		if (snapshot == null) {
			return;
		}
		int px = previewX();
		int py = previewY();
		VoxelRoomRenderState state = VoxelRoomRenderState.of(
				snapshot,
				lastResult.snapshotId(),
				previewYaw,
				PREVIEW_PITCH,
				px,
				py,
				px + PREVIEW,
				py + PREVIEW,
				graphics.scissorStack.peek());
		if (state != null) {
			graphics.guiRenderState.addPicturesInPictureState(state);
			state.submitDebugOutline(graphics);
		}
	}

	private int previewX() {
		int px = this.width / 2 + BUTTON_WIDTH / 2 + 16;
		if (px + PREVIEW > this.width - 4) {
			px = this.width - PREVIEW - 4;
		}
		return Math.max(px, 4);
	}

	private int previewY() {
		int py = (this.height - PREVIEW) / 2;
		if (py + PREVIEW > this.height - 4) {
			py = this.height - PREVIEW - 4;
		}
		return Math.max(py, 4);
	}

	private boolean isOnPreview(double mouseX, double mouseY) {
		int px = previewX();
		int py = previewY();
		return mouseX >= px && mouseX < px + PREVIEW && mouseY >= py && mouseY < py + PREVIEW;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && lastResult != null && lastResult.hasSnapshot() && isOnPreview(event.x(), event.y())) {
			rotating = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (rotating) {
			previewYaw += (float) dragX * 1.5F;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (rotating) {
			rotating = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void updatePinButton() {
		if (pinButton == null) {
			return;
		}
		int snapshotId = lastResult == null ? Integer.MIN_VALUE : lastResult.snapshotId();
		if (snapshotId != previewYawSnapshotId) {
			previewYawSnapshotId = snapshotId;
			previewYaw = snapshotId >= 0 ? PreviewCache.getYaw(snapshotId) : PreviewCache.DEFAULT_YAW;
		}
		boolean changed = snapshotId >= 0 && Math.abs(Mth.wrapDegrees(previewYaw - PreviewCache.getYaw(snapshotId))) > 0.5F;
		pinButton.visible = changed;
		pinButton.active = changed;
		if (changed) {
			pinButton.setX(previewX() + (PREVIEW - pinButton.getWidth()) / 2);
			pinButton.setY(previewY() + PREVIEW + 4);
		}
	}

	private void pinRotation() {
		if (lastResult == null || !lastResult.hasSnapshot()) {
			return;
		}
		PreviewCache.setYaw(lastResult.snapshotId(), previewYaw);
		updatePinButton();
	}

	private void updateButtons() {
		SimulationManager.RoomStatus status = menu.roomStatus();
		Objects.requireNonNull(teleportButton).active = status != SimulationManager.RoomStatus.RUNNING;
		if (menu.roomIndex() < 0) {
			Objects.requireNonNull(simulateButton).active = false;
			simulateButton.setTooltip(null);
			return;
		}
		boolean ready = status == SimulationManager.RoomStatus.READY;
		Objects.requireNonNull(simulateButton).active = ready;
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
		SimulationTooltip tooltip = lastResult.failed() ? null : SimulationTooltip.of(lastResult, true, false);
		if (tooltip == null && !lastResult.failed()) {
			simulateButton.setTooltip(null);
			return;
		}
		MutableComponent message = Component.translatable("gui.spacefold.last_result");
		if (lastResult.failed()) {
			message.append(Component.literal("\n"))
					.append(Component.translatable(lastResult.failure().orElse("tip.spacefold.simulation_failed")));
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
		Objects.requireNonNull(this.minecraft.gameMode).handleInventoryButtonClick(this.menu.containerId, buttonId);
	}

	@Override
	public SimulatorMenu getMenu() {
		return this.menu;
	}

	@Override
	public void onClose() {
		Objects.requireNonNull(this.minecraft.player).closeContainer();
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
