package snownee.spacefold.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import snownee.kiwi.network.KPacketSender;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.RoomSnapshot;

@KiwiPacket
public record CSnapshotRequestPacket(int id) implements CustomPacketPayload {
	public static final Type<CSnapshotRequestPacket> TYPE = new Type<>(SpaceFold.id("snapshot_request"));

	@Override
	public Type<CSnapshotRequestPacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<CSnapshotRequestPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, CSnapshotRequestPacket> STREAM_CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, CSnapshotRequestPacket::id, CSnapshotRequestPacket::new);

		@Override
		public void handle(CSnapshotRequestPacket packet, PayloadContext payloadContext) {
			ServerPlayer player = payloadContext.serverPlayer();
			payloadContext.execute(() -> {
				RoomSnapshot snapshot = RoomManager.snapshot(player.level(), packet.id);
				KPacketSender.send(new SSnapshotPacket(packet.id, snapshot), player);
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CSnapshotRequestPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
