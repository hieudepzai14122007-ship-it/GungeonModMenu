package com.avatarbending.ability;

import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.BlizzardEntity;
import com.avatarbending.entity.MeteorEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.VolcanoEntity;
import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Spells only the Avatar can use.
 */
public final class AvatarAbilities {
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private AvatarAbilities() {
	}

	/** Bright version of each element's color, used for the Avatar's elemental effects. */
	private static int auraColor(Element element) {
		return switch (element) {
			case AIR -> Colors.WHITE;
			case WATER -> Colors.WATER;
			case EARTH -> Colors.EARTH_GREEN;
			case FIRE -> Colors.FIRE;
			case AVATAR -> Colors.AVATAR;
		};
	}

	private static void elementSound(ServerWorld world, Vec3d pos, Element element, float volume) {
		switch (element) {
			case AIR -> Sfx.play(world, pos, ModSounds.AIR_BLAST, volume, 1.1f);
			case WATER -> Sfx.play(world, pos, ModSounds.WATER_SURGE, volume, 1.2f);
			case EARTH -> Sfx.play(world, pos, ModSounds.ROCK_IMPACT, volume, 0.8f);
			case FIRE -> Sfx.play(world, pos, ModSounds.FIRE_WHOOSH, volume, 0.9f);
			default -> {
			}
		}
	}

	/** Hits a creature with one element's signature effect. */
	private static void elementHit(AbilityContext ctx, LivingEntity target, Element element, Vec3d from, float damage) {
		ctx.damage(target, element, damage);
		switch (element) {
			case AIR -> ctx.knockback(target, target.getPos().subtract(from), 1.4, 0.5);
			case WATER -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
			case EARTH -> {
				target.setVelocity(target.getVelocity().x, 0.8, target.getVelocity().z);
				target.velocityModified = true;
			}
			case FIRE -> target.setOnFireForTicks(80);
			default -> {
			}
		}
	}

