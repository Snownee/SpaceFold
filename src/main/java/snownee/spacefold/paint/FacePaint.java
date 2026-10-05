package snownee.spacefold.paint;

import java.util.Arrays;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FacePaint(int[] pixels, int rotation) {
	public static final int SIZE = 16;
	public static final int PIXEL_COUNT = SIZE * SIZE;

	public static final Codec<FacePaint> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT.listOf()
							.fieldOf("pixels")
							.forGetter(FacePaint::pixelList),
					Codec.INT.optionalFieldOf("rotation", 0)
							.forGetter(FacePaint::rotation))
			.apply(i, FacePaint::fromList));

	public FacePaint {
		pixels = Arrays.copyOf(pixels, PIXEL_COUNT);
		rotation = Math.floorMod(rotation, 4);
	}

	private static FacePaint fromList(List<Integer> pixels, int rotation) {
		int[] array = new int[PIXEL_COUNT];
		for (int i = 0; i < PIXEL_COUNT && i < pixels.size(); i++) {
			array[i] = pixels.get(i);
		}
		return new FacePaint(array, rotation);
	}

	public List<Integer> pixelList() {
		return Arrays.stream(pixels).boxed().toList();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FacePaint(int[] pixels1, int rotation1) && rotation == rotation1 && Arrays.equals(pixels, pixels1);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(pixels) + rotation;
	}
}
