package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.common.base.Preconditions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.Vec3;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFold;
import snownee.spacefold.block.ChamberWallBlock;
import snownee.spacefold.block.InterfaceBlock;
import snownee.spacefold.block.SimulatorBlock;
import snownee.spacefold.block.entity.SimulatorBlockEntity;
import snownee.spacefold.network.SHighlightBlocksPacket;

public final class RoomManager {
	public static final int HIGHLIGHT_COLOR = 0xFF3B30;
	public static final int HIGHLIGHT_LIFE = 200;

	private static final int TELEPORT_PRELOAD_RADIUS = 2;
	private static final int MAX_SNAPSHOTS_PER_ROOM = 3;

	private static final SavedDataType<PlanRooms> ROOMS = new SavedDataType<>(
			SpaceFold.id("rooms"),
			PlanRooms::new,
			PlanRooms.CODEC,
			DataFixTypes.LEVEL);

	private static final Direction[] FACE_DIRECTIONS = {
			Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

	private RoomManager() {
	}

	public static void useSimulator(ServerLevel level, ServerPlayer player, BlockPos pos, boolean windows) {
		SimulatorBlockEntity blockEntity = (SimulatorBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
		int room = blockEntity.getRoomIndex();
		if (isRoomDimension(level.dimension())) {
			GlobalPos sourceRef = room < 0 ? null : source(level, room);
			if (sourceRef == null) {
				player.sendOverlayMessage(Component.translatable("tip.spacefold.link_broken"));
				return;
			}
			ServerLevel destination = level.getServer().getLevel(sourceRef.dimension());
			BlockPos link = sourceRef.pos();
			if (destination == null || !(destination.getBlockState(link).getBlock() instanceof SimulatorBlock)) {
				player.sendOverlayMessage(Component.translatable("tip.spacefold.link_broken"));
				return;
			}
			Vec3 landing = safeLanding(destination, link);
			teleportLookingAt(player, destination, landing.x, landing.y, landing.z, link);
			return;
		}

		ServerLevel plan = Objects.requireNonNull(level.getServer().getLevel(Plan.DIMENSION));
		if (room < 0) {
			room = allocate(plan, player.getUUID());
			blockEntity.setRoom(room);
		}
		setSource(level, room, pos, level.dimension());
		BlockPos inner = innerPos(room);
		if (!(plan.getBlockState(inner).getBlock() instanceof SimulatorBlock)) {
			buildRoom(plan, room, windows);
		}
		((SimulatorBlockEntity) Objects.requireNonNull(plan.getBlockEntity(inner))).setRoom(room);

		int targetRoom = room;
		ResourceKey<Level> sourceDim = level.dimension();
		preloadAndRun(plan, targetRoom, () -> teleportIntoRoom(player, sourceDim, Plan.DIMENSION, targetRoom));
	}

	public static boolean isRoomDimension(ResourceKey<Level> dimension) {
		return dimension.equals(Plan.DIMENSION) || dimension.equals(Simulate.DIMENSION);
	}

	public static boolean simulate(ServerLevel level, ServerPlayer player, BlockPos pos) {
		SimulatorBlockEntity blockEntity = (SimulatorBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
		int room = blockEntity.getRoomIndex();
		if (room < 0) {
			return false;
		}
		SimulationManager.RoomStatus status = SimulationManager.roomStatus(level, room);
		if (status != SimulationManager.RoomStatus.READY) {
			player.sendOverlayMessage(Component.translatable(status == SimulationManager.RoomStatus.RUNNING ?
					"tip.spacefold.simulation_running" : "tip.spacefold.simulation_cooldown"));
			return false;
		}
		ServerLevel source = Objects.requireNonNull(level.getServer().getLevel(Plan.DIMENSION));
		ServerLevel destination = Objects.requireNonNull(level.getServer().getLevel(Simulate.DIMENSION));
		RoomCheck check = checkRoom(source, room);
		if (!check.valid()) {
			if (check.reason() != null) {
				player.sendOverlayMessage(check.reason());
			}
			KPacketSender.send(new SHighlightBlocksPacket(check.illegal(), HIGHLIGHT_COLOR, HIGHLIGHT_LIFE), player);
			return false;
		}
		setInitiator(level, room, player.getUUID());
		mirror(source, destination, room);
		if (level.dimension().equals(Plan.DIMENSION)) {
			GlobalPos ref = source(level, room);
			if (ref == null) {
				return false;
			}
			startAndTeleportWhenReady(player, destination, room, ref.dimension(), ref.pos());
		} else {
			SimulationManager.start(destination, room, level.dimension(), pos);
		}
		return true;
	}

	private static void startAndTeleportWhenReady(
			ServerPlayer player,
			ServerLevel simulateLevel,
			int room,
			ResourceKey<Level> sourceDim,
			BlockPos sourcePos) {
		preloadAndRun(
				simulateLevel, room, () -> {
					SimulationManager.start(simulateLevel, room, sourceDim, sourcePos);
					teleportIntoRoom(player, Plan.DIMENSION, Simulate.DIMENSION, room);
				});
	}

	private static void preloadAndRun(ServerLevel level, int room, Runnable action) {
		BlockPos origin = roomOrigin(room);
		ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		TicketType ticket = CoreModule.SIMULATION_TICKET.getOrCreate();
		level.getChunkSource()
				.addTicketAndLoadWithRadius(ticket, chunk, TELEPORT_PRELOAD_RADIUS)
				.whenComplete((unused, throwable) -> level.getServer().execute(() -> {
					level.getChunkSource().removeTicketWithRadius(ticket, chunk, TELEPORT_PRELOAD_RADIUS);
					action.run();
				}));
	}

	private static void teleportIntoRoom(ServerPlayer player, ResourceKey<Level> fromDimension, ResourceKey<Level> toDimension, int room) {
		if (player.isRemoved() || !player.level().dimension().equals(fromDimension)) {
			return;
		}
		teleportToRoomOutside(player, toDimension, room);
		PlanConfinement.lock(player, room);
		PlanFlight.enable(player);
	}

	private static void mirror(ServerLevel source, ServerLevel destination, int room) {
		BlockPos origin = roomOrigin(room);
		BlockPos max = origin.offset(Plan.ROOM_SIZE - 1, Plan.ROOM_SIZE - 1, Plan.ROOM_SIZE - 1);
		destination.getChunkAt(origin);
		destination.getChunkAt(max);
		BoundingBox box = BoundingBox.fromCorners(origin, max);
		HolderLookup.Provider registries = source.registryAccess();
		RoomProtection.runInternal(() -> {
			for (int dx = 0; dx < Plan.ROOM_SIZE; dx++) {
				for (int dy = 0; dy < Plan.ROOM_SIZE; dy++) {
					for (int dz = 0; dz < Plan.ROOM_SIZE; dz++) {
						BlockPos at = origin.offset(dx, dy, dz);
						BlockState state = source.getBlockState(at);
						destination.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						if (!state.isAir()) {
							BlockEntity sourceBlockEntity = source.getBlockEntity(at);
							if (sourceBlockEntity != null) {
								CompoundTag tag = sourceBlockEntity.saveWithFullMetadata(registries);
								BlockEntity targetBlockEntity = BlockEntity.loadStatic(at, state, tag, registries);
								if (targetBlockEntity != null) {
									destination.setBlockEntity(targetBlockEntity);
								}
							}
						}
					}
				}
			}
			destination.getBlockTicks().clearArea(box);
			destination.getBlockTicks().copyAreaFrom(source.getBlockTicks(), box, Vec3i.ZERO);
			destination.getFluidTicks().clearArea(box);
			destination.getFluidTicks().copyAreaFrom(source.getFluidTicks(), box, Vec3i.ZERO);
		});
	}

	public static RoomCheck checkRoom(ServerLevel plan, int room) {
		BlockPos origin = roomOrigin(room);
		int max = Plan.ROOM_SIZE - 1;
		List<BlockPos> illegal = new ArrayList<>();
		List<List<BlockPos>> faces = new ArrayList<>(6);
		for (int i = 0; i < 6; i++) {
			faces.add(new ArrayList<>());
		}
		for (int dx = 0; dx < Plan.ROOM_SIZE; dx++) {
			for (int dy = 0; dy < Plan.ROOM_SIZE; dy++) {
				for (int dz = 0; dz < Plan.ROOM_SIZE; dz++) {
					BlockPos at = origin.offset(dx, dy, dz);
					BlockState state = plan.getBlockState(at);
					Block block = state.getBlock();
					if (block instanceof InterfaceBlock) {
						if (dx == 0) {
							faces.get(0).add(at.immutable());
						}
						if (dx == max) {
							faces.get(1).add(at.immutable());
						}
						if (dy == 0) {
							faces.get(2).add(at.immutable());
						}
						if (dy == max) {
							faces.get(3).add(at.immutable());
						}
						if (dz == 0) {
							faces.get(4).add(at.immutable());
						}
						if (dz == max) {
							faces.get(5).add(at.immutable());
						}
						continue;
					}
					if (dx == 0 || dx == max || dy == 0 || dy == max || dz == 0 || dz == max) {
						continue;
					}
					if (block instanceof SimulatorBlock || CoreModule.REPEATER.is(block)) {
						continue;
					}
					if (state.is(CoreModule.ROOM_INVALID)) {
						illegal.add(at.immutable());
						continue;
					}
					if (state.is(CoreModule.ROOM_IGNORED)) {
						continue;
					}
					BlockEntity blockEntity = plan.getBlockEntity(at);
					if (blockEntity != null && !BlockContentChecker.isEmpty(plan, at, blockEntity)) {
						illegal.add(at.immutable());
					}
				}
			}
		}
		List<Direction> overloaded = new ArrayList<>();
		for (int i = 0; i < faces.size(); i++) {
			List<BlockPos> face = faces.get(i);
			if (face.size() > 1) {
				overloaded.add(FACE_DIRECTIONS[i]);
				illegal.addAll(face);
			}
		}
		Component reason = null;
		if (!overloaded.isEmpty()) {
			reason = Component.translatable("tip.spacefold.interface_limit", faceList(overloaded));
		} else if (!illegal.isEmpty()) {
			reason = Component.translatable("tip.spacefold.room_invalid");
		}
		return new RoomCheck(illegal.isEmpty(), reason, illegal, SimulationManager.materials(plan, room));
	}

	private static Component faceList(List<Direction> faces) {
		MutableComponent list = Component.empty();
		for (int i = 0; i < faces.size(); i++) {
			if (i > 0) {
				list.append(Component.literal(", "));
			}
			list.append(Component.translatable("face.spacefold." + faces.get(i).getSerializedName()));
		}
		return list;
	}

	public static int createRoom(ServerLevel plan, UUID creator) {
		return allocate(plan, creator);
	}

	private static int allocate(ServerLevel plan, UUID creator) {
		PlanRooms rooms = rooms(plan);
		int index = rooms.nextIndex++;
		rooms.rooms.put(index, roomData(rooms, index).withCreator(creator));
		rooms.setDirty();
		return index;
	}

	private static PlanRooms rooms(ServerLevel level) {
		return level.getServer().getDataStorage().computeIfAbsent(ROOMS);
	}

	public static @Nullable GlobalPos source(ServerLevel level, int room) {
		return rooms(level).sources.get(room);
	}

	public static void setSource(ServerLevel level, int room, BlockPos pos, ResourceKey<Level> dimension) {
		PlanRooms rooms = rooms(level);
		rooms.sources.put(room, GlobalPos.of(dimension, pos.immutable()));
		rooms.setDirty();
	}

	public static long lastStart(ServerLevel level, int room) {
		PlanRooms.RoomData data = rooms(level).rooms.get(room);
		return data == null ? 0L : data.lastStart();
	}

	public static void setLastStart(ServerLevel level, int room, long time) {
		PlanRooms rooms = rooms(level);
		rooms.rooms.put(room, roomData(rooms, room).withLastStart(time));
		rooms.setDirty();
	}

	public static void setInitiator(ServerLevel level, int room, UUID initiator) {
		PlanRooms rooms = rooms(level);
		rooms.rooms.put(room, roomData(rooms, room).withInitiator(initiator));
		rooms.setDirty();
	}

	public static @Nullable RoomSnapshot previewSnapshot(ServerLevel level, int room) {
		return level.getServer().getDataStorage().get(RoomSnapshot.type(RoomSnapshot.previewId(room)));
	}

	public static void setPreviewSnapshot(ServerLevel level, int room, RoomSnapshot snapshot) {
		level.getServer().getDataStorage().set(RoomSnapshot.type(RoomSnapshot.previewId(room)), snapshot);
	}

	public static @Nullable SimulationResult simulationResult(ServerLevel level, int room) {
		PlanRooms.RoomData data = rooms(level).rooms.get(room);
		if (data == null) {
			return null;
		}
		SimulationResult result = data.result();
		if (result == null) {
			if (level.dimension().equals(Plan.DIMENSION)) {
				RoomManager.setPreviewSnapshot(level, room, RoomManager.captureSnapshot(level, room));
				result = SimulationResult.previewOnly(RoomSnapshot.previewId(room));
			} else if (RoomManager.previewSnapshot(level, room) != null) {
			 	result = SimulationResult.previewOnly(RoomSnapshot.previewId(room));
			}
		}
		return result;
	}

	public static void setSimulationResult(ServerLevel level, int room, SimulationResult result) {
		PlanRooms rooms = rooms(level);
		rooms.rooms.put(room, roomData(rooms, room).withResult(result));
		rooms.setDirty();
	}

	public static void clearSimulationResult(ServerLevel level, int room) {
		PlanRooms rooms = rooms(level);
		PlanRooms.RoomData data = rooms.rooms.get(room);
		if (data == null || data.result() == null) {
			return;
		}
		rooms.rooms.put(room, data.withResult(null));
		rooms.setDirty();
	}

	private static PlanRooms.RoomData roomData(PlanRooms rooms, int room) {
		PlanRooms.RoomData data = rooms.rooms.get(room);
		return data == null ? new PlanRooms.RoomData(null, null, null, 0L) : data;
	}

	public static RoomSnapshot captureSnapshot(ServerLevel level, int room) {
		return RoomSnapshot.capture(level, room, roomOrigin(room), Plan.ROOM_SIZE);
	}

	public static int allocateSnapshot(ServerLevel level, RoomSnapshot snapshot) {
		PlanRooms rooms = rooms(level);
		SavedDataStorage storage = level.getServer().getDataStorage();
		List<Integer> existing = rooms.snapshots.get(snapshot.room());
		if (existing != null) {
			for (int i = existing.size() - 1; i >= 0; i--) {
				int id = existing.get(i);
				if (snapshot.equals(storage.get(RoomSnapshot.type(id)))) {
					return id;
				}
			}
		}
		int id = rooms.nextSnapshotId++;
		List<Integer> ids = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
		ids.add(id);
		while (ids.size() > MAX_SNAPSHOTS_PER_ROOM) {
			ids.remove(0);
		}
		rooms.snapshots.put(snapshot.room(), ids);
		rooms.setDirty();
		storage.set(RoomSnapshot.type(id), snapshot);
		return id;
	}

	public static int createSnapshotId(ServerLevel level) {
		PlanRooms rooms = rooms(level);
		int id = rooms.nextSnapshotId++;
		rooms.setDirty();
		return id;
	}

	public static void putSnapshot(ServerLevel level, int id, RoomSnapshot snapshot) {
		level.getServer().getDataStorage().set(RoomSnapshot.type(id), snapshot);
	}

	public static void placeSnapshot(ServerLevel plan, int room, RoomSnapshot snapshot) {
		BlockPos origin = roomOrigin(room);
		plan.getChunkAt(origin);
		HolderLookup.Provider registries = plan.registryAccess();
		RoomProtection.runInternal(() -> {
			for (RoomSnapshot.Entry entry : snapshot.blocks()) {
				BlockPos at = origin.offset(entry.x(), entry.y(), entry.z());
				BlockState state = snapshot.palette().get(entry.state());
				plan.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				CompoundTag tag = entry.nbt().orElse(null);
				if (tag != null) {
					BlockEntity blockEntity = BlockEntity.loadStatic(at, state, tag, registries);
					if (blockEntity != null) {
						plan.setBlockEntity(blockEntity);
					}
				}
			}
		});
		BlockPos inner = innerPos(room);
		if (plan.getBlockEntity(inner) instanceof SimulatorBlockEntity simulator) {
			simulator.setRoom(room);
		}
	}

	public static @Nullable RoomSnapshot snapshot(ServerLevel level, int id) {
		if (id < 0) {
			return null;
		}
		return level.getServer().getDataStorage().get(RoomSnapshot.type(id));
	}

	public static BlockPos roomOrigin(int room) {
		return Plan.roomOrigin(room);
	}

	public static BlockPos innerPos(int room) {
		return roomOrigin(room).offset(Plan.INNER_POS_OFFSET);
	}

	private static void buildRoom(ServerLevel plan, int room, boolean windows) {
		BlockPos origin = roomOrigin(room);
		plan.getChunkAt(origin);
		BlockPos inner = innerPos(room);
		RoomProtection.runInternal(() -> {
			BlockState wall = CoreModule.CHAMBER_WALL.get().defaultBlockState();
			BlockState window = windows ? CoreModule.CHAMBER_WINDOW.get().defaultBlockState() : wall;
			BlockState air = Blocks.AIR.defaultBlockState();
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (int dx = 0; dx < Plan.ROOM_SIZE; dx++) {
				for (int dy = 0; dy < Plan.ROOM_SIZE; dy++) {
					for (int dz = 0; dz < Plan.ROOM_SIZE; dz++) {
						Direction facing = inwardDirection(dx, dy, dz);
						BlockState state = facing == null ? air : (facing == Direction.UP ? wall : window).setValue(
								ChamberWallBlock.FACING,
								facing);
						plan.setBlock(origin.offset(dx, dy, dz), state, flags);
					}
				}
			}
			plan.setBlock(inner, CoreModule.SIMULATOR.get().defaultBlockState(), Block.UPDATE_ALL);
		});
		((SimulatorBlockEntity) Objects.requireNonNull(plan.getBlockEntity(inner))).setRoom(room);
	}

	static @Nullable Direction inwardDirection(int dx, int dy, int dz) {
		int max = Plan.ROOM_SIZE - 1;
		boolean xMin = dx == 0;
		boolean xMax = dx == max;
		boolean yMin = dy == 0;
		boolean yMax = dy == max;
		boolean zMin = dz == 0;
		boolean zMax = dz == max;
		int boundaries = (xMin || xMax ? 1 : 0) + (yMin || yMax ? 1 : 0) + (zMin || zMax ? 1 : 0);
		if (boundaries != 1) {
			return null;
		}
		if (xMin) {
			return Direction.EAST;
		}
		if (xMax) {
			return Direction.WEST;
		}
		if (zMin) {
			return Direction.SOUTH;
		}
		if (zMax) {
			return Direction.NORTH;
		}
		if (yMin) {
			return Direction.UP;
		}
		return Direction.DOWN;
	}

	public static int roomAt(ServerLevel plan, BlockPos pos) {
		int gridX = Math.floorDiv(pos.getX(), Plan.ROOM_GAP);
		int gridZ = Math.floorDiv(pos.getZ(), Plan.ROOM_GAP);
		int localX = pos.getX() - gridX * Plan.ROOM_GAP;
		int localZ = pos.getZ() - gridZ * Plan.ROOM_GAP;
		int localY = pos.getY() - Plan.FLOOR_Y;
		if (localX >= Plan.ROOM_SIZE || localZ >= Plan.ROOM_SIZE || localY < 0 || localY >= Plan.ROOM_SIZE) {
			return -1;
		}
		int room = gridZ * Plan.GRID_WIDTH + gridX;
		return room >= 0 && room < roomCount(plan) ? room : -1;
	}

	public static boolean isShell(int room, BlockPos pos) {
		BlockPos origin = roomOrigin(room);
		int lx = pos.getX() - origin.getX();
		int ly = pos.getY() - origin.getY();
		int lz = pos.getZ() - origin.getZ();
		int max = Plan.ROOM_SIZE - 1;
		return lx == 0 || lx == max || ly == 0 || ly == max || lz == 0 || lz == max;
	}

	public static int currentRoom(ServerLevel plan, ServerPlayer player) {
		int baseX = Math.floorDiv((int) Math.floor(player.getX()), Plan.ROOM_GAP);
		int baseZ = Math.floorDiv((int) Math.floor(player.getZ()), Plan.ROOM_GAP);
		int count = roomCount(plan);
		for (int gx = baseX; gx <= baseX + 1; gx++) {
			for (int gz = baseZ; gz <= baseZ + 1; gz++) {
				int room = gz * Plan.GRID_WIDTH + gx;
				if (room < 0 || room >= count) {
					continue;
				}
				if (contains(room, player.getX(), player.getY(), player.getZ())) {
					return room;
				}
			}
		}
		return -1;
	}

	private static boolean contains(int room, double x, double y, double z) {
		BlockPos origin = roomOrigin(room);
		double minX = origin.getX() - Plan.ROOM_MARGIN;
		double maxX = origin.getX() + Plan.ROOM_SIZE - 1 + Plan.ROOM_MARGIN;
		double minY = origin.getY() - Plan.ROOM_MARGIN;
		double maxY = origin.getY() + Plan.ROOM_SIZE - 1 + Plan.ROOM_MARGIN;
		double minZ = origin.getZ() - Plan.ROOM_MARGIN;
		double maxZ = origin.getZ() + Plan.ROOM_SIZE - 1 + Plan.ROOM_MARGIN;
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public static int nearestRoom(ServerLevel plan, BlockPos pos) {
		int count = roomCount(plan);
		if (count <= 0) {
			return -1;
		}
		int lastGridZ = (count - 1) / Plan.GRID_WIDTH;
		int gridX = Math.clamp(Math.floorDiv(pos.getX(), Plan.ROOM_GAP), 0, Plan.GRID_WIDTH - 1);
		int gridZ = Math.clamp(Math.floorDiv(pos.getZ(), Plan.ROOM_GAP), 0, lastGridZ);
		gridX = Math.min(gridX, count - 1 - gridZ * Plan.GRID_WIDTH);
		return gridZ * Plan.GRID_WIDTH + gridX;
	}

	public static int roomCount(ServerLevel plan) {
		return rooms(plan).nextIndex;
	}

	static void teleportToRoomOutside(ServerPlayer player, ResourceKey<Level> dimension, int room) {
		Preconditions.checkArgument(isRoomDimension(dimension), "Dimension must be a room dimension");
		BlockPos origin = roomOrigin(room);
		ServerLevel level = Objects.requireNonNull(player.level().getServer()).getLevel(dimension);
		if (level == null) {
			return;
		}
		teleportLookingAt(player, level, origin.getX() - 1.5, Plan.FLOOR_Y + 2.0, origin.getZ() + Plan.ROOM_SIZE / 2.0, innerPos(room));
	}

	static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
		player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yRot, xRot, Set.of(), TeleportTransition.DO_NOTHING));
	}

	private static void teleportLookingAt(ServerPlayer player, ServerLevel level, double x, double y, double z, BlockPos target) {
		double dx = target.getX() + 0.5 - x;
		double dy = target.getY() + 0.5 - (y + player.getEyeHeight());
		double dz = target.getZ() + 0.5 - z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yRot = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float xRot = (float) -(Mth.atan2(dy, horizontal) * (180.0 / Math.PI));
		teleport(player, level, x, y, z, yRot, xRot);
	}

	private static Vec3 safeLanding(ServerLevel level, BlockPos link) {
		return RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, link)
				.orElseGet(() -> new Vec3(link.getX() + 0.5, link.getY() + 1.0, link.getZ() + 0.5));
	}

	public record RoomCheck(
			boolean valid,
			@Nullable Component reason,
			List<BlockPos> illegal,
			List<SimulationResult.Material> materials) {}
}
