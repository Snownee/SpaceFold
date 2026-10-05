package snownee.spacefold.client.paint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.mixin.client.TextureAtlasSpriteInvoker;
import snownee.spacefold.paint.FacePaint;

public final class PaintAtlas {
	private static final Identifier RESERVED = SpaceFold.id("block/paint_slots");
	private static final int SIZE = FacePaint.SIZE;
	private static final int PAD = 1;
	private static final int STRIDE = SIZE + PAD * 2;
	private static final int RESERVED_SIZE = 512;
	private static final int COLUMNS = RESERVED_SIZE / STRIDE;
	private static final int SLOTS = COLUMNS * COLUMNS;
	private static final PaintAtlas INSTANCE = new PaintAtlas();

	private final Map<PixelKey, Slot> slots = new ConcurrentHashMap<>();
	private final Set<PixelKey> pending = ConcurrentHashMap.newKeySet();
	private final Set<Long> dirtySections = ConcurrentHashMap.newKeySet();
	private final Slot[] grid = new Slot[SLOTS];

	private volatile int version;
	private boolean fullRemesh;
	private long tickCounter;
	private boolean ready;
	private GpuTexture texture;
	private int baseX;
	private int baseY;
	private int atlasWidth;
	private int atlasHeight;
	private int mipLevels;

	private PaintAtlas() {
	}

	public static PaintAtlas get() {
		return INSTANCE;
	}