	/** All four elements orbit the Avatar and blast everything nearby, one element after another. */
	public static void elementalStorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = 100;
		double radius = 9 * ctx.power();
		Fx.attach(player, AttachedFxType.ELEMENT_STORM, duration);
		Fx.sigil(world, ctx.feet(), Colors.AVATAR, 5f, 40);
		Fx.burst(world, ctx.chest(), Colors.AVATAR, 1.5f);
		ctx.sfx(ModSounds.AVATAR_PULSE, 2f, 0.8f);
		ctx.sfx(ModSounds.WIND_HOWL, 2f, 0.7f);
		ctx.sfx(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			if (age % 10 != 5) {
				return false;
			}
			Element element = Element.BENDABLE.get((age / 10) % 4);
			Vec3d center = player.getPos().add(0, 1, 0);
			Fx.ring(world, center, UP, auraColor(element), (float) radius, 10);
			for (LivingEntity target : ctx.targetsAround(center, radius)) {
				Vec3d at = target.getPos().add(0, target.getHeight() / 2, 0);
				elementHit(ctx, target, element, center, 4);
				Fx.explosion(world, at, element, 0.5f);
				Fx.line(world, center, at, auraColor(element), 1.5f);
			}
			elementSound(world, center, element, 1.8f);
			return false;
		});
	}

	/**
	 * Grabs the spirit of the target in a beam of light. Mobs take huge damage and are weakened;
	 * enemy benders lose their bending for 30 seconds.
	 */
	public static void energybending(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		LivingEntity target = ctx.targetEntity(24 * ctx.power());
		if (target == null) {
			player.sendMessage(Text.translatable("message.avatarbending.no_target").formatted(Formatting.GRAY), true);
			ctx.fail();
			return;
		}
		int duration = 40;
		Fx.attach(player, AttachedFxType.ENERGY_LINK, duration + 2, target.getId());
		Fx.attach(target, AttachedFxType.SPIRIT_FORM, duration + 2);
		Fx.sigil(world, target.getPos(), Colors.SPIRIT, 2.5f, duration);
		ctx.sfx(ModSounds.ENERGY_BEAM, 1.5f, 0.8f);
		ctx.sfx(ModSounds.SPIRIT_CHIME, 1.5f, 0.7f);
		ctx.sfx(SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.5f, 0.6f);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, duration + 40, 0));
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || !target.isAlive()) {
				Fx.detach(player, AttachedFxType.ENERGY_LINK);
				return true;
			}
			// Lift and hold the target in the air.
			target.setVelocity(0, target.getY() < player.getY() + 1.5 ? 0.12 : 0.0, 0);
			target.velocityModified = true;
			target.fallDistance = 0;
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0);
			if (age < duration) {
				if (age % 8 == 0) {
					Sfx.play(world, to, ModSounds.SPIRIT_CHIME, 1.2f, 0.8f + age * 0.02f);
				}
				return false;
			}
			Fx.pillar(world, target.getPos(), Colors.SPIRIT, 16f, 24);
			Fx.burst(world, to, Colors.SPIRIT, 2.2f);
			Fx.flash(world, to, Colors.SPIRIT, 24f);
			Sfx.play(world, to, ModSounds.AVATAR_PULSE, 2.5f, 1.3f);
			Sfx.play(world, to, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.6f);
			ctx.damage(target, Element.AVATAR, 18);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 600, 1));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
			if (target instanceof ServerPlayerEntity victim && BendingManager.get(victim).hasBending()) {
				BendingManager.get(victim).setSuppressedTicks(600);
				victim.sendMessage(Text.translatable("message.avatarbending.energybent", player.getDisplayName())
					.formatted(Formatting.DARK_PURPLE), false);
				BendingManager.sync(victim, true);
			}
			return true;
		});
	}

	/** Pulls a flaming meteor out of the sky onto the target. */
	public static void meteorStrike(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(64);
		Vec3d target = hit.getPos();
		if (hit.getType() == HitResult.Type.MISS) {
			// Nothing in sight: drop it on the ground below the aim point.
			BlockPos ground = ctx.groundAt(target.x, target.y, target.z, 0, 40);
			if (ground != null) {
				target = Vec3d.ofBottomCenter(ground);
			}
		}
		Vec3d back = ctx.flatLook().multiply(-6);
		Vec3d start = ctx.clearPath(target.add(0, 1, 0), target.add(back.x, 30, back.z), 2.5);
		Vec3d velocity = target.subtract(start).normalize().multiply(1.6);
		MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR, world);
		meteor.setup(player, start, velocity, 18f, ctx.power());
		world.spawnEntity(meteor);
		Fx.sigil(world, target, Colors.LAVA, 7f, 30);
		Fx.beam(world, ctx.hands(), start, Colors.AVATAR, 0.4f, 12);
		Fx.flames(world, start, Colors.FIRE, 2.5f);
		ctx.sfx(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.5f, 0.6f);
		Sfx.play(world, start, ModSounds.FIRE_ROAR, 5f, 0.5f);
	}

	/** Charge the four elements together, then fire a beam of pure energy that pierces everything. */
	public static void elementalBeam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int charge = ctx.avatarState() ? 10 : 20;
		int duration = 40;
		double range = 40;
		Fx.attach(player, AttachedFxType.CHARGE_HANDS, charge, Colors.AVATAR);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.6f, 0.7f);
		ctx.sfx(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);
		EffectScheduler.schedule(charge, age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			if (age == 0) {
				Fx.attach(player, AttachedFxType.ELEMENTAL_BEAM, duration);
				Fx.shake(world, player.getPos(), 2f, 24);
				Sfx.play(world, player.getPos(), ModSounds.ENERGY_BEAM, 2.5f, 0.8f);
				Sfx.play(world, player.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.6f);
			} else if (age % 10 == 0) {
				Sfx.play(world, player.getPos(), ModSounds.ENERGY_BEAM, 1.5f, 0.9f);
			}
			if (age % 2 != 0) {
				return false;
			}
			Vec3d look = player.getRotationVec(1f);
			Vec3d start = player.getEyePos().add(look.multiply(0.8)).add(0, -0.2, 0);
			Vec3d far = start.add(look.multiply(range));
			BlockHitResult blockHit = world.raycast(new RaycastContext(start, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
			Vec3d end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getPos();
			Vec3d axis = end.subtract(start);
			double len = Math.max(axis.length(), 1.0E-3);
			for (LivingEntity target : ctx.targetsIn(new Box(start, end).expand(1.6))) {
				Vec3d center = target.getPos().add(0, target.getHeight() / 2, 0);
				double t = Math.max(0, Math.min(len, center.subtract(start).dotProduct(axis) / len));
				Vec3d closest = start.add(axis.multiply(t / len));
				if (closest.distanceTo(center) > 1.2 + target.getWidth() / 2) {
					continue;
				}
				ctx.damage(target, Element.AVATAR, 2.5f);
				target.setOnFireForTicks(40);
				ctx.knockback(target, look, 0.25, 0.05);
			}
			if (age % 6 == 0 && blockHit.getType() != HitResult.Type.MISS) {
				Fx.explosion(world, end, Element.AVATAR, 0.6f);
			}
			return false;
		});
	}

	/** A towering tornado of fire that drags enemies into its burning core. */
	public static void fireTornado(AbilityContext ctx) {
		AirAbilities.spawnTornado(ctx, true);
		ctx.sfx(ModSounds.FIRE_ROAR, 3f, 0.6f);
		ctx.sfx(ModSounds.WIND_HOWL, 2.5f, 0.6f);
		ctx.sfx(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
	}

	/** Summons a howling blizzard that slows everything inside, then freezes it all solid. */
	public static void blizzard(AbilityContext ctx) {
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(30);
		Vec3d point = hit.getPos();
		BlockPos ground = ctx.groundAt(point.x, point.y + 0.5, point.z, 2, 16);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : point;
		BlizzardEntity blizzard = new BlizzardEntity(ModEntities.BLIZZARD, world);
		blizzard.setup(ctx.player(), pos, ctx.flatLook(), ctx.power());
		world.spawnEntity(blizzard);
		Fx.frost(world, pos.add(0, 1, 0), 2f);
		Fx.sigil(world, pos, Colors.ICE, (float) (BlizzardEntity.RADIUS * ctx.power()), 120);
		Sfx.play(world, pos, ModSounds.WIND_HOWL, 3f, 1.0f);
		Sfx.play(world, pos, ModSounds.ICE_CRACK, 2f, 0.7f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.2f, 1.2f);
	}

	/** Raises an erupting volcano that rains lava bombs, then sinks back into the ground. */
	public static void volcano(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(32);
		Vec3d point = hit.getPos();
		BlockPos ground = null;
		if (point.squaredDistanceTo(player.getPos()) >= 6 * 6) {
			ground = ctx.groundAt(point.x, point.y + 0.5, point.z, 3, 16);
		} else {
			// Too close: raise it a few blocks in front instead, never on top of the Avatar.
			for (double distance : new double[] {7, 6, 8, 9, 5}) {
				Vec3d spot = player.getPos().add(ctx.flatLook().multiply(distance));
				ground = ctx.groundAt(spot.x, spot.y + 0.5, spot.z, 3, 8);
				if (ground != null) {
					break;
				}
			}
		}
		if (ground == null) {
			player.sendMessage(Text.translatable("message.avatarbending.need_ground").formatted(Formatting.GRAY), true);
			ctx.fail();
			return;
		}
		Vec3d pos = Vec3d.ofBottomCenter(ground);
		VolcanoEntity volcano = new VolcanoEntity(ModEntities.VOLCANO, world);
		volcano.setup(player, pos, ctx.flatLook(), ctx.power());
		world.spawnEntity(volcano);
		Fx.sigil(world, pos, Colors.LAVA, 6f, 20);
		Fx.flames(world, ctx.hands(), Colors.LAVA, 0.8f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.5f, 0.5f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 2f, 0.6f);
	}

	/**
	 * The Avatar leaves their body as a glowing spirit: they can fly, become invisible, take far less
	 * damage and mobs lose track of them.
	 */
	public static void spiritForm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = 160;
		BenderData data = BendingManager.get(player);
		data.setSpiritTicks(duration);
		data.grantFallImmunity(duration + 100);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, duration, 0, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 2, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 1, false, false, true));
		player.setVelocity(player.getVelocity().add(0, 0.5, 0));
		player.velocityModified = true;
		Fx.attach(player, AttachedFxType.SPIRIT_FORM, duration);
		Fx.pillar(world, ctx.feet(), Colors.SPIRIT, 10f, 20);
		Fx.burst(world, ctx.chest(), Colors.SPIRIT, 1.6f);
		ctx.sfx(ModSounds.SPIRIT_CHIME, 2f, 0.8f);
		ctx.sfx(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.5f);
		loseTrack(world, player);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age >= duration || data.spiritTicks() <= 0) {
				Fx.burst(world, player.getPos().add(0, 1, 0), Colors.SPIRIT, 1.2f);
				Sfx.play(world, player.getPos(), ModSounds.SPIRIT_CHIME, 1.4f, 1.3f);
				return true;
			}
			if (age % 10 == 0) {
				loseTrack(world, player);
			}
			return false;
		});
	}

	private static void loseTrack(ServerWorld world, ServerPlayerEntity player) {
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32),
			m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
	}

	/**
	 * The light of Raava: fully heals the Avatar, heals allies, cleanses curses and burns away the
	 * undead and other dark creatures.
	 */
	public static void raavasLight(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d center = ctx.chest();
		double radius = 12 * ctx.power();
		cleanse(player, player.getMaxHealth());
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1));
		List<LivingEntity> allies = world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(radius),
			e -> e != player && e.isAlive() && (e instanceof PlayerEntity || (e instanceof TameableEntity t && t.isOwner(player))));
		for (LivingEntity ally : allies) {
			cleanse(ally, 12f);
			Fx.attach(ally, AttachedFxType.HEALING, 40);
		}
		for (LivingEntity target : ctx.targetsAround(center, radius)) {
			Vec3d at = target.getPos().add(0, target.getHeight() / 2, 0);
			if (target.getType().isIn(EntityTypeTags.UNDEAD)) {
				ctx.damage(target, Element.AVATAR, 20);
				target.setOnFireForTicks(100);
				Fx.burst(world, at, Colors.SPIRIT, 1.0f);
			} else if (target instanceof Monster) {
				ctx.damage(target, Element.AVATAR, 6);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
				Fx.burst(world, at, Colors.SPIRIT, 0.6f);
			} else {
				continue;
			}
			ctx.knockback(target, target.getPos().subtract(player.getPos()), 1.6, 0.6);
		}
		Fx.attach(player, AttachedFxType.HEALING, 60);
		Fx.pillar(world, ctx.feet(), Colors.SPIRIT, 30f, 40);
		Fx.flash(world, center, Colors.SPIRIT, 48f);
		Fx.groundShockwave(world, ctx.feet(), Colors.SPIRIT, (float) radius);
		Fx.ring(world, center, UP, Colors.WHITE, (float) radius, 16);
		Fx.spiral(world, ctx.feet(), Colors.SPIRIT, 1.4f, 4f);
		ctx.sfx(ModSounds.SPIRIT_CHIME, 2f, 1.0f);
		ctx.sfx(ModSounds.AVATAR_PULSE, 2f, 1.4f);
		ctx.sfx(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
	}

	private static void cleanse(LivingEntity entity, float heal) {
		entity.heal(heal);
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
		entity.removeStatusEffect(StatusEffects.POISON);
		entity.removeStatusEffect(StatusEffects.WITHER);
		entity.removeStatusEffect(StatusEffects.WEAKNESS);
		entity.removeStatusEffect(StatusEffects.SLOWNESS);
		entity.removeStatusEffect(StatusEffects.BLINDNESS);
		entity.removeStatusEffect(StatusEffects.DARKNESS);
		entity.removeStatusEffect(StatusEffects.NAUSEA);
		entity.removeStatusEffect(StatusEffects.MINING_FATIGUE);
		entity.extinguish();
		entity.setFrozenTicks(0);
	}

	/**
	 * Only in the Avatar State: the Avatar rises and unleashes waves of every element and bolts of
	 * lightning, ending in a cataclysmic explosion.
	 */
	public static void avatarsWrath(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = 100;
		double radius = 14;
		Vec3d origin = player.getPos();
		player.setVelocity(player.getVelocity().x, 1.0, player.getVelocity().z);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(duration + 200);
		Fx.attach(player, AttachedFxType.ELEMENT_STORM, duration);
		Fx.sigil(world, origin, Colors.AVATAR, (float) radius, duration + 20);
		Fx.pillar(world, origin, Colors.AVATAR, 40f, duration);
		Fx.flash(world, ctx.chest(), Colors.AVATAR, 64f);
		Fx.shake(world, origin, 3f, 64);
		ctx.sfx(ModSounds.AVATAR_STATE, 3f, 0.8f);
		ctx.sfx(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.6f);
		ctx.sfx(ModSounds.WIND_HOWL, 3f, 0.6f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			Vec3d center = player.getPos();
			if (age < duration) {
				if (age % 20 == 0) {
					Element element = Element.BENDABLE.get((age / 20) % 4);
					Vec3d ground = groundBelow(ctx, center);
					Fx.groundShockwave(world, ground, auraColor(element), (float) radius);
					Fx.ring(world, center.add(0, 1, 0), UP, auraColor(element), (float) radius, 12);
					for (LivingEntity target : ctx.targetsAround(center, radius)) {
						elementHit(ctx, target, element, center, 6);
						Fx.explosion(world, target.getPos().add(0, target.getHeight() / 2, 0), element, 0.7f);
					}
					elementSound(world, center, element, 3f);
					Sfx.play(world, center, ModSounds.AVATAR_PULSE, 2.5f, 0.9f + age * 0.004f);
				}
				if (age % 10 == 5) {
					strike(ctx, center, radius);
				}
				return false;
			}
			// The cataclysm.
			Vec3d core = center.add(0, 1, 0);
			Fx.explosion(world, core, Element.AVATAR, 3f);
			Fx.groundShockwave(world, groundBelow(ctx, center), Colors.AVATAR, (float) (radius * 1.4));
			Fx.ring(world, core, UP, Colors.WHITE, (float) (radius * 1.4), 16);
			Fx.ring(world, core, new Vec3d(1, 0.4, 0), Colors.AVATAR, (float) radius, 14);
			Fx.ring(world, core, new Vec3d(-0.4, 0.3, 1), Colors.FIRE, (float) radius, 14);
			Fx.flash(world, core, Colors.WHITE, 80f);
			Fx.shake(world, core, 6f, 80);
			Sfx.play(world, core, ModSounds.QUAKE_BOOM, 6f, 0.6f);
			Sfx.play(world, core, ModSounds.FIRE_EXPLOSION, 6f, 0.7f);
			Sfx.play(world, core, ModSounds.AVATAR_PULSE, 5f, 0.7f);
			Sfx.play(world, core, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 4f, 0.6f);
			double blast = radius * 1.2;
			for (LivingEntity target : ctx.targetsAround(core, blast)) {
				double falloff = 1 - Math.min(0.5, target.getPos().distanceTo(core) / blast * 0.5);
				ctx.damage(target, Element.AVATAR, (float) (25 * falloff));
				target.setOnFireForTicks(100);
				ctx.knockback(target, target.getPos().subtract(core), 2.5, 1.0);
			}
			return true;
		});
	}

	/** A bolt of lightning from the sky onto a random enemy near the Avatar (or a random spot). */
	private static void strike(AbilityContext ctx, Vec3d center, double radius) {
		ServerWorld world = ctx.world();
		List<LivingEntity> targets = ctx.targetsAround(center, radius);
		Vec3d point;
		if (!targets.isEmpty()) {
			point = targets.get(world.random.nextInt(targets.size())).getPos();
		} else {
			double angle = world.random.nextDouble() * Math.PI * 2;
			double r = 3 + world.random.nextDouble() * (radius - 3);
			point = groundBelow(ctx, center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r));
		}
		Vec3d sky = ctx.clearPath(point.add(0, 0.5, 0),
			point.add((world.random.nextDouble() - 0.5) * 4, 22, (world.random.nextDouble() - 0.5) * 4), 0.5);
		Fx.lightning(world, sky, point, Colors.LIGHTNING, 1.1f);
		Sfx.play(world, point, ModSounds.LIGHTNING_STRIKE, 4f, 1.0f);
		for (LivingEntity target : ctx.targetsAround(point, 2.5)) {
			ctx.damage(target, Element.FIRE, 8);
			target.setOnFireForTicks(60);
		}
	}

	private static Vec3d groundBelow(AbilityContext ctx, Vec3d pos) {
		BlockPos ground = ctx.groundAt(pos.x, pos.y, pos.z, 1, 24);
		return ground != null ? Vec3d.ofBottomCenter(ground) : pos;
	}
}
