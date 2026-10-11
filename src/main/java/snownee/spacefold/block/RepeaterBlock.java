package snownee.spacefold.block;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import snownee.kiwi.block.IKiwiBlock;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.entity.RepeaterBlockEntity;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.paint.FacePaint;
import snownee.spacefold.paint.RepeaterPaints;

public class RepeaterBlock extends DirectionalBlock implements EntityBlock, IKiwiBlock {
	public static final MapCodec<RepeaterBlock> CODEC = simpleCodec(RepeaterBlock::new);

	public RepeaterBlock(BlockBehaviour.Properties properties) {
		super(properties.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F));
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	private static void writeData(RepeaterBlockEntity blockEntity, ItemStack stack) {
		Component name = blockEntity.getCustomName();
		if (name != null) {
			stack.set(DataComponents.CUSTOM_NAME, name);
		}
		SimulationResult result = blockEntity.getResult();
		if (result != null) {
			stack.set(CoreModule.SIMULATION_RESULT.get(), result);
		}
		Map<Direction, FacePaint> paints = blockEntity.getPaints();
		if (!paints.isEmpty()) {
			stack.set(CoreModule.REPEATER_PAINT.get(), paints);
		}
	}

	public static ItemStack createCloneStack(BlockState state, CompoundTag data, RegistryOps<Tag> ops) {
		ItemStack stack = new ItemStack(state.getBlock());
		Tag customName = data.get("CustomName");
		if (customName != null) {
			ComponentSerialization.CODEC.parse(ops, customName).result()
					.ifPresent(name -> stack.set(DataComponents.CUSTOM_NAME, name));
		}
		Tag result = data.get("result");
		if (result != null) {
			SimulationResult.CODEC.parse(ops, result).result()
					.ifPresent(value -> stack.set(CoreModule.SIMULATION_RESULT.get(), value));
		}
		Tag paints = data.get("paints");
		if (paints != null) {
			RepeaterPaints.CODEC.parse(ops, paints).result()
					.ifPresent(value -> stack.set(CoreModule.REPEATER_PAINT.get(), value));
		}
		return stack;
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public Component getName(ItemStack stack) {
		return super.getName();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new RepeaterBlockEntity(worldPosition, blockState);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (tickLevel, pos, state, blockEntity) -> ((RepeaterBlockEntity) blockEntity).tick();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof RepeaterBlockEntity blockEntity)) {
			return;
		}
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result != null) {
			blockEntity.setResult(result);
		}
		Map<Direction, FacePaint> paints = stack.get(CoreModule.REPEATER_PAINT.get());
		if (paints != null && !paints.isEmpty()) {
			blockEntity.setPaints(paints);
		}
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RepeaterBlockEntity blockEntity) {
			for (ItemStack stack : drops) {
				writeData(blockEntity, stack);
			}
		}
		return drops;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
		if (includeData && level.getBlockEntity(pos) instanceof RepeaterBlockEntity blockEntity) {
			writeData(blockEntity, stack);
		}
		return stack;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && player.preventsBlockDrops()
				&& level.getBlockEntity(pos) instanceof RepeaterBlockEntity blockEntity) {
			ItemStack stack = new ItemStack(state.getBlock());
			writeData(blockEntity, stack);
			popResource(level, pos, stack);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
