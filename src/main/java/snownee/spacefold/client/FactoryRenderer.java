package snownee.spacefold.client;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.util.ClientProxy;

public class FactoryRenderer implements BlockEntityRenderer<FactoryBlockEntity, FactoryRenderState> {
	private static final float SMALL_DECAY_TICKS = 1f;
	private static final float LARGE_DECAY_TICKS = 8f;
	private static final float[][] SHAKE_FREQUENCIES = {
			{ 13.17f, 7.53f, 21.37f },
			{ 11.29f, 5.91f, 17.11f },
			{ 12.73f, 8.31f, 19.79f },
	};

	private final ItemModelResolver itemRenderer;
	private final EntityRenderDispatcher entityRenderer;

	public FactoryRenderer(BlockEntityRendererProvider.Context context) {
		itemRenderer = context.itemModelResolver();
		entityRenderer = context.entityRenderer();
	}

	@Override
	public FactoryRenderState createRenderState() {
		return new FactoryRenderState();
	}

	private static float wave(float t, float seed, float[] frequencies) {
		return Mth.sin(t * frequencies[0] + seed) * 0.55f
				+ Mth.sin(t * frequencies[1] + seed * 1.7f) * 0.3f
				+ Mth.sin(t * frequencies[2] + seed * 2.3f) * 0.15f;
	}

	private static void addShake(FactoryRenderState state, float age, float amplitude, float seed) {
		if (amplitude <= 0) {
			return;
		}
		state.shakeX += amplitude * wave(age, seed, SHAKE_FREQUENCIES[0]);
		state.shakeY += amplitude * wave(age, seed + 1.3f, SHAKE_FREQUENCIES[1]) * 0.5f;
		state.shakeZ += amplitude * wave(age, seed + 2.7f, SHAKE_FREQUENCIES[2]);
	}

	@Override
	public void extractRenderState(
			FactoryBlockEntity blockEntity,
			FactoryRenderState state,
			float partialTicks,
			Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		state.item.entityType = EntityType.ITEM;
		ItemStack item = blockEntity.getItem(FactoryBlockEntity.TEMPLATE_SLOT);
		if (item.isEmpty()) {
			return;
		}
		Level level = blockEntity.getLevel();
		if (level == null) {
			return;
		}
		BlockPos above = blockEntity.getBlockPos().above();
		state.item.ageInTicks = Minecraft.getInstance().clientTickCount + partialTicks;
		state.item.count = ItemClusterRenderState.getRenderedAmount(item.getCount());
		state.item.seed = ItemClusterRenderState.getSeedForItemStack(item);
		state.item.setData(ClientProxy.NO_BOB, Unit.INSTANCE);
		itemRenderer.updateForTopItem(state.item.item, item, ItemDisplayContext.GROUND, null, null, state.item.seed);
		state.item.lightCoords = LightCoordsUtil.pack(
				level.getBrightness(LightLayer.BLOCK, above),
				level.getBrightness(LightLayer.SKY, above));
		long gameTime = level.getGameTime();
		float seed = blockEntity.getBlockPos().hashCode() * 0.013f;
		state.shakeX = 0;
		state.shakeY = 0;
		state.shakeZ = 0;
		boolean large = false;
		long shakeTime = blockEntity.getShakeTime();
		if (shakeTime >= 0) {
			float age = gameTime - shakeTime + partialTicks;
			if (age >= 0 && age < LARGE_DECAY_TICKS) {
				large = true;
				addShake(state, age, (1 - age / LARGE_DECAY_TICKS) * (float) SpaceFoldConfig.factoryShakeLargeAmplitude, seed);
			}
		}
		if (!large) {
			long progressTime = blockEntity.getProgressTime();
			if (progressTime >= 0) {
				float age = gameTime - progressTime + partialTicks;
				if (age >= 0 && age < SMALL_DECAY_TICKS) {
					addShake(state, age, (1 - age / SMALL_DECAY_TICKS) * (float) SpaceFoldConfig.factoryShakeAmplitude, seed);
				}
			}
		}
	}

	@Override
	public void submit(
			FactoryRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera) {
		if (state.item.item.isEmpty()) {
			return;
		}
		entityRenderer.submit(
				state.item,
				camera,
				0.5 + state.shakeX,
				1.25 + state.shakeY,
				0.5 + state.shakeZ,
				poseStack,
				submitNodeCollector);
	}
}
