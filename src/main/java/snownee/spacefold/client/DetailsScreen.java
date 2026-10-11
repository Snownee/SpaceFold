package snownee.spacefold.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.kiwi.loader.Platform;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.client.room.RadialGraphLayout;
import snownee.spacefold.client.room.RoomSkyRenderState;
import snownee.spacefold.client.room.VoxelRoomRenderState;
import snownee.spacefold.compat.jade.JadeCompat;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.CRepeaterConfigPacket;
import snownee.spacefold.network.SViewDetailsPacket.Entry;

public class DetailsScreen extends Screen {
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
	private static final long ANIMATION_MS = 350L;
	private static final long FOV_ANIMATION_MS = 3000L;
	/**
	 * UI 元素相对天空延后出现的比例（0~1）：天空先淡入，面板/文字/图标随后再进（退场反之，先退）。
	 */
	private static final float UI_DELAY = 0.35F;
	private static final int SLIDE_PIXELS = 10;
	private static final float PREVIEW_PITCH = 25.0F;
	private static final float MIN_ZOOM = 0.25F;
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
	private int left;
	private int top;
	private int rows;
	private final int configSlot;
	private final ItemStack configIcon;
	private int graphSnapshotId = Integer.MIN_VALUE;
	private RadialGraphLayout.@Nullable Node hoveredNode;

	public DetailsScreen(List<Entry> entries, boolean showProgress, int snapshotId) {
		this(entries, showProgress, snapshotId, -1, ItemStack.EMPTY);
	}

	public DetailsScreen(List<Entry> entries, boolean showProgress, int snapshotId, int configSlot, ItemStack configIcon) {
		super(Component.translatable("gui.spacefold.details"));
		this.entries = entries;
		this.showProgress = showProgress;
		this.snapshotId = snapshotId;
		this.configSlot = configSlot;
		this.configIcon = configIcon;
	}

	@Override
	protected void init() {
		tabNavigationBar = TabNavigationBar.builder(tabManager, width).addTabs(spaceTab, materialsTab).build();
		addWidget(tabNavigationBar);
		tabNavigationBar.updateWidth(width);
		tabNavigationBar.selectTab(lastTabIndex, false);
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
		rows = Math.max(1, (entries.size() + COLUMNS - 1) / COLUMNS);
		int contentTop = contentTop();
		left = (width - COLUMNS * CELL_WIDTH) / 2;
		top = contentTop + Math.max(PADDING + 12, (height - contentTop - rows * CELL_HEIGHT) / 2);
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
		if (isSpaceTab()) {
			updateFocus();
			updateCamera();
			updateBackgroundRotation();
			renderSpace(graphics, mouseX, mouseY);
		} else {
			renderMaterials(graphics, mouseX, mouseY);
		}
		renderTabBar(graphics, mouseX, mouseY);
		renderConfigButton(graphics, mouseX, mouseY);
		renderDebugOverlay(graphics);
	}

