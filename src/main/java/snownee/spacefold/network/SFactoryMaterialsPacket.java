package snownee.spacefold.network;

import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.FactoryMaterialsScreen;

@KiwiPacket
public record SFactoryMaterialsPacket(List<Entry> entries, boolean showProgress) implements CustomPacketPayload {
	public static final Type<SFactoryMaterialsPacket> TYPE = new Type<>(SpaceFold.id("factory_materials"));

	@Override
	public Type<SFactoryMaterialsPacket> type() {
		return TYPE;
	}

	public record Entry(ItemVariant item, long provided, long total) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
				ItemVariant.PACKET_CODEC, Entry::item,
				ByteBufCodecs.VAR_LONG, Entry::provided,
				ByteBufCodecs.VAR_LONG, Entry::total,
				Entry::new);
	}

	public static class Handler implements PlayPacketHandler<SFactoryMaterialsPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SFactoryMaterialsPacket> STREAM_CODEC = StreamCodec.composite(
				Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SFactoryMaterialsPacket::entries,
				ByteBufCodecs.BOOL, SFactoryMaterialsPacket::showProgress,
				SFactoryMaterialsPacket::new);

		@Override
		public void handle(SFactoryMaterialsPacket packet, PayloadContext context) {
			context.execute(() -> {
				Minecraft minecraft = Minecraft.getInstance();
				minecraft.player.closeContainer();
				minecraft.setScreen(new FactoryMaterialsScreen(packet.entries(), packet.showProgress()));
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SFactoryMaterialsPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
