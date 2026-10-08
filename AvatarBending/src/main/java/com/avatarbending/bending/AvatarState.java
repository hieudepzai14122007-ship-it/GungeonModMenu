package com.avatarbending.bending;

import com.avatarbending.AvatarBending;
import com.avatarbending.ability.AbilityContext;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.network.ModPayloads.AvatarVisualPayload;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * The Avatar State: a temporary super mode for Avatars with flight, buffs, free spells, an aura that
 * blasts away nearby enemies and turns back incoming projectiles. The four-element aura itself is
 * drawn by each client (AvatarAuraFx).
 */
public final class AvatarState {
	public static final int DURATION = 30 * 20;
	public static final int COOLDOWN = 120 * 20;
	/** The Avatar State triggers by itself when an Avatar drops below this much health. */
	public static final float AUTO_TRIGGER_HEALTH = 6f;
	public static final float POWER = 1.75f;
	/** Enemies this close are blasted away by the aura every couple of seconds. */
	private static final double PULSE_RADIUS = 5;
	private static final int PULSE_INTERVAL = 40;
	/** Projectiles this close to the Avatar are turned around. */
	private static final double DEFLECT_RADIUS = 4;
	private static final Vec3d UP = new Vec3d(0, 1, 0);

	private AvatarState() {
	}

