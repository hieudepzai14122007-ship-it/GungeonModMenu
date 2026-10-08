package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.AirBladeEntity;
import com.avatarbending.entity.AirBlastEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.TornadoEntity;
import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class AirAbilities {
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private AirAbilities() {
	}

	private static AirBlastEntity blast(AbilityContext ctx, Vec3d direction, float speed, float damage, double knockback) {
		AirBlastEntity blast = new AirBlastEntity(ModEntities.AIR_BLAST, ctx.world());
		Vec3d start = ctx.eyes().add(direction.multiply(0.6)).subtract(0, 0.35, 0);
		blast.setup(ctx.player(), start, direction.multiply(speed), damage, ctx.power());
		blast.setKnockback(knockback);
		ctx.world().spawnEntity(blast);
		return blast;
	}

	/** A powerful gust that flings everything in its path. */
	public static void airBlast(AbilityContext ctx) {
		blast(ctx, ctx.look(), 1.5f, 3f, 2.0);
		Fx.windBurst(ctx.world(), ctx.hands(), ctx.look(), 0.6f);
		ctx.sfx(ModSounds.AIR_WHOOSH, 1.3f, 1.1f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_SHOOT, 0.8f, 1.2f);
	}

	/** A fan of five gusts. */
	public static void airBarrage(AbilityContext ctx) {
		Vec3d look = ctx.look();
		for (int i = -2; i <= 2; i++) {
			blast(ctx, AbilityContext.rotateY(look, i * 11), 1.4f, 2f, 1.3);
		}
		Fx.windBurst(ctx.world(), ctx.hands(), look, 1.1f);
		ctx.sfx(ModSounds.AIR_WHOOSH, 1.5f, 0.85f);
		Sfx.later(ctx.world(), 2, ctx.feet(), ModSounds.AIR_WHOOSH, 1.2f, 1.25f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 0.9f, 1.3f);
	}

	/** A crescent blade of wind that slices through every mob in a line. */
	public static void airBlade(AbilityContext ctx) {
		AirBladeEntity blade = new AirBladeEntity(ModEntities.AIR_BLADE, ctx.world());
		Vec3d look = ctx.look();
		Vec3d start = ctx.eyes().add(look.multiply(1.0)).subtract(0, 0.6, 0);
		blade.setup(ctx.player(), start, look.multiply(1.6), 7f, ctx.power());
		ctx.world().spawnEntity(blade);
		Fx.slash(ctx.world(), ctx.hands(), look, Colors.AIR, 1.4f);
		ctx.sfx(ModSounds.AIR_SLASH, 1.4f, 1.0f);
		ctx.sfx(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
		ctx.player().swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
	}

	/** A huge leap followed by a gentle glide. */
	public static void airLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d flat = ctx.flatLook();
		double up = 1.25 * Math.sqrt(ctx.power());
		player.setVelocity(flat.x * 1.1, up, flat.z * 1.1);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(140);
		Fx.groundShockwave(ctx.world(), ctx.feet(), Colors.AIR, 4f);
		Fx.windBurst(ctx.world(), ctx.feet().add(0, 0.3, 0), UP, 1.4f);
		ctx.sfx(ModSounds.AIR_WHOOSH, 1.6f, 0.75f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_JUMP, 1.2f, 1.1f);
		EffectScheduler.schedule(10, age -> {
			if (player.isAlive()) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
			return true;
		});
	}

	/** Ride a spinning ball of air: super speed, high jumps and no fall damage. */
	public static void airScooter(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int duration = (int) (160 * ctx.power());
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 3, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, duration, 1, false, false, true));
		BendingManager.get(player).grantFallImmunity(duration + 40);
		Fx.attach(player, AttachedFxType.AIR_SCOOTER, duration);
		Fx.windBurst(ctx.world(), ctx.feet().add(0, 0.3, 0), UP, 1.0f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_CHARGE, 1f, 1.4f);
		ctx.sfx(ModSounds.AIR_WHOOSH, 1.2f, 1.3f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			if (age % 30 == 15) {
				Sfx.play(ctx.world(), player.getPos(), ModSounds.AIR_WHOOSH, 0.5f, 1.6f);
			}
			return false;
		});
	}

	/** A spinning dome of wind that turns away arrows and keeps mobs at a distance. */
	public static void windShield(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = (int) (200 * ctx.power());
		double radius = 3.2;
		Fx.attach(player, AttachedFxType.WIND_SHIELD, duration);
		Fx.windBurst(world, ctx.chest(), UP, 1.6f);
		ctx.sfx(ModSounds.WIND_HOWL, 1.5f, 1.3f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_INHALE, 1f, 1.2f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			Vec3d center = player.getPos().add(0, 1, 0);
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(radius),
				p -> p.getOwner() != player)) {
				Vec3d away = projectile.getPos().subtract(center).normalize();
				projectile.setVelocity(away.multiply(Math.max(0.8, projectile.getVelocity().length())));
				projectile.setOwner(player);
				projectile.velocityModified = true;
				Fx.windBurst(world, projectile.getPos(), away, 0.5f);
				Sfx.play(world, projectile.getPos(), SoundEvents.ENTITY_BREEZE_DEFLECT, 1f, 1.2f);
			}
			for (LivingEntity target : ctx.targetsAround(center, radius)) {
				Vec3d away = target.getPos().subtract(player.getPos());
				ctx.knockback(target, away, 0.8, 0.25);
				if (age % 20 == 0) {
					ctx.damage(target, Element.AIR, 1.5f);
				}
			}
			if (age % 60 == 59) {
				Sfx.play(world, player.getPos(), ModSounds.WIND_HOWL, 0.9f, 1.4f);
			}
			return false;
		});
	}

	/** Creates a vacuum: everything nearby is sucked into one point and smashed together. */
	public static void vacuum(AbilityContext ctx) {
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(24);
		Vec3d point = hit.getType() == HitResult.Type.MISS ? ctx.eyes().add(ctx.look().multiply(12)) : hit.getPos();
		int duration = 50;
		double radius = 12 * ctx.power();
		Fx.charge(world, point, Colors.AIR, 6f, duration);
		Fx.ring(world, point, UP, Colors.AIR, (float) radius, 30);
		Sfx.play(world, point, ModSounds.WIND_HOWL, 2.5f, 0.6f);
		Sfx.play(world, point, ModSounds.CHARGE_MAGIC, 1.5f, 0.6f);
		Sfx.play(world, point, SoundEvents.ENTITY_BREEZE_INHALE, 2f, 0.5f);
		EffectScheduler.schedule(age -> {
			if (age < duration) {
				Box box = new Box(point, point).expand(radius);
				for (LivingEntity target : ctx.targetsIn(box)) {
					Vec3d to = point.subtract(target.getPos().add(0, target.getHeight() / 2, 0));
					double dist = to.length();
					if (dist > radius || dist < 0.1) {
						continue;
					}
					Vec3d in = to.multiply(1 / dist);
					Vec3d swirl = in.crossProduct(UP).multiply(0.15);
					target.setVelocity(in.multiply(Math.min(0.55, 0.12 + dist * 0.04)).add(swirl).add(0, 0.03, 0));
					target.velocityModified = true;
					target.fallDistance = 0;
				}
				for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
					item.setVelocity(point.subtract(item.getPos()).normalize().multiply(0.4));
					item.velocityModified = true;
				}
				return false;
			}
			Fx.explosion(world, point, Element.AIR, 2.2f);
			Fx.ring(world, point, UP, Colors.WHITE, 7f, 12);
			Sfx.play(world, point, ModSounds.AIR_BLAST, 3f, 0.9f);
			Sfx.play(world, point, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.5f, 1.3f);
			for (LivingEntity target : ctx.targetsAround(point, 5 * ctx.power())) {
				ctx.damage(target, Element.AIR, 10);
				Vec3d away = target.getPos().subtract(point);
				ctx.knockback(target, away, 1.0, 1.2);
			}
			return true;
		});
	}

	/** A 360 degree shockwave of air that throws everything away and turns arrows around. */
	public static void airSphere(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = ctx.chest();
		double radius = 7 * ctx.power();
		for (LivingEntity target : ctx.targetsAround(center, radius)) {
			Vec3d away = target.getPos().subtract(player.getPos());
			ctx.knockback(target, away, 2.4, 0.7);
			ctx.damage(target, Element.AIR, 4);
		}
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class,
			player.getBoundingBox().expand(radius + 1), p -> p.getOwner() != player)) {
			Vec3d away = projectile.getPos().subtract(center).normalize();
			projectile.setVelocity(away.multiply(Math.max(1.0, projectile.getVelocity().length())));
			projectile.setOwner(player);
			projectile.velocityModified = true;
		}
		Fx.windBurst(world, center, UP, 2.4f);
		Fx.ring(world, center, UP, Colors.WHITE, (float) radius, 12);
		Fx.ring(world, center, ctx.look(), Colors.AIR, (float) radius * 0.8f, 10);
		Fx.groundShockwave(world, ctx.feet(), Colors.AIR, (float) radius);
		ctx.sfx(ModSounds.AIR_BLAST, 2.5f, 1.2f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, 0.8f);
	}

	/** Summons a moving tornado. */
	public static void tornado(AbilityContext ctx) {
		spawnTornado(ctx, false);
		ctx.sfx(ModSounds.WIND_HOWL, 3f, 0.7f);
		ctx.sfx(SoundEvents.ENTITY_BREEZE_INHALE, 2f, 0.5f);
	}

	static void spawnTornado(AbilityContext ctx, boolean fiery) {
		Vec3d flat = ctx.flatLook();
		Vec3d spot = ctx.feet().add(flat.multiply(3));
		BlockPos ground = ctx.groundAt(spot.x, spot.y, spot.z, 3, 6);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : spot;
		TornadoEntity tornado = new TornadoEntity(ModEntities.TORNADO, ctx.world());
		tornado.setup(ctx.player(), pos, flat, ctx.power());
		tornado.setFiery(fiery);
		ctx.world().spawnEntity(tornado);
		Fx.windBurst(ctx.world(), pos.add(0, 1, 0), UP, 2f);
		Fx.groundShockwave(ctx.world(), pos, fiery ? Colors.FIRE : Colors.AIR, 5f);
	}

	/** Gathers the wind into a compressed cannon blast that pierces everything in a long line. */
	public static void airCannon(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int charge = ctx.avatarState() ? 10 : 24;
		Fx.attach(player, AttachedFxType.AIR_CANNON_CHARGE, charge);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.6f, 0.9f);
		ctx.sfx(ModSounds.WIND_HOWL, 1.6f, 1.5f);
		EffectScheduler.schedule(charge, age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			Vec3d start = ctx.hands();
			Vec3d look = ctx.look();
			Vec3d far = start.add(look.multiply(40));
			BlockHitResult blockHit = world.raycast(new RaycastContext(start, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
			Vec3d end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getPos();
			Vec3d axis = end.subtract(start);
			double len = axis.length();
			Box box = new Box(start, end).expand(2.4);
			for (LivingEntity target : ctx.targetsIn(box)) {
				Vec3d rel = target.getPos().add(0, target.getHeight() / 2, 0).subtract(start);
				double t = len < 1.0E-3 ? 0 : Math.max(0, Math.min(len, rel.dotProduct(axis) / len));
				Vec3d closest = start.add(axis.multiply(t / Math.max(len, 1.0E-3)));
				if (closest.distanceTo(target.getPos().add(0, target.getHeight() / 2, 0)) > 2.4) {
					continue;
				}
				ctx.damage(target, Element.AIR, 14);
				ctx.knockback(target, look, 3.5, 0.9);
			}
			Fx.beam(world, start, end, Colors.WHITE, 2.4f, 10);
			for (double d = 3; d < len; d += 5) {
				Fx.ring(world, start.add(look.multiply(d)), look, Colors.AIR, 1.8f, 10);
			}
			for (double d = 1; d < len; d += 2.5) {
				Vec3d p = start.add(look.multiply(d));
				world.spawnParticles(net.minecraft.particle.ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			Fx.windBurst(world, start, look, 1.6f);
			Fx.explosion(world, end, Element.AIR, 1.6f);
			Fx.shake(world, start, 2.5f, 30);
			Sfx.play(world, start, ModSounds.AIR_BLAST, 4f, 0.75f);
			Sfx.play(world, start, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 1.25f);
			player.setVelocity(player.getVelocity().add(look.multiply(-0.6)));
			player.velocityModified = true;
			return true;
		});
	}
}
