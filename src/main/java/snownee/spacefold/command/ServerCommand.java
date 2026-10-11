package snownee.spacefold.command;

import java.util.Objects;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.Plan;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.RoomTreeGenerator;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.SHighlightBlocksPacket;

public final class ServerCommand {
	public static final PermissionCheck PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	private ServerCommand() {
	}

	public static void register(
			CommandDispatcher<CommandSourceStack> dispatcher,
			CommandBuildContext context,
			Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SpaceFold.ID)
				.then(Commands.literal("check_room").executes(ServerCommand::checkRoom));
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			root.then(Commands.literal("gen_rooms")
					.requires(Commands.hasPermission(PERMISSION_CHECK))
					.then(Commands.argument("n", IntegerArgumentType.integer(1, 256))
							.executes(ServerCommand::genRooms)));
		}
		dispatcher.register(root);
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

	private static int genRooms(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int n = IntegerArgumentType.getInteger(context, "n");
		ServerLevel plan = Objects.requireNonNull(player.level().getServer().getLevel(Plan.DIMENSION));
		int room = RoomManager.createRoom(plan, player.getUUID());
		RoomTreeGenerator.Result result = RoomTreeGenerator.generate(plan, room, n, player.getRandom());
		RoomManager.placeSnapshot(plan, room, result.rootSnapshot());
		RoomManager.setPreviewSnapshot(plan, room, result.rootSnapshot());
		ItemStack stack = new ItemStack(CoreModule.SIMULATOR.asItem());
		stack.set(CoreModule.SIMULATION_RESULT.get(), result.rootResult());
		CompoundTag tag = new CompoundTag();
		tag.putInt("room", room);
		stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(CoreModule.SIMULATOR_BE.get(), tag));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		player.sendSystemMessage(Component.translatable("command.spacefold.gen_rooms.success", n, result.rootId(), room));
		return 1;
	}
}