	private void renderDebugOverlay(GuiGraphicsExtractor graphics) {
		if (!SpaceFoldClientConfig.debugGraph) {
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
			graphics.blitSprite(
					RenderPipelines.GUI_TEXTURED,
					TAB_SPRITES.get(selected, highlighted),
					button.getX(),
					button.getY(),
					button.getWidth(),
					button.getHeight(),
					color);
			Component label = button.getMessage();
			int labelY = button.getY() + (selected ? 8 : 9);
			graphics.centeredText(font, label, button.getX() + button.getWidth() / 2, labelY, color);
			if (selected) {
				int textWidth = Math.min(font.width(label), button.getWidth() - 4);
				int textLeft = button.getX() + (button.getWidth() - textWidth) / 2;
				int underlineY = button.getY() + button.getHeight() - 2;
				graphics.fill(textLeft, underlineY, textLeft + textWidth, underlineY + 1, color);
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
		if (!configIcon.isEmpty()) {
			graphics.item(configIcon, x + 2, y + 2);
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
		float linksAlpha = p * 0.85F * (1.0F - focusAmount);
		if (linksAlpha > 0.001F) {
			for (RadialGraphLayout.Node node : graphLayout.nodes()) {
				RadialGraphLayout.Node parent = node.parent();
				if (parent != null) {
					drawLink(
							graphics,
							displayX(parent),
							displayY(parent),
							displayX(node),
							displayY(node),
							tierColor(node.tier()),
							linksAlpha);
				}
			}
		}
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			if (node.snapshotId() >= 0 && node.tier() > 0) {
				graphics.centeredText(
						font,
						Component.literal("T" + node.tier()),
						Math.round(displayX(node)),
						Math.round(displayY(node) - nodeSize / 2.0F - 11.0F),
						withAlpha(TEXT_COLOR, p * (1.0F - focusAmount)));
			}
		}
		float backgroundAlpha = p * (1.0F - focusAmount);
		if (backgroundAlpha > 0.001F) {
			for (RadialGraphLayout.Node node : graphLayout.nodes()) {
				renderNodeBackground(
						graphics,
						node.parent() == null ? ROOT_SPRITE : NODE_SPRITE,
						displayX(node),
						displayY(node),
						nodeSize,
						backgroundAlpha);
			}
		}
		float half = nodeSize / 2.0F;
		hoveredNode = null;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			float x = displayX(node);
			float y = displayY(node);
			float nodeAlpha = node == focusedNode ? p : p * (1.0F - focusAmount);
			if (nodeAlpha > 0.001F) {
				renderNode(graphics, node, x, y, nodeSize, nodeAlpha);
			}
			if (mouseX >= x - half && mouseX < x + half && mouseY >= y - half && mouseY < y + half) {
				hoveredNode = node;
			}
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
		int contentWidth = Math.max(1, Math.round(size));
		int contentHeight = Math.max(1, Math.round(size));
		int x = Math.round(cx) - contentWidth / 2 - NODE_BACKGROUND_MARGIN;
		int y = Math.round(cy) - contentHeight / 2 - NODE_BACKGROUND_MARGIN;
		int width = contentWidth + NODE_BACKGROUND_MARGIN * 2;
		int height = contentHeight + NODE_BACKGROUND_MARGIN * 2;
		int color = withAlpha(0xFFFFFFFF, alpha);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getBackgroundSprite(sprite), x, y, width, height, color);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, getFrameSprite(sprite), x, y, width, height, color);
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
		if (snapshotId == graphSnapshotId) {
			return;
		}
		graphSnapshotId = snapshotId;
		List<SimulationResult> leaves = snapshot.topRepeaterResults(Objects.requireNonNull(minecraft.level).registryAccess());
		graphLayout.reset(snapshotId, leaves);
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
		focusedNode = null;
		focusAmount = 0.0F;
	}