	private static NativeImage downsample(NativeImage source, int width, int height) {
		NativeImage image = new NativeImage(width, height, true);
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(
						x,
						y,
						source.getPixel(
								Math.min(sourceWidth - 1, x * sourceWidth / width),
								Math.min(sourceHeight - 1, y * sourceHeight / height)));
			}
		}
		return image;
	}

	public int version() {
		return version;
	}

	public TextureAtlasSprite sprite(FacePaint paint) {
		PixelKey key = new PixelKey(paint.pixels());
		Slot slot = slots.get(key);
		if (slot != null) {
			slot.lastUse = tickCounter;
			return slot.sprite;
		}
		pending.add(key);
		return null;
	}

	public void markSection(BlockPos pos) {
		if (ready) {
			dirtySections.add(SectionPos.asLong(pos));
		}
	}

	public void tick() {
		AbstractTexture abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		if (!(abstractTexture instanceof TextureAtlas atlas)) {
			return;
		}
		GpuTexture current;
		try {
			current = atlas.getTexture();
		} catch (IllegalStateException e) {
			return;
		}
		if (current != texture) {
			reset();
			texture = current;
			if (!reserve(atlas, current)) {
				texture = null;
				return;
			}
		}
		tickCounter++;
		if (ready && !pending.isEmpty()) {
			if (flush()) {
				if (fullRemesh) {
					fullRemesh = false;
					PaintClientHooks.markAllDirty();
				} else {
					for (long section : dirtySections) {
						PaintClientHooks.markSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
					}
				}
				dirtySections.clear();
			}
		}
	}

	public void export(Path path) throws IOException {
		if (!ready) {
			throw new IOException("Paint atlas is not ready yet");
		}
		NativeImage image = new NativeImage(RESERVED_SIZE, RESERVED_SIZE, true);
		try {
			for (Slot slot : grid) {
				if (slot == null || slot.image == null) {
					continue;
				}
				int originX = slot.x - baseX + PAD;
				int originY = slot.y - baseY + PAD;
				for (int y = 0; y < SIZE; y++) {
					for (int x = 0; x < SIZE; x++) {
						image.setPixel(originX + x, originY + y, slot.image.getPixel(x, y));
					}
				}
			}
			image.writeToFile(path);
		} finally {
			image.close();
		}
	}

	private void reset() {
		for (Slot slot : grid) {
			if (slot != null && slot.sprite != null) {
				slot.sprite.close();
			}
		}
		slots.clear();
		pending.clear();
		dirtySections.clear();
		Arrays.fill(grid, null);
		fullRemesh = false;
		ready = false;
		version++;
	}

	private boolean reserve(TextureAtlas atlas, GpuTexture texture) {
		TextureAtlasSprite reserved = atlas.getSprite(RESERVED);
		if (!reserved.contents().name().equals(RESERVED)) {
			SpaceFold.LOGGER.warn("Paint atlas region {} is not stitched", RESERVED);
			return false;
		}
		atlasWidth = texture.getWidth(0);
		atlasHeight = texture.getHeight(0);
		mipLevels = texture.getMipLevels();
		baseX = Math.round(reserved.getU0() * atlasWidth);
		baseY = Math.round(reserved.getV0() * atlasHeight);
		ready = true;
		return true;
	}

	private boolean flush() {
		boolean changed = false;
		for (PixelKey key : pending) {
			pending.remove(key);
			if (slots.containsKey(key)) {
				continue;
			}
			int index = allocate();
			if (index < 0) {
				break;
			}
			Slot slot = new Slot(index, cellX(index), cellY(index), key);
			write(slot);
			slot.sprite = createSprite(slot);
			slot.lastUse = tickCounter;
			slots.put(key, slot);
			grid[index] = slot;
			changed = true;
		}
		if (changed) {
			version++;
		}
		return changed;
	}

	private int allocate() {
		for (int i = 0; i < SLOTS; i++) {
			if (grid[i] == null) {
				return i;
			}
		}
		int lru = -1;
		long oldest = Long.MAX_VALUE;
		for (int i = 0; i < SLOTS; i++) {
			Slot slot = grid[i];
			if (slot != null && slot.lastUse < oldest) {
				oldest = slot.lastUse;
				lru = i;
			}
		}
		if (lru < 0) {
			return -1;
		}
		Slot evicted = grid[lru];
		slots.remove(evicted.key);
		if (evicted.sprite != null) {
			evicted.sprite.close();
		}
		grid[lru] = null;
		fullRemesh = true;
		return lru;
	}

	private int cellX(int index) {
		return baseX + (index % COLUMNS) * STRIDE;
	}

	private int cellY(int index) {
		return baseY + (index / COLUMNS) * STRIDE;
	}

	private void write(Slot slot) {
		int[] pixels = slot.key.pixels;
		NativeImage content = new NativeImage(SIZE, SIZE, true);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				content.setPixel(x, y, pixels[x + y * SIZE]);
			}
		}
		slot.image = content;
		NativeImage physical = new NativeImage(STRIDE, STRIDE, true);
		for (int y = 0; y < STRIDE; y++) {
			int sy = Math.max(0, Math.min(SIZE - 1, y - PAD));
			for (int x = 0; x < STRIDE; x++) {
				int sx = Math.max(0, Math.min(SIZE - 1, x - PAD));
				physical.setPixel(x, y, content.getPixel(sx, sy));
			}
		}
		for (int level = 0; level < mipLevels; level++) {
			int x0 = slot.x >> level;
			int y0 = slot.y >> level;
			int x1 = (slot.x + STRIDE) >> level;
			int y1 = (slot.y + STRIDE) >> level;
			int width = x1 - x0;
			int height = y1 - y0;
			if (width <= 0 || height <= 0) {
				continue;
			}
			NativeImage image = width == STRIDE && height == STRIDE ? physical : downsample(physical, width, height);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, level, 0, x0, y0, width, height, 0, 0);
			if (image != physical) {
				image.close();
			}
		}
		physical.close();
	}

	private TextureAtlasSprite createSprite(Slot slot) {
		SpriteContents contents = new SpriteContents(RESERVED, new FrameSize(SIZE, SIZE), slot.image);
		return TextureAtlasSpriteInvoker.spacefold$create(
				TextureAtlas.LOCATION_BLOCKS,
				contents,
				atlasWidth,
				atlasHeight,
				slot.x,
				slot.y,
				PAD);
	}

	private static final class Slot {
		final int index;
		final int x;
		final int y;
		final PixelKey key;
		TextureAtlasSprite sprite;
		NativeImage image;
		volatile long lastUse;

		Slot(int index, int x, int y, PixelKey key) {
			this.index = index;
			this.x = x;
			this.y = y;
			this.key = key;
		}
	}

	static final class PixelKey {
		final int[] pixels;
		private final int hash;

		PixelKey(int[] pixels) {
			this.pixels = pixels;
			this.hash = Arrays.hashCode(pixels);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof PixelKey other && Arrays.equals(pixels, other.pixels);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}
}
