package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.FireBlastEntity;
import com.avatarbending.entity.FireDragonEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FireAbilities {
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private FireAbilities() {
	}

	private static FireBlastEntity shoot(AbilityContext ctx, Vec3d direction, Vec3d offset, float speed, float damage) {
		FireBlastEntity blast = new FireBlastEntity(ModEntities.FIRE_BLAST, ctx.world());
		Vec3d start = ctx.eyes().add(direction.multiply(0.7)).add(offset).subtract(0, 0.3, 0);
		blast.setup(ctx.player(), start, direction.multiply(speed), damage, ctx.power());
		ctx.world().spawnEntity(blast);
		return blast;
	}

	/** A blast of fire that sets the target ablaze. */
	public static void fireBlast(AbilityContext ctx) {
		shoot(ctx, ctx.look(), Vec3d.ZERO, 1.7f, 5f);
		Fx.flames(ctx.world(), ctx.hands(), Colors.FIRE, 0.45f);
		ctx.sfx(ModSounds.FIRE_WHOOSH, 1.3f, 1.1f);
		ctx.sfx(SoundEvents.ENTITY_BLAZE_SHOOT, 0.7f, 1.3f);
		ctx.player().swingHand(Hand.MAIN_HAND, true);
	}

	/** Four rapid-fire fire punches, alternating fists. */
	public static void fireFists(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age % 3 == 0) {
				int punch = age / 3;
				Vec3d flat = ctx.flatLook();
				Vec3d side = new Vec3d(-flat.z, 0, flat.x).multiply(punch % 2 == 0 ? 0.4 : -0.4);
				shoot(ctx, ctx.look(), side, 1.9f, 3.5f);
				player.swingHand(punch % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
				Fx.flames(world, ctx.hands().add(side), Colors.FIRE, 0.3f);
				Sfx.play(world, player.getPos(), ModSounds.FIRE_WHOOSH, 1.0f, 1.2f + punch * 0.1f);
				Sfx.play(world, player.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.45f, 1.4f + punch * 0.1f);
			}
			return age >= 9;
		});
	}

	/** A long whip of fire that unrolls in a wide arc and lashes everything in front of you. */
	public static void fireWhip(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double reach = 8 * ctx.power();
		Set<UUID> hit = new HashSet<>();
		player.swingHand(Hand.MAIN_HAND, true);
		ctx.sfx(ModSounds.FIRE_WHOOSH, 1.5f, 0.85f);
		ctx.sfx(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			// The whip unrolls outwards over four ticks, then cracks.
			float radius = (float) (reach * (age + 1) / 4.0);
			Fx.slash(world, ctx.hands(), ctx.look(), Colors.FIRE, radius);
			if (age < 3) {
				return false;
			}
			Sfx.play(world, player.getPos(), ModSounds.WHIP_CRACK, 1.8f, 0.85f);
			Sfx.play(world, player.getPos(), ModSounds.FIRE_ROAR, 1.0f, 1.5f);
			Vec3d origin = player.getEyePos();
			Vec3d forward = ctx.flatLook();
			for (LivingEntity target : ctx.targetsAround(origin, reach + 1)) {
				Vec3d to = target.getPos().add(0, target.getHeight() / 2, 0).subtract(origin);
				Vec3d flat = new Vec3d(to.x, 0, to.z);
				boolean inArc = flat.lengthSquared() < 1 || flat.normalize().dotProduct(forward) > 0.45;
				if (!inArc || Math.abs(to.y) > 4 || !hit.add(target.getUuid())) {
					continue;
				}
				ctx.damage(target, Element.FIRE, 7);
				target.setOnFireForTicks(100);
				ctx.knockback(target, flat, 1.1, 0.35);
				Fx.flames(world, target.getPos().add(0, target.getHeight() / 2, 0), Colors.FIRE, 0.5f);
			}
			return true;
		});
	}

	/** Rocket along on jets of flame. Steer by looking around; anything you fly past gets scorched. */
	public static void fireJet(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		double speed = 1.5 * Math.sqrt(ctx.power());
		int thrust = ctx.avatarState() ? 10 : 6;
		int duration = 26;
		BendingManager.get(player).grantFallImmunity(140);
		Fx.attach(player, AttachedFxType.FIRE_JET, duration);
		Fx.flames(world, ctx.feet(), Colors.FIRE, 0.9f);
		Fx.ring(world, ctx.feet().add(0, 0.1, 0), ctx.look(), Colors.FIRE_CORE, 2f, 10);
		ctx.sfx(ModSounds.FIRE_ROAR, 1.6f, 1.3f);
		ctx.sfx(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
		Set<UUID> burned = new HashSet<>();
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age > duration) {
				return true;
			}
			if (age < thrust) {
				Vec3d look = player.getRotationVec(1f);
				Vec3d velocity = look.multiply(speed);
				if (look.y < 0.3) {
					velocity = velocity.add(0, 0.25, 0);
				}
				player.setVelocity(velocity);
				player.velocityModified = true;
			}
			Vec3d feet = player.getPos();
			for (LivingEntity target : ctx.targetsAround(feet, 2.0)) {
				if (burned.add(target.getUuid())) {
					target.setOnFireForTicks(80);
					ctx.damage(target, Element.FIRE, 3);
				}
			}
			if (age % 8 == 4) {
				Sfx.play(world, feet, ModSounds.FIRE_WHOOSH, 0.8f, 1.4f);
			}
			return false;
		});
	}

	/** A ring of fire explodes outwards from the bender, burning everything around them. */
	public static void infernoRing(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d origin = player.getPos().add(0, 0.2, 0);
		double maxRadius = 8 * ctx.power();
		int expandTicks = (int) Math.ceil((maxRadius - 0.8) / 0.85);
		Set<UUID> hit = new HashSet<>();
		Fx.fireRing(world, origin, Colors.FIRE, (float) maxRadius, expandTicks);
		Fx.groundShockwave(world, origin, Colors.FIRE, (float) maxRadius);
		Fx.flames(world, ctx.chest(), Colors.FIRE, 1.3f);
		ctx.sfx(ModSounds.FIRE_ROAR, 2.5f, 0.8f);
		ctx.sfx(ModSounds.FIRE_EXPLOSION, 1.8f, 1.1f);
		player.swingHand(Hand.MAIN_HAND, true);
		EffectScheduler.schedule(age -> {
			double radius = 0.8 + age * 0.85;
			if (radius > maxRadius + 0.5) {
				return true;
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
			return false;
		});
	}

	/** Breathe a roaring cone of blue dragon fire. */
	public static void dragonBreath(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = 40;
		double range = 10 * ctx.power();
		Fx.attach(player, AttachedFxType.DRAGON_BREATH, duration, Colors.BLUE_FIRE);
		ctx.sfx(ModSounds.FIRE_ROAR, 2f, 1.25f);
		ctx.sfx(SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1.4f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			if (age == 20) {
				Sfx.play(world, player.getPos(), ModSounds.FIRE_ROAR, 2f, 1.15f);
			}
			if (age % 4 != 0) {
				return false;
			}
			Vec3d origin = player.getEyePos();
			Vec3d look = player.getRotationVec(1f);
			for (LivingEntity target : ctx.targetsAround(origin, range)) {
				Vec3d to = target.getPos().add(0, target.getHeight() / 2, 0).subtract(origin);
				double dist = to.length();
				double minDot = dist < 2.5 ? 0.5 : 0.92;
				if (dist < 0.1 || to.multiply(1 / dist).dotProduct(look) < minDot || !player.canSee(target)) {
					continue;
				}
				ctx.damage(target, Element.FIRE, 2f);
				target.setOnFireForTicks(80);
			}
			return false;
		});
	}

	/** A dragon made of fire that hunts down the nearest enemy and explodes. */
	public static void fireDragon(AbilityContext ctx) {
		ServerWorld world = ctx.world();
		FireDragonEntity dragon = new FireDragonEntity(ModEntities.FIRE_DRAGON, world);
		Vec3d look = ctx.look();
		Vec3d start = ctx.eyes().add(look.multiply(1.2)).add(0, 0.2, 0);
		dragon.setup(ctx.player(), start, look.multiply(0.75), 10f, ctx.power());
		world.spawnEntity(dragon);
		Fx.flames(world, ctx.hands(), Colors.FIRE, 1.2f);
		Fx.ring(world, ctx.hands(), look, Colors.FIRE_CORE, 2.2f, 12);
		ctx.sfx(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.7f);
		ctx.sfx(ModSounds.FIRE_ROAR, 2f, 0.9f);
		ctx.player().swingHand(Hand.MAIN_HAND, true);
	}

	/** Focus energy into the third eye, then fire an explosive beam (Combustion). */
	public static void combustion(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int chargeTicks = ctx.avatarState() ? 6 : 12;
		Fx.attach(player, AttachedFxType.CHARGE_HANDS, chargeTicks, Colors.FIRE_CORE);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.3f, 1.5f);
		ctx.sfx(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.4f);
		EffectScheduler.schedule(chargeTicks, age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			Vec3d forehead = player.getEyePos().add(ctx.look().multiply(0.4)).add(0, 0.15, 0);
			HitResult hit = ctx.raycast(48 * ctx.power());
			Vec3d end = hit.getPos();
			double radius = 4 * ctx.power();
			Fx.beam(world, forehead, end, Colors.FIRE_CORE, 0.35f, 6);
			Fx.explosion(world, end, Element.FIRE, 2.5f);
			Fx.groundShockwave(world, end, Colors.FIRE, (float) radius * 1.5f);
			Fx.shake(world, end, 3f, 40);
			Sfx.play(world, forehead, ModSounds.ENERGY_BEAM, 1.2f, 1.8f);
			Sfx.play(world, end, ModSounds.FIRE_EXPLOSION, 4f, 0.8f);
			Sfx.play(world, end, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2f, 0.9f);
			for (LivingEntity target : ctx.targetsAround(end, radius)) {
				double falloff = 1 - Math.min(0.6, target.getPos().distanceTo(end) / radius * 0.6);
				ctx.damage(target, Element.FIRE, (float) (12 * falloff));
				target.setOnFireForTicks(100);
				ctx.knockback(target, target.getPos().subtract(end), 1.2, 0.5);
			}
			return true;
		});
	}

	/**
	 * Charge up, then fire a bolt of lightning at whatever you aim at. The bolt chains to up to
	 * three more enemies close to the first one.
	 */
	public static void lightning(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int chargeTicks = ctx.avatarState() ? 10 : 22;
		Fx.attach(player, AttachedFxType.CHARGE_HANDS, chargeTicks, Colors.LIGHTNING);
		Fx.spiral(world, ctx.feet(), Colors.LIGHTNING, 0.9f, 2.2f);
		ctx.sfx(ModSounds.ELECTRIC_CHARGE, 1.6f, 1.0f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age < chargeTicks) {
				if (age % 6 == 0) {
					Fx.ring(world, player.getPos().add(0, 0.1, 0), UP, Colors.LIGHTNING, 1.6f, 8);
				}
				return false;
			}
			HitResult hit = ctx.raycast(56 * ctx.power());
			Vec3d end = hit.getPos();
			Fx.lightning(world, ctx.hands(), end, Colors.LIGHTNING, 1.3f);
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
			if (bolt != null) {
				bolt.refreshPositionAfterTeleport(end.x, end.y, end.z);
				bolt.setCosmetic(true);
				world.spawnEntity(bolt);
			}
			Sfx.play(world, player.getPos(), ModSounds.LIGHTNING_STRIKE, 3f, 1.1f);
			Sfx.play(world, end, ModSounds.LIGHTNING_STRIKE, 5f, 0.9f);
			float bonus = world.isThundering() ? 1.5f : 1f;
			Set<UUID> struck = new HashSet<>();
			LivingEntity direct = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity l ? l : null;
			if (direct != null) {
				struck.add(direct.getUuid());
				ctx.damage(direct, Element.FIRE, 14 * bonus);
				direct.setOnFireForTicks(80);
			}
			for (LivingEntity target : ctx.targetsAround(end, 2.5 * ctx.power())) {
				if (struck.add(target.getUuid())) {
					ctx.damage(target, Element.FIRE, 5 * bonus);
					target.setOnFireForTicks(60);
				}
			}
			// Chain lightning: jump to the nearest enemies that were not hit yet.
			Vec3d from = end;
			for (int jump = 0; jump < 3; jump++) {
				Vec3d source = from;
				LivingEntity next = ctx.targetsAround(source, 7 * ctx.power()).stream()
					.filter(e -> !struck.contains(e.getUuid()))
					.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(source)))
					.orElse(null);
				if (next == null) {
					break;
				}
				struck.add(next.getUuid());
				Vec3d to = next.getPos().add(0, next.getHeight() / 2, 0);
				Fx.lightning(world, source, to, Colors.LIGHTNING, 0.7f);
				Sfx.play(world, to, ModSounds.ELECTRIC_CHARGE, 1.2f, 1.8f);
				ctx.damage(next, Element.FIRE, 7 * bonus);
				next.setOnFireForTicks(40);
				from = to;
			}
			return true;
		});
	}

	/** Fire rains from the sky: dozens of burning meteors pound the area you aim at. */
	public static void firestorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(40);
		Vec3d point = hit.getPos();
		BlockPos ground = ctx.groundAt(point.x, point.y + 0.5, point.z, 2, 24);
		Vec3d center = ground != null ? Vec3d.ofBottomCenter(ground) : point;
		double radius = 8 * ctx.power();
		int duration = 80;
		Fx.sigil(world, center, Colors.FIRE, (float) radius, duration + 15);
		Fx.charge(world, ctx.hands(), Colors.FIRE, 1.6f, 10);
		Sfx.play(world, center, ModSounds.FIRE_ROAR, 4f, 0.6f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.5f, 0.8f);
		ctx.sfx(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			if (age % 2 == 0) {
				Vec3d target = center;
				if (age > 0) {
					double angle = world.random.nextDouble() * Math.PI * 2;
					double r = Math.sqrt(world.random.nextDouble()) * radius;
					target = center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
				}
				Vec3d sky = target.add((world.random.nextDouble() - 0.5) * 6, 24, (world.random.nextDouble() - 0.5) * 6);
				Vec3d start = ctx.clearPath(target.add(0, 0.5, 0), sky, 1.5);
				FireBlastEntity meteor = new FireBlastEntity(ModEntities.FIRE_BLAST, world);
				meteor.setup(player, start, target.subtract(start).normalize().multiply(1.1), 5f, ctx.power());
				meteor.setStyle(FireBlastEntity.STYLE_METEOR);
				world.spawnEntity(meteor);
			}
			if (age % 20 == 10) {
				Sfx.play(world, center.add(0, 12, 0), ModSounds.FIRE_ROAR, 3f, 0.7f);
			}
			return false;
		});
	}
}
