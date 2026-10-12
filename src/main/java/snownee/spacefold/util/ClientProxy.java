package snownee.spacefold.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import snownee.kiwi.loader.ClientPlatform;
import snownee.kiwi.loader.Platform;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.ClientSimulationTooltip;
import snownee.spacefold.client.FacingCtmModel;
import snownee.spacefold.client.FactoryRenderer;
import snownee.spacefold.client.FactoryScreen;
import snownee.spacefold.client.RepeaterScreen;
import snownee.spacefold.client.SimulationHud;
import snownee.spacefold.client.SimulationTooltip;
import snownee.spacefold.client.SimulatorScreen;
import snownee.spacefold.client.ViewDetailsKey;
import snownee.spacefold.client.paint.PaintAtlas;
import snownee.spacefold.client.paint.PaintRoot;
import snownee.spacefold.client.paint.RepeaterPaintSpecialRenderer;
import snownee.spacefold.client.particle.BeamParticle;
import snownee.spacefold.client.particle.BeamParticleGroup;
import snownee.spacefold.client.particle.BeamParticleProvider;
import snownee.spacefold.client.particle.BeamRenderType;
import snownee.spacefold.client.particle.CubeParticle;
import snownee.spacefold.client.particle.CubeParticleGroup;
import snownee.spacefold.client.particle.CubeParticleProvider;
import snownee.spacefold.client.particle.CubeRenderType;
import snownee.spacefold.client.room.RoomSkyRenderer;
import snownee.spacefold.client.room.VoxelRoomRenderer;
import snownee.spacefold.client.sky.FogVolume;
import snownee.spacefold.command.ClientCommand;
import snownee.spacefold.dim.SimulationResult;

public class ClientProxy implements ClientModInitializer {
	public static final RenderStateDataKey<Unit> NO_BOB = RenderStateDataKey.create(() -> "no_bob");

	public static void registerFactoryRenderer() {
		ClientPlatform.registerBlockEntityRenderer(CoreModule.FACTORY_BE.getOrCreate(), FactoryRenderer::new);
	}

	private static void appendSimulationTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result == null) {
			return;
		}
		if (Minecraft.getInstance().hasShiftDown()) {
			addSection(lines, "gui.spacefold.inputs", result.inputs());
			addSection(lines, "gui.spacefold.outputs", result.outputs());
		}
		float progress = ViewDetailsKey.progress();
		int shade = 0xAA + Math.round((0xFF - 0xAA) * progress);
		lines.add(Component.translatable(
						"gui.spacefold.repeater_materials_hint",
						Component.keybind("key.spacefold.view_details"))
				.withColor(shade << 16 | shade << 8 | shade));
	}

	private static void addSection(List<Component> lines, String header, SimulationResult.Rates rates) {
		if (rates.items().isEmpty() && rates.fluids().isEmpty() && rates.energy() <= 0) {
			return;
		}
		lines.add(Component.translatable(header));
		for (Map.Entry<ItemVariant, Double> entry : rates.items().entrySet()) {
			lines.add(Component.translatable(
					"gui.spacefold.item_rate",
					entry.getKey().toStack().getHoverName(),
					format(entry.getValue())));
		}
		for (Map.Entry<FluidVariant, Double> entry : rates.fluids().entrySet()) {
			lines.add(Component.translatable(
					"gui.spacefold.fluid_rate",
					FluidVariantAttributes.getName(entry.getKey()),
					format(FluidAmounts.toMilliBuckets(entry.getValue()))));
		}
		if (rates.energy() > 0) {
			lines.add(Component.translatable("gui.spacefold.energy_rate", format(rates.energy())));
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	@Override
	public void onInitializeClient() {
		if (!Platform.isProduction() && Boolean.getBoolean("spacefold.autoQuit")) {
			ClientLifecycleEvents.CLIENT_STARTED.register(Minecraft::stop);
		}
		ViewDetailsKey.register();
		if (Platform.isModLoaded("athena")) {
			FacingCtmModel.register();
		}
		MenuScreens.register(CoreModule.SIMULATOR_MENU.getOrCreate(), SimulatorScreen::new);
		MenuScreens.register(CoreModule.FACTORY_MENU.getOrCreate(), FactoryScreen::new);
		MenuScreens.register(CoreModule.REPEATER_MENU.getOrCreate(), RepeaterScreen::new);
		ParticleProviderRegistry.getInstance().register(CoreModule.CUBE.getOrCreate(), new CubeParticleProvider());
		ParticleProviderRegistry.getInstance().register(CoreModule.BEAM.getOrCreate(), new BeamParticleProvider());
		ParticleGroupRegistry.register(CubeParticle.RENDER_TYPE, CubeParticleGroup::new);
		ParticleGroupRegistry.register(BeamParticle.RENDER_TYPE, BeamParticleGroup::new);
		CubeRenderType.bootstrap();
		BeamRenderType.bootstrap();
		FogVolume.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.SCOREBOARD, SpaceFold.id("simulation_rates"), new SimulationHud());
		ItemTooltipCallback.EVENT.register(ClientProxy::appendSimulationTooltip);
		ClientTooltipComponentCallback.EVENT.register(component -> component instanceof SimulationTooltip tooltip
				? new ClientSimulationTooltip(tooltip)
				: null);
		PictureInPictureRendererRegistry.register(context -> new VoxelRoomRenderer(context.bufferSource()));
		PictureInPictureRendererRegistry.register(context -> new RoomSkyRenderer(context.bufferSource()));
		SpecialModelRenderers.ID_MAPPER.put(SpaceFold.id("repeater_paint"), RepeaterPaintSpecialRenderer.Unbaked.MAP_CODEC);
		ModelLoadingPlugin.register(context -> {
			context.modifyBlockModelOnLoad().register((model, ctx) -> {
				if (ctx.state().is(CoreModule.REPEATER.get())) {
					return new PaintRoot(model);
				}
				return model;
			});
			context.modifyItemModelBeforeBake().register((model, ctx) -> {
				if (ctx.itemId().equals(SpaceFold.id("repeater"))) {
					Identifier base = SpaceFold.id("block/repeater");
					ItemModel.Unbaked special = ItemModelUtils.specialModel(base, new RepeaterPaintSpecialRenderer.Unbaked());
					ItemModel.Unbaked painted = ItemModelUtils.composite(model, special);
					return ItemModelUtils.conditional(ItemModelUtils.hasComponent(CoreModule.REPEATER_PAINT.get()), painted, model);
				}
				return model;
			});
		});
		ClientTickEvents.END_CLIENT_TICK.register(_ -> PaintAtlas.get().tick());
		ClientCommandRegistrationCallback.EVENT.register(ClientCommand::register);
	}
}
