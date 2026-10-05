package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import snownee.spacefold.dim.RoomProtection;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void spacefold$enterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		RoomProtection.enter(player);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void spacefold$exitDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		RoomProtection.exit();
	}

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void spacefold$enterUseItemOn(
			ServerPlayer player,
			Level level,
			ItemStack itemStack,
			InteractionHand hand,
			BlockHitResult hitResult,
			CallbackInfoReturnable<InteractionResult> cir) {
		RoomProtection.enter(player);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void spacefold$exitUseItemOn(
			ServerPlayer player,
			Level level,
			ItemStack itemStack,
			InteractionHand hand,
			BlockHitResult hitResult,
			CallbackInfoReturnable<InteractionResult> cir) {
		RoomProtection.exit();
	}

	@Inject(method = "useItem", at = @At("HEAD"))
	private void spacefold$enterUseItem(
			ServerPlayer player,
			Level level,
			ItemStack itemStack,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		RoomProtection.enter(player);
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void spacefold$exitUseItem(
			ServerPlayer player,
			Level level,
			ItemStack itemStack,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		RoomProtection.exit();
	}
}
