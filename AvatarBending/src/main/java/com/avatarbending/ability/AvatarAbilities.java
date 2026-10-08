package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.entity.MeteorEntity;
import com.avatarbending.entity.ModEntities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Spells only the Avatar can use.
 */
public final class AvatarAbilities {
	private AvatarAbilities() {
	}

	/** All four elements orbit the Avatar and blast everything nearby again and again. */
	public static void elementalStorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = 100;
		double maxRadius = 9 * ctx.power();
		ctx.sound(SoundEvents.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_WITHER_SHOOT, 1f, 0.6f);
		ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2f, 0.5f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			Vec3d center = player.getPos().add(0, 1, 0);
			double pulse = 0.5 + 0.5 * Math.sin(age * 0.2);
			double radius = 2.5 + (maxRadius - 2.5) * pulse;
			int i = 0;
			for (Element element : Element.BENDABLE) {
				for (int k = 0; k < 6; k++) {
					double angle = age * 0.35 + i * (Math.PI / 2) + k * 0.12;
					double tilt = Math.sin(age * 0.1 + i) * 1.2;
					double x = center.x + Math.cos(angle) * radius;
					double z = center.z + Math.sin(angle) * radius;
					double y = center.y + tilt * Math.sin(angle);
					switch (element) {
						case AIR -> world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 1, 0, 0, 0, 0);
						case WATER -> world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.05, 0.05, 0.05, 0);
						case EARTH -> world.spawnParticles(AbilityContext.blockDust(ctx.groundBlock()), x, y, z, 1, 0, 0, 0, 0);
						case FIRE -> world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
						default -> {
						}
					}
					world.spawnParticles(ctx.dust(element, 1.5f), x, y, z, 1, 0, 0, 0, 0);
				}
				i++;
			}
			if (age % 10 == 0) {
				Element element = Element.BENDABLE.get((age / 10) % 4);
				for (LivingEntity target : ctx.targetsAround(center, maxRadius)) {
					ctx.damage(target, element, 4);
					switch (element) {
						case AIR -> ctx.knockback(target, target.getPos().subtract(center), 1.4, 0.5);
						case WATER -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
						case EARTH -> {
							target.setVelocity(target.getVelocity().x, 0.8, target.getVelocity().z);
							target.velocityModified = true;
						}
						case FIRE -> target.setOnFireForTicks(80);
						default -> {
						}
					}
					ctx.elementBurst(element, target.getPos().add(0, 1, 0), 10);
				}
				switch (element) {
					case AIR -> ctx.sound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.5f, 1f);
					case WATER -> ctx.sound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.8f);
					case EARTH -> ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.7f);
					case FIRE -> ctx.sound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);
					default -> {
					}
				}
			}
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
		ctx.sound(SoundEvents.BLOCK_CONDUIT_ACTIVATE, 2f, 0.6f);
		ctx.sound(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 0.6f);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, duration + 40, 0));
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || !target.isAlive()) {
				return true;
			}
			Vec3d from = player.getEyePos().subtract(0, 0.3, 0);
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0);
			ctx.line(ParticleTypes.END_ROD, from, to, 0.5, 0.03);
			ctx.line(ctx.dust(Element.AVATAR, 1.2f), from, to, 0.4, 0.05);
			world.spawnParticles(ParticleTypes.GLOW, to.x, to.y, to.z, 4, 0.3, 0.5, 0.3, 0.02);
			// Lift and hold the target in the air.
			target.setVelocity(0, target.getY() < player.getY() + 1.5 ? 0.12 : 0.0, 0);
			target.velocityModified = true;
			target.fallDistance = 0;
			if (age < duration) {
				if (age % 8 == 0) {
					ctx.soundAt(to, SoundEvents.BLOCK_BEACON_AMBIENT, 1.5f, 1.5f + age * 0.01f);
				}
				return false;
			}
			world.spawnParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 80, 0.3, 0.6, 0.3, 0.3);
			world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, to.x, to.y, to.z, 40, 0.3, 0.6, 0.3, 0.4);
			ctx.soundAt(to, SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.6f);
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
		Vec3d start = target.add(back.x, 30, back.z);
		Vec3d velocity = target.subtract(start).normalize().multiply(1.6);
		MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR, world);
		meteor.setup(player, start, velocity, 18f, ctx.power());
		world.spawnEntity(meteor);
		ctx.sound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_WITHER_SHOOT, 1.5f, 0.4f);
		ctx.particles(ParticleTypes.END_ROD, player.getEyePos(), 30, 0.4, 0.2);
		// A warning ring where it will land.
		Vec3d landing = target;
		EffectScheduler.schedule(age -> {
			ctx.ring(ParticleTypes.FLAME, landing.add(0, 0.2, 0), 3.5, 28, 0);
			return age > 20 || meteor.isRemoved();
		});
	}
}
