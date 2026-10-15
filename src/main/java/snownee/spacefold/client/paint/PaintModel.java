package snownee.spacefold.client.paint;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.block.entity.RepeaterBlockEntity;
import snownee.spacefold.paint.FacePaint;
import snownee.spacefold.paint.FacingRotation;
import snownee.spacefold.paint.RepeaterPaints;

public class PaintModel extends WrapperBlockStateModel {
	public PaintModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	private static RepeaterPaints paintsAt(BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof RepeaterBlockEntity repeater ? repeater.getPaints() : RepeaterPaints.EMPTY;
	}

	@Override
	public void emitQuads(
			QuadEmitter emitter,
			BlockAndTintGetter level,
			BlockPos pos,
			BlockState state,
			RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		RepeaterPaints paints = paintsAt(level, pos);
		Direction facing = state.getValue(DirectionalBlock.FACING);
		Map<Direction, TextureAtlasSprite> sprites = new EnumMap<>(Direction.class);
		Map<Direction, FacePaint> works = new EnumMap<>(Direction.class);
		for (Map.Entry<Direction, FacePaint> entry : paints.entries()) {
			Direction worldFace = FacingRotation.toWorld(facing, entry.getKey());
			TextureAtlasSprite sprite = PaintAtlas.get().sprite(entry.getValue());
			if (sprite != null) {
				sprites.put(worldFace, sprite);
				works.put(worldFace, entry.getValue());
			}
		}
		if (sprites.size() < paints.size()) {
			PaintAtlas.get().markSection(pos);
		}
		if (sprites.isEmpty()) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}
		emitter.pushTransform(quad -> {
			Direction face = quad.nominalFace();
			return face == null || !sprites.containsKey(face);
		});
		super.emitQuads(emitter, level, pos, state, random, cullTest);
		emitter.popTransform();
		float[] out = new float[3];
		for (Map.Entry<Direction, FacePaint> entry : works.entrySet()) {
			Direction worldFace = entry.getKey();
			Direction modelFace = FacingRotation.toModel(facing, worldFace);
			TextureAtlasSprite sprite = sprites.get(worldFace);
			int rotation = entry.getValue().rotation();
			for (int i = 0; i < 4; i++) {
				FacingRotation.rotatePosition(
						facing,
						PaintGeometry.x(modelFace, i),
						PaintGeometry.y(modelFace, i),
						PaintGeometry.z(modelFace, i),
						out);
				emitter.pos(i, out[0], out[1], out[2]);
				emitter.uv(i, PaintGeometry.u(rotation, i), PaintGeometry.v(rotation, i));
			}
			emitter.cullFace(worldFace);
			emitter.materialBake(new Material.Baked(sprite, false), MutableQuadView.BAKE_NORMALIZED);
			emitter.emit();
		}
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return new GeometryKey(paintsAt(level, pos), PaintAtlas.get().version());
	}

	private record GeometryKey(RepeaterPaints paints, int atlasVersion) {
	}
}
