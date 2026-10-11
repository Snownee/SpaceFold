package snownee.spacefold.network;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import snownee.kiwi.network.KiwiPacket;
import snownee.kiwi.network.PayloadContext;
import snownee.kiwi.network.PlayPacketHandler;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.client.DetailsScreen;
import snownee.spacefold.dim.SimulationResult;

@KiwiPacket
public record SViewDetailsPacket(List<Entry> entries, boolean showProgress, int snapshotId) implements CustomPacketPayload {
	public static final Type<SViewDetailsPacket> TYPE = new Type<>(SpaceFold.id("view_details"));

	@Override
	public Type<SViewDetailsPacket> type() {
		return TYPE;
	}

	public static List<Entry> entriesFrom(ItemStack stack) {
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		if (result == null) {
			return List.of();
		}
		List<Entry> entries = new ArrayList<>();
		for (SimulationResult.Material material : result.materials()) {
			entries.add(new Entry(material.item(), material.count(), material.count()));
		}
		return entries;
	}

	public static int snapshotIdFrom(ItemStack stack) {
		SimulationResult result = stack.get(CoreModule.SIMULATION_RESULT.get());
		return result == null ? SimulationResult.NO_SNAPSHOT : result.snapshotId();
	}

	public record Entry(ItemVariant item, long provided, long total) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
				ItemVariant.PACKET_CODEC, Entry::item,
				ByteBufCodecs.VAR_LONG, Entry::provided,
				ByteBufCodecs.VAR_LONG, Entry::total,
				Entry::new);
	}

	public static class Handler implements PlayPacketHandler<SViewDetailsPacket> {
		public static final StreamCodec<RegistryFriendlyByteBuf, SViewDetailsPacket> STREAM_CODEC = StreamCodec.composite(
				Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SViewDetailsPacket::entries,
				ByteBufCodecs.BOOL, SViewDetailsPacket::showProgress,
				ByteBufCodecs.VAR_INT, SViewDetailsPacket::snapshotId,
				SViewDetailsPacket::new);

		@Override
		public void handle(SViewDetailsPacket packet, PayloadContext context) {
			context.execute(() -> {
				Minecraft minecraft = Minecraft.getInstance();
				minecraft.player.closeContainer();
				minecraft.setScreen(new DetailsScreen(packet.entries(), packet.showProgress(), packet.snapshotId()));
			});
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SViewDetailsPacket> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
