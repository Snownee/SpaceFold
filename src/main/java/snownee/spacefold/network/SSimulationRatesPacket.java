package snownee.spacefold.network;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.SimulationHud;

@KiwiPacket
public record SSimulationRatesPacket(int progress, Section inputs, Section outputs) implements CustomPacketPayload {
	public static final Type<SSimulationRatesPacket> TYPE = new Type<>(SpaceFold.id("simulation_rates"));

	@Override
	public Type<SSimulationRatesPacket> type() {
		return TYPE;
	}

	public record Section(List<Entry> entries, double energy, int hidden) {
		public static final Section EMPTY = new Section(List.of(), 0, 0);

		public static final StreamCodec<RegistryFriendlyByteBuf, Section> STREAM_CODEC = StreamCodec.composite(
				Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Section::entries,
				ByteBufCodecs.DOUBLE, Section::energy,
				ByteBufCodecs.VAR_INT, Section::hidden,
				Section::new);
	}

	public record Entry(@Nullable ItemVariant item, @Nullable FluidVariant fluid, double rate) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
				(output, value) -> {
					boolean fluid = value.fluid() != null;
					output.writeBoolean(fluid);
					if (fluid) {
						FluidVariant.PACKET_CODEC.encode(output, value.fluid());
					} else {
						ItemVariant.PACKET_CODEC.encode(output, Objects.requireNonNull(value.item()));
					}
					output.writeDouble(value.rate());
				},
				input -> {
					if (input.readBoolean()) {
						return new Entry(null, FluidVariant.PACKET_CODEC.decode(input), input.readDouble());
					}
					return new Entry(ItemVariant.PACKET_CODEC.decode(input), null, input.readDouble());
				});
	}

	public static class Handler implements PlayPacketHandler<SSimulationRatesPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SSimulationRatesPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SSimulationRatesPacket::progress,
				Section.STREAM_CODEC, SSimulationRatesPacket::inputs,
				Section.STREAM_CODEC, SSimulationRatesPacket::outputs,
				SSimulationRatesPacket::new);

		@Override
		public void handle(SSimulationRatesPacket packet, PayloadContext context) {
			context.execute(() -> SimulationHud.accept(packet));
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SSimulationRatesPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
