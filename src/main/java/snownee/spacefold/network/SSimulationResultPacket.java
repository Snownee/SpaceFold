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
import snownee.spacefold.client.SimulatorScreen;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.dim.SimulationResult;

@KiwiPacket
public record SSimulationResultPacket(int containerId, @Nullable SimulationResult result) implements CustomPacketPayload {
	public static final Type<SSimulationResultPacket> TYPE = new Type<>(SpaceFold.id("simulation_result"));

	private static final StreamCodec<RegistryFriendlyByteBuf, SimulationResult> RESULT_CODEC =
			ByteBufCodecs.fromCodecWithRegistries(SimulationResult.CODEC);

	@Override
	public Type<SSimulationResultPacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<SSimulationResultPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SSimulationResultPacket> STREAM_CODEC = StreamCodec.of(
				(output, packet) -> {
					ByteBufCodecs.VAR_INT.encode(output, packet.containerId());
					output.writeBoolean(packet.result() != null);
					if (packet.result() != null) {
						RESULT_CODEC.encode(output, packet.result());
					}
				},
				input -> {
					int containerId = ByteBufCodecs.VAR_INT.decode(input);
					SimulationResult result = input.readBoolean() ? RESULT_CODEC.decode(input) : null;
					return new SSimulationResultPacket(containerId, result);
				});

		@Override
		public void handle(SSimulationResultPacket packet, PayloadContext context) {
			context.execute(() -> {
				if (packet.result() != null && RoomSnapshot.isPreviewId(packet.result().snapshotId())) {
					PreviewCache.invalidate(packet.result().snapshotId());
				}
				SimulatorScreen.acceptResult(packet.containerId(), packet.result());
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SSimulationResultPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
