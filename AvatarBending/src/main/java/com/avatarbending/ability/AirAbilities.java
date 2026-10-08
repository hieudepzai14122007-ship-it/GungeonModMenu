package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.AirBlastEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.TornadoEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class AirAbilities {
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
		ctx.sound(SoundEvents.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
		ctx.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
		ctx.particles(ParticleTypes.SMALL_GUST, ctx.eyes().add(ctx.look()), 2, 0.1, 0);
	}

	/** A fan of five gusts. */
	public static void airBarrage(AbilityContext ctx) {
		Vec3d look = ctx.look();
		for (int i = -2; i <= 2; i++) {
			blast(ctx, AbilityContext.rotateY(look, i * 11), 1.4f, 2f, 1.3);
		}
		ctx.sound(SoundEvents.ENTITY_BREEZE_SHOOT, 1f, 0.9f);
		ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 0.8f, 1.4f);
	}

	/** A huge leap followed by a gentle glide. */
	public static void airLeap(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d flat = ctx.flatLook();
		double up = 1.25 * Math.sqrt(ctx.power());
		player.setVelocity(flat.x * 1.1, up, flat.z * 1.1);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(140);
		Vec3d feet = player.getPos().add(0, 0.1, 0);
		ctx.ring(ParticleTypes.CLOUD, feet, 1.2, 24, 0.12);
		ctx.particles(ParticleTypes.GUST_EMITTER_SMALL, feet, 1, 0, 0);
		ctx.sound(SoundEvents.ENTITY_BREEZE_JUMP, 1.2f, 1.1f);
		ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 0.7f, 1.6f);
		EffectScheduler.schedule(10, age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, true));
			return true;
		});
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age > 40 || (age > 5 && player.isOnGround())) {
				return true;
			}
			ctx.world().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
			return false;
		});
	}

	/** Ride a spinning ball of air: super speed, high jumps and no fall damage. */
	public static void airScooter(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int duration = (int) (160 * ctx.power());
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 3, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, duration, 1, false, false, true));
		BendingManager.get(player).grantFallImmunity(duration + 40);
		ctx.sound(SoundEvents.ENTITY_BREEZE_CHARGE, 1f, 1.4f);
		ServerWorld world = ctx.world();
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			double t = age * 0.8;
			for (int i = 0; i < 3; i++) {
				double angle = t + i * (Math.PI * 2 / 3);
				double x = player.getX() + Math.cos(angle) * 0.45;
				double z = player.getZ() + Math.sin(angle) * 0.45;
				world.spawnParticles(ParticleTypes.CLOUD, x, player.getY() + 0.15 + Math.sin(t + i) * 0.2, z, 1, 0, 0, 0, 0);
			}
			if (age % 10 == 0) {
				world.spawnParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0, 0, 0, 0);
			}
			if (age % 30 == 0) {
				ctx.sound(SoundEvents.ENTITY_BREEZE_SLIDE, 0.6f, 1.5f);
			}
			return false;
		});
	}

	/** Summons a moving tornado. */
	public static void tornado(AbilityContext ctx) {
		Vec3d flat = ctx.flatLook();
		Vec3d spot = ctx.player().getPos().add(flat.multiply(3));
		BlockPos ground = ctx.groundAt(spot.x, spot.y, spot.z, 3, 6);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : spot;
		TornadoEntity tornado = new TornadoEntity(ModEntities.TORNADO, ctx.world());
		tornado.setup(ctx.player(), pos, flat, ctx.power());
		ctx.world().spawnEntity(tornado);
		ctx.particles(ParticleTypes.GUST_EMITTER_LARGE, pos.add(0, 1, 0), 1, 0, 0);
		ctx.sound(SoundEvents.ENTITY_BREEZE_INHALE, 2f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.5f, 0.5f);
	}

	/** A 360° shockwave of air that throws everything away and turns arrows around. */
	public static void airSphere(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d center = player.getPos().add(0, 1, 0);
		double radius = 7 * ctx.power();
		for (LivingEntity target : ctx.targetsAround(center, radius)) {
			Vec3d away = target.getPos().subtract(player.getPos());
			ctx.knockback(target, away, 2.4, 0.7);
			ctx.damage(target, Element.AIR, 4);
		}
		for (ProjectileEntity projectile : ctx.world().getEntitiesByClass(ProjectileEntity.class,
			player.getBoundingBox().expand(radius + 1), p -> p.getOwner() != player)) {
			Vec3d away = projectile.getPos().subtract(center).normalize();
			projectile.setVelocity(away.multiply(Math.max(1.0, projectile.getVelocity().length())));
			projectile.setOwner(player);
			projectile.velocityModified = true;
		}
		ctx.particles(ParticleTypes.GUST_EMITTER_LARGE, center, 1, 0, 0);
		ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, 0.8f);
		ctx.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
		ServerWorld world = ctx.world();
		// Expanding sphere of wind.
		EffectScheduler.schedule(age -> {
			double r = 1 + age * (radius / 6.0);
			int points = 60;
			double golden = Math.PI * (3 - Math.sqrt(5));
			for (int i = 0; i < points; i++) {
				double y = 1 - (i / (double) (points - 1)) * 2;
				double ring = Math.sqrt(1 - y * y);
				double theta = golden * i + age;
				world.spawnParticles(ParticleTypes.CLOUD, center.x + Math.cos(theta) * ring * r, center.y + y * r * 0.7,
					center.z + Math.sin(theta) * ring * r, 1, 0, 0, 0, 0);
			}
			return age >= 6;
		});
	}
}
