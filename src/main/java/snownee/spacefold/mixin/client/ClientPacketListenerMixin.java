package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import snownee.spacefold.dim.RoomManager;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Shadow
	private LevelLoadTracker levelLoadTracker;

	@Unique
	private boolean spacefold$skipLoadingScreen;

	@Inject(method = "handleRespawn", at = @At("HEAD"))
	private void spacefold$markSkipLoadingScreen(ClientboundRespawnPacket packet, CallbackInfo ci) {
		spacefold$skipLoadingScreen = RoomManager.isRoomDimension(packet.commonPlayerSpawnInfo().dimension());
	}

	@Inject(method = "startWaitingForNewLevel", at = @At("HEAD"), cancellable = true)
	private void spacefold$skipLoadingScreen(LocalPlayer player, ClientLevel level, LevelLoadingScreen.Reason reason, CallbackInfo ci) {
		if (!spacefold$skipLoadingScreen) {
			return;
		}
		spacefold$skipLoadingScreen = false;
		if (this.levelLoadTracker == null) {
			this.levelLoadTracker = new LevelLoadTracker();
		}
		this.levelLoadTracker.startClientLoad(player, level, Minecraft.getInstance().levelRenderer);
		ci.cancel();
	}
}
