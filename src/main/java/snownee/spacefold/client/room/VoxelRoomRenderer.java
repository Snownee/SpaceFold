package snownee.spacefold.client.room;

import java.util.List;
import java.util.Objects;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.mixin.client.PictureInPictureRendererAccessor;

public class VoxelRoomRenderer extends PictureInPictureRenderer<VoxelRoomRenderState> {
	private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
	private static final CardinalLighting FLAT_LIGHT = new CardinalLighting(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
	private static final int LIGHT = 0xF000F0;

	private static final Int2ObjectMap<BlockEntity> BE_CACHE = new Int2ObjectOpenHashMap<>();
	private static int beCacheSnapshotId = Integer.MIN_VALUE;
	private static int beCacheContentVersion = Integer.MIN_VALUE;
	private static @Nullable Level beCacheLevel;

	public static void clearCache() {
		BE_CACHE.clear();
		beCacheSnapshotId = Integer.MIN_VALUE;
		beCacheContentVersion = Integer.MIN_VALUE;
		beCacheLevel = null;
	}

	private @Nullable AltModelBlockRenderer blockRenderer;
	private @Nullable ContentKey cachedContentKey;

	public VoxelRoomRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<VoxelRoomRenderState> getRenderStateClass() {
		return VoxelRoomRenderState.class;
	}

	@Override
	protected boolean textureIsReadyToBlit(VoxelRoomRenderState state) {
		return contentKey(state).equals(cachedContentKey);
	}

	@Override
	protected void blitTexture(VoxelRoomRenderState state, GuiRenderState guiRenderState) {
		int a = Mth.clamp(Math.round(state.alpha() * 255.0F), 0, 255);
		int color = (a << 24) | (a << 16) | (a << 8) | a;
		GpuTextureView textureView = Objects.requireNonNull(((PictureInPictureRendererAccessor) this).spacefold$textureView());
		guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
				RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
				TextureSetup.singleTexture(textureView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
				state.pose(),
				state.x0(),
				state.y0(),
				state.x1(),
				state.y1(),
				0.0F,
				1.0F,
				1.0F,
				0.0F,
				color,
				state.scissorArea()));
	}

