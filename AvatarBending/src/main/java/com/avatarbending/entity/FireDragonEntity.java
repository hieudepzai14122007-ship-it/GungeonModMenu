package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;

/**
 * A serpentine dragon made of fire. It weaves through the air, hunts down the nearest enemy in
 * front of the firebender and explodes in a fireball.
 */
public class FireDragonEntity extends BendingProjectileEntity {
	private static final double SPEED = 0.75;
	private boolean exploded;

	public FireDragonEntity(EntityType<? extends FireDragonEntity> type, World world) {
		super(type, world);
		this.maxLife = 110;
	}

	@Override
	protected Element element() {
		return Element.FIRE;
	}

	@Override
	protected void serverTick(ServerWorld world) {
		if (age % 14 == 0) {
			Sfx.play(world, getPos(), ModSounds.FIRE_WHOOSH, 1.2f, 0.8f);
		}
		if (!(getOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		AbilityContext context = new AbilityContext(player, world, false);
		Vec3d heading = getVelocity().normalize();
		LivingEntity target = context.targetsAround(getPos(), 24).stream()
			.filter(e -> e.getPos().subtract(getPos()).normalize().dotProduct(heading) > 0.2)
			.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(this)))
			.orElse(null);
		Vec3d desired = heading;
		if (target != null) {
			desired = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos()).normalize();
		}
		Vec3d side = heading.crossProduct(new Vec3d(0, 1, 0));
		if (side.lengthSquared() > 1.0E-4) {
			desired = desired.add(side.normalize().multiply(Math.sin(age * 0.3) * 0.45)).normalize();
		}
		Vec3d steered = heading.multiply(0.86).add(desired.multiply(0.14)).normalize().multiply(SPEED);
		setVelocity(steered);
		velocityModified = true;
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (getWorld() instanceof ServerWorld world) {
			explode(world, hit.getPos());
		}
		discard();
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (getWorld() instanceof ServerWorld world) {
			explode(world, hit.getPos());
		}
		discard();
	}

	@Override
	protected void expire(ServerWorld world) {
		explode(world, getPos());
	}

	private void explode(ServerWorld world, Vec3d pos) {
		if (exploded) {
			return;
		}
		exploded = true;
		Fx.explosion(world, pos, Element.FIRE, 2.2f);
		Fx.groundShockwave(world, pos, Colors.FIRE, 6f);
		Sfx.play(world, pos, ModSounds.FIRE_EXPLOSION, 3f, 0.85f);
		Sfx.play(world, pos, ModSounds.FIRE_ROAR, 2f, 1.2f);
		if (!(getOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		AbilityContext context = new AbilityContext(player, world, false);
		for (LivingEntity target : context.targetsAround(pos, 3.5 * power)) {
			target.timeUntilRegen = 0;
			target.damage(AbilityContext.damageSource(world, Element.FIRE, this, player), damage * power);
			target.setOnFireForTicks(120);
			Vec3d away = target.getPos().subtract(pos);
			away = away.lengthSquared() < 0.01 ? new Vec3d(0, 1, 0) : away.normalize();
			target.addVelocity(away.x * 0.9, 0.5, away.z * 0.9);
			target.velocityModified = true;
		}
	}
}
