package snownee.spacefold.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import snownee.kiwi.item.ModBlockItem;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.menu.RepeaterMenu;
import snownee.spacefold.network.SFactoryMaterialsPacket;

public class RepeaterItem extends ModBlockItem {
	public RepeaterItem(Block block, Properties builder) {
		super(block, builder);
	}

	private static List<SFactoryMaterialsPacket.Entry> materialEntries(ItemStack stack) {
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result == null) {
			return List.of();
		}
		List<SFactoryMaterialsPacket.Entry> entries = new ArrayList<>();
		for (SimulationResult.Material material : result.materials()) {
			entries.add(new SFactoryMaterialsPacket.Entry(material.item(), material.count(), material.count()));
		}
		return entries;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			boolean canConfigure = player.isCreative() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
			if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				ItemStack stack = player.getItemInHand(hand);
				if (canConfigure) {
					serverPlayer.openMenu(new SimpleMenuProvider(
							(containerId, inventory, p) -> new RepeaterMenu(containerId, inventory, stack),
							Component.translatable("gui.spacefold.repeater_config")));
				} else {
					KPacketSender.send(new SFactoryMaterialsPacket(materialEntries(stack), false), serverPlayer);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.use(level, player, hand);
	}
}
