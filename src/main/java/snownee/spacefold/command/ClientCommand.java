package snownee.spacefold.command;

import java.nio.file.Path;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.paint.PaintAtlas;

public class ClientCommand {
	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(ClientCommands.literal(SpaceFold.ID + "c")
				.then(ClientCommands.literal("export_atlas").executes(context -> ClientCommand.exportAtlas(context.getSource()))));
	}

	public static int exportAtlas(FabricClientCommandSource source) {
		try {
			Path path = FabricLoader.getInstance().getGameDir().resolve("spacefold_paint_atlas.png");
			PaintAtlas.get().export(path);
			source.sendFeedback(Component.literal("Exported paint atlas to " + path.toAbsolutePath()));
			return 1;
		} catch (Exception e) {
			source.sendError(Component.literal("Failed to export paint atlas: " + e.getMessage()));
			return 0;
		}
	}
}
