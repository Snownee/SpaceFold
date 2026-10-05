package snownee.spacefold.compat.jade;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.block.SimulatorBlock;

public final class FacingProvider implements IBlockComponentProvider {
	public static final FacingProvider INSTANCE = new FacingProvider();

	private FacingProvider() {
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		Direction facing;
		if (accessor.getBlockState().getBlock() instanceof ChamberWallBlock) {
			facing = accessor.getBlockState().getValue(ChamberWallBlock.FACING).getOpposite();
		} else if (accessor.getBlock() instanceof SimulatorBlock) {
			facing = accessor.getSide();
		} else {
			return;
		}
		tooltip.add(Component.translatable("jade.spacefold.facing", Component.translatable("jade." + facing.getSerializedName())));
	}

	@Override
	public Identifier getUid() {
		return SpaceFold.id("facing");
	}
}
