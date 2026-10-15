package snownee.spacefold;

import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import snownee.kiwi.AbstractModule;
import snownee.kiwi.BlockObject;
import snownee.kiwi.ItemObject;
import snownee.kiwi.KiwiGO;
import snownee.kiwi.KiwiModule;
import snownee.kiwi.loader.Platform;
import snownee.kiwi.loader.event.InitEvent;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.block.FactoryBlock;
import snownee.spacefold.block.InterfaceBlock;
import snownee.spacefold.block.RepeaterBlock;
import snownee.spacefold.block.SimulatorBlock;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.block.entity.InterfaceBlockEntity;
import snownee.spacefold.block.entity.RepeaterBlockEntity;
import snownee.spacefold.block.entity.SimulatorBlockEntity;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.item.OpenerItem;
import snownee.spacefold.menu.FactoryMenu;
import snownee.spacefold.menu.RepeaterMenu;
import snownee.spacefold.menu.SimulatorMenu;
import snownee.spacefold.paint.FacePaint;
import snownee.spacefold.paint.RepeaterPaints;
import snownee.spacefold.particle.BeamParticleOptions;
import snownee.spacefold.particle.BeamParticleType;
import snownee.spacefold.particle.CubeParticleOptions;
import snownee.spacefold.particle.CubeParticleType;
import snownee.spacefold.recipe.SimulationSlotDisplay;
import snownee.spacefold.util.ClientProxy;
import team.reborn.energy.api.EnergyStorage;

@KiwiModule(modId = SpaceFold.ID)
@KiwiModule.Category("spacefold:main")
public final class CoreModule extends AbstractModule {
	public static final TagKey<Block> ROOM_INVALID = blockTag("room_invalid");
	public static final TagKey<Block> ROOM_IGNORED = blockTag("room_ignored");
	public static final TagKey<Block> MIRROR_NBT_IGNORED = blockTag("mirror_nbt_ignored");
	public static final TagKey<Block> CHAMBER_WINDOWS = blockTag("chamber_windows");

	public static final BlockObject<ChamberWallBlock> CHAMBER_WALL = block(ChamberWallBlock::new);
	public static final BlockObject<ChamberWallBlock> CHAMBER_WINDOW = block(ChamberWallBlock::new);
	public static final BlockObject<InterfaceBlock> INTERFACE = block(InterfaceBlock::new);
	public static final BlockObject<InterfaceBlock> INTERFACE_WINDOW = block(InterfaceBlock::new);
	public static final BlockObject<SimulatorBlock> SIMULATOR = block(SimulatorBlock::new, () -> Blocks.CRYING_OBSIDIAN);
	public static final BlockObject<RepeaterBlock> REPEATER = block(RepeaterBlock::new, () -> Blocks.IRON_BLOCK);
	public static final BlockObject<FactoryBlock> FACTORY = block(FactoryBlock::new, () -> Blocks.CRYING_OBSIDIAN);
	@KiwiModule.Name("factory")
	public static final KiwiGO<PoiType> FACTORY_POI = go(() -> new PoiType(
			Set.copyOf(FACTORY.getOrCreate()
					.getStateDefinition()
					.getPossibleStates()), 0, 1));
	public static final ItemObject<OpenerItem> OPENER = item(OpenerItem::new);
	@KiwiModule.Name("simulation")
	public static final KiwiGO<TicketType> SIMULATION_TICKET = go(
			() -> new TicketType(
					TicketType.NO_TIMEOUT,
					TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE),
			Registries.TICKET_TYPE);
	@KiwiModule.Name("simulation")
	public static final KiwiGO<MenuType<SimulatorMenu>> SIMULATOR_MENU = go(
			() -> new MenuType<>(
					SimulatorMenu::new,
					FeatureFlags.VANILLA_SET), Registries.MENU);
	@KiwiModule.Name("room_side")
	public static final KiwiGO<DataComponentType<Boolean>> ROOM_SIDE = go(
			() -> DataComponentType.<Boolean>builder()
					.persistent(Codec.BOOL)
					.build(), Registries.DATA_COMPONENT_TYPE);
	@KiwiModule.Name("factory")
	public static final KiwiGO<MenuType<FactoryMenu>> FACTORY_MENU = go(
			() -> new MenuType<>(FactoryMenu::new, FeatureFlags.VANILLA_SET),
			Registries.MENU);
	@KiwiModule.Name("simulation_result")
	public static final KiwiGO<DataComponentType<SimulationResult>> SIMULATION_RESULT = go(
			() -> DataComponentType.<SimulationResult>builder()
					.persistent(SimulationResult.CODEC)
					.cacheEncoding()
					.build(),
			Registries.DATA_COMPONENT_TYPE);
	@KiwiModule.Name("repeater_config")
	public static final KiwiGO<MenuType<RepeaterMenu>> REPEATER_MENU = go(
			() -> new MenuType<>(RepeaterMenu::new, FeatureFlags.VANILLA_SET),
			Registries.MENU);
	@KiwiModule.Name("repeater_paint")
	public static final KiwiGO<DataComponentType<Map<Direction, FacePaint>>> REPEATER_PAINT = go(
			() -> DataComponentType.<Map<Direction, FacePaint>>builder()
					.persistent(RepeaterPaints.CODEC)
					.cacheEncoding()
					.build(),
			Registries.DATA_COMPONENT_TYPE);
	@KiwiModule.Name("cube")
	public static final KiwiGO<ParticleType<CubeParticleOptions>> CUBE = go(CubeParticleType::new, Registries.PARTICLE_TYPE);
	@KiwiModule.Name("beam")
	public static final KiwiGO<ParticleType<BeamParticleOptions>> BEAM = go(BeamParticleType::new, Registries.PARTICLE_TYPE);
	@KiwiModule.Name("main")
	public static final KiwiGO<CreativeModeTab> TAB = go(() -> itemCategory(
			SpaceFold.id("main"),
			() -> new ItemStack(SIMULATOR)).build());
	@KiwiModule.Name("simulation")
	public static final KiwiGO<SlotDisplay.Type<SimulationSlotDisplay>> SIMULATION_DISPLAY = go(
			() -> SimulationSlotDisplay.TYPE,
			Registries.SLOT_DISPLAY);

