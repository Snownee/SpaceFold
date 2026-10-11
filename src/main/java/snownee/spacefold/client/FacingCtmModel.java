package snownee.spacefold.client;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.gson.JsonObject;

import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.AthenaModelAttributes;
import earth.terrarium.athena.api.client.models.AthenaModelFactory;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.models.FactoryManager;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.SpaceFold;

public class FacingCtmModel implements AthenaBlockModel {
	public static final AthenaModelFactory FACTORY = FacingCtmModel::create;

	private final AthenaBlockModel delegate;

	public FacingCtmModel(AthenaBlockModel delegate) {
		this.delegate = delegate;
	}

	private static Supplier<AthenaBlockModel> create(JsonObject json) {
		Supplier<AthenaBlockModel> base = ConnectedBlockModel.FACTORY.create(json);
		return () -> new FacingCtmModel(base.get());
	}

	public static void register() {
		FactoryManager.register(SpaceFold.id("facing_ctm"), FACTORY);
	}

	@Override
	public List<AthenaQuad> getQuads(AppearanceAndTintGetter level, BlockState state, BlockPos pos, Direction direction) {
		if (direction != state.getValue(DirectionalBlock.FACING)) {
			return List.of();
		}
		return delegate.getQuads(level, state, pos, direction);
	}

	@Override
	public Map<Direction, List<AthenaQuad>> getDefaultQuads(Direction direction) {
		if (direction != Direction.NORTH) {
			return Object2ObjectMaps.emptyMap();
		}
		return delegate.getDefaultQuads(direction);
	}

	@Override
	public Int2ObjectMap<Material.Baked> getTextures(Function<Material, Material.Baked> getter) {
		return delegate.getTextures(getter);
	}

	@Override
	public AthenaModelAttributes getAttributes() {
		return delegate.getAttributes();
	}
}
