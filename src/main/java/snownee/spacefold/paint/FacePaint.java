package snownee.spacefold.paint;

import java.util.Arrays;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FacePaint(int[] pixels, int rotation) {
	public static final int SIZE = 16;
	public static final int PIXEL_COUNT = SIZE * SIZE;

	public static final Codec<FacePaint> CODEC = RecordCodecBuilder.create(i -> i.group(
					Codec.INT_STREAM
							.fieldOf("pixels")
							.forGetter(FacePaint::pixelStream),
					Codec.INT.optionalFieldOf("rotation", 0)
							.forGetter(FacePaint::rotation))
			.apply(i, FacePaint::fromStream));

	public FacePaint {
		pixels = Arrays.copyOf(pixels, PIXEL_COUNT);
		rotation = Math.floorMod(rotation, 4);
	}

	private static FacePaint fromStream(IntStream pixels, int rotation) {
		int[] array = new int[PIXEL_COUNT];
		int[] decoded = pixels.limit(PIXEL_COUNT).toArray();
		System.arraycopy(decoded, 0, array, 0, decoded.length);
		return new FacePaint(array, rotation);
	}

	public IntStream pixelStream() {
		return Arrays.stream(pixels);
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
