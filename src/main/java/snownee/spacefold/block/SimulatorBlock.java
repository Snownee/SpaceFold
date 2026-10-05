package snownee.spacefold.block;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.entity.SimulatorBlockEntity;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.menu.SimulatorMenu;

public class SimulatorBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<SimulatorBlock> CODEC = simpleCodec(SimulatorBlock::new);

	public SimulatorBlock(BlockBehaviour.Properties properties) {
		super(properties.mapColor(MapColor.COLOR_PURPLE).strength(1.5F));
	}

	private static @Nullable Component placementError(ItemStack stack, Level level, BlockPos pos) {
		boolean roomSide = stack.getOrDefault(CoreModule.ROOM_SIDE.get(), false);
		boolean inRoom = RoomManager.isRoomDimension(level.dimension());
		if (roomSide != inRoom) {
			return Component.translatable("tip.spacefold.simulator_wrong_side");
		}
		if (!inRoom) {
			return null;
		}
		int room = roomOf(stack);
		if (room < 0) {
			return Component.translatable("tip.spacefold.simulator_wrong_room");
		}
		if (level instanceof ServerLevel serverLevel && RoomManager.roomAt(serverLevel, pos) != room) {
			return Component.translatable("tip.spacefold.simulator_wrong_room");
		}
		return null;
	}

	private static int roomOf(ItemStack stack) {
		TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		return data == null ? -1 : data.copyTagWithoutId().getIntOr("room", -1);
	}

	private static void writeData(Level level, SimulatorBlockEntity blockEntity, ItemStack stack) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		blockEntity.saveCustomOnly(output);
		BlockItem.setBlockEntityData(stack, blockEntity.getType(), output);
		Component name = blockEntity.getCustomName();
		if (name != null) {
			stack.set(DataComponents.CUSTOM_NAME, name);
		}
		stack.set(CoreModule.ROOM_SIDE.get(), RoomManager.isRoomDimension(level.dimension()));
		attachResult(level, blockEntity, stack);
	}

	private static void attachResult(Level level, SimulatorBlockEntity blockEntity, ItemStack stack) {
		if (!(level instanceof ServerLevel serverLevel) || blockEntity.getRoomIndex() < 0) {
			return;
		}
		SimulationResult result = RoomManager.simulationResult(serverLevel, blockEntity.getRoomIndex());
		if (result != null && !result.failed()) {
			stack.set(CoreModule.SIMULATION_RESULT.get(), result);
		}
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new SimulatorBlockEntity(worldPosition, blockState);
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		Component title = level.getBlockEntity(pos) instanceof SimulatorBlockEntity blockEntity && blockEntity.getCustomName() != null
				? blockEntity.getCustomName()
				: Component.translatable("gui.spacefold.simulator");
		return new SimpleMenuProvider(
				(containerId, inventory, p) -> new SimulatorMenu(containerId, inventory, pos),
				title);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(getMenuProvider(state, level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
		Component error = placementError(context.getItemInHand(), context.getLevel(), context.getClickedPos());
		if (error != null) {
			if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(error);
			}
			return null;
		}
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
		if (includeData && level instanceof Level realLevel
				&& realLevel.getBlockEntity(pos) instanceof SimulatorBlockEntity blockEntity && blockEntity.getRoomIndex() >= 0) {
			stack.set(CoreModule.ROOM_SIDE.get(), RoomManager.isRoomDimension(realLevel.dimension()));
			attachResult(realLevel, blockEntity, stack);
		}
		return stack;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SimulatorBlockEntity blockEntity
				&& blockEntity.getRoomIndex() >= 0) {
			for (ItemStack stack : drops) {
				writeData(params.getLevel(), blockEntity, stack);
			}
		}
		return drops;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops()
				&& level.getBlockEntity(pos) instanceof SimulatorBlockEntity blockEntity && blockEntity.getRoomIndex() >= 0) {
			ItemStack stack = new ItemStack(state.getBlock());
			writeData(level, blockEntity, stack);
			popResource(level, pos, stack);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || RoomManager.isRoomDimension(level.dimension())) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof SimulatorBlockEntity blockEntity) || blockEntity.getRoomIndex() < 0) {
			return;
		}
		RoomManager.setSource((ServerLevel) level, blockEntity.getRoomIndex(), pos, level.dimension());
	}
}
