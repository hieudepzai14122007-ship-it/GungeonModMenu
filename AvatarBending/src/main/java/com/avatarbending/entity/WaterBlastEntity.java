package com.avatarbending.entity;

import com.avatarbending.bending.Element;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A spiraling whip of water. Knocks back, puts out fires and soaks the target.
 */
public class WaterBlastEntity extends BendingProjectileEntity {
	public WaterBlastEntity(EntityType<? extends WaterBlastEntity> type, World world) {
		super(type, world);
		this.maxLife = 26;
	}

	@Override
	protected Element element() {
		return Element.WATER;
	}

	@Override
	protected double gravity() {
		return 0.008;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.extinguish();
		Vec3d push = getVelocity().normalize().multiply(0.9 * power);
		target.addVelocity(push.x, 0.25, push.z);
		target.velocityModified = true;
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z, 40, 0.4, 0.3, 0.4, 0.3);
		world.spawnParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
		world.spawnParticles(ParticleTypes.FALLING_WATER, pos.x, pos.y, pos.z, 15, 0.4, 0.2, 0.4, 0);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.6f, 1.3f);
	}

	@Override
	protected void expire(ServerWorld world) {
		world.spawnParticles(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 12, 0.3, 0.2, 0.3, 0);
	}

	@Override
	protected void clientTrail() {
		World world = getWorld();
		Vec3d v = getVelocity();
		Vec3d side = new Vec3d(-v.z, 0, v.x);
		if (side.lengthSquared() < 1.0E-4) {
			side = new Vec3d(1, 0, 0);
		}
		side = side.normalize();
		Vec3d up = side.crossProduct(v).normalize();
		double y = getY() + getHeight() / 2;
		for (int strand = 0; strand < 2; strand++) {
			double angle = age * 1.2 + strand * Math.PI;
			Vec3d offset = side.multiply(Math.cos(angle) * 0.3).add(up.multiply(Math.sin(angle) * 0.3));
			world.addParticle(ParticleTypes.SPLASH, getX() + offset.x, y + offset.y, getZ() + offset.z, 0, 0, 0);
			world.addParticle(ParticleTypes.BUBBLE_POP, getX() + offset.x, y + offset.y, getZ() + offset.z, 0, 0, 0);
		}
		world.addParticle(ParticleTypes.DRIPPING_WATER, getX(), y, getZ(), 0, 0, 0);
		if (age % 2 == 0) {
			world.addParticle(ParticleTypes.NAUTILUS, getX(), y, getZ(), 0, 0, 0);
		}
	}
}
