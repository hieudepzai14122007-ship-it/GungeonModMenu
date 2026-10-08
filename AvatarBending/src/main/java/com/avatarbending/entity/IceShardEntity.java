package com.avatarbending.entity;

import com.avatarbending.bending.Element;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A small spinning shard of ice that chills whatever it hits.
 */
public class IceShardEntity extends BlockProjectileEntity {
	public IceShardEntity(EntityType<? extends IceShardEntity> type, World world) {
		super(type, world);
		this.maxLife = 30;
	}

	@Override
	protected BlockState defaultBlock() {
		return Blocks.PACKED_ICE.getDefaultState();
	}

	@Override
	protected Element element() {
		return Element.WATER;
	}

	@Override
	protected double gravity() {
		return 0.015;
	}

	@Override
	public float spinSpeed() {
		return 30f;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
		target.setFrozenTicks(Math.min(target.getFrozenTicks() + 50, target.getMinFreezeDamageTicks() + 40));
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.05);
		world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.05);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.5f, 1.6f);
	}

	@Override
	protected void clientTrail() {
		getWorld().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.15, getZ(), 0, 0, 0);
	}
}
