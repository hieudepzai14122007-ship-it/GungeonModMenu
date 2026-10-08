package com.avatarbending.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.world.World;

/**
 * A projectile drawn as a spinning block (boulders, ice shards, meteors). The block is purely
 * visual: no block is taken from or added to the world.
 */
public abstract class BlockProjectileEntity extends BendingProjectileEntity {
	private static final TrackedData<BlockState> BLOCK = DataTracker.registerData(BlockProjectileEntity.class, TrackedDataHandlerRegistry.BLOCK_STATE);

	protected BlockProjectileEntity(EntityType<? extends BlockProjectileEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(BLOCK, defaultBlock());
	}

	protected BlockState defaultBlock() {
		return Blocks.STONE.getDefaultState();
	}

	public BlockState getBlockState() {
		return dataTracker.get(BLOCK);
	}

	public void setBlockState(BlockState state) {
		dataTracker.set(BLOCK, state);
	}

	/** Render the block at full brightness (glowing meteors). */
	public boolean fullBright() {
		return false;
	}

	/** Degrees per tick the block spins while flying. */
	public float spinSpeed() {
		return 14f;
	}
}
