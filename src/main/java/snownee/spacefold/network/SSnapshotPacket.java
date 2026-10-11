package snownee.spacefold.network;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.PreviewCache;
import snownee.spacefold.dim.RoomSnapshot;

@KiwiPacket
public record SSnapshotPacket(int id, @Nullable RoomSnapshot snapshot) implements CustomPacketPayload {
	public static final Type<SSnapshotPacket> TYPE = new Type<>(SpaceFold.id("snapshot"));

	private static final StreamCodec<RegistryFriendlyByteBuf, RoomSnapshot> SNAPSHOT_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(RoomSnapshot.CODEC);

	@Override
	public Type<SSnapshotPacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<SSnapshotPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SSnapshotPacket> STREAM_CODEC = StreamCodec.of(
				(output, packet) -> {
					ByteBufCodecs.VAR_INT.encode(output, packet.id());
					output.writeBoolean(packet.snapshot() != null);
					if (packet.snapshot() != null) {
						SNAPSHOT_CODEC.encode(output, packet.snapshot());
					}
				},
				input -> {
					int id = ByteBufCodecs.VAR_INT.decode(input);
					RoomSnapshot snapshot = input.readBoolean() ? SNAPSHOT_CODEC.decode(input) : null;
					return new SSnapshotPacket(id, snapshot);
				});

		@Override
		public void handle(SSnapshotPacket packet, PayloadContext context) {
			context.execute(() -> PreviewCache.accept(packet.id(), packet.snapshot()));
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SSnapshotPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
