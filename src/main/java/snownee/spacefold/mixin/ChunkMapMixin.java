package snownee.spacefold.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import snownee.spacefold.dim.RoomManager;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Shadow
	@Final
	private Long2ByteMap chunkTypeCache;

	@Shadow
	@Final
	private static byte CHUNK_TYPE_FULL;

	@Inject(
			method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private void spacefold$skipEmptyRoomChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (!(chunk instanceof LevelChunk levelChunk)) {
			return;
		}
		Level level = levelChunk.getLevel();
		if (!RoomManager.isRoomDimension(level) || !isEmpty(levelChunk)) {
			return;
		}
		if (!chunk.tryMarkSaved()) {
			cir.setReturnValue(false);
			return;
		}
		ChunkPos pos = chunk.getPos();
		if (chunkTypeCache.get(pos.pack()) == CHUNK_TYPE_FULL) {
			((SimpleRegionStorage) (Object) this).write(pos, IOWorker.STORE_EMPTY);
		}
		cir.setReturnValue(true);
	}

	@Unique
	private static boolean isEmpty(LevelChunk chunk) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (!section.hasOnlyAir()) {
				return false;
			}
		}
		if (!chunk.getBlockEntitiesPos().isEmpty() || chunk.getBlockTicks().count() != 0 || chunk.getFluidTicks().count() != 0) {
			return false;
		}
		LevelLightEngine lightEngine = chunk.getLevel().getChunkSource().getLightEngine();
		for (int sectionY = lightEngine.getMinLightSection(); sectionY < lightEngine.getMaxLightSection(); sectionY++) {
			SectionPos sectionPos = SectionPos.of(chunk.getPos(), sectionY);
			DataLayer blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
			if (blockLight != null && !blockLight.isEmpty()) {
				return false;
			}
			DataLayer skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
			if (skyLight != null && !skyLight.isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
