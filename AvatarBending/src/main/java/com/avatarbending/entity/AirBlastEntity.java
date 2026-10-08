package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A gust of air that flies through everything in its path, flinging mobs and deflecting projectiles.
 * It only stops when it hits a block.
 */
public class AirBlastEntity extends BendingProjectileEntity {
	private final Set<UUID> hit = new HashSet<>();
	private double knockback = 1.8;

	public AirBlastEntity(EntityType<? extends AirBlastEntity> type, World world) {
		super(type, world);
		this.maxLife = 22;
	}

	public void setKnockback(double knockback) {
		this.knockback = knockback;
	}

	@Override
	protected Element element() {
		return Element.AIR;
	}

	@Override
	protected double drag() {
		return 0.97;
	}

	@Override
	protected boolean canHit(Entity entity) {
		// Entities are handled in serverTick so the gust passes through them.
		return false;
	}

	@Override
	protected void serverTick(ServerWorld world) {
		Box box = getBoundingBox().stretch(getVelocity()).expand(0.6);
		Vec3d dir = getVelocity().normalize();
		Entity owner = getOwner();
		for (Entity entity : world.getOtherEntities(this, box, e -> e != owner && e.isAlive())) {
			if (entity instanceof ProjectileEntity projectile && !(entity instanceof BendingProjectileEntity)) {
				projectile.setVelocity(dir.multiply(projectile.getVelocity().length() + 0.5));
				projectile.setOwner(owner);
				projectile.velocityModified = true;
				continue;
			}
			if (!(entity instanceof LivingEntity living) || !super.canHit(entity) || !hit.add(entity.getUuid())) {
				continue;
			}
			if (owner instanceof net.minecraft.server.network.ServerPlayerEntity player
				&& !new AbilityContext(player, world, false).isTarget(living)) {
				continue;
			}
			hitEntity(world, living);
			double strength = knockback * power;
			living.setVelocity(living.getVelocity().multiply(0.2).add(dir.x * strength, 0.35 + Math.max(0, dir.y) * strength, dir.z * strength));
			living.velocityModified = true;
			living.extinguish();
			world.spawnParticles(ParticleTypes.GUST, living.getX(), living.getBodyY(0.5), living.getZ(), 1, 0, 0, 0, 0);
		}
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.GUST, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.08);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 0.6f, 1.3f);
	}

	@Override
	protected void expire(ServerWorld world) {
		world.spawnParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
	}

	@Override
	protected void clientTrail() {
		World world = getWorld();
		Vec3d v = getVelocity();
		for (int i = 0; i < 4; i++) {
			double angle = (age * 0.9) + i * (Math.PI / 2);
			Vec3d side = new Vec3d(-v.z, 0, v.x).normalize().multiply(Math.cos(angle) * 0.35);
			double up = Math.sin(angle) * 0.35;
			world.addParticle(ParticleTypes.CLOUD, getX() + side.x, getY() + 0.25 + up, getZ() + side.z,
				-v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
		}
		if (age % 3 == 0) {
			world.addParticle(ParticleTypes.SMALL_GUST, getX(), getY() + 0.25, getZ(), 0, 0, 0);
		}
	}
}
