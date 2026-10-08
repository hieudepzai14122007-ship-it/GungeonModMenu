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
 * A blast of fire that sets its target ablaze. Never sets blocks on fire.
 */
public class FireBlastEntity extends BendingProjectileEntity {
	public FireBlastEntity(EntityType<? extends FireBlastEntity> type, World world) {
		super(type, world);
		this.maxLife = 30;
	}

	@Override
	protected Element element() {
		return Element.FIRE;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.setOnFireForTicks((int) (80 * power));
		Vec3d push = getVelocity().normalize().multiply(0.5);
		target.addVelocity(push.x, 0.2, push.z);
		target.velocityModified = true;
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 25, 0.25, 0.25, 0.25, 0.12);
		world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.PLAYERS, 0.7f, 1.2f);
	}

	@Override
	protected void expire(ServerWorld world) {
		world.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.02);
	}

	@Override
	protected void clientTrail() {
		World world = getWorld();
		Vec3d v = getVelocity();
		double y = getY() + getHeight() / 2;
		for (int i = 0; i < 6; i++) {
			world.addParticle(ParticleTypes.FLAME,
				getX() + (random.nextDouble() - 0.5) * 0.3, y + (random.nextDouble() - 0.5) * 0.3, getZ() + (random.nextDouble() - 0.5) * 0.3,
				-v.x * 0.08, -v.y * 0.08, -v.z * 0.08);
		}
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SMOKE, getX(), y, getZ(), 0, 0.02, 0);
		}
		if (random.nextInt(5) == 0) {
			world.addParticle(ParticleTypes.LAVA, getX(), y, getZ(), 0, 0, 0);
		}
	}
}
