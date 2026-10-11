package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import org.joml.Matrix3x2f;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import snownee.jade.overlay.FloatBlitRenderState;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.client.room.RadialGraphLayout;
import snownee.spacefold.client.room.RoomSkyRenderState;
import snownee.spacefold.client.room.VoxelRoomRenderState;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.CRepeaterConfigPacket;
import snownee.spacefold.network.SViewDetailsPacket.Entry;
import snownee.spacefold.util.SpaceFoldUtil;

public class DetailsScreen extends Screen {
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final long ANIMATION_MS = 350L;
	private static final long FOV_ANIMATION_MS = 3000L;
	/**
	 * UI 元素相对天空延后出现的比例（0~1）：天空先淡入，面板/文字/图标随后再进（退场反之，先退）。
	 */
	private static final float UI_DELAY = 0.35F;
	private static final float PREVIEW_PITCH = 25.0F;
	private static final float MIN_ZOOM = 0.2F;
	private static final float MAX_ZOOM = 4.0F;
	private static final float INITIAL_ZOOM = 3.0F;
	private static final double ZOOM_SPEED = 0.15;
	private static final double ZOOM_SMOOTHING_MS = 90.0;
	private static final int CONFIG_SIZE = 20;
	private static final int CONFIG_MARGIN = 6;
	private static final int CONFIG_COLOR = 0xFF1E1E1E;
	private static final int CONFIG_HOVER_COLOR = 0xFF4A4A4A;
	private static final float GRAPH_NODE_SIZE = 64.0F;
	private static final float FOCUS_ZOOM = 2.25F;
	private static final long FOCUS_FADE_MS = 250L;
	private static final float PULL_SCREEN_FRACTION = 0.55F;
	private static final float FOCUS_CAPTURE_SCREEN_FRACTION = 0.35F;
	private static final float ZOOM_NUDGE = 0.35F;
	private static final double CAM_SPRING = 90.0;
	private static final double CAM_DRAG_EASE = 12.0;
	private static final double CAM_FRICTION = 6.0;
	private static final Identifier NODE_SPRITE = SpaceFold.id("node");
	private static final Identifier ROOT_SPRITE = SpaceFold.id("root");
	private static final int NODE_BACKGROUND_MARGIN = 12;
	private static final float FOCUS_YAW_SPEED = 1.5F;
	private static final float PAN_BACKGROUND_ROTATION = 0.0005F;
	private static final ClientTooltipPositioner CENTERED_TOOLTIP_POSITIONER =
			(screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) ->
					new Vector2i(mouseX - tooltipWidth / 2, mouseY);
	private static final WidgetSprites TAB_SPRITES = new WidgetSprites(
			Identifier.withDefaultNamespace("widget/tab_selected"),
			Identifier.withDefaultNamespace("widget/tab"),
			Identifier.withDefaultNamespace("widget/tab_selected_highlighted"),
			Identifier.withDefaultNamespace("widget/tab_highlighted"));
	private static int lastTabIndex;

	private final List<Entry> entries;
	private final boolean showProgress;
	private final int snapshotId;
	private final long openedAt = Util.getMillis();
	private final Tab spaceTab = new SimpleTab(Component.translatable("gui.spacefold.details.space"));
	private final Tab materialsTab = new SimpleTab(Component.translatable("gui.spacefold.details.materials"));
	private final List<Tab> tabs = List.of(spaceTab, materialsTab);
	private final TabManager tabManager = new TabManager(
			this::addRenderableWidget, this::removeWidget, tab -> lastTabIndex = tabs.indexOf(tab), tab -> {
	});
	private final RadialGraphLayout graphLayout = new RadialGraphLayout();
	private @Nullable TabNavigationBar tabNavigationBar;
	private long closingAt;
	private boolean closing;
	private float closeStartProgress = 1.0F;
	private float closeStartFov = 1.0F;
	private float zoom = INITIAL_ZOOM;
	private float targetZoom = INITIAL_ZOOM;
	private double zoomVelocity;
	private boolean focusZoomActive;
	private long zoomUpdatedAt = Util.getMillis();
	private double camX;
	private double camY;
	private double camVx;
	private double camVy;
	private double camTargetX;
	private double camTargetY;
	private boolean hasCamTarget;
	private double clampedX;
	private double clampedY;
	private final Int2FloatMap sessionYaws = new Int2FloatOpenHashMap();
	private int yawDraggingId = -1;
	private RadialGraphLayout.@Nullable Node nodeDragging;
	private float backgroundRotation;
	private double lastCamX;
	private long camUpdatedAt = Util.getMillis();
	private double dragAccumX;
	private double dragAccumY;
	private boolean dragging;
	private RadialGraphLayout.@Nullable Node focusedNode;
	private float focusAmount;
	private long focusUpdatedAt = Util.getMillis();
	private final int configSlot;
	private final ItemStack viewedStack;
	private int graphSnapshotId = Integer.MIN_VALUE;
	private int graphVersion = Integer.MIN_VALUE;
	private RadialGraphLayout.@Nullable Node hoveredNode;
	private MaterialList materialList;
	private boolean materialsVisible;

