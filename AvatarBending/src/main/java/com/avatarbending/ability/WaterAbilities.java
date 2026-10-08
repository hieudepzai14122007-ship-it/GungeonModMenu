package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.entity.IceShardEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.TidalWaveEntity;
import com.avatarbending.entity.WaterBlastEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WaterAbilities {
	private WaterAbilities() {
	}

	/** Water benders hit harder when standing in water or rain. */
	private static float wetBonus(AbilityContext ctx) {
		return ctx.player().isTouchingWaterOrRain() ? 1.5f : 1f;
	}

	/** A fast, spiraling whip of water. */
	public static void waterWhip(AbilityContext ctx) {
		WaterBlastEntity whip = new WaterBlastEntity(ModEntities.WATER_BLAST, ctx.world());
		Vec3d start = ctx.eyes().add(ctx.look().multiply(0.6)).subtract(0, 0.3, 0);
		whip.setup(ctx.player(), start, ctx.look().multiply(1.8), 5f * wetBonus(ctx), ctx.power());
		ctx.world().spawnEntity(whip);
		ctx.sound(SoundEvents.ENTITY_PLAYER_SPLASH, 0.8f, 1.6f);
		ctx.sound(SoundEvents.ITEM_BUCKET_EMPTY, 0.8f, 1.3f);
		ctx.particles(ParticleTypes.SPLASH, start, 10, 0.2, 0.1);
	}

	/** A spray of seven razor-sharp ice shards. */
	public static void iceShards(AbilityContext ctx) {
		Vec3d look = ctx.look();
		float damage = 2.5f * wetBonus(ctx);
		for (int i = 0; i < 7; i++) {
			IceShardEntity shard = new IceShardEntity(ModEntities.ICE_SHARD, ctx.world());
			Vec3d dir = AbilityContext.rotateY(look, (i - 3) * 6).add(0, (ctx.world().random.nextDouble() - 0.5) * 0.1, 0).normalize();
			Vec3d start = ctx.eyes().add(dir.multiply(0.7)).subtract(0, 0.25, 0);
			shard.setup(ctx.player(), start, dir.multiply(1.7), damage, ctx.power());
			ctx.world().spawnEntity(shard);
		}
		ctx.sound(SoundEvents.BLOCK_GLASS_BREAK, 0.8f, 1.8f);
		ctx.sound(SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.2f);
		ctx.particles(ParticleTypes.SNOWFLAKE, ctx.eyes().add(look), 20, 0.3, 0.05);
	}

	/** A wave of ice spikes that races along the ground and freezes mobs solid. */
	public static void iceWave(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = ctx.flatLook();
		Vec3d origin = player.getPos();
		int length = (int) (14 * ctx.power());
		Set<UUID> frozen = new HashSet<>();
		ctx.sound(SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.6f);
		double[] lastY = {origin.y};
		int[] misses = {0};
		EffectScheduler.schedule(age -> {
			int step = age + 2;
			if (step > length) {
				return true;
			}
			Vec3d p = origin.add(dir.multiply(step));
			BlockPos ground = ctx.groundAt(p.x, lastY[0], p.z, 2, 4);
			if (ground == null) {
				// Freeze water into a path of ice.
				BlockPos water = ctx.waterSurfaceAt(p.x, lastY[0], p.z, 1, 3);
				if (water != null) {
					TempBlocks.place(world, water, Blocks.PACKED_ICE.getDefaultState(), 160 + step * 2);
					ground = water.up();
				}
			}
			if (ground == null) {
				return ++misses[0] > 3;
			}
			misses[0] = 0;
			lastY[0] = ground.getY();
			int height = 1 + world.random.nextInt(2) + (step % 3 == 0 ? 1 : 0);
			for (int h = 0; h < height; h++) {
				TempBlocks.place(world, ground.up(h), Blocks.PACKED_ICE.getDefaultState(), 100 + step * 2);
			}
			Vec3d center = Vec3d.ofBottomCenter(ground);
			world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1, center.z, 12, 0.6, 0.6, 0.6, 0.05);
			world.spawnParticles(AbilityContext.blockDust(Blocks.PACKED_ICE.getDefaultState()), center.x, center.y + 0.5, center.z, 10, 0.4, 0.4, 0.4, 0.1);
			if (step % 2 == 0) {
				ctx.soundAt(center, SoundEvents.BLOCK_GLASS_BREAK, 0.6f, 0.7f + world.random.nextFloat() * 0.4f);
			}
			for (LivingEntity target : ctx.targetsAround(center.add(0, 0.5, 0), 2.2)) {
				if (frozen.add(target.getUuid())) {
					ctx.damage(target, Element.WATER, 5);
					freeze(world, target, (int) (80 * ctx.power()));
					target.setVelocity(0, 0.5, 0);
					target.velocityModified = true;
				}
			}
			return false;
		});
	}

	/** Freezes a mob in place and builds a little ice prison around its feet. */
	public static void freeze(ServerWorld world, LivingEntity target, int ticks) {
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 5));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, ticks, 2));
		target.setFrozenTicks(target.getMinFreezeDamageTicks() + ticks);
		BlockPos feet = target.getBlockPos();
		for (Direction side : Direction.Type.HORIZONTAL) {
			TempBlocks.place(world, feet.offset(side), Blocks.PACKED_ICE.getDefaultState(), ticks);
		}
		world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
	}

	/** Glowing healing water for you, nearby players and your pets. */
	public static void healingWaters(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float heal = 8f * ctx.power() * wetBonus(ctx);
		heal(player, heal);
		for (LivingEntity ally : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(8),
			e -> e != player && e.isAlive() && (e instanceof PlayerEntity || (e instanceof TameableEntity t && t.isOwner(player))))) {
			heal(ally, heal * 0.75f);
			world.spawnParticles(ParticleTypes.HEART, ally.getX(), ally.getBodyY(1.0), ally.getZ(), 3, 0.3, 0.2, 0.3, 0);
		}
		ctx.sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
		ctx.sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1f);
		ctx.sound(SoundEvents.ITEM_BOTTLE_FILL, 1f, 0.8f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			for (int strand = 0; strand < 3; strand++) {
				double angle = age * 0.5 + strand * (Math.PI * 2 / 3);
				double y = player.getY() + (age % 20) * 0.1;
				double x = player.getX() + Math.cos(angle) * 0.9;
				double z = player.getZ() + Math.sin(angle) * 0.9;
				world.spawnParticles(ParticleTypes.GLOW, x, y, z, 1, 0, 0, 0, 0);
				world.spawnParticles(ParticleTypes.SPLASH, x, y, z, 2, 0.05, 0.05, 0.05, 0);
			}
			if (age % 5 == 0) {
				world.spawnParticles(ParticleTypes.DRIPPING_WATER, player.getX(), player.getY() + 2.2, player.getZ(), 4, 0.4, 0.1, 0.4, 0);
			}
			return age >= 40;
		});
	}

	private static void heal(LivingEntity entity, float amount) {
		entity.heal(amount);
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
		entity.removeStatusEffect(StatusEffects.POISON);
		entity.removeStatusEffect(StatusEffects.WITHER);
		entity.extinguish();
		entity.setFrozenTicks(0);
	}

	/** Ride a towering column of water and hover above the battlefield. */
	public static void waterSpout(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = (int) (140 * ctx.power());
		double hoverHeight = 6 * ctx.power();
		BendingManager.get(player).grantFallImmunity(duration + 60);
		ctx.sound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.6f);
		ctx.sound(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5f, 1f);
		player.setVelocity(player.getVelocity().x, 0.8, player.getVelocity().z);
		player.velocityModified = true;
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				player.removeStatusEffect(StatusEffects.LEVITATION);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, true));
				return true;
			}
			// Find the ground (or water surface) below.
			BlockPos feet = player.getBlockPos();
			int groundY = feet.getY() - 24;
			for (int dy = 0; dy < 24; dy++) {
				BlockPos p = feet.down(dy);
				if (!world.getBlockState(p).getCollisionShape(world, p).isEmpty() || !world.getFluidState(p).isEmpty()) {
					groundY = p.getY() + 1;
					break;
				}
			}
			double above = player.getY() - groundY;
			if (player.isSneaking()) {
				player.removeStatusEffect(StatusEffects.LEVITATION);
			} else if (above < hoverHeight) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 6, 2, false, false, false));
			} else {
				player.removeStatusEffect(StatusEffects.LEVITATION);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 6, 0, false, false, false));
			}
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 10, 0, false, false, false));
			// The water column under the player.
			for (double y = groundY; y < player.getY(); y += 0.6) {
				double angle = age * 0.7 + y;
				world.spawnParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(angle) * 0.4, y, player.getZ() + Math.sin(angle) * 0.4, 1, 0.05, 0, 0.05, 0);
				world.spawnParticles(ParticleTypes.BUBBLE_POP, player.getX() + Math.cos(angle + Math.PI) * 0.3, y, player.getZ() + Math.sin(angle + Math.PI) * 0.3, 1, 0, 0, 0, 0);
			}
			world.spawnParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY(), player.getZ(), 4, 0.5, 0.1, 0.5, 0);
			if (age % 20 == 0) {
				ctx.sound(SoundEvents.BLOCK_WATER_AMBIENT, 1f, 1.2f);
			}
			return false;
		});
	}

	/** A massive wave that sweeps the battlefield. */
	public static void tidalWave(AbilityContext ctx) {
		Vec3d flat = ctx.flatLook();
		Vec3d spot = ctx.player().getPos().add(flat.multiply(2));
		BlockPos ground = ctx.groundAt(spot.x, spot.y, spot.z, 2, 5);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : spot;
		TidalWaveEntity wave = new TidalWaveEntity(ModEntities.TIDAL_WAVE, ctx.world());
		wave.setup(ctx.player(), pos, flat, ctx.power() * (ctx.player().isTouchingWaterOrRain() ? 1.25f : 1f));
		ctx.world().spawnEntity(wave);
		ctx.sound(SoundEvents.AMBIENT_UNDERWATER_EXIT, 2f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.6f);
	}
}
