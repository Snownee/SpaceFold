package snownee.spacefold.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.entity.FactoryBlockEntity;
import snownee.spacefold.menu.RepeaterMenu;

@KiwiPacket
public record CRepeaterConfigPacket(int slot) implements CustomPacketPayload {
	public static final Type<CRepeaterConfigPacket> TYPE = new Type<>(SpaceFold.id("repeater_config"));

	@Override
	public Type<CRepeaterConfigPacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<CRepeaterConfigPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, CRepeaterConfigPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				CRepeaterConfigPacket::slot,
				CRepeaterConfigPacket::new);

		@Override
		public void handle(CRepeaterConfigPacket packet, PayloadContext payloadContext) {
			ServerPlayer player = payloadContext.serverPlayer();
			payloadContext.execute(() -> {
				Inventory inventory = player.getInventory();
				int slot = packet.slot();
				if (slot < 0 || slot >= inventory.getContainerSize()) {
					return;
				}
				ItemStack stack = inventory.getItem(slot);
				if (!FactoryBlockEntity.isTemplateStack(stack) || !RepeaterMenu.canConfigure(player, stack)) {
					return;
				}
				player.openMenu(new SimpleMenuProvider(
						(containerId, inv, p) -> new RepeaterMenu(containerId, inv, stack),
						Component.translatable("gui.spacefold.repeater_config")));
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CRepeaterConfigPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
