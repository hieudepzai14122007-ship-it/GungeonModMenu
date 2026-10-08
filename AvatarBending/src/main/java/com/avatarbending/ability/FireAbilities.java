package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.FireBlastEntity;
import com.avatarbending.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FireAbilities {
	private FireAbilities() {
	}

	private static void shoot(AbilityContext ctx, Vec3d offset, float speed, float damage) {
		FireBlastEntity blast = new FireBlastEntity(ModEntities.FIRE_BLAST, ctx.world());
		Vec3d look = ctx.look();
		Vec3d start = ctx.eyes().add(look.multiply(0.7)).add(offset).subtract(0, 0.3, 0);
		blast.setup(ctx.player(), start, look.multiply(speed), damage, ctx.power());
		ctx.world().spawnEntity(blast);
	}

	/** A blast of fire that sets the target ablaze. */
	public static void fireBlast(AbilityContext ctx) {
		shoot(ctx, Vec3d.ZERO, 1.7f, 5f);
		ctx.sound(SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 1.1f);
		ctx.sound(SoundEvents.ITEM_FIRECHARGE_USE, 0.6f, 1.3f);
	}

	/** Four rapid-fire fire punches, alternating fists. */
	public static void fireFists(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age % 3 == 0) {
				int punch = age / 3;
				Vec3d flat = ctx.flatLook();
				Vec3d side = new Vec3d(-flat.z, 0, flat.x).multiply(punch % 2 == 0 ? 0.4 : -0.4);
				shoot(ctx, side, 1.9f, 3.5f);
				player.swingHand(punch % 2 == 0 ? net.minecraft.util.Hand.MAIN_HAND : net.minecraft.util.Hand.OFF_HAND, true);
				ctx.sound(SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f + punch * 0.1f);
			}
			return age >= 9;
		});
	}

	/** Rocket forward on jets of flame, setting nearby mobs alight. */
	public static void fireJet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d look = ctx.look();
		double speed = 1.7 * Math.sqrt(ctx.power());
		Vec3d velocity = look.multiply(speed);
		if (look.y < 0.3) {
			velocity = velocity.add(0, 0.35, 0);
		}
		player.setVelocity(velocity);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(120);
		ctx.sound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
		ctx.sound(SoundEvents.ITEM_FIRECHARGE_USE, 1f, 0.6f);
		ctx.particles(ParticleTypes.FLAME, player.getPos(), 30, 0.3, 0.15);
		Set<UUID> burned = new HashSet<>();
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age > 24) {
				return true;
			}
			Vec3d feet = player.getPos();
			world.spawnParticles(ParticleTypes.FLAME, feet.x, feet.y + 0.2, feet.z, 8, 0.15, 0.1, 0.15, 0.03);
			world.spawnParticles(ParticleTypes.SMALL_FLAME, feet.x, feet.y + 0.6, feet.z, 4, 0.25, 0.3, 0.25, 0.01);
			world.spawnParticles(ParticleTypes.SMOKE, feet.x, feet.y, feet.z, 3, 0.1, 0.1, 0.1, 0.01);
			for (LivingEntity target : ctx.targetsAround(feet, 1.8)) {
				if (burned.add(target.getUuid())) {
					target.setOnFireForTicks(80);
					ctx.damage(target, Element.FIRE, 3);
				}
			}
			if (age % 6 == 0) {
				ctx.soundAt(feet, SoundEvents.ENTITY_BLAZE_BURN, 0.7f, 1.2f);
			}
			return false;
		});
	}

	/** An expanding ring of fire around the bender. */
	public static void infernoRing(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d origin = player.getPos().add(0, 0.2, 0);
		double maxRadius = 8 * ctx.power();
		Set<UUID> hit = new HashSet<>();
		ctx.sound(SoundEvents.ITEM_FIRECHARGE_USE, 1.5f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
		ctx.particles(ParticleTypes.LAVA, origin, 15, 0.5, 0);
		EffectScheduler.schedule(age -> {
			double radius = 0.8 + age * 0.85;
			if (radius > maxRadius) {
				return true;
			}
			int points = (int) (radius * 9);
			for (int i = 0; i < points; i++) {
				double angle = (Math.PI * 2 * i) / points + age * 0.1;
				double x = origin.x + Math.cos(angle) * radius;
				double z = origin.z + Math.sin(angle) * radius;
				world.spawnParticles(ParticleTypes.FLAME, x, origin.y, z, 1, 0.05, 0.25, 0.05, 0.02);
				if (i % 2 == 0) {
					world.spawnParticles(ParticleTypes.FLAME, x, origin.y + 0.6, z, 1, 0.05, 0.2, 0.05, 0.01);
				}
				if (i % 5 == 0) {
					world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, origin.y + 1, z, 1, 0.1, 0.1, 0.1, 0.01);
				}
			}
			for (LivingEntity target : ctx.targetsAround(origin, radius + 0.5)) {
				double dist = Math.hypot(target.getX() - origin.x, target.getZ() - origin.z);
				if (dist < radius - 1.5 || Math.abs(target.getY() - origin.y) > 3 || !hit.add(target.getUuid())) {
					continue;
				}
				ctx.damage(target, Element.FIRE, 6);
				target.setOnFireForTicks(120);
				ctx.knockback(target, target.getPos().subtract(origin), 0.9, 0.35);
			}
			if (age % 2 == 0) {
				ctx.soundAt(origin, SoundEvents.BLOCK_FIRE_AMBIENT, 1.5f, 0.8f);
			}
			return false;
		});
	}

	/** Focus energy into the third eye, then fire an explosive beam (Combustion). */
	public static void combustion(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int chargeTicks = ctx.avatarState() ? 6 : 12;
		ctx.sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 1.4f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			Vec3d forehead = player.getEyePos().add(ctx.look().multiply(0.4)).add(0, 0.15, 0);
			if (age < chargeTicks) {
				double r = 1.2 - age * (1.0 / chargeTicks);
				for (int i = 0; i < 4; i++) {
					double angle = age * 0.6 + i * (Math.PI / 2);
					world.spawnParticles(ParticleTypes.SMALL_FLAME, forehead.x + Math.cos(angle) * r, forehead.y + Math.sin(angle) * r * 0.5,
						forehead.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
				}
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, forehead.x, forehead.y, forehead.z, 2, 0.05, 0.05, 0.05, 0.02);
				return false;
			}
			HitResult hit = ctx.raycast(48 * ctx.power());
			Vec3d end = hit.getPos();
			ctx.line(ParticleTypes.FLAME, forehead, end, 0.35, 0.03);
			ctx.line(ParticleTypes.CRIT, forehead, end, 0.8, 0.05);
			ctx.sound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.8f, 1.8f);
			explode(ctx, end, 4 * ctx.power(), 12);
			return true;
		});
	}

	private static void explode(AbilityContext ctx, Vec3d pos, double radius, float damage) {
		ServerWorld world = ctx.world();
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 80, radius * 0.4, radius * 0.4, radius * 0.4, 0.2);
		world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 20, radius * 0.3, radius * 0.3, radius * 0.3, 0);
		ctx.soundAt(pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 3f, 0.8f);
		for (LivingEntity target : ctx.targetsAround(pos, radius)) {
			double falloff = 1 - Math.min(0.6, target.getPos().distanceTo(pos) / radius * 0.6);
			ctx.damage(target, Element.FIRE, (float) (damage * falloff));
			target.setOnFireForTicks(100);
			Vec3d away = target.getPos().subtract(pos);
			ctx.knockback(target, away, 1.2, 0.5);
		}
	}

	/** Charge up, then fire a bolt of lightning at whatever you aim at. */
	public static void lightning(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int chargeTicks = ctx.avatarState() ? 10 : 22;
		ctx.sound(SoundEvents.BLOCK_BEACON_AMBIENT, 2f, 2f);
		ctx.sound(SoundEvents.ENTITY_CREEPER_PRIMED, 0.6f, 1.8f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age < chargeTicks) {
				// Sparks spiral up around the bender.
				for (int strand = 0; strand < 2; strand++) {
					double angle = age * 0.7 + strand * Math.PI;
					double y = player.getY() + (age % 10) * 0.2;
					world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(angle) * 0.8, y,
						player.getZ() + Math.sin(angle) * 0.8, 2, 0.05, 0.05, 0.05, 0.05);
				}
				Vec3d hand = player.getEyePos().add(ctx.look().multiply(0.6)).subtract(0, 0.4, 0);
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 3, 0.1, 0.1, 0.1, 0.1);
				if (age % 5 == 0) {
					ctx.sound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.8f);
				}
				return false;
			}
			HitResult hit = ctx.raycast(56 * ctx.power());
			Vec3d end = hit.getPos();
			Vec3d start = player.getEyePos().add(ctx.look().multiply(0.6)).subtract(0, 0.4, 0);
			jaggedBolt(ctx, start, end);

			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
			if (bolt != null) {
				bolt.refreshPositionAfterTeleport(end.x, end.y, end.z);
				bolt.setCosmetic(true);
				world.spawnEntity(bolt);
			}
			float bonus = world.isThundering() ? 1.5f : 1f;
			LivingEntity direct = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity l ? l : null;
			if (direct != null) {
				ctx.damage(direct, Element.FIRE, 14 * bonus);
				direct.setOnFireForTicks(80);
			}
			for (LivingEntity target : ctx.targetsAround(end, 3.5 * ctx.power())) {
				if (target != direct) {
					ctx.damage(target, Element.FIRE, 6 * bonus);
					target.setOnFireForTicks(60);
				}
			}
			world.spawnParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 60, 1, 1, 1, 0.6);
			ctx.sound(SoundEvents.ITEM_TRIDENT_THUNDER.value(), 2f, 1.2f);
			return true;
		});
	}

	private static void jaggedBolt(AbilityContext ctx, Vec3d start, Vec3d end) {
		ServerWorld world = ctx.world();
		Vec3d prev = start;
		Vec3d delta = end.subtract(start);
		int segments = Math.max(3, (int) (delta.length() / 2.5));
		for (int i = 1; i <= segments; i++) {
			Vec3d point = start.add(delta.multiply(i / (double) segments));
			if (i < segments) {
				point = point.add((world.random.nextDouble() - 0.5) * 1.2, (world.random.nextDouble() - 0.5) * 1.2, (world.random.nextDouble() - 0.5) * 1.2);
			}
			ctx.line(ParticleTypes.ELECTRIC_SPARK, prev, point, 0.25, 0.02);
			ctx.line(ParticleTypes.END_ROD, prev, point, 0.6, 0.01);
			prev = point;
		}
	}
}