	@Override
	protected void renderToTexture(VoxelRoomRenderState state, PoseStack poseStack) {
		cachedContentKey = contentKey(state);
		Minecraft minecraft = Minecraft.getInstance();
		if (blockRenderer == null) {
			blockRenderer = Renderer.get().altModelBlockRenderer(true, true, minecraft.getBlockColors());
		}
		minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch()));
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
		float center = state.size() / 2.0F;
		poseStack.translate(-center, -center, -center);

		Level level = minecraft.level;
		BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
		Int2ObjectMap<@Nullable BlockEntity> blockEntities = level == null ? Int2ObjectMaps.emptyMap() : blockEntities(state, level, dispatcher);
		CameraRenderState camera = new CameraRenderState();
		camera.pos = Vec3.ZERO;
		camera.initialized = true;

		RoomBlockGetter getter = new RoomBlockGetter(state.blocks(), state.size());
		BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
		FeatureRenderDispatcher featureRenderDispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
		SubmitNodeCollector collector = featureRenderDispatcher.getSubmitNodeStorage();
		CapturingRenderState specialState = new CapturingRenderState();
		QuadEmitter emitter = Renderer.get().quadEmitter(quad -> {
			VertexConsumer consumer = this.bufferSource.getBuffer(renderType(quad.chunkLayer()));
			quad.buffer(OverlayTexture.NO_OVERLAY, poseStack.last(), consumer);
		});
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int size = state.size();
		boolean anySubmitted = false;
		BlockModelLighter.enableCaching();
		try {
			for (int y = 0; y < size; y++) {
				for (int z = 0; z < size; z++) {
					for (int x = 0; x < size; x++) {
						int index = x + y * size + z * size * size;
						BlockState blockState = state.blocks().get(index);
						if (blockState.isAir()) {
							continue;
						}
						pos.set(x, y, z);
						boolean modelShape = blockState.getRenderShape() == RenderShape.MODEL;
						if (modelShape) {
							blockRenderer.tesselateBlock(
									emitter,
									x,
									y,
									z,
									getter,
									pos,
									blockState,
									modelSet.get(blockState),
									blockState.getSeed(pos));
						}
						BlockEntity blockEntity = blockEntities.get(index);
						if (blockEntity != null) {
							anySubmitted |= submitBlockEntity(dispatcher, blockEntity, poseStack, collector, camera, x, y, z);
							continue;
						}
						if (!modelShape) {
							minecraft.blockModelResolver.update(specialState, blockState, DISPLAY_CONTEXT);
							if (specialState.special) {
								poseStack.pushPose();
								poseStack.translate(x, y, z);
								specialState.submit(poseStack, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
								poseStack.popPose();
								anySubmitted = true;
							}
						}
					}
				}
			}
		} finally {
			BlockModelLighter.clearCache();
		}
		if (anySubmitted) {
			featureRenderDispatcher.renderAllFeatures();
		}
	}

	private static Int2ObjectMap<@Nullable BlockEntity> blockEntities(
			VoxelRoomRenderState state,
			Level level,
			BlockEntityRenderDispatcher dispatcher) {
		if (state.snapshotId() == beCacheSnapshotId && state.version() == beCacheContentVersion && level == beCacheLevel) {
			return BE_CACHE;
		}
		BE_CACHE.clear();
		int size = state.size();
		for (Int2ObjectMap.Entry<CompoundTag> entry : state.blockEntities().int2ObjectEntrySet()) {
			int index = entry.getIntKey();
			int x = index % size;
			int y = (index / size) % size;
			int z = index / (size * size);
			BlockEntity blockEntity = BlockEntity.loadStatic(
					new BlockPos(x, y, z),
					state.blocks().get(index),
					entry.getValue(),
					level.registryAccess());
			if (blockEntity == null) {
				continue;
			}
			blockEntity.setLevel(level);
			if (dispatcher.getRenderer(blockEntity) == null) {
				continue;
			}
			BE_CACHE.put(index, blockEntity);
		}
		beCacheSnapshotId = state.snapshotId();
		beCacheContentVersion = state.version();
		beCacheLevel = level;
		return BE_CACHE;
	}

	private static boolean submitBlockEntity(
			BlockEntityRenderDispatcher dispatcher,
			BlockEntity blockEntity,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			CameraRenderState camera,
			int x,
			int y,
			int z) {
		dispatcher.prepare(Vec3.atCenterOf(blockEntity.getBlockPos()));
		BlockEntityRenderState renderState = dispatcher.tryExtractRenderState(blockEntity, 0.0F, null);
		if (renderState == null) {
			return false;
		}
		renderState.lightCoords = LIGHT;
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		dispatcher.submit(renderState, poseStack, collector, camera);
		poseStack.popPose();
		return true;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "voxel_room";
	}

	private static RenderType renderType(ChunkSectionLayer layer) {
		return layer == ChunkSectionLayer.TRANSLUCENT ? Sheets.translucentBlockSheet() : Sheets.cutoutBlockSheet();
	}

	private static ContentKey contentKey(VoxelRoomRenderState state) {
		return new ContentKey(state.snapshotId(), state.version(), state.size(), state.yaw(), state.pitch(), state.scale());
	}

	private record ContentKey(int snapshotId, int contentVersion, int size, float yaw, float pitch, float scale) {
	}

	private static final class CapturingRenderState extends BlockModelRenderState {
		private boolean special;

		@Override
		public void clear() {
			super.clear();
			special = false;
		}

		@Override
		public <T> void setupSpecialModel(SpecialModelRenderer<T> renderer, Matrix4fc transformation) {
			special = true;
			super.setupSpecialModel(renderer, transformation);
		}
	}

	private static final class RoomBlockGetter implements BlockAndTintGetter {
		private final List<BlockState> blocks;
		private final int size;

		private RoomBlockGetter(List<BlockState> blocks, int size) {
			this.blocks = blocks;
			this.size = size;
		}

		@Override
		public CardinalLighting cardinalLighting() {
			return FLAT_LIGHT;
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public int getBrightness(LightLayer layer, BlockPos pos) {
			return 15;
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			if (color == BiomeColors.GRASS_COLOR_RESOLVER) {
				return GrassColor.getDefaultColor();
			}
			if (color == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
				return FoliageColor.FOLIAGE_DEFAULT;
			}
			if (color == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
				return DryFoliageColor.FOLIAGE_DRY_DEFAULT;
			}
			return -1;
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) {
				return Blocks.AIR.defaultBlockState();
			}
			return blocks.get(x + y * size + z * size * size);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return Fluids.EMPTY.defaultFluidState();
		}

		@Override
		public int getHeight() {
			return size;
		}

		@Override
		public int getMinY() {
			return 0;
		}
	}
}
