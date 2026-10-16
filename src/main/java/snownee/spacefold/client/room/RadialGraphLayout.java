package snownee.spacefold.client.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import snownee.spacefold.CoreModule;
import snownee.spacefold.dim.SimulationResult;

/**
 * 参考 CodeFlower（d3.layout.force）实现的力导向放射树：根节点固定在中心，父子间为弹簧约束，
 * 节点间存在库仑斥力，整体受指向中心的向心力，并以 alpha 模拟退火逐步收敛。
 * 参数按本界面节点像素尺寸放大；斥力用精确两两求和的 O(n^2)，等价于 theta=0 的 Barnes-Hut。
 */
public final class RadialGraphLayout {
	private static final float ALPHA_START = 0.1F;
	private static final float ALPHA_DECAY = 0.99F;
	private static final float ALPHA_MIN = 0.005F;
	private static final float FRICTION = 0.9F;
	private static final float CHARGE = 3600.0F;
	private static final float LEAF_LINK_DISTANCE = 100.0F;
	private static final float SUBTREE_LINK_SCALE = 14.0F;
	private static final float GRAVITY_SCALE = 0.5F;
	private static final long TICK_MS = 16L;
	private static final int MAX_TICKS_PER_FRAME = 4;

	private final List<Node> nodes = new ArrayList<>();
	private final Set<Integer> visitedSnapshotIds = new HashSet<>();
	private @Nullable Node root;
	private float alpha;
	private float gravity;
	private long updatedAt = Util.getMillis();
	private double accumulator;

	public List<Node> nodes() {
		return nodes;
	}

	public @Nullable Node root() {
		return root;
	}

	public void reset(int rootSnapshotId, int rootTier, ItemStack rootIcon, IntFunction<@Nullable List<ItemStack>> childrenLookup) {
		nodes.clear();
		visitedSnapshotIds.clear();
		root = new Node(null, rootSnapshotId, rootTier, rootIcon);
		root.fixed = true;
		root.x = root.px = 0.0F;
		root.y = root.py = 0.0F;
		nodes.add(root);
		if (rootSnapshotId >= 0) {
			visitedSnapshotIds.add(rootSnapshotId);
		}
		expandPending(childrenLookup);
		structureChanged();
	}

	/**
	 * 为所有尚未展开的结点挂载其子结点。childrenLookup 对给定快照返回子（repeater 复制物品）；
	 * 返回 null 表示对应快照尚未加载（留待下次重试），返回空列表表示已加载但无子结点。
	 */
	public boolean expandPending(IntFunction<@Nullable List<ItemStack>> childrenLookup) {
		boolean changed = false;
		for (int index = 0; index < nodes.size(); index++) {
			Node node = nodes.get(index);
			if (node.expanded || node.snapshotId < 0) {
				continue;
			}
			List<ItemStack> children = childrenLookup.apply(node.snapshotId);
			if (children == null) {
				continue;
			}
			node.expanded = true;
			int count = children.size();
			double baseAngle = node.parent == null
					? 0.0
					: Math.atan2(node.y - node.parent.y, node.x - node.parent.x);
			for (int i = 0; i < count; i++) {
				ItemStack icon = children.get(i);
				SimulationResult result = icon.get(CoreModule.SIMULATION_RESULT.get());
				int snapshotId = result == null ? SimulationResult.NO_SNAPSHOT : result.snapshotId();
				if (snapshotId >= 0 && !visitedSnapshotIds.add(snapshotId)) {
					continue;
				}
				int tier = result == null ? 1 : result.tier();
				Node child = new Node(node, snapshotId, tier, icon);
				double angle = count == 1 ? baseAngle : baseAngle + Math.PI * 2 * i / count;
				child.x = node.x + (float) Math.cos(angle) * LEAF_LINK_DISTANCE;
				child.y = node.y + (float) Math.sin(angle) * LEAF_LINK_DISTANCE;
				child.px = child.x;
				child.py = child.y;
				node.children.add(child);
				nodes.add(child);
				changed = true;
			}
		}
		if (changed) {
			structureChanged();
		}
		return changed;
	}

	public Node addChild(Node parent, ItemStack icon) {
		Node node = new Node(parent, -1, parent.tier + 1, icon);
		double angle = Math.random() * Math.PI * 2;
		node.x = parent.x + (float) Math.cos(angle) * LEAF_LINK_DISTANCE;
		node.y = parent.y + (float) Math.sin(angle) * LEAF_LINK_DISTANCE;
		node.px = node.x;
		node.py = node.y;
		parent.children.add(node);
		nodes.add(node);
		structureChanged();
		return node;
	}

	public void setNodeFixed(Node node, boolean fixed) {
		if (node != root) {
			node.fixed = fixed;
		}
	}

	public void moveNode(Node node, float x, float y) {
		if (node == root) {
			return;
		}
		node.x = node.px = x;
		node.y = node.py = y;
		alpha = ALPHA_START;
	}

