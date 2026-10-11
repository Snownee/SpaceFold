package snownee.spacefold.client.room;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
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
	private static final float CHARGE = 2400.0F;
	private static final float LEAF_LINK_DISTANCE = 110.0F;
	private static final float BRANCH_LINK_DISTANCE = 160.0F;
	private static final long TICK_MS = 16L;
	private static final int MAX_TICKS_PER_FRAME = 4;

	private final List<Node> nodes = new ArrayList<>();
	private @Nullable Node root;
	private float alpha;
	private float gravity;
	private long updatedAt = Util.getMillis();
	private double accumulator;

	public List<Node> nodes() {
		return nodes;
	}

	public void reset(int rootSnapshotId, List<SimulationResult> leaves) {
		nodes.clear();
		root = new Node(null, rootSnapshotId, 0, ItemStack.EMPTY);
		root.fixed = true;
		root.x = root.px = 0.0F;
		root.y = root.py = 0.0F;
		nodes.add(root);
		int count = leaves.size();
		for (int i = 0; i < count; i++) {
			SimulationResult result = leaves.get(i);
			Node node = new Node(root, result.snapshotId(), result.tier(), ItemStack.EMPTY);
			double angle = Math.PI * 2 * i / Math.max(1, count);
			node.x = (float) Math.cos(angle) * LEAF_LINK_DISTANCE;
			node.y = (float) Math.sin(angle) * LEAF_LINK_DISTANCE;
			node.px = node.x;
			node.py = node.y;
			root.children.add(node);
			nodes.add(node);
		}
		structureChanged();
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
		node.parent.children.remove(node);
	}

	private void structureChanged() {
		for (Node node : nodes) {
			node.weight = node.children.size() + (node.parent == null ? 0 : 1);
		}
		alpha = ALPHA_START;
		gravity = (float) (Math.atan(nodes.size() / 50.0) / Math.PI * 0.4);
		accumulator = 0.0;
		updatedAt = Util.getMillis();
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
			float distance = node.children.isEmpty() ? LEAF_LINK_DISTANCE : BRANCH_LINK_DISTANCE;
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
		private float x;
		private float y;
		private float px;
		private float py;
		private boolean fixed;

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
