package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
			if (owner instanceof ServerPlayerEntity player && !new AbilityContext(player, world, false).isTarget(living)) {
				continue;
			}
			hitEntity(world, living);
			double strength = knockback * power;
			living.setVelocity(living.getVelocity().multiply(0.2).add(dir.x * strength, 0.35 + Math.max(0, dir.y) * strength, dir.z * strength));
			living.velocityModified = true;
			living.extinguish();
			Fx.windBurst(world, living.getPos().add(0, living.getHeight() * 0.5, 0), dir, 0.7f);
			Sfx.play(world, living.getPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST, 0.7f, 1.3f);
		}
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.windBurst(world, pos, getVelocity().multiply(-1), 1.0f);
		Sfx.play(world, pos, SoundEvents.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
		Sfx.play(world, pos, ModSounds.AIR_WHOOSH, 0.6f, 1.4f);
	}

	@Override
	protected void expire(ServerWorld world) {
		Fx.windBurst(world, getPos(), getVelocity(), 0.5f);
	}
}
