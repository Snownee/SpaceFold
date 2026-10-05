package snownee.spacefold.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.particle.CubeParticleHighlighter;

@KiwiPacket
public record SHighlightBlocksPacket(List<BlockPos> positions, int color, int life) implements CustomPacketPayload {
	public static final Type<SHighlightBlocksPacket> TYPE = new Type<>(SpaceFold.id("highlight_blocks"));

	@Override
	public Type<SHighlightBlocksPacket> type() {
		return TYPE;
	}

	public static class Handler implements PlayPacketHandler<SHighlightBlocksPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SHighlightBlocksPacket> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), SHighlightBlocksPacket::positions,
				ByteBufCodecs.INT, SHighlightBlocksPacket::color,
				ByteBufCodecs.VAR_INT, SHighlightBlocksPacket::life,
				SHighlightBlocksPacket::new);

		@Override
		public void handle(SHighlightBlocksPacket packet, PayloadContext context) {
			context.execute(() -> CubeParticleHighlighter.highlight(packet.positions(), packet.color(), packet.life()));
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SHighlightBlocksPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
