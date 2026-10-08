package com.avatarbending.bending;

import com.avatarbending.AvatarBending;
import com.avatarbending.ability.AbilityContext;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.network.ModPayloads.AvatarVisualPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The Avatar State: a temporary super mode for Avatars with flight, buffs, free spells and a
 * four-element aura.
 */
public final class AvatarState {
	public static final int DURATION = 30 * 20;
	public static final int COOLDOWN = 120 * 20;
	/** The Avatar State triggers by itself when an Avatar drops below this much health. */
	public static final float AUTO_TRIGGER_HEALTH = 6f;
	public static final float POWER = 1.75f;

	private AvatarState() {
	}

	public static void enter(ServerPlayerEntity player, BenderData data) {
		ServerWorld world = player.getServerWorld();
		data.setAvatarStateTicks(DURATION);
		data.setAvatarStateCooldown(COOLDOWN);
		data.setChi(BenderData.MAX_CHI);
		applyBuffs(player);
		grantFlight(player, data);

		Vec3d center = player.getPos().add(0, 1, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2f, 0.6f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.7f, 1.4f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.8f, 1.2f);
		world.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 120, 0.4, 0.8, 0.4, 0.45);

		AbilityContext context = new AbilityContext(player, world, true);
		for (Element element : Element.BENDABLE) {
			context.elementBurst(element, center, 40);
		}
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
		revokeFlight(player, data);
		data.grantFallImmunity(200);
		if (!player.isOnGround()) {
			// Float gently back down instead of dropping out of the sky.
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 160, 0, false, false, true));
		}
		if (playEffects) {
			ServerWorld world = player.getServerWorld();
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.5f, 0.8f);
			world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
		}
		broadcastVisual(player, false);
		BendingManager.sync(player, true);
	}

	static void tick(ServerPlayerEntity player, BenderData data) {
		if (!data.inAvatarState()) {
			if (data.grantedFlight()) {
				revokeFlight(player, data);
			}
			return;
		}
		if (!data.isAvatar() || !player.isAlive()) {
			exit(player, data, false);
			return;
		}
		data.setAvatarStateTicks(data.avatarStateTicks() - 1);
		if (data.avatarStateTicks() <= 0) {
			exit(player, data, true);
			return;
		}
		if (!player.getAbilities().allowFlying) {
			grantFlight(player, data);
		}
		if (player.age % 20 == 0) {
			applyBuffs(player);
		}
		data.grantFallImmunity(40);
		spawnAura(player);
	}

	private static void applyBuffs(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
	}

	/** Four rings of element-colored dust orbiting the Avatar at different heights. */
	private static void spawnAura(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		float t = player.age * 0.25f;
		int i = 0;
		for (Element element : Element.BENDABLE) {
			double angle = t + i * (Math.PI / 2);
			double radius = 1.4;
			double height = 0.3 + i * 0.45 + 0.15 * MathHelper.sin(t * 1.3f + i);
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			double y = player.getY() + height;
			world.spawnParticles(new DustParticleEffect(element.colorVector(), 1.4f), x, y, z, 2, 0.05, 0.05, 0.05, 0);
			switch (element) {
				case AIR -> world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 1, 0, 0, 0, 0.01);
				case WATER -> world.spawnParticles(ParticleTypes.DRIPPING_WATER, x, y, z, 1, 0, 0, 0, 0);
				case EARTH -> world.spawnParticles(ParticleTypes.COMPOSTER, x, y, z, 1, 0, 0, 0, 0);
				case FIRE -> world.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0.01);
				default -> {
				}
			}
			i++;
		}
		if (player.age % 4 == 0) {
			world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.6, 0.4, 0.02);
		}
	}

	private static void grantFlight(ServerPlayerEntity player, BenderData data) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		player.getAbilities().allowFlying = true;
		player.sendAbilitiesUpdate();
		data.setGrantedFlight(true);
	}

	public static void revokeFlight(ServerPlayerEntity player, BenderData data) {
		data.setGrantedFlight(false);
		if (player.isCreative() || player.isSpectator()) {
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