	public void remove(Node node) {
		if (node == root) {
			return;
		}
		removeSubtree(node);
		structureChanged();
	}

	private void removeSubtree(Node node) {
		for (Node child : new ArrayList<>(node.children)) {
			removeSubtree(child);
		}
		nodes.remove(node);
		Objects.requireNonNull(node.parent).children.remove(node);
	}

	private void structureChanged() {
		for (Node node : nodes) {
			node.weight = node.children.size() + (node.parent == null ? 0 : 1);
		}
		if (root != null) {
			computeSubtreeSize(root);
		}
		alpha = ALPHA_START;
		gravity = (float) (Math.atan(nodes.size() / 50.0) / Math.PI * 0.4) * GRAVITY_SCALE;
		accumulator = 0.0;
		updatedAt = Util.getMillis();
	}

	private int computeSubtreeSize(Node node) {
		int size = 0;
		for (Node child : node.children) {
			size += 1 + computeSubtreeSize(child);
		}
		node.subtreeSize = size;
		return size;
	}

	public void simulate() {
		if (root == null) {
			return;
		}
		root.x = root.px = 0.0F;
		root.y = root.py = 0.0F;
		long now = Util.getMillis();
		long elapsed = now - updatedAt;
		updatedAt = now;
		if (alpha <= 0.0F) {
			return;
		}
		accumulator += elapsed;
		int ticks = 0;
		while (accumulator >= TICK_MS && ticks < MAX_TICKS_PER_FRAME) {
			tick();
			accumulator -= TICK_MS;
			ticks++;
			if (alpha <= 0.0F) {
				accumulator = 0.0;
				break;
			}
		}
		if (accumulator > TICK_MS * MAX_TICKS_PER_FRAME) {
			accumulator = 0.0;
		}
	}

	private void tick() {
		if ((alpha *= ALPHA_DECAY) < ALPHA_MIN) {
			alpha = 0.0F;
			return;
		}
		relaxLinks();
		applyGravity();
		applyCharge();
		integrate();
	}

	private void relaxLinks() {
		for (Node node : nodes) {
			Node parent = node.parent;
			if (parent == null) {
				continue;
			}
			float x = node.x - parent.x;
			float y = node.y - parent.y;
			float distSq = x * x + y * y;
			if (distSq <= 0.0F) {
				continue;
			}
			float dist = (float) Math.sqrt(distSq);
			float distance = LEAF_LINK_DISTANCE + SUBTREE_LINK_SCALE * (float) Math.sqrt(node.subtreeSize);
			float scalar = alpha * (dist - distance) / dist;
			float dx = x * scalar;
			float dy = y * scalar;
			float k = parent.weight / (float) (parent.weight + node.weight);
			node.x -= dx * k;
			node.y -= dy * k;
			parent.x += dx * (1.0F - k);
			parent.y += dy * (1.0F - k);
		}
	}

	private void applyGravity() {
		Node root = Objects.requireNonNull(this.root);
		float k = alpha * gravity;
		for (Node node : nodes) {
			node.x += (root.x - node.x) * k;
			node.y += (root.y - node.y) * k;
		}
	}

	private void applyCharge() {
		for (Node a : nodes) {
			if (a.fixed) {
				continue;
			}
			for (Node b : nodes) {
				if (a == b) {
					continue;
				}
				float dx = a.x - b.x;
				float dy = a.y - b.y;
				float distSq = dx * dx + dy * dy;
				if (distSq <= 1.0E-4F) {
					continue;
				}
				float k = alpha * CHARGE / distSq;
				a.px -= dx * k;
				a.py -= dy * k;
			}
		}
	}

	private void integrate() {
		for (Node node : nodes) {
			if (node.fixed) {
				node.x = node.px;
				node.y = node.py;
				continue;
			}
			float px = node.px;
			float py = node.py;
			float x = node.x;
			float y = node.y;
			node.px = x;
			node.py = y;
			node.x = x + (x - px) * FRICTION;
			node.y = y + (y - py) * FRICTION;
		}
	}

	public static final class Node {
		private final @Nullable Node parent;
		private final List<Node> children = new ArrayList<>();
		private final int snapshotId;
		private final int tier;
		private final ItemStack icon;
		private int weight = 1;
		private int subtreeSize;
		private float x;
		private float y;
		private float px;
		private float py;
		private boolean fixed;
		private boolean expanded;

		private Node(@Nullable Node parent, int snapshotId, int tier, ItemStack icon) {
			this.parent = parent;
			this.snapshotId = snapshotId;
			this.tier = tier;
			this.icon = icon;
		}

		public @Nullable Node parent() {
			return parent;
		}

		public int snapshotId() {
			return snapshotId;
		}

		public int tier() {
			return tier;
		}

		public ItemStack icon() {
			return icon;
		}

		public float x() {
			return x;
		}

		public float y() {
			return y;
		}
	}
}