	public DetailsScreen(List<Entry> entries, boolean showProgress, int snapshotId, int configSlot, ItemStack viewedStack) {
		super(Component.translatable("gui.spacefold.details"));
		this.entries = entries;
		this.showProgress = showProgress;
		this.snapshotId = snapshotId;
		this.configSlot = configSlot;
		this.viewedStack = viewedStack;
	}

	@Override
	protected void init() {
		materialList = new MaterialList(minecraft, entries, showProgress);
		tabNavigationBar = TabNavigationBar.builder(tabManager, width).addTabs(spaceTab, materialsTab).build();
		addWidget(tabNavigationBar);
		addWidget(materialList);
		tabNavigationBar.updateWidth(width);
		if (snapshotId == SimulationResult.NO_SNAPSHOT) {
			tabNavigationBar.setTabActiveState(tabs.indexOf(spaceTab), false);
			tabNavigationBar.selectTab(tabs.indexOf(materialsTab), false);
		} else {
			tabNavigationBar.selectTab(lastTabIndex, false);
		}
		materialsVisible = isMaterialsTab();
		materialList.visible = materialsVisible;
		layoutContent();
	}

	@Override
	public void repositionElements() {
		if (tabNavigationBar != null) {
			tabNavigationBar.updateWidth(width);
		}
		layoutContent();
	}

	private void layoutContent() {
		if (materialList != null) {
			materialList.updateLayout(width / 2, contentTop(), height);
		}
	}

	private int contentTop() {
		return tabNavigationBar == null ? 24 : tabNavigationBar.getRectangle().bottom();
	}

	private boolean isSpaceTab() {
		return tabManager.getCurrentTab() == spaceTab;
	}

	private boolean isMaterialsTab() {
		return tabManager.getCurrentTab() == materialsTab;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.guiRenderState.addPicturesInPictureState(new RoomSkyRenderState(
				0,
				0,
				width,
				height,
				1.0F,
				progress(),
				fovProgress(),
				backgroundRotation,
				null));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		updateZoom();
		hoveredNode = null;
		boolean materials = isMaterialsTab();
		if (materialList != null) {
			if (materialsVisible != materials) {
				materialsVisible = materials;
				if (!materials && getFocused() == materialList) {
					clearFocus();
				}
			}
			materialList.visible = materials;
			materialList.setAlpha(uiProgress());
		}
		if (materials) {
			renderMaterials(graphics, mouseX, mouseY, partialTick);
		} else {
			updateFocus();
			updateCamera();
			updateBackgroundRotation();
			renderSpace(graphics, mouseX, mouseY);
		}
		renderTabBar(graphics, mouseX, mouseY);
		renderConfigButton(graphics, mouseX, mouseY);
		renderDebugOverlay(graphics);
	}

	private void renderDebugOverlay(GuiGraphicsExtractor graphics) {
		if (!SpaceFoldClientConfig.debugGraph || !isSpaceTab()) {
			return;
		}
		float p = uiProgress();
		if (p <= 0.001F) {
			return;
		}
		Component text = Component.literal(String.format(Locale.ENGLISH, "zoom: %.3f", zoom));
		graphics.text(font, text, CONFIG_MARGIN, height - CONFIG_MARGIN - font.lineHeight, withAlpha(0xFFFFDD00, p), true);
	}

