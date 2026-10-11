package snownee.spacefold.client.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import snownee.spacefold.SpaceFoldClientConfig;
import snownee.spacefold.dim.RoomSnapshot;

public record VoxelRoomRenderState(
		List<BlockState> blocks,
		Map<Integer, CompoundTag> blockEntities,
		int size,
		int snapshotId,
		float yaw,
		float pitch,
		int x0,
		int y0,
		int x1,
		int y1,
		float scale,
		float alpha,
		@Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
	public VoxelRoomRenderState(
			List<BlockState> blocks,
			Map<Integer, CompoundTag> blockEntities,
			int size,
			int snapshotId,
			float yaw,
			float pitch,
			int x0,
			int y0,
			int x1,
			int y1,
			float scale,
			float alpha,
			@Nullable ScreenRectangle scissorArea) {
		this(
				blocks,
				blockEntities,
				size,
				snapshotId,
				yaw,
				pitch,
				x0,
				y0,
				x1,
				y1,
				scale,
				alpha,
				scissorArea,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}

	public static @Nullable VoxelRoomRenderState of(
			@Nullable RoomSnapshot snapshot,
			int snapshotId,
			float yaw,
			float pitch,
			int x0,
			int y0,
			int x1,
			int y1,
			@Nullable ScreenRectangle scissorArea) {
		return of(snapshot, snapshotId, yaw, pitch, x0, y0, x1, y1, 1.0F, 1.0F, scissorArea);
	}

	public static @Nullable VoxelRoomRenderState of(
			@Nullable RoomSnapshot snapshot,
			int snapshotId,
			float yaw,
			float pitch,
			int x0,
			int y0,
			int x1,
			int y1,
			float zoom,
			float alpha,
			@Nullable ScreenRectangle scissorArea) {
		if (snapshot == null) {
			return null;
		}
		List<BlockState> blocks = flatten(snapshot);
		Map<Integer, CompoundTag> blockEntities = new HashMap<>();
		for (RoomSnapshot.Entry entry : snapshot.blocks()) {
			entry.nbt().ifPresent(nbt -> blockEntities.put(index(entry, snapshot.size()), nbt));
		}
		float scale = Math.min(x1 - x0, y1 - y0) / (float) snapshot.size() * 0.75F * zoom;
		return new VoxelRoomRenderState(
				blocks,
				blockEntities,
				snapshot.size(),
				snapshotId,
				yaw,
				pitch,
				x0,
				y0,
				x1,
				y1,
				scale,
				alpha,
				scissorArea);
	}

	private static List<BlockState> flatten(RoomSnapshot snapshot) {
		int size = snapshot.size();
		List<BlockState> blocks = new ArrayList<>(Collections.nCopies(size * size * size, Blocks.AIR.defaultBlockState()));
		for (RoomSnapshot.Entry entry : snapshot.blocks()) {
			blocks.set(index(entry, size), snapshot.palette().get(entry.state()));
		}
		return blocks;
	}

	private static int index(RoomSnapshot.Entry entry, int size) {
		return entry.x() + entry.y() * size + entry.z() * size * size;
	}

	public void submitDebugOutline(GuiGraphicsExtractor graphics) {
		if (SpaceFoldClientConfig.debugPip && Minecraft.getInstance().hasControlDown()) {
			graphics.outline(x0, y0, x1 - x0, y1 - y0, 0xFFFF00FF);
		}
	}
}
