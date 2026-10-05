package snownee.spacefold.compat.jade;

import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.entity.RepeaterBlockEntity;

public class RepeaterProvider implements StreamServerDataProvider<BlockAccessor, Double> {
	public static final RepeaterProvider INSTANCE = new RepeaterProvider();

	private RepeaterProvider() {
	}

	@Override
	public Double streamData(BlockAccessor accessor) {
		RepeaterBlockEntity blockEntity = accessor.typedBlockEntity();
		return blockEntity.getThrottle();
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, Double> streamCodec() {
		return ByteBufCodecs.DOUBLE.cast();
	}

	@Override
	public Identifier getUid() {
		return SpaceFold.id("repeater");
	}

	public static class Client implements IBlockComponentProvider {
		public static final Client INSTANCE = new Client();

		private Client() {
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
			Optional<Double> throttle = RepeaterProvider.INSTANCE.decodeFromData(accessor);
			throttle.ifPresent(aDouble -> tooltip.add(Component.translatable(
					"jade.spacefold.repeater_percent",
					Math.round(aDouble * 100) + "%")));
		}

		@Override
		public Identifier getUid() {
			return SpaceFold.id("repeater");
		}
	}
}
