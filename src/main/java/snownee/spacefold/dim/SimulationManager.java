package snownee.spacefold.dim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.SpaceFoldConfig;
import snownee.spacefold.block.InterfaceBlock;
import snownee.spacefold.block.SimulationRedstoneBlock;
import snownee.spacefold.block.entity.RepeaterBlockEntity;
import snownee.spacefold.network.SSimulationRatesPacket;
import snownee.spacefold.network.SSimulationRatesPacket.Entry;
import snownee.spacefold.network.SSimulationRatesPacket.Section;

public final class SimulationManager {
	private static final int ENTITY_TICKING_RADIUS = 2;
	private static final int UPDATE_INTERVAL = 10;
	private static final int SECTION_LINES = 5;
	private static final double WARMUP_FRACTION = 0.25;
	private static final double TRAILING_FRACTION = 0.25;

	private static final Int2ObjectMap<Session> ACTIVE = new Int2ObjectOpenHashMap<>();

	private static long serverTicks;
	private static long simTime;
	private static long copyTriggerOffset;

	private SimulationManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SimulationManager::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
	}

	public static void reset() {
		ACTIVE.clear();
		serverTicks = 0;
		simTime = 0;
		copyTriggerOffset = 0;
	}

	public static long gameTime() {
		return simTime;
	}

	public static void advanceGameTime() {
		simTime++;
	}

	public static long copyTriggerOffset() {
		return copyTriggerOffset;
	}

	public static void withCopyTriggerOffset(long offset, Runnable action) {
		copyTriggerOffset = offset;
		try {
			action.run();
		} finally {
			copyTriggerOffset = 0;
		}
	}

	private static int tickMultiplier() {
		return Math.max(1, SpaceFoldConfig.simulationTickMultiplier);
	}

	public static RoomStatus roomStatus(ServerLevel level, int room) {
		if (ACTIVE.containsKey(room)) {
			return RoomStatus.RUNNING;
		}
		return cooldownRemainingSeconds(level, room) > 0 ? RoomStatus.COOLDOWN : RoomStatus.READY;
	}

	public static int cooldownRemainingSeconds(ServerLevel level, int room) {
		long lastStart = RoomManager.lastStart(level, room);
		if (lastStart <= 0) {
			return 0;
		}
		long remaining = SpaceFoldConfig.simulationCooldown * 1000L - (System.currentTimeMillis() - lastStart);
		return remaining <= 0 ? 0 : (int) ((remaining + 999) / 1000);
	}

	public static void start(ServerLevel simulate, int room, @Nullable ResourceKey<Level> sourceDim, @Nullable BlockPos sourcePos) {
		RoomManager.setLastStart(simulate, room, System.currentTimeMillis());
		RoomManager.clearSimulationResult(simulate, room);
		long startTime = simulate.getGameTime();
		Session session = new Session(
				simulate,
				room,
				startTime,
				startTime + SpaceFoldConfig.simulationDuration * 20L * tickMultiplier(),
				materials(simulate, room),
				RoomManager.captureSnapshot(simulate, room),
				sourceDim,
				sourcePos,
				collectInterfaces(simulate, room),
				new Resources(),
				new Resources(),
				new Warmup(),
				new Counted());
		Session previous = ACTIVE.put(room, session);
		//noinspection ConstantValue
		if (previous != null) {
			setChunksLoaded(simulate.getServer(), previous, false);
		}
		setChunksLoaded(simulate.getServer(), session, true);
	}

	public static boolean hasActive() {
		return !ACTIVE.isEmpty();
	}

	public static int activeRoomAt(ServerLevel simulate, BlockPos pos) {
		int room = RoomManager.roomAt(simulate, pos);
		return ACTIVE.containsKey(room) ? room : -1;
	}

	public static void recordItem(int room, ItemVariant resource, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.inputs().items.mergeLong(resource, amount, Long::sum);
			}
		});
	}

	public static void recordFluid(int room, FluidVariant resource, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.inputs().fluids.mergeLong(resource, amount, Long::sum);
			}
		});
	}

	public static void recordEnergy(int room, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.inputs().energy += amount;
			}
		});
	}

	public static void recordOutputItem(int room, ItemVariant resource, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.outputs().items.mergeLong(resource, amount, Long::sum);
			}
		});
	}

	public static void recordOutputFluid(int room, FluidVariant resource, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.outputs().fluids.mergeLong(resource, amount, Long::sum);
			}
		});
	}

	public static void recordOutputEnergy(int room, long amount, TransactionContext transaction) {
		Session session = ACTIVE.get(room);
		transaction.addOuterCloseCallback(result -> {
			if (result.wasCommitted()) {
				advancePhases(session);
				session.outputs().energy += amount;
			}
		});
	}

	private static boolean counting(Session session) {
		return session.level().getGameTime() - session.startTime() > warmupTicks(session);
	}

	private static boolean trailing(Session session) {
		return session.level().getGameTime() >= session.endTime() - trailingTicks(session);
	}

	private static void advancePhases(Session session) {
		ensureWarmup(session);
		ensureCounted(session);
	}

	private static void ensureWarmup(Session session) {
		Warmup warmup = session.warmup();
		if (warmup.done || !counting(session)) {
			return;
		}
		warmup.done = true;
		double seconds = warmupTicks(session) / 20.0;
		if (seconds > 0) {
			warmup.inputs = rateSet(session.inputs(), seconds);
			warmup.outputs = rateSet(session.outputs(), seconds);
		}
		session.inputs().reset();
		session.outputs().reset();
		setRedstones(session, true);
	}

	private static void ensureCounted(Session session) {
		Counted counted = session.counted();
		if (counted.done || !trailing(session)) {
			return;
		}
		counted.done = true;
		counted.inputs = session.inputs().copy();
		counted.outputs = session.outputs().copy();
		session.inputs().reset();
		session.outputs().reset();
		setRedstones(session, false);
	}

	private static void setRedstones(Session session, boolean powered) {
		ServerLevel level = session.level();
		BlockPos origin = RoomManager.roomOrigin(session.room());
		int size = SpaceFoldConfig.roomSize;
		BlockPos center = origin.offset(size / 2, size / 2, size / 2);
		Predicate<Holder<PoiType>> predicate = holder -> CoreModule.SIMULATION_REDSTONE_POI.is(holder.value());
		Block block = CoreModule.SIMULATION_REDSTONE.get();
		level.getPoiManager()
				.findAll(predicate, pos -> insideRoom(origin, pos, size), center, size, PoiManager.Occupancy.ANY)
				.forEach(pos -> {
					BlockState state = level.getBlockState(pos);
					if (state.is(block) && state.getValue(SimulationRedstoneBlock.POWERED) != powered) {
						level.setBlock(pos, state.setValue(SimulationRedstoneBlock.POWERED, powered), Block.UPDATE_ALL);
					}
				});
	}

	private static boolean insideRoom(BlockPos origin, BlockPos pos, int size) {
		int lx = pos.getX() - origin.getX();
		int ly = pos.getY() - origin.getY();
		int lz = pos.getZ() - origin.getZ();
		return lx >= 0 && lx < size && ly >= 0 && ly < size && lz >= 0 && lz < size;
	}

	private static long warmupTicks(Session session) {
		return (long) ((session.endTime() - session.startTime()) * WARMUP_FRACTION);
	}

	private static long trailingTicks(Session session) {
		return (long) ((session.endTime() - session.startTime()) * TRAILING_FRACTION);
	}

	private static long countedTicks(Session session) {
		return session.endTime() - session.startTime() - warmupTicks(session) - trailingTicks(session);
	}

	private static double countedSeconds(Session session) {
		return countedTicks(session) / 20.0;
	}

	public static @Nullable BlockPos interfaceTarget(ResourceKey<Level> dim, BlockPos pos, Direction side) {
		for (Session session : ACTIVE.values()) {
			if (Objects.equals(pos, session.sourcePos()) && Objects.equals(dim, session.sourceDim())) {
				return session.targets()[side.ordinal()];
			}
		}
		return null;
	}

	private static BlockPos[] collectInterfaces(ServerLevel simulate, int room) {
		BlockPos origin = RoomManager.roomOrigin(room);
		BlockPos[] targets = new BlockPos[6];
		for (int dx = 0; dx < SpaceFoldConfig.roomSize; dx++) {
			for (int dy = 0; dy < SpaceFoldConfig.roomSize; dy++) {
				for (int dz = 0; dz < SpaceFoldConfig.roomSize; dz++) {
					BlockPos at = origin.offset(dx, dy, dz);
					BlockState state = simulate.getBlockState(at);
					if (state.getBlock() instanceof InterfaceBlock) {
						targets[state.getValue(InterfaceBlock.FACING).getOpposite().ordinal()] = at.immutable();
					}
				}
			}
		}
		return targets;
	}

	private static void onServerTick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		ServerLevel simulate = server.getLevel(Simulate.DIMENSION);
		if (simulate == null) {
			return;
		}
		long now = simulate.getGameTime();
		Iterator<Int2ObjectMap.Entry<Session>> iterator = ACTIVE.int2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			Int2ObjectMap.Entry<Session> entry = iterator.next();
			int room = entry.getIntKey();
			Session session = entry.getValue();
			advancePhases(session);
			if (now >= session.endTime()) {
				iterator.remove();
				end(server, simulate, room, session);
			}
		}
		serverTicks++;
		if (serverTicks % UPDATE_INTERVAL == 0) {
			broadcast(server, simulate, now);
		}
	}

	private static void broadcast(MinecraftServer server, ServerLevel simulate, long now) {
		for (Int2ObjectMap.Entry<Session> active : ACTIVE.int2ObjectEntrySet()) {
			int room = active.getIntKey();
			Session session = active.getValue();
			send(server, simulate, session, room, packet(session, now));
		}
	}

	private static void send(MinecraftServer server, ServerLevel simulate, Session session, int room, SSimulationRatesPacket packet) {
		for (ServerPlayer player : simulate.players()) {
			if (RoomManager.currentRoom(simulate, player) == room) {
				KPacketSender.send(packet, player);
			}
		}
		ResourceKey<Level> sourceDim = session.sourceDim();
		BlockPos sourcePos = session.sourcePos();
		if (sourceDim == null || sourcePos == null) {
			return;
		}
		ServerLevel source = server.getLevel(sourceDim);
		if (source == null) {
			return;
		}
		for (ServerPlayer player : source.players()) {
			if (isLookingAt(source, player, sourcePos)) {
				KPacketSender.send(packet, player);
			}
		}
	}

	private static boolean isLookingAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
	}

	private static SSimulationRatesPacket packet(Session session, long now) {
		advancePhases(session);
		long duration = session.endTime() - session.startTime();
		int total = (int) duration;
		int remaining = (int) Math.max(0L, session.endTime() - now);
		int progress = total <= 0 ? 100 : (int) Math.round((total - remaining) * 100.0 / total);
		double elapsed = (total - remaining) / 20.0;
		double warmupSeconds = warmupTicks(session) / 20.0;
		if (elapsed <= warmupSeconds) {
			return new SSimulationRatesPacket(progress, section(session.inputs(), elapsed), section(session.outputs(), elapsed));
		}
		double countedDuration = countedTicks(session) / 20.0;
		Counted counted = session.counted();
		if (counted.done) {
			return new SSimulationRatesPacket(
					progress,
					section(counted.inputs, countedDuration),
					section(counted.outputs, countedDuration));
		}
		double countedElapsed = Math.min(countedDuration, elapsed - warmupSeconds);
		double p = countedDuration <= 0 ? 1.0 : Math.min(1.0, countedElapsed / countedDuration);
		Warmup warmup = session.warmup();
		return new SSimulationRatesPacket(
				progress,
				blend(warmup.inputs, session.inputs(), countedElapsed, p),
				blend(warmup.outputs, session.outputs(), countedElapsed, p));
	}

	private static Section section(Resources resources, double elapsed) {
		if (elapsed <= 0) {
			return Section.EMPTY;
		}
		double divisor = elapsed;
		Map<ItemVariant, Double> items = new LinkedHashMap<>();
		for (Object2LongMap.Entry<ItemVariant> entry : resources.items.object2LongEntrySet()) {
			items.put(entry.getKey(), entry.getLongValue() / divisor);
		}
		Map<FluidVariant, Double> fluids = new LinkedHashMap<>();
		for (Object2LongMap.Entry<FluidVariant> entry : resources.fluids.object2LongEntrySet()) {
			fluids.put(entry.getKey(), entry.getLongValue() / divisor);
		}
		return section(items, fluids, resources.energy / divisor);
	}

	private static Section blend(SimulationResult.Rates warmup, Resources resources, double elapsed, double p) {
		double divisor = elapsed;
		Map<ItemVariant, Double> items = new LinkedHashMap<>();
		for (Object2DoubleMap.Entry<ItemVariant> entry : warmup.items().object2DoubleEntrySet()) {
			items.merge(entry.getKey(), entry.getDoubleValue() * (1.0 - p), Double::sum);
		}
		for (Object2LongMap.Entry<ItemVariant> entry : resources.items.object2LongEntrySet()) {
			items.merge(entry.getKey(), entry.getLongValue() / divisor * p, Double::sum);
		}
		Map<FluidVariant, Double> fluids = new LinkedHashMap<>();
		for (Object2DoubleMap.Entry<FluidVariant> entry : warmup.fluids().object2DoubleEntrySet()) {
			fluids.merge(entry.getKey(), entry.getDoubleValue() * (1.0 - p), Double::sum);
		}
		for (Object2LongMap.Entry<FluidVariant> entry : resources.fluids.object2LongEntrySet()) {
			fluids.merge(entry.getKey(), entry.getLongValue() / divisor * p, Double::sum);
		}
		return section(items, fluids, warmup.energy() * (1.0 - p) + resources.energy / divisor * p);
	}

	private static Section section(Map<ItemVariant, Double> items, Map<FluidVariant, Double> fluids, double energy) {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<ItemVariant, Double> entry : items.entrySet()) {
			if (entry.getValue() > 1.0E-9) {
				entries.add(new Entry(entry.getKey(), null, entry.getValue()));
			}
		}
		for (Map.Entry<FluidVariant, Double> entry : fluids.entrySet()) {
			if (entry.getValue() > 1.0E-9) {
				entries.add(new Entry(null, entry.getKey(), entry.getValue()));
			}
		}
		entries.sort(Comparator.comparingDouble(Entry::rate).reversed());
		double effectiveEnergy = energy > 1.0E-9 ? energy : 0;
		int entryCap = SECTION_LINES - (effectiveEnergy > 0 ? 1 : 0);
		int hidden = 0;
		if (entries.size() > entryCap) {
			hidden = entries.size() - (entryCap - 1);
			entries = new ArrayList<>(entries.subList(0, entryCap - 1));
		}
		return new Section(entries, effectiveEnergy, hidden);
	}

	private static void end(MinecraftServer server, ServerLevel simulate, int room, Session session) {
		setChunksLoaded(server, session, false);
		List<SimulationResult.Material> currentMaterials = materials(simulate, room);
		boolean blocksChanged = !currentMaterials.equals(session.materials());
		SimulationResult simulationResult = result(session, blocksChanged, tier(simulate, room));
		if (!simulationResult.failed()) {
			int snapshotId = RoomManager.allocateSnapshot(simulate, session.snapshot());
			simulationResult = simulationResult.withSnapshotId(snapshotId);
		}
		RoomManager.setSimulationResult(simulate, room, simulationResult);
		send(server, simulate, session, room, packet(session, session.endTime()));
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : List.copyOf(simulate.players())) {
			if (RoomManager.currentRoom(simulate, player) == room) {
				players.add(player);
				RoomManager.teleportToRoomOutside(player, Plan.DIMENSION, room);
			}
		}
		if (simulationResult.failed()) {
			String key = simulationResult.failure().orElse("tip.spacefold.simulation_failed");
			for (ServerPlayer player : players) {
				player.sendOverlayMessage(Component.translatable(key));
			}
		}
		if (blocksChanged && SpaceFoldConfig.debug) {
			for (Component line : materialChanges(session.materials(), currentMaterials)) {
				for (ServerPlayer player : players) {
					player.sendSystemMessage(line);
				}
			}
		}
		clearRoom(simulate, room, RoomManager.roomOrigin(room));
	}

	private static List<Component> materialChanges(List<SimulationResult.Material> before, List<SimulationResult.Material> after) {
		Map<ItemVariant, Integer> deltas = new LinkedHashMap<>();
		for (SimulationResult.Material material : before) {
			deltas.merge(material.item(), -material.count(), Integer::sum);
		}
		for (SimulationResult.Material material : after) {
			deltas.merge(material.item(), material.count(), Integer::sum);
		}
		List<Component> lines = new ArrayList<>();
		for (Map.Entry<ItemVariant, Integer> entry : deltas.entrySet()) {
			int delta = entry.getValue();
			if (delta != 0) {
				String amount = (delta > 0 ? "+" : "") + delta;
				lines.add(Component.translatable(
						"tip.spacefold.material_changed",
						entry.getKey().toStack().getHoverName(),
						amount));
			}
		}
		return lines;
	}

	private static SimulationResult result(Session session, boolean blocksChanged, int tier) {
		advancePhases(session);
		double seconds = countedSeconds(session);
		Counted counted = session.counted();
		SimulationResult.Rates rawInputs = rateSet(counted.done ? counted.inputs : session.inputs(), seconds);
		SimulationResult.Rates rawOutputs = rateSet(counted.done ? counted.outputs : session.outputs(), seconds);

		Object2DoubleMap<ItemVariant> items = new Object2DoubleLinkedOpenHashMap<>(rawOutputs.items());
		for (Object2DoubleMap.Entry<ItemVariant> entry : rawInputs.items().object2DoubleEntrySet()) {
			items.mergeDouble(entry.getKey(), -entry.getDoubleValue(), Double::sum);
		}
		Object2DoubleMap<FluidVariant> fluids = new Object2DoubleLinkedOpenHashMap<>(rawOutputs.fluids());
		for (Object2DoubleMap.Entry<FluidVariant> entry : rawInputs.fluids().object2DoubleEntrySet()) {
			fluids.mergeDouble(entry.getKey(), -entry.getDoubleValue(), Double::sum);
		}
		double energy = rawOutputs.energy() - rawInputs.energy();

		Object2DoubleMap<ItemVariant> inputItems = new Object2DoubleLinkedOpenHashMap<>();
		Object2DoubleMap<ItemVariant> outputItems = new Object2DoubleLinkedOpenHashMap<>();
		for (Object2DoubleMap.Entry<ItemVariant> entry : items.object2DoubleEntrySet()) {
			double value = entry.getDoubleValue();
			if (value > 1.0E-9) {
				outputItems.put(entry.getKey(), value);
			} else if (value < -1.0E-9) {
				inputItems.put(entry.getKey(), -value);
			}
		}
		Object2DoubleMap<FluidVariant> inputFluids = new Object2DoubleLinkedOpenHashMap<>();
		Object2DoubleMap<FluidVariant> outputFluids = new Object2DoubleLinkedOpenHashMap<>();
		for (Object2DoubleMap.Entry<FluidVariant> entry : fluids.object2DoubleEntrySet()) {
			double value = entry.getDoubleValue();
			if (value > 1.0E-9) {
				outputFluids.put(entry.getKey(), value);
			} else if (value < -1.0E-9) {
				inputFluids.put(entry.getKey(), -value);
			}
		}
		SimulationResult.Rates inputs = new SimulationResult.Rates(inputItems, inputFluids, Math.max(0, -energy));
		SimulationResult.Rates outputs = new SimulationResult.Rates(outputItems, outputFluids, Math.max(0, energy));
		boolean noOutput = outputItems.isEmpty() && outputFluids.isEmpty() && outputs.energy() <= 0;
		boolean tooManyTypes =
				inputs.kindCount() > SpaceFoldConfig.simulationMaxInputTypes ||
						outputs.kindCount() > SpaceFoldConfig.simulationMaxOutputTypes;
		boolean sufficient = false;
		for (double rate : outputItems.values()) {
			if (rate >= SpaceFoldConfig.simulationMinItemOutputRate) {
				sufficient = true;
				break;
			}
		}
		if (!sufficient) {
			for (double rate : outputFluids.values()) {
				if (rate >= SpaceFoldConfig.simulationMinFluidOutputRate) {
					sufficient = true;
					break;
				}
			}
		}
		if (!sufficient && outputs.energy() >= SpaceFoldConfig.simulationMinEnergyOutputRate) {
			sufficient = true;
		}
		String failure = null;
		if (blocksChanged) {
			failure = "tip.spacefold.simulation_blocks_changed";
		} else if (tooManyTypes) {
			failure = "tip.spacefold.simulation_too_many_types";
		} else if (noOutput) {
			failure = "tip.spacefold.simulation_no_output";
		} else if (!sufficient) {
			failure = "tip.spacefold.simulation_low_output";
		}
		return new SimulationResult(
				rawInputs,
				rawOutputs,
				session.materials(),
				Optional.ofNullable(failure),
				tier,
				RoomSnapshot.previewId(session.room));
	}

	private static int tier(ServerLevel level, int room) {
		BlockPos origin = RoomManager.roomOrigin(room);
		int max = 0;
		for (int dx = 1; dx < SpaceFoldConfig.roomSize - 1; dx++) {
			for (int dy = 1; dy < SpaceFoldConfig.roomSize - 1; dy++) {
				for (int dz = 1; dz < SpaceFoldConfig.roomSize - 1; dz++) {
					if (level.getBlockEntity(origin.offset(dx, dy, dz)) instanceof RepeaterBlockEntity repeater) {
						SimulationResult result = repeater.getResult();
						if (result != null) {
							max = Math.max(max, result.tier());
						}
					}
				}
			}
		}
		return max + 1;
	}

	static List<SimulationResult.Material> materials(ServerLevel level, int room) {
		BlockPos origin = RoomManager.roomOrigin(room);
		Map<ItemVariant, Integer> counts = new LinkedHashMap<>();
		ObjIntConsumer<ItemVariant> sink = (variant, count) -> counts.merge(variant, count, Integer::sum);
		for (int dx = 1; dx < SpaceFoldConfig.roomSize - 1; dx++) {
			for (int dy = 1; dy < SpaceFoldConfig.roomSize - 1; dy++) {
				for (int dz = 1; dz < SpaceFoldConfig.roomSize - 1; dz++) {
					BlockPos at = origin.offset(dx, dy, dz);
					BlockMaterials.collect(level, at, materialState(level, at), sink);
				}
			}
		}
		List<SimulationResult.Material> materials = new ArrayList<>();
		for (Map.Entry<ItemVariant, Integer> entry : counts.entrySet()) {
			materials.add(new SimulationResult.Material(entry.getKey(), entry.getValue()));
		}
		materials.sort(Comparator.comparing(material -> BuiltInRegistries.ITEM.getKey(material.item().getItem()).toString()));
		return materials;
	}

	private static BlockState materialState(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.MOVING_PISTON) && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving) {
			return moving.getMovedState();
		}
		return state;
	}

	private static SimulationResult.Rates rateSet(Resources resources, double seconds) {
		double divisor = seconds;
		Object2DoubleMap<ItemVariant> items = new Object2DoubleLinkedOpenHashMap<>();
		for (Object2LongMap.Entry<ItemVariant> entry : resources.items.object2LongEntrySet()) {
			items.put(entry.getKey(), entry.getLongValue() / divisor);
		}
		Object2DoubleMap<FluidVariant> fluids = new Object2DoubleLinkedOpenHashMap<>();
		for (Object2LongMap.Entry<FluidVariant> entry : resources.fluids.object2LongEntrySet()) {
			fluids.put(entry.getKey(), entry.getLongValue() / divisor);
		}
		return new SimulationResult.Rates(items, fluids, resources.energy / divisor);
	}

	private static void clearRoom(ServerLevel simulate, int room, BlockPos origin) {
		AABB bounds = new AABB(
				origin.getX(),
				origin.getY(),
				origin.getZ(),
				origin.getX() + SpaceFoldConfig.roomSize,
				origin.getY() + SpaceFoldConfig.roomSize,
				origin.getZ() + SpaceFoldConfig.roomSize);
		for (Entity entity : simulate.getEntitiesOfClass(Entity.class, bounds)) {
			if (!(entity instanceof ServerPlayer)) {
				entity.discard();
			}
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (int dx = 0; dx < SpaceFoldConfig.roomSize; dx++) {
			for (int dy = 0; dy < SpaceFoldConfig.roomSize; dy++) {
				for (int dz = 0; dz < SpaceFoldConfig.roomSize; dz++) {
					simulate.setBlock(origin.offset(dx, dy, dz), air, flags);
				}
			}
		}
	}

	private static void setChunksLoaded(MinecraftServer server, Session session, boolean loaded) {
		int radius = SpaceFoldConfig.simulationSourceChunkRadius;
		if (radius < 0 || session.sourceDim() == null || session.sourcePos() == null) {
			return;
		}

		TicketType ticket = CoreModule.SIMULATION_TICKET.getOrCreate();
		ServerLevel plan = server.getLevel(Plan.DIMENSION);
		ServerLevel simulate = server.getLevel(Simulate.DIMENSION);
		ServerLevel sourceLevel = server.getLevel(session.sourceDim());
		if (plan == null || simulate == null || sourceLevel == null) {
			return;
		}

		{
			BlockPos origin = RoomManager.roomOrigin(session.room());
			ChunkPos chunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
			if (loaded) {
				plan.getChunkSource().addTicketWithRadius(ticket, chunk, ENTITY_TICKING_RADIUS);
				simulate.getChunkSource().addTicketWithRadius(ticket, chunk, ENTITY_TICKING_RADIUS);
			} else {
				plan.getChunkSource().removeTicketWithRadius(ticket, chunk, ENTITY_TICKING_RADIUS);
				simulate.getChunkSource().removeTicketWithRadius(ticket, chunk, ENTITY_TICKING_RADIUS);
			}
		}
		{
			ChunkPos chunk = ChunkPos.containing(session.sourcePos());
			int loadRadius = radius + ENTITY_TICKING_RADIUS;
			if (loaded) {
				sourceLevel.getChunkSource().addTicketWithRadius(ticket, chunk, loadRadius);
			} else {
				sourceLevel.getChunkSource().removeTicketWithRadius(ticket, chunk, loadRadius);
			}
		}
	}

	private record Session(
			ServerLevel level,
			int room,
			long startTime,
			long endTime,
			List<SimulationResult.Material> materials,
			RoomSnapshot snapshot,
			@Nullable ResourceKey<Level> sourceDim,
			@Nullable BlockPos sourcePos,
			BlockPos[] targets,
			Resources inputs,
			Resources outputs,
			Warmup warmup,
			Counted counted) {}

	public enum RoomStatus {
		READY,
		RUNNING,
		COOLDOWN
	}

	private static final class Warmup {
		private boolean done;
		private SimulationResult.Rates inputs = SimulationResult.Rates.EMPTY;
		private SimulationResult.Rates outputs = SimulationResult.Rates.EMPTY;
	}

	private static final class Counted {
		private boolean done;
		private Resources inputs = new Resources();
		private Resources outputs = new Resources();
	}

	private static final class Resources {
		private final Object2LongMap<ItemVariant> items = new Object2LongOpenHashMap<>();
		private final Object2LongMap<FluidVariant> fluids = new Object2LongOpenHashMap<>();
		private long energy;

		private void reset() {
			items.clear();
			fluids.clear();
			energy = 0;
		}

		private Resources copy() {
			Resources copy = new Resources();
			copy.items.putAll(items);
			copy.fluids.putAll(fluids);
			copy.energy = energy;
			return copy;
		}
	}
}