	@Override
	protected void addEntries() {
		if (Platform.isPhysicalClient()) {
			ClientProxy.registerFactoryRenderer();
		}
	}

	@Override
	protected void init(InitEvent event) {
		event.enqueueWork(() -> {
			if (!Platform.isModLoaded("neoforge")) {
				PoiTypes.registerBlockStates(FACTORY_POI.holderOrThrow(), FACTORY_POI.get().matchingStates());
			}
			ItemStorage.SIDED.registerForBlockEntity(SimulatorBlockEntity::getItemStorage, SIMULATOR_BE.getOrCreate());
			FluidStorage.SIDED.registerForBlockEntity(SimulatorBlockEntity::getFluidStorage, SIMULATOR_BE.getOrCreate());
			EnergyStorage.SIDED.registerForBlockEntity(SimulatorBlockEntity::getEnergyStorage, SIMULATOR_BE.getOrCreate());
			ItemStorage.SIDED.registerForBlockEntity((blockEntity, side) -> blockEntity.itemSink(), INTERFACE_BE.getOrCreate());
			FluidStorage.SIDED.registerForBlockEntity((blockEntity, side) -> blockEntity.fluidSink(), INTERFACE_BE.getOrCreate());
			EnergyStorage.SIDED.registerForBlockEntity((blockEntity, side) -> blockEntity.energySink(), INTERFACE_BE.getOrCreate());
			ItemStorage.SIDED.registerForBlockEntity(RepeaterBlockEntity::getItemStorage, REPEATER_BE.getOrCreate());
			FluidStorage.SIDED.registerForBlockEntity(RepeaterBlockEntity::getFluidStorage, REPEATER_BE.getOrCreate());
			EnergyStorage.SIDED.registerForBlockEntity(RepeaterBlockEntity::getEnergyStorage, REPEATER_BE.getOrCreate());
			ItemStorage.SIDED.registerForBlockEntity(FactoryBlockEntity::getItemStorage, FACTORY_BE.getOrCreate());
			EnergyStorage.SIDED.registerForBlockEntity(FactoryBlockEntity::getEnergyStorage, FACTORY_BE.getOrCreate());
		});
	}


	@KiwiModule.Name("simulator")
	public static final KiwiGO<BlockEntityType<SimulatorBlockEntity>> SIMULATOR_BE = blockEntity(
			SimulatorBlockEntity::new,
			SIMULATOR::getOrCreate);
	@KiwiModule.Name("interface")
	public static final KiwiGO<BlockEntityType<InterfaceBlockEntity>> INTERFACE_BE = blockEntity(
			InterfaceBlockEntity::new,
			INTERFACE::getOrCreate,
			INTERFACE_WINDOW::getOrCreate);
	@KiwiModule.Name("repeater")
	public static final KiwiGO<BlockEntityType<RepeaterBlockEntity>> REPEATER_BE = blockEntity(
			RepeaterBlockEntity::new,
			REPEATER::getOrCreate);
	@KiwiModule.Name("factory")
	public static final KiwiGO<BlockEntityType<FactoryBlockEntity>> FACTORY_BE = blockEntity(FactoryBlockEntity::new, FACTORY::getOrCreate);


}
