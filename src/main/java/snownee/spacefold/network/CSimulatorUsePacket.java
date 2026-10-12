package snownee.spacefold.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.Simulate;

@KiwiPacket
public record CSimulatorUsePacket(BlockPos pos, boolean windows) implements CustomPacketPayload {
	public static final Type<CSimulatorUsePacket> TYPE = new Type<>(SpaceFold.id("simulator_use"));

	@Override
	public Type<CSimulatorUsePacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<CSimulatorUsePacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, CSimulatorUsePacket> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC,
				CSimulatorUsePacket::pos,
				ByteBufCodecs.BOOL,
				CSimulatorUsePacket::windows,
				CSimulatorUsePacket::new);

		@Override
		public void handle(CSimulatorUsePacket packet, PayloadContext payloadContext) {
			ServerPlayer player = payloadContext.serverPlayer();
			payloadContext.execute(() -> {
				ServerLevel level = player.level();
				if (!level.dimension().equals(Simulate.DIMENSION)) {
					return;
				}
				BlockPos pos = packet.pos();
				if (!player.isWithinBlockInteractionRange(pos, 1.0D)) {
					return;
				}
				RoomManager.useSimulator(level, player, pos, packet.windows());
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CSimulatorUsePacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