	public static void enter(ServerPlayerEntity player, BenderData data) {
		ServerWorld world = player.getServerWorld();
		data.setAvatarStateTicks(DURATION);
		data.setAvatarStateCooldown(COOLDOWN);
		data.setChi(BenderData.MAX_CHI);
		applyBuffs(player);
		grantFlight(player, data);

		// Rise into the air inside a pillar of light while the four elements burst out.
		Vec3d feet = player.getPos();
		Vec3d center = feet.add(0, 1, 0);
		player.setVelocity(player.getVelocity().x * 0.3, 0.9, player.getVelocity().z * 0.3);
		player.velocityModified = true;
		Fx.pillar(world, feet, Colors.AVATAR, 48f, 40);
		Fx.flash(world, center, Colors.AVATAR, 48f);
		Fx.shake(world, center, 4f, 48);
		Fx.groundShockwave(world, feet, Colors.AVATAR, 10f);
		Fx.explosion(world, center, Element.AVATAR, 2f);
		int[] colors = {Colors.WHITE, Colors.WATER, Colors.EARTH_GREEN, Colors.FIRE};
		Vec3d[] axes = {UP, new Vec3d(1, 0.35, 0), new Vec3d(0, 0.35, 1), new Vec3d(1, 0.2, -1)};
		for (int i = 0; i < 4; i++) {
			Fx.ring(world, center, axes[i], colors[i], 6f + i * 1.5f, 16 + i * 2);
		}
		Sfx.play(world, center, ModSounds.AVATAR_STATE, 4f, 1f);
		Sfx.play(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
		Sfx.play(world, center, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);

		AbilityContext context = new AbilityContext(player, world, true);
		// Shockwave that throws everything away.
		for (LivingEntity target : context.targetsAround(center, 9)) {
			Vec3d push = target.getPos().subtract(player.getPos()).normalize();
			context.knockback(target, push, 2.2, 0.8);
			context.damage(target, Element.AVATAR, 6);
		}
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class,
			player.getBoundingBox().expand(9), p -> p.getOwner() != player)) {
			projectile.setVelocity(projectile.getVelocity().multiply(-1.2));
			projectile.setOwner(player);
			projectile.velocityModified = true;
		}

		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 15));
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.avatarbending.avatar_state")
			.formatted(Formatting.AQUA, Formatting.BOLD)));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.avatarbending.avatar_state.sub")
			.formatted(Formatting.WHITE)));

		broadcastVisual(player, true);
		AvatarBending.grantAdvancement(player, "avatar_state");
		BendingManager.sync(player, true);
	}

	public static void exit(ServerPlayerEntity player, BenderData data, boolean playEffects) {
		data.setAvatarStateTicks(0);
		updateFlight(player, data);
		if (playEffects) {
			ServerWorld world = player.getServerWorld();
			Vec3d center = player.getPos().add(0, 1, 0);
			Fx.burst(world, center, Colors.AVATAR, 1.6f);
			Fx.ring(world, center, UP, Colors.AVATAR, 4f, 14);
			Sfx.play(world, center, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.8f);
			Sfx.play(world, center, ModSounds.SPIRIT_CHIME, 1.2f, 0.7f);
		}
		broadcastVisual(player, false);
		BendingManager.sync(player, true);
	}

	static void tick(ServerPlayerEntity player, BenderData data) {
		if (!player.isAlive()) {
			data.setSpiritTicks(0);
		}
		if (data.inAvatarState()) {
			if (!data.isAvatar() || !player.isAlive()) {
				exit(player, data, false);
			} else {
				data.setAvatarStateTicks(data.avatarStateTicks() - 1);
				if (data.avatarStateTicks() <= 0) {
					exit(player, data, true);
				} else {
					if (player.age % 20 == 0) {
						applyBuffs(player);
					}
					deflectProjectiles(player);
					if (data.avatarStateTicks() % PULSE_INTERVAL == 0) {
						pulse(player);
					}
				}
			}
		}
		updateFlight(player, data);
	}

	/** Gives or takes flight depending on the Avatar State and Spirit Form. */
	private static void updateFlight(ServerPlayerEntity player, BenderData data) {
		if (data.wantsFlight() && player.isAlive()) {
			if (!player.getAbilities().allowFlying) {
				grantFlight(player, data);
			}
			data.grantFallImmunity(40);
		} else if (data.grantedFlight()) {
			boolean airborne = player.getAbilities().flying || !player.isOnGround();
			revokeFlight(player, data);
			data.grantFallImmunity(200);
			if (airborne && player.isAlive()) {
				// Float gently back down instead of dropping out of the sky.
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 160, 0, false, false, true));
			}
		}
	}

	/** The aura blasts away enemies that get too close. */
	private static void pulse(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		AbilityContext context = new AbilityContext(player, world, true);
		Vec3d center = player.getPos().add(0, 1, 0);
		var near = context.targetsAround(center, PULSE_RADIUS);
		if (near.isEmpty()) {
			return;
		}
		for (LivingEntity target : near) {
			context.knockback(target, target.getPos().subtract(player.getPos()), 1.3, 0.45);
			context.damage(target, Element.AVATAR, 2);
		}
		Fx.ring(world, center, UP, Colors.AVATAR, (float) PULSE_RADIUS + 1, 12);
		Fx.windBurst(world, center, UP, 1.2f);
		Sfx.play(world, center, ModSounds.AVATAR_PULSE, 1.6f, 1.1f);
	}

	/** Turns back arrows, fireballs and enemy bending before they reach the Avatar. */
	private static void deflectProjectiles(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d center = player.getPos().add(0, 1, 0);
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class,
			player.getBoundingBox().expand(DEFLECT_RADIUS), p -> p.getOwner() != player)) {
			Vec3d velocity = projectile.getVelocity();
			Vec3d toward = center.subtract(projectile.getPos());
			if (velocity.lengthSquared() < 0.01 || velocity.dotProduct(toward) <= 0) {
				continue;
			}
			Vec3d away = projectile.getPos().subtract(center).normalize();
			projectile.setVelocity(away.multiply(Math.max(1.0, velocity.length())));
			projectile.setOwner(player);
			projectile.velocityModified = true;
			Fx.burst(world, projectile.getPos(), Colors.AVATAR, 0.5f);
			Sfx.play(world, projectile.getPos(), SoundEvents.ENTITY_BREEZE_DEFLECT, 1f, 1.3f);
		}
	}

	private static void applyBuffs(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
	}

	/** Creative and spectator players already fly; their flight is never touched. */
	private static boolean managesOwnFlight(ServerPlayerEntity player) {
		return player.getAbilities().creativeMode || player.isSpectator();
	}

	private static void grantFlight(ServerPlayerEntity player, BenderData data) {
		if (managesOwnFlight(player)) {
			return;
		}
		player.getAbilities().allowFlying = true;
		player.sendAbilitiesUpdate();
		data.setGrantedFlight(true);
	}

	public static void revokeFlight(ServerPlayerEntity player, BenderData data) {
		data.setGrantedFlight(false);
		if (managesOwnFlight(player)) {
			return;
		}
		player.getAbilities().allowFlying = false;
		player.getAbilities().flying = false;
		player.sendAbilitiesUpdate();
	}

	public static void broadcastVisual(ServerPlayerEntity player, boolean active) {
		AvatarVisualPayload payload = new AvatarVisualPayload(player.getId(), active);
		ModPayloads.send(player, payload);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
			if (viewer != player) {
				ModPayloads.send(viewer, payload);
			}
		}
	}
}
