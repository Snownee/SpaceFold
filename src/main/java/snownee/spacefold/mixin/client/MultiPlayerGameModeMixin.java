package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.block.SimulatorBlock;
import snownee.spacefold.dim.Simulate;
import snownee.spacefold.network.CSimulatorUsePacket;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void spacefold$simulatorInteract(
			LocalPlayer player,
			InteractionHand hand,
			BlockHitResult blockHit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		if (!player.isSpectator() || !player.level().dimension().equals(Simulate.DIMENSION)) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (!(player.level().getBlockState(pos).getBlock() instanceof SimulatorBlock)) {
			return;
		}
		KPacketSender.sendToServer(new CSimulatorUsePacket(pos, SpaceFoldClientConfig.roomWindows));
		cir.setReturnValue(InteractionResult.CONSUME);
	}
}
