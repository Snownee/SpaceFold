package snownee.spacefold.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.Plan;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.SHighlightBlocksPacket;

public final class ServerCommand {
	private ServerCommand() {
	}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext context,
			Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal(SpaceFold.ID)
				.then(Commands.literal("check_room").executes(ServerCommand::checkRoom)));
	}

	private static int checkRoom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		if (!level.dimension().equals(Plan.DIMENSION)) {
			context.getSource().sendFailure(Component.translatable("command.spacefold.wrong_dimension"));
			return 0;
		}
		int room = RoomManager.currentRoom(level, player);
		if (room < 0) {
			context.getSource().sendFailure(Component.translatable("command.spacefold.no_room"));
			return 0;
		}
		RoomManager.RoomCheck check = RoomManager.checkRoom(level, room);
		if (check.valid()) {
			player.sendSystemMessage(Component.translatable("command.spacefold.check_room.valid"));
			player.sendSystemMessage(Component.translatable("gui.spacefold.materials"));
			for (SimulationResult.Material material : check.materials()) {
				player.sendSystemMessage(Component.translatable(
						"gui.spacefold.material_count",
						material.item().toStack().getHoverName(),
						material.count()));
			}
			return 1;
		}
		player.sendSystemMessage(Component.translatable("command.spacefold.check_room.invalid"));
		KPacketSender.send(
				new SHighlightBlocksPacket(check.illegal(), RoomManager.HIGHLIGHT_COLOR, RoomManager.HIGHLIGHT_LIFE),
				player);
		return 1;
	}
}