	private void renderNode(GuiGraphicsExtractor graphics, RadialGraphLayout.Node node, float x, float y, float size, float alpha) {
		int id = node.snapshotId();
		RoomSnapshot snapshot = id >= 0 ? PreviewCache.getSnapshot(id) : null;
		if (snapshot != null) {
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
		int half = Math.max(1, Math.round(size / 2.0F));
		int x0 = Math.round(cx) - half;
		int y0 = Math.round(cy) - half;
		VoxelRoomRenderState state = VoxelRoomRenderState.of(
				snapshot,
				id,
				sessionYaws.containsKey(id) ? sessionYaws.get(id) : PreviewCache.getYaw(id),
				PREVIEW_PITCH,
				x0,
				y0,
				x0 + half * 2,
				y0 + half * 2,
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

	private void renderMaterials(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		float p = uiProgress();
		int dy = contentOffsetY();
		graphics.fill(
				left - PADDING,
				top - PADDING + dy,
				left + COLUMNS * CELL_WIDTH + PADDING,
				top + rows * CELL_HEIGHT + PADDING + dy,
				withAlpha(PANEL_COLOR, p));
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			int x = left + i % COLUMNS * CELL_WIDTH;
			int y = top + i / COLUMNS * CELL_HEIGHT + dy;
			renderEntry(graphics, entry, x, y, mouseX, mouseY, p);
		}
	}

	private void renderEntry(GuiGraphicsExtractor graphics, Entry entry, int x, int y, int mouseX, int mouseY, float p) {
		ItemStack stack = entry.item().toStack();
		// 物品图标无法逐帧调透明度，改用它自身的 pose 做缩放淡入（与面板 alpha 同步，不依赖绘制顺序）。
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + ICON_SIZE / 2.0F, y + ICON_SIZE / 2.0F);
		graphics.pose().scale(p, p);
		graphics.pose().translate(-(x + ICON_SIZE / 2.0F), -(y + ICON_SIZE / 2.0F));
		graphics.item(stack, x, y);
		graphics.pose().popMatrix();
		graphics.text(font, stack.getHoverName(), x + ICON_SIZE + 4, y, withAlpha(TEXT_COLOR, p), false);
		if (!showProgress) {
			graphics.text(
					font,
					Component.translatable("gui.spacefold.material_quantity", entry.total()),
					x + ICON_SIZE + 4,
					y + 9,
					withAlpha(MUTED_COLOR, p),
					false);
			if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
			return;
		}
		int percent = entry.total() <= 0 ? 100 : (int) Math.round(100.0 * entry.provided() / entry.total());
		Component progress = Component.translatable("gui.spacefold.material_progress", entry.provided(), entry.total(), percent);
		graphics.text(font, progress, x + ICON_SIZE + 4, y + 9, withAlpha(MUTED_COLOR, p), false);
		int barX = x + ICON_SIZE + 4;
		int barY = y + 18;
		int barWidth = CELL_WIDTH - ICON_SIZE - 12;
		graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, withAlpha(BAR_BACKGROUND, p));
		int filled = entry.total() <= 0 ? barWidth : (int) Math.round(barWidth * (double) entry.provided() / entry.total());
		if (filled > 0) {
			graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, withAlpha(BAR_COLOR, p));
		}
		if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
			graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (showConfig() && event.button() == 0 && isOverConfig(event.x(), event.y())) {
			KPacketSender.sendToServer(new CRepeaterConfigPacket(configSlot));
			return true;
		}
		if (isMaterialsTab() && (event.button() == 0 || event.button() == 1)) {
			int dy = contentOffsetY();
			for (int i = 0; i < entries.size(); i++) {
				int x = left + i % COLUMNS * CELL_WIDTH;
				int y = top + i / COLUMNS * CELL_HEIGHT + dy;
				if (event.x() >= x && event.x() < x + ICON_SIZE && event.y() >= y && event.y() < y + ICON_SIZE) {
					if (Platform.isModLoaded("jade")) {
						JadeCompat.lookupRecipes(entries.get(i).item().toStack(), event.button() == 1);
					}
					return true;
				}
			}
		}
		if (isSpaceTab() && event.button() == 0 && event.y() >= contentTop()) {
			if (isFocusMode()) {
				if (focusedNode != null && focusedNode.snapshotId() >= 0) {
					yawDraggingId = focusedNode.snapshotId();
					return true;
				}
			} else if (minecraft.hasControlDown()) {
				RadialGraphLayout.Node node = nodeAt(event.x(), event.y());
				if (node != null) {
					nodeDragging = node;
					graphLayout.setNodeFixed(node, true);
					return true;
				}
			} else {
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

	private RadialGraphLayout.@Nullable Node nodeAt(double screenX, double screenY) {
		float half = GRAPH_NODE_SIZE * zoom / 2.0F;
		RadialGraphLayout.Node best = null;
		double bestDist = Double.MAX_VALUE;
		for (RadialGraphLayout.Node node : graphLayout.nodes()) {
			if (node.parent() == null) {
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

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isSpaceTab() && scrollY != 0.0) {
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
		if (SpaceFoldClientConfig.debugGraph && hoveredNode != null) {
			if (event.key() == InputConstants.KEY_N) {
				graphLayout.addChild(hoveredNode, randomItem());
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

	private static ItemStack randomItem() {
		List<Item> items = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
		return new ItemStack(items.get((int) (Math.random() * items.size())));
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

	private int contentOffsetY() {
		return Math.round((1.0F - uiProgress()) * SLIDE_PIXELS);
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
