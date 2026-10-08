package com.avatarbending.effect;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary blocks placed by bending (earth walls, ice spikes...). They only replace air or
 * replaceable blocks like grass and water, never drop items, and always turn back into the
 * original block: when they expire, when a player breaks them, and when the server stops.
 */
public final class TempBlocks {
	private record Key(RegistryKey<World> world, BlockPos pos) {
	}

	private record Entry(ServerWorld world, BlockPos pos, BlockState original, BlockState placed, long expireTick) {
	}

	private static final Map<Key, Entry> ENTRIES = new HashMap<>();
	private static long ticks;

	private TempBlocks() {
	}

	/**
	 * Places a temporary block.
	 *
	 * @return true if the block was placed
	 */
	public static boolean place(ServerWorld world, BlockPos pos, BlockState state, int duration) {
		if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) {
			return false;
		}
		Key key = new Key(world.getRegistryKey(), pos.toImmutable());
		if (ENTRIES.containsKey(key)) {
			return false;
		}
		BlockState current = world.getBlockState(pos);
		if (!(current.isAir() || current.isReplaceable()) || world.getBlockEntity(pos) != null) {
			return false;
		}
		// Flags: update clients, but do not trigger neighbor physics (keeps dripstone/sand stable).
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		ENTRIES.put(key, new Entry(world, key.pos(), current, state, ticks + duration));
		return true;
	}

	/**
	 * Temporarily removes a natural block (used by Fissure). Only simple blocks are removed: never
	 * containers, unbreakable or very hard blocks, fluids or falling blocks. Neighbor physics are
	 * not triggered, so nothing collapses or flows while the gap is open.
	 *
	 * @return true if the block was removed
	 */
	public static boolean remove(ServerWorld world, BlockPos pos, int duration) {
		if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) {
			return false;
		}
		Key key = new Key(world.getRegistryKey(), pos.toImmutable());
		if (ENTRIES.containsKey(key)) {
			return false;
		}
		BlockState current = world.getBlockState(pos);
		float hardness = current.getHardness(world, pos);
		if (current.isAir() || !current.getFluidState().isEmpty() || world.getBlockEntity(pos) != null
			|| hardness < 0 || hardness >= 20 || current.getBlock() instanceof net.minecraft.block.FallingBlock
			|| !current.isSolidBlock(world, pos)) {
			return false;
		}
		BlockState air = net.minecraft.block.Blocks.AIR.getDefaultState();
		world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		ENTRIES.put(key, new Entry(world, key.pos(), current, air, ticks + duration));
		return true;
	}

	public static boolean isTemp(World world, BlockPos pos) {
		return ENTRIES.containsKey(new Key(world.getRegistryKey(), pos));
	}

	/** Restores a temporary block immediately (used when a player breaks it). */
	public static boolean restore(World world, BlockPos pos) {
		Entry entry = ENTRIES.remove(new Key(world.getRegistryKey(), pos));
		if (entry == null) {
			return false;
		}
		revert(entry);
		return true;
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		if (ENTRIES.isEmpty()) {
			return;
		}
		List<Entry> expired = new ArrayList<>();
		for (Entry entry : ENTRIES.values()) {
			if (entry.expireTick() <= ticks) {
				expired.add(entry);
			}
		}
		// Higher blocks first so tips and tops never lose their support before they disappear.
		expired.sort(Comparator.comparingInt((Entry e) -> e.pos().getY()).reversed());
		for (Entry entry : expired) {
			ENTRIES.remove(new Key(entry.world().getRegistryKey(), entry.pos()));
			revert(entry);
		}
	}

	private static void revert(Entry entry) {
		ServerWorld world = entry.world();
		// Reading the state loads the chunk if needed, so blocks are restored even far away.
		BlockState now = world.getBlockState(entry.pos());
		boolean removal = entry.placed().isAir();
		if (now.equals(entry.placed()) || now.isAir() || (removal && now.isReplaceable())) {
			int flags = entry.original().getFluidState().isEmpty()
				? Block.NOTIFY_LISTENERS | Block.FORCE_STATE
				: Block.NOTIFY_ALL;
			world.setBlockState(entry.pos(), entry.original(), flags);
		}
	}

	/** Restores every temporary block. Called when the server stops so nothing stays forever. */
	public static void restoreAll() {
		List<Entry> all = new ArrayList<>(ENTRIES.values());
		ENTRIES.clear();
		all.sort(Comparator.comparingInt((Entry e) -> e.pos().getY()).reversed());
		for (Entry entry : all) {
			revert(entry);
		}
	}

	public static int count() {
		return ENTRIES.size();
	}
}