	private void renderTabBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		float p = uiProgress();
		if (p <= 0.001F || tabNavigationBar == null) {
			return;
		}
		List<TabButton> buttons = new ArrayList<>();
		for (GuiEventListener child : tabNavigationBar.children()) {
			if (child instanceof TabButton button) {
				buttons.add(button);
			}
		}
		if (buttons.isEmpty()) {
			return;
		}
		int color = withAlpha(TEXT_COLOR, p);
		TabButton first = buttons.get(0);
		TabButton last = buttons.get(buttons.size() - 1);
		int separatorY = first.getY() + first.getHeight() - 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, 0, separatorY, 0.0F, 0.0F, first.getX(), 2, 32, 2, color);
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				Screen.HEADER_SEPARATOR,
				last.getRight(),
				separatorY,
				0.0F,
				0.0F,
				width,
				2,
				32,
				2,
				color);
		for (TabButton button : buttons) {
			boolean selected = button.isSelected();
			boolean highlighted = button.isFocused() ||
					(mouseX >= button.getX() && mouseX < button.getRight() && mouseY >= button.getY() && mouseY < button.getBottom());
			int buttonColor = button.isActive() ? color : withAlpha(MUTED_COLOR, p);
			graphics.blitSprite(
					RenderPipelines.GUI_TEXTURED,
					TAB_SPRITES.get(selected, highlighted),
					button.getX(),
					button.getY(),
					button.getWidth(),
					button.getHeight(),
					buttonColor);
			Component label = button.getMessage();
			int labelY = button.getY() + (selected ? 8 : 9);
			graphics.centeredText(font, label, button.getX() + button.getWidth() / 2, labelY, buttonColor);
			if (selected) {
				int textWidth = Math.min(font.width(label), button.getWidth() - 4);
				int textLeft = button.getX() + (button.getWidth() - textWidth) / 2;
				int underlineY = button.getY() + button.getHeight() - 2;
				graphics.fill(textLeft, underlineY, textLeft + textWidth, underlineY + 1, buttonColor);
			}
			if (!button.isActive() && highlighted) {
				graphics.setTooltipForNextFrame(font, Component.translatable("gui.spacefold.details.no_snapshot"), mouseX, mouseY);
			}
		}
	}

	private boolean showConfig() {
		return configSlot >= 0;
	}

	private int configX() {
		return width - CONFIG_MARGIN - CONFIG_SIZE;
	}

	private int configY() {
		return height - CONFIG_MARGIN - CONFIG_SIZE;
	}

	private boolean isOverConfig(double mouseX, double mouseY) {
		int x = configX();
		int y = configY();
		return mouseX >= x && mouseX < x + CONFIG_SIZE && mouseY >= y && mouseY < y + CONFIG_SIZE;
	}

	private void renderConfigButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!showConfig()) {
			return;
		}
		float p = uiProgress();
		int x = configX();
		int y = configY();
		boolean hover = isOverConfig(mouseX, mouseY);
		graphics.fill(x, y, x + CONFIG_SIZE, y + CONFIG_SIZE, withAlpha(hover ? CONFIG_HOVER_COLOR : CONFIG_COLOR, p));
		if (!viewedStack.isEmpty()) {
			graphics.item(viewedStack, x + 2, y + 2);
		}
		if (hover) {
			graphics.setTooltipForNextFrame(font, Component.translatable("gui.spacefold.repeater_config"), mouseX, mouseY);
		}
	}

	private void updateZoom() {
		long now = Util.getMillis();
		long elapsed = now - zoomUpdatedAt;
		zoomUpdatedAt = now;
		if (elapsed <= 0) {
			return;
		}
		if (focusZoomActive && isFocusMode() && focusedNode != null) {
			double dt = Mth.clamp(elapsed / 1000.0, 0.0, 0.05);
			double c = 2.0 * Math.sqrt(CAM_SPRING);
			zoomVelocity += (CAM_SPRING * (targetZoom - zoom) - c * zoomVelocity) * dt;
			zoom += (float) (zoomVelocity * dt);
			if (Math.abs(targetZoom - zoom) < 0.001F && Math.abs(zoomVelocity) < 0.02) {
				zoom = targetZoom;
				zoomVelocity = 0.0;
				focusZoomActive = false;
			}
			return;
		}
		focusZoomActive = false;
		zoomVelocity = 0.0;
		if (Math.abs(targetZoom - zoom) < 0.001F) {
			zoom = targetZoom;
			return;
		}
		float t = 1.0F - (float) Math.exp(-elapsed / ZOOM_SMOOTHING_MS);
		zoom += (targetZoom - zoom) * t;
	}

	private boolean isFocusMode() {
		return targetZoom >= FOCUS_ZOOM;
	}

	private void updateFocus() {
		boolean focus = isFocusMode();
		if (focus && (focusedNode == null || !graphLayout.nodes().contains(focusedNode))) {
			focusedNode = nearestNode(camX, camY);
		}
		long now = Util.getMillis();
		long elapsed = now - focusUpdatedAt;
		focusUpdatedAt = now;
		if (elapsed > 0) {
			float target = focus && focusedNode != null ? 1.0F : 0.0F;
			float t = 1.0F - (float) Math.exp(-elapsed / (double) FOCUS_FADE_MS);
			focusAmount += (target - focusAmount) * t;
			if (Math.abs(target - focusAmount) < 0.001F) {
				focusAmount = target;
			}
		}
		if (!focus && focusAmount <= 0.0F) {
			focusedNode = null;
		}
	}

	private void updateCamera() {
		if (graphLayout.nodes().isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		double dt = Mth.clamp((now - camUpdatedAt) / 1000.0, 0.0, 0.05);
		camUpdatedAt = now;
		if (dt <= 0.0) {
			return;
		}
		double c = 2.0 * Math.sqrt(CAM_SPRING);
		if (isFocusMode() && focusedNode != null) {
			hasCamTarget = false;
			camVx += (CAM_SPRING * (focusedNode.x() - camX) - c * camVx) * dt;
			camVy += (CAM_SPRING * (focusedNode.y() - camY) - c * camVy) * dt;
			camX += camVx * dt;
			camY += camVy * dt;
			return;
		}
		double prevX = camX;
		double prevY = camY;
		if (dragging) {
			hasCamTarget = false;
			camX += dragAccumX;
			camY += dragAccumY;
			dragAccumX = 0.0;
			dragAccumY = 0.0;
			clampPoint(camX, camY);
			double e = Math.min(1.0, CAM_DRAG_EASE * dt);
			camX += (clampedX - camX) * e;
			camY += (clampedY - camY) * e;
			camTargetX = camX;
			camTargetY = camY;
			camVx = (camX - prevX) / dt;
			camVy = (camY - prevY) / dt;
			return;
		}
		if (hasCamTarget) {
			clampPoint(camTargetX, camTargetY);
			camTargetX = clampedX;
			camTargetY = clampedY;
			camVx += (CAM_SPRING * (camTargetX - camX) - c * camVx) * dt;
			camVy += (CAM_SPRING * (camTargetY - camY) - c * camVy) * dt;
			camX += camVx * dt;
			camY += camVy * dt;
			if (Math.abs(camTargetX - camX) < 0.5 && Math.abs(camTargetY - camY) < 0.5) {
				hasCamTarget = false;
			}
			return;
		}
		double decay = Math.exp(-CAM_FRICTION * dt);
		camVx *= decay;
		camVy *= decay;
		camX += camVx * dt;
		camY += camVy * dt;
		clampPoint(camX, camY);
		double e = Math.min(1.0, CAM_DRAG_EASE * dt);
		camX += (clampedX - camX) * e;
		camY += (clampedY - camY) * e;
	}

	/**
	 * 合法区域＝所有节点外接矩形四周各外扩 pullLayout() 的余量；把点 (x,y) 夹进该矩形，写入 clampedX/clampedY。
	 */
	private void clampPoint(double x, double y) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			minX = Math.min(minX, node.x());
			minY = Math.min(minY, node.y());
			maxX = Math.max(maxX, node.x());
			maxY = Math.max(maxY, node.y());
		}
		double margin = pullLayout();
		clampedX = Mth.clamp(x, minX - margin, maxX + margin);
		clampedY = Mth.clamp(y, minY - margin, maxY + margin);
	}

	private RadialGraphLayout.@Nullable Node nearestNode(double x, double y) {
		RadialGraphLayout.Node best = null;
		double bestDist = Double.MAX_VALUE;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			double dx = node.x() - x;
			double dy = node.y() - y;
			double dist = dx * dx + dy * dy;
			if (dist < bestDist) {
				bestDist = dist;
				best = node;
			}
		}
		return best;
	}

	private RadialGraphLayout.@Nullable Node nearestNodeToScreen(double screenX, double screenY) {
		RadialGraphLayout.Node best = null;
		double bestDist = Double.MAX_VALUE;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			double dx = displayX(node) - screenX;
			double dy = displayY(node) - screenY;
			double dist = dx * dx + dy * dy;
			if (dist < bestDist) {
				bestDist = dist;
				best = node;
			}
		}
		return best;
	}

	private void nudgeCameraTowards(double screenX, double screenY) {
		RadialGraphLayout.Node target = nearestNodeToScreen(screenX, screenY);
		if (target == null) {
			return;
		}
		if (!hasCamTarget) {
			camTargetX = camX;
			camTargetY = camY;
			hasCamTarget = true;
		}
		camTargetX += (target.x() - camTargetX) * ZOOM_NUDGE;
		camTargetY += (target.y() - camTargetY) * ZOOM_NUDGE;
	}

	private void centerOnRoot() {
		RadialGraphLayout.Node root = graphLayout.root();
		if (root == null) {
			return;
		}
		hasCamTarget = true;
		camTargetX = root.x();
		camTargetY = root.y();
	}

	private double halfMin() {
		return Math.min(width, height - contentTop()) / 2.0;
	}

	private double pullLayout() {
		return PULL_SCREEN_FRACTION * halfMin();
	}

	private boolean canCaptureFocus() {
		RadialGraphLayout.Node nn = nearestNode(camX, camY);
		if (nn == null) {
			return false;
		}
		double dx = camX - nn.x();
		double dy = camY - nn.y();
		double dist = Math.sqrt(dx * dx + dy * dy);
		return dist * zoom <= FOCUS_CAPTURE_SCREEN_FRACTION * halfMin();
	}

	private void renderSpace(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		RoomSnapshot snapshot = PreviewCache.getSnapshot(snapshotId);
		if (snapshot == null) {
			return;
		}
		float p = uiProgress();
		ensureGraph(snapshot);
		graphLayout.simulate();
		float nodeSize = GRAPH_NODE_SIZE * zoom;
		float half = nodeSize / 2.0F;
		float cullRadius = half + NODE_BACKGROUND_MARGIN + 2.0F;
		float linksAlpha = p * 0.85F * (1.0F - focusAmount);
		if (linksAlpha > 0.001F) {
			for (RadialGraphLayout.Node node : graphLayout.nodes()) {
				RadialGraphLayout.Node parent = node.parent();
				if (parent != null) {
					float x0 = displayX(parent);
					float y0 = displayY(parent);
					float x1 = displayX(node);
					float y1 = displayY(node);
					if (isSegmentVisible(x0, y0, x1, y1)) {
						drawLink(graphics, x0, y0, x1, y1, tierColor(node.tier()), linksAlpha);
					}
				}
			}
		}
//		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
//			if (node.snapshotId() >= 0 && node.tier() > 0) {
//				graphics.centeredText(
//						font,
//						Component.literal("T" + node.tier()),
//						Math.round(displayX(node)),
//						Math.round(displayY(node) - nodeSize / 2.0F - 11.0F),
//						withAlpha(TEXT_COLOR, p * (1.0F - focusAmount)));
//			}
//		}
		float backgroundAlpha = p * (1.0F - focusAmount);
		if (backgroundAlpha > 0.001F) {
			for (RadialGraphLayout.Node node : graphLayout.nodes()) {
				float x = displayX(node);
				float y = displayY(node);
				if (isCircleVisible(x, y, cullRadius)) {
					renderNodeBackground(
							graphics,
							node.parent() == null ? ROOT_SPRITE : NODE_SPRITE,
							x,
							y,
							nodeSize,
							backgroundAlpha);
				}
			}
		}
		hoveredNode = null;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			float x = displayX(node);
			float y = displayY(node);
			if (!isCircleVisible(x, y, cullRadius)) {
				continue;
			}
			float nodeAlpha = node == focusedNode ? p : p * (1.0F - focusAmount);
			if (nodeAlpha > 0.001F) {
				renderNode(graphics, node, x, y, nodeSize, nodeAlpha);
			}
			if (mouseX >= x - half && mouseX < x + half && mouseY >= y - half && mouseY < y + half) {
				hoveredNode = node;
			}
		}
		tooltip:
		if (!isFocusMode() && hoveredNode != null && !closing) {
			ItemStack icon = hoveredNode.icon();
			if (icon.isEmpty()) {
				break tooltip;
			}
			List<FormattedCharSequence> lines = List.of();
			Optional<TooltipComponent> component = Optional.empty();
			SimulationResult result = icon.get(CoreModule.SIMULATION_RESULT.get());
			if (result == null || icon.getCustomName() != null) {
				lines = List.of(icon.getHoverName().getVisualOrderText());
			}
			if (result != null) {
				component = Optional.ofNullable(SimulationTooltip.of(result, true, false));
			}
			if (lines.isEmpty() && component.isEmpty()) {
				break tooltip;
			}
			int cx = Math.round(displayX(hoveredNode));
			int cy = Math.round(displayY(hoveredNode) + half + 6.0F);
			graphics.setTooltipForNextFrame(
					font,
					lines,
					component,
					CENTERED_TOOLTIP_POSITIONER,
					cx,
					cy,
					false,
					null);
		}
		if (SpaceFoldClientConfig.debugGraph && hoveredNode != null) {
			int outlineHalf = Math.round(half);
			graphics.outline(
					Math.round(displayX(hoveredNode)) - outlineHalf,
					Math.round(displayY(hoveredNode)) - outlineHalf,
					outlineHalf * 2,
					outlineHalf * 2,
					withAlpha(0xFFFFDD00, p));
		}
		if (SpaceFoldClientConfig.debugGraph) {
			int cx = Math.round(graphCenterX());
			int cy = Math.round(graphCenterY());
			int color = withAlpha(0xFFFFDD00, p);
			graphics.fill(cx - 6, cy, cx + 7, cy + 1, color);
			graphics.fill(cx, cy - 6, cx + 1, cy + 7, color);
		}
	}

	/**
	 * 参考 TooltipRenderUtil#extractTooltipBackground，用九宫格 tooltip 贴图给结点做背景。
	 */
	private static void renderNodeBackground(
			GuiGraphicsExtractor graphics,
			Identifier sprite,
			float cx,
			float cy,
			float size,
			float alpha) {
		float half = size / 2.0F;
		float x = cx - half - NODE_BACKGROUND_MARGIN;
		float y = cy - half - NODE_BACKGROUND_MARGIN;
		float width = size + NODE_BACKGROUND_MARGIN * 2.0F;
		float height = size + NODE_BACKGROUND_MARGIN * 2.0F;
		int color = withAlpha(0xFFFFFFFF, alpha);
		blitSprite(graphics, RenderPipelines.GUI_TEXTURED, getBackgroundSprite(sprite), x, y, width, height, color);
		blitSprite(graphics, RenderPipelines.GUI_TEXTURED, getFrameSprite(sprite), x, y, width, height, color);
	}

	private static void blitSprite(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			Identifier spriteId,
			float x,
			float y,
			float width,
			float height,
			int color) {
		if (width <= 0.0F || height <= 0.0F) {
			return;
		}
		TextureAtlasSprite sprite = graphics.guiSprites.getSprite(spriteId);
		GuiSpriteScaling scaling = sprite.contents()
				.getAdditionalMetadata(GuiMetadataSection.TYPE)
				.map(GuiMetadataSection::scaling)
				.orElse(GuiSpriteScaling.DEFAULT);
		if (scaling instanceof GuiSpriteScaling.NineSlice nineSlice) {
			blitNineSlicedSprite(graphics, pipeline, sprite, nineSlice, x, y, width, height, color);
		} else {
			blitSprite(
					graphics,
					pipeline,
					sprite,
					x,
					y,
					x + width,
					y + height,
					sprite.getU0(),
					sprite.getV0(),
					sprite.getU1(),
					sprite.getV1(),
					color);
		}
	}

	private static void blitNineSlicedSprite(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			TextureAtlasSprite sprite,
			GuiSpriteScaling.NineSlice nineSlice,
			float x,
			float y,
			float width,
			float height,
			int color) {
		GuiSpriteScaling.NineSlice.Border border = nineSlice.border();
		float texWidth = nineSlice.width();
		float texHeight = nineSlice.height();
		float left = Math.min(border.left(), width / 2.0F);
		float right = Math.min(border.right(), width / 2.0F);
		float top = Math.min(border.top(), height / 2.0F);
		float bottom = Math.min(border.bottom(), height / 2.0F);
		if (width == texWidth && height == texHeight) {
			blitSprite(
					graphics,
					pipeline,
					sprite,
					x,
					y,
					x + width,
					y + height,
					sprite.getU0(),
					sprite.getV0(),
					sprite.getU1(),
					sprite.getV1(),
					color);
			return;
		}
		float uLeft = left / texWidth;
		float uRight = (texWidth - right) / texWidth;
		float vTop = top / texHeight;
		float vBottom = (texHeight - bottom) / texHeight;
		float middleX = x + left;
		float middleY = y + top;
		float rightX = x + width - right;
		float bottomY = y + height - bottom;
		float edgeRight = x + width;
		float edgeBottom = y + height;
		blitSprite(
				graphics,
				pipeline,
				sprite,
				x,
				y,
				middleX,
				middleY,
				sprite.getU(0.0F),
				sprite.getV(0.0F),
				sprite.getU(uLeft),
				sprite.getV(vTop),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				rightX,
				y,
				edgeRight,
				middleY,
				sprite.getU(uRight),
				sprite.getV(0.0F),
				sprite.getU(1.0F),
				sprite.getV(vTop),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				x,
				bottomY,
				middleX,
				edgeBottom,
				sprite.getU(0.0F),
				sprite.getV(vBottom),
				sprite.getU(uLeft),
				sprite.getV(1.0F),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				rightX,
				bottomY,
				edgeRight,
				edgeBottom,
				sprite.getU(uRight),
				sprite.getV(vBottom),
				sprite.getU(1.0F),
				sprite.getV(1.0F),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				middleX,
				y,
				rightX,
				middleY,
				sprite.getU(uLeft),
				sprite.getV(0.0F),
				sprite.getU(uRight),
				sprite.getV(vTop),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				middleX,
				bottomY,
				rightX,
				edgeBottom,
				sprite.getU(uLeft),
				sprite.getV(vBottom),
				sprite.getU(uRight),
				sprite.getV(1.0F),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				x,
				middleY,
				middleX,
				bottomY,
				sprite.getU(0.0F),
				sprite.getV(vTop),
				sprite.getU(uLeft),
				sprite.getV(vBottom),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				rightX,
				middleY,
				edgeRight,
				bottomY,
				sprite.getU(uRight),
				sprite.getV(vTop),
				sprite.getU(1.0F),
				sprite.getV(vBottom),
				color);
		blitSprite(
				graphics,
				pipeline,
				sprite,
				middleX,
				middleY,
				rightX,
				bottomY,
				sprite.getU(uLeft),
				sprite.getV(vTop),
				sprite.getU(uRight),
				sprite.getV(vBottom),
				color);
	}

	private static void blitSprite(
			GuiGraphicsExtractor graphics,
			RenderPipeline pipeline,
			TextureAtlasSprite sprite,
			float x0,
			float y0,
			float x1,
			float y1,
			float u0,
			float v0,
			float u1,
			float v1,
			int color) {
		if (x0 == x1 || y0 == y1) {
			return;
		}
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
		graphics.guiRenderState.addGuiElement(new FloatBlitRenderState(
				pipeline,
				TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
				new Matrix3x2f(graphics.pose()),
				x0,
				y0,
				x1,
				y1,
				u0,
				u1,
				v0,
				v1,
				color,
				graphics.scissorStack.peek()));
	}

	private static Identifier getBackgroundSprite(Identifier style) {
		return style.withPath(path -> "tooltip/" + path + "_background");
	}

	private static Identifier getFrameSprite(Identifier style) {
		return style.withPath(path -> "tooltip/" + path + "_frame");
	}

	private float displayX(RadialGraphLayout.Node node) {
		return (float) (graphCenterX() + (node.x() - camX) * zoom);
	}

	private float displayY(RadialGraphLayout.Node node) {
		return (float) (graphCenterY() + (node.y() - camY) * zoom);
	}

	private void ensureGraph(RoomSnapshot snapshot) {
		int version = PreviewCache.version();
		boolean rootChanged = snapshotId != graphSnapshotId;
		if (!rootChanged && version == graphVersion) {
			return;
		}
		graphSnapshotId = snapshotId;
		graphVersion = version;
		IntFunction<List<ItemStack>> childrenLookup = id -> {
			RoomSnapshot child = id == snapshotId ? snapshot : PreviewCache.getSnapshot(id);
			return child == null ? null : child.topRepeaterResults(Objects.requireNonNull(minecraft.level).registryAccess());
		};
		if (!rootChanged) {
			graphLayout.expandPending(childrenLookup);
			return;
		}
		SimulationResult rootResult = viewedStack.get(CoreModule.SIMULATION_RESULT.get());
		int rootTier = rootResult == null ? 0 : rootResult.tier();
		graphLayout.reset(snapshotId, rootTier, viewedStack, childrenLookup);
		camX = 0.0;
		camY = 0.0;
		camTargetX = 0.0;
		camTargetY = 0.0;
		camVx = 0.0;
		camVy = 0.0;
		hasCamTarget = false;
		zoom = INITIAL_ZOOM;
		targetZoom = INITIAL_ZOOM;
		dragging = false;
		yawDraggingId = -1;
		nodeDragging = null;
		backgroundRotation = 0.0F;
		lastCamX = 0.0;
		sessionYaws.clear();
		focusAmount = isFocusMode() ? 1.0F : 0.0F;
		focusedNode = isFocusMode() ? graphLayout.root() : null;
	}

	private void renderNode(GuiGraphicsExtractor graphics, RadialGraphLayout.Node node, float x, float y, float size, float alpha) {
		int id = node.snapshotId();
		RoomSnapshot snapshot = id >= 0 ? PreviewCache.getSnapshot(id) : null;
		if (snapshot != null && !minecraft.hasShiftDown() /*&& zoom > 0.3F*/) {
			renderRoomNode(graphics, snapshot, id, x, y, size, alpha);
		} else if (!node.icon().isEmpty()) {
			renderItemNode(graphics, node.icon(), x, y, size, alpha);
		} else {
			drawNode(graphics, x, y, Math.max(3.0F, size * 0.08F), withAlpha(tierColor(node.tier()), alpha));
		}
	}

	private static void renderItemNode(GuiGraphicsExtractor graphics, ItemStack stack, float cx, float cy, float size, float alpha) {
		float scale = size / 16.0F * 0.75F * alpha;
		if (scale <= 0.01F) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(scale, scale);
		graphics.item(stack, -8, -8);
		graphics.pose().popMatrix();
	}

	private void renderRoomNode(GuiGraphicsExtractor graphics, RoomSnapshot snapshot, int id, float cx, float cy, float size, float alpha) {
		float half = size / 2.0F;
		VoxelRoomRenderState state = VoxelRoomRenderState.of(
				snapshot,
				id,
				sessionYaws.containsKey(id) ? sessionYaws.get(id) : PreviewCache.getYaw(id),
				PREVIEW_PITCH,
				cx - half,
				cy - half,
				cx + half,
				cy + half,
				1.0F,
				alpha,
				graphics.scissorStack.peek());
		if (state != null) {
			graphics.guiRenderState.addPicturesInPictureState(state);
		}
	}

	private float graphCenterX() {
		return width / 2.0F;
	}

	private boolean isCircleVisible(float x, float y, float radius) {
		return x + radius >= 0.0F && x - radius <= width && y + radius >= 0.0F && y - radius <= height;
	}

	private boolean isSegmentVisible(float x0, float y0, float x1, float y1) {
		float minX = Math.min(x0, x1);
		float maxX = Math.max(x0, x1);
		float minY = Math.min(y0, y1);
		float maxY = Math.max(y0, y1);
		return maxX >= 0.0F && minX <= width && maxY >= 0.0F && minY <= height;
	}

	private float graphCenterY() {
		int top = contentTop();
		return top + (height - top) / 2.0F;
	}

	private static int tierColor(int tier) {
		return Mth.hsvToArgb((tier * 0.137F) % 1.0F, 0.7F, 1.0F, 255);
	}

	/**
	 * 连线：外宽内亮的三层叠加做发光效果，核心为加白后的细线。
	 */
	private static void drawLink(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color, float alpha) {
		drawLine(graphics, x0, y0, x1, y1, withAlpha(color, alpha * 0.20F), 3.0F);
		drawLine(graphics, x0, y0, x1, y1, withAlpha(color, alpha * 0.45F), 2.0F);
		drawLine(graphics, x0, y0, x1, y1, withAlpha(lighten(color, 0.5F), alpha), 1.0F);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int color, float thickness) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5F) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x0, y0);
		graphics.pose().rotate((float) Math.atan2(dy, dx));
		graphics.pose().scale(1.0F, thickness);
		graphics.pose().translate(0.0F, -0.5F);
		graphics.fill(0, 0, Math.round(length), 1, color);
		graphics.pose().popMatrix();
	}

	private static int lighten(int color, float amount) {
		int r = Math.round(((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount);
		int g = Math.round(((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount);
		int b = Math.round((color & 0xFF) + (255 - (color & 0xFF)) * amount);
		return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static void drawNode(GuiGraphicsExtractor graphics, float x, float y, float radius, int color) {
		int ir = Math.max(1, Math.round(radius));
		int ix = Math.round(x);
		int iy = Math.round(y);
		for (int dy = -ir; dy <= ir; dy++) {
			int half = (int) Math.sqrt(ir * ir - dy * dy);
			graphics.fill(ix - half, iy + dy, ix + half + 1, iy + dy + 1, color);
		}
	}

	private void renderMaterials(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (materialList != null) {
			materialList.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (showConfig() && event.button() == 0 && isOverConfig(event.x(), event.y())) {
			KPacketSender.sendToServer(new CRepeaterConfigPacket(configSlot));
			return true;
		}
		if (isSpaceTab() && event.button() == 0 && event.y() >= contentTop()) {
			if (isFocusMode()) {
				if (focusedNode != null && focusedNode.snapshotId() >= 0) {
					yawDraggingId = focusedNode.snapshotId();
					return true;
				}
			} else {
				RadialGraphLayout.Node node = nodeAt(event.x(), event.y(), doubleClick);
				if (doubleClick && node != null) {
					enterFocus(node);
					return true;
				}
				if (minecraft.hasControlDown() && node != null) {
					nodeDragging = node;
					graphLayout.setNodeFixed(node, true);
					return true;
				}
				dragging = true;
				dragAccumX = 0.0;
				dragAccumY = 0.0;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (nodeDragging != null) {
			graphLayout.moveNode(nodeDragging, nodeDragging.x() + (float) dragX / zoom, nodeDragging.y() + (float) dragY / zoom);
			return true;
		}
		if (dragging) {
			dragAccumX += -dragX / zoom;
			dragAccumY += -dragY / zoom;
			return true;
		}
		if (yawDraggingId >= 0) {
			float yaw = sessionYaws.getOrDefault(yawDraggingId, PreviewCache.getYaw(yawDraggingId)) + (float) dragX * FOCUS_YAW_SPEED;
			sessionYaws.put(yawDraggingId, yaw);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (nodeDragging != null) {
			graphLayout.setNodeFixed(nodeDragging, false);
			nodeDragging = null;
			return true;
		}
		if (dragging) {
			dragging = false;
			return true;
		}
		if (yawDraggingId >= 0) {
			yawDraggingId = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	/**
	 * 移动模式下相机位移的小幅映射到夜空偏航角，制造背景跟随移动的视差感。
	 */
	private void updateBackgroundRotation() {
		double delta = camX - lastCamX;
		lastCamX = camX;
		if (!isFocusMode()) {
			backgroundRotation += (float) (delta * PAN_BACKGROUND_ROTATION);
		}
	}

	private RadialGraphLayout.@Nullable Node nodeAt(double screenX, double screenY, boolean includeRoot) {
		float half = GRAPH_NODE_SIZE * zoom / 2.0F;
		RadialGraphLayout.Node best = null;
		double bestDist = Double.MAX_VALUE;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			if (!includeRoot && node.parent() == null) {
				continue;
			}
			double dx = displayX(node) - screenX;
			double dy = displayY(node) - screenY;
			if (Math.abs(dx) <= half && Math.abs(dy) <= half) {
				double dist = dx * dx + dy * dy;
				if (dist < bestDist) {
					bestDist = dist;
					best = node;
				}
			}
		}
		return best;
	}

	private void enterFocus(RadialGraphLayout.Node node) {
		focusedNode = node;
		targetZoom = Math.max(targetZoom, FOCUS_ZOOM);
		camVx = 0.0;
		camVy = 0.0;
		zoomVelocity = 0.0;
		focusZoomActive = true;
		hasCamTarget = false;
		dragging = false;
		dragAccumX = 0.0;
		dragAccumY = 0.0;
		nodeDragging = null;
		yawDraggingId = -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isSpaceTab() && scrollY != 0.0) {
			focusZoomActive = false;
			float newTarget = Mth.clamp(targetZoom * (float) Math.exp(scrollY * ZOOM_SPEED), MIN_ZOOM, MAX_ZOOM);
			if (scrollY > 0.0 && targetZoom < FOCUS_ZOOM && newTarget >= FOCUS_ZOOM && !canCaptureFocus()) {
				nudgeCameraTowards(mouseX, mouseY);
				return true;
			}
			targetZoom = newTarget;
			if (scrollY > 0.0 && !isFocusMode()) {
				nudgeCameraTowards(mouseX, mouseY);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isSpaceTab() && event.key() == InputConstants.KEY_SPACE) {
			centerOnRoot();
			return true;
		}
		if (SpaceFoldClientConfig.debugGraph && hoveredNode != null) {
			if (event.key() == InputConstants.KEY_N) {
				graphLayout.addChild(hoveredNode, SpaceFoldUtil.randomItem());
				return true;
			}
			if (event.key() == InputConstants.KEY_D) {
				graphLayout.remove(hoveredNode);
				hoveredNode = null;
				return true;
			}
		}
		if (tabNavigationBar.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
		super.tick();
		if (closing && Util.getMillis() - closingAt >= ANIMATION_MS) {
			super.onClose();
		}
	}

	@Override
	public void onClose() {
		if (closing) {
			return;
		}
		closeStartProgress = progress();
		closeStartFov = fovProgress();
		closing = true;
		closingAt = Util.getMillis();
	}

	private float progress() {
		long from = closing ? closingAt : openedAt;
		float t = Mth.clamp((Util.getMillis() - from) / (float) ANIMATION_MS, 0.0F, 1.0F);
		float eased = easeOutCubic(t);
		return closing ? closeStartProgress * (1.0F - eased) : eased;
	}

	/**
	 * UI 元素用的进度：在天空进度基础上延后 {@link #UI_DELAY} 再出现；退场因进度反向，即先于天空退去。
	 */
	private float uiProgress() {
		float t = Mth.clamp((progress() - UI_DELAY) / (1.0F - UI_DELAY), 0.0F, 1.0F);
		return easeOutCubic(t);
	}

	/**
	 * FOV 独立于其它内容：进场时长更长，且用更强的高次缓出，变化速度越来越慢。
	 */
	private float fovProgress() {
		if (closing) {
			float t = Mth.clamp((Util.getMillis() - closingAt) / (float) ANIMATION_MS, 0.0F, 1.0F);
			return closeStartFov * (1.0F - easeOutCubic(t));
		}
		float t = Mth.clamp((Util.getMillis() - openedAt) / (float) FOV_ANIMATION_MS, 0.0F, 1.0F);
		return easeOutQuint(t);
	}

	private static float easeOutCubic(float t) {
		float inv = 1.0F - t;
		return 1.0F - inv * inv * inv;
	}

	private static float easeOutQuint(float t) {
		float inv = 1.0F - t;
		return 1.0F - inv * inv * inv * inv * inv;
	}

	private static int withAlpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
		return (color & 0x00FFFFFF) | (a << 24);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private record SimpleTab(Component title) implements Tab {
		@Override
		public Component getTabTitle() {
			return title;
		}

		@Override
		public Component getTabExtraNarration() {
			return CommonComponents.EMPTY;
		}

		@Override
		public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
		}

		@Override
		public void doLayout(ScreenRectangle screenRectangle) {
		}
	}
}
