package snownee.spacefold.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import snownee.kiwi.network.KPacketSender;
import snownee.spacefold.CoreModule;
import snownee.spacefold.block.entity.SimulatorBlockEntity;
import snownee.spacefold.dim.Plan;
import snownee.spacefold.dim.RoomManager;
import snownee.spacefold.dim.RoomSnapshot;
import snownee.spacefold.dim.SimulationManager;
import snownee.spacefold.dim.SimulationResult;
import snownee.spacefold.network.SSimulationResultPacket;

public class SimulatorMenu extends AbstractContainerMenu {
	public static final int BUTTON_TELEPORT = 0;
	public static final int BUTTON_TELEPORT_NO_WINDOWS = 1;
	public static final int BUTTON_SIMULATE = 2;

	private final BlockPos pos;
	private final @Nullable ServerLevel level;
	private final SimpleContainerData data = new SimpleContainerData(3);

	public SimulatorMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, BlockPos.ZERO);
	}

	public SimulatorMenu(int containerId, Inventory inventory, BlockPos pos) {
		super(CoreModule.SIMULATOR_MENU.get(), containerId);
		this.pos = pos;
		if (inventory.player.level() instanceof ServerLevel serverLevel) {
			this.level = serverLevel;
			data.set(0, serverLevel.getBlockEntity(pos) instanceof SimulatorBlockEntity blockEntity ? blockEntity.getRoomIndex() : -1);
		} else {
			this.level = null;
			data.set(0, -1);
		}
		updateStatus();
		addDataSlots(data);
		if (level != null && data.get(0) >= 0 && inventory.player instanceof ServerPlayer serverPlayer) {
			int room = data.get(0);
			SimulationResult result = RoomManager.simulationResult(level, room);
			KPacketSender.send(new SSimulationResultPacket(containerId, result), serverPlayer);
		}
	}

	@Override
	public void broadcastChanges() {
		updateStatus();
		super.broadcastChanges();
	}

	public int roomIndex() {
		return data.get(0);
	}

	public SimulationManager.RoomStatus roomStatus() {
		return SimulationManager.RoomStatus.values()[data.get(1)];
	}

	public int cooldownSeconds() {
		return data.get(2);
	}

	private void updateStatus() {
		int room = data.get(0);
		data.set(1, level == null ? SimulationManager.RoomStatus.READY.ordinal() : SimulationManager.roomStatus(level, room).ordinal());
		data.set(2, level == null ? 0 : SimulationManager.cooldownRemainingSeconds(level, room));
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		boolean windows = true;
		if (buttonId == BUTTON_TELEPORT_NO_WINDOWS) {
			buttonId = BUTTON_TELEPORT;
			windows = false;
		}
		if (buttonId == BUTTON_TELEPORT && player instanceof ServerPlayer serverPlayer) {
			RoomManager.useSimulator(serverPlayer.level(), serverPlayer, pos, windows);
			return true;
		}
		if (buttonId == BUTTON_SIMULATE && player instanceof ServerPlayer serverPlayer) {
			RoomManager.simulate(serverPlayer.level(), serverPlayer, pos);
			serverPlayer.closeContainer();
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
