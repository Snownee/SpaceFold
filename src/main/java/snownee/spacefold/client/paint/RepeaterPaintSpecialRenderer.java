package snownee.spacefold.client.paint;

import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.CoreModule;
import snownee.spacefold.paint.FacePaint;

public class RepeaterPaintSpecialRenderer implements SpecialModelRenderer<RepeaterPaintSpecialRenderer.Argument> {
	private static void submitFace(
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int light,
			Direction face,
			int rotation,
			TextureAtlasSprite sprite) {
		float nx = face.getStepX();
		float ny = face.getStepY();
		float nz = face.getStepZ();
		float offset = 0.0005F;
		float expand = 0.0015F;
		float u0 = sprite.getU0();
		float uSpan = sprite.getU1() - u0;
		float v0 = sprite.getV0();
		float vSpan = sprite.getV1() - v0;
		collector.submitCustomGeometry(
				poseStack, Sheets.cutoutBlockItemSheet(), (pose, vb) -> {
					for (int i = 0; i < 4; i++) {
						float px = PaintGeometry.x(face, i);
						float py = PaintGeometry.y(face, i);
						float pz = PaintGeometry.z(face, i);
						switch (face.getAxis()) {
							case X -> {
								py = expand(py, expand);
								pz = expand(pz, expand);
							}
							case Y -> {
								px = expand(px, expand);
								pz = expand(pz, expand);
							}
							case Z -> {
								px = expand(px, expand);
								py = expand(py, expand);
							}
						}
						vb.addVertex(pose, px + nx * offset, py + ny * offset, pz + nz * offset)
								.setColor(255, 255, 255, 255)
								.setUv(u0 + PaintGeometry.u(rotation, i) * uSpan, v0 + PaintGeometry.v(rotation, i) * vSpan)
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(light)
								.setNormal(pose, nx, ny, nz);
					}
				});
	}

	private static float expand(float value, float amount) {
		return value < 0.5F ? value - amount : value + amount;
	}

	@Override
	public @Nullable Argument extractArgument(ItemStack stack) {
		Map<Direction, FacePaint> paints = stack.get(CoreModule.REPEATER_PAINT.get());
		if (paints == null || paints.isEmpty()) {
			return null;
		}
		return new Argument(paints, PaintAtlas.get().version());
	}

	@Override
	public void submit(
			@Nullable Argument argument,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int lightCoords,
			int overlayCoords,
			boolean hasFoil,
			int outlineColor) {
		if (argument == null) {
			return;
		}
		for (Map.Entry<Direction, FacePaint> entry : argument.paints().entrySet()) {
			Direction face = entry.getKey();
			FacePaint paint = entry.getValue();
			TextureAtlasSprite sprite = PaintAtlas.get().sprite(paint);
			if (sprite != null) {
				submitFace(poseStack, collector, lightCoords, face, paint.rotation(), sprite);
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		for (int x = 0; x <= 1; x++) {
			for (int y = 0; y <= 1; y++) {
				for (int z = 0; z <= 1; z++) {
					output.accept(new Vector3f(x, y, z));
				}
			}
		}
	}

	public record Argument(Map<Direction, FacePaint> paints, int atlasVersion) {
	}

	public static final class Unbaked implements SpecialModelRenderer.Unbaked<Argument> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

		@Override
		public SpecialModelRenderer<Argument> bake(BakingContext context) {
			return new RepeaterPaintSpecialRenderer();
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<Argument>> type() {
			return MAP_CODEC;
		}
	}
}
