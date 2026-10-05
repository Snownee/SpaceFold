package snownee.spacefold.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

@Mixin(TextureAtlasSprite.class)
public interface TextureAtlasSpriteInvoker {
	@Invoker("<init>")
	static TextureAtlasSprite spacefold$create(
			Identifier atlasLocation,
			SpriteContents contents,
			int atlasWidth,
			int atlasHeight,
			int x,
			int y,
			int padding) {
		throw new AssertionError();
	}
}
