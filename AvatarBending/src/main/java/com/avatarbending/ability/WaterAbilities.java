package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.entity.IceShardEntity;
import com.avatarbending.entity.MaelstromEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.TidalWaveEntity;
import com.avatarbending.entity.WaterBlastEntity;
import com.avatarbending.entity.WaterOrbEntity;
import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WaterAbilities {
	private static final Vec3d UP = new Vec3d(0, 1, 0);

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
		Fx.splash(ctx.world(), ctx.hands(), 0.35f);
		ctx.sfx(ModSounds.WATER_WHOOSH, 1.3f, 1.1f);
		ctx.sfx(ModSounds.WHIP_CRACK, 0.9f, 1.3f);
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
		Fx.frost(ctx.world(), ctx.hands(), 0.6f);
		ctx.sfx(ModSounds.ICE_CRACK, 1.3f, 1.3f);
		ctx.sfx(ModSounds.WATER_WHOOSH, 1f, 1.4f);
		ctx.sfx(SoundEvents.BLOCK_GLASS_BREAK, 0.8f, 1.8f);
	}

	/** A wave of ice spikes that races along the ground and freezes mobs solid. */
	public static void iceWave(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = ctx.flatLook();
		Vec3d origin = player.getPos();
		int length = (int) (14 * ctx.power());
		Set<UUID> frozen = new HashSet<>();
		ctx.sfx(ModSounds.ICE_CRACK, 1.5f, 0.7f);
		ctx.sfx(SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.6f);
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
				ctx.placeTemp(ground.up(h), Blocks.PACKED_ICE.getDefaultState(), 100 + step * 2);
			}
			Vec3d center = Vec3d.ofBottomCenter(ground);
			Fx.frost(world, center.add(0, 0.8, 0), 0.7f);
			if (step % 2 == 0) {
				Sfx.play(world, center, ModSounds.ICE_CRACK, 1.0f, 0.8f + world.random.nextFloat() * 0.5f);
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
			BlockPos p = feet.offset(side);
			if (world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(p), LivingEntity::isAlive).isEmpty()) {
				TempBlocks.place(world, p, Blocks.PACKED_ICE.getDefaultState(), ticks);
			}
		}
		Fx.attach(target, AttachedFxType.FROZEN, ticks);
		Fx.frost(world, target.getPos().add(0, target.getHeight() / 2, 0), 0.8f);
	}

	/** Hurls a glowing water orb that explodes into a field of ice. */
	public static void glacierBomb(AbilityContext ctx) {
		WaterOrbEntity orb = new WaterOrbEntity(ModEntities.WATER_ORB, ctx.world());
		Vec3d look = ctx.look();
		orb.setup(ctx.player(), ctx.hands(), look.multiply(1.3).add(0, 0.12, 0), 6f * wetBonus(ctx), ctx.power());
		ctx.world().spawnEntity(orb);
		Fx.splash(ctx.world(), ctx.hands(), 0.5f);
		ctx.sfx(ModSounds.WATER_WHOOSH, 1.4f, 0.8f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 0.8f, 1.6f);
	}

	/** Glowing healing water for you, nearby players and your pets. */
	public static void healingWaters(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float heal = 8f * ctx.power() * wetBonus(ctx);
		heal(player, heal);
		Fx.attach(player, AttachedFxType.HEALING, 60);
		for (LivingEntity ally : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(8),
			e -> e != player && e.isAlive() && (e instanceof PlayerEntity || (e instanceof TameableEntity t && t.isOwner(player))))) {
			heal(ally, heal * 0.75f);
			Fx.attach(ally, AttachedFxType.HEALING, 40);
		}
		Fx.spiral(world, ctx.feet(), Colors.HEAL, 1.1f, 2.6f);
		Fx.groundShockwave(world, ctx.feet(), Colors.HEAL, 8f);
		Fx.burst(world, ctx.chest(), Colors.HEAL, 1.2f);
		ctx.sfx(ModSounds.SPIRIT_CHIME, 1.5f, 1.1f);
		ctx.sfx(SoundEvents.ITEM_BOTTLE_FILL, 1f, 0.8f);
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
		Fx.attach(player, AttachedFxType.WATER_SPOUT, duration);
		Fx.splash(world, ctx.feet(), 1.5f);
		ctx.sfx(ModSounds.WATER_SURGE, 1.6f, 1.1f);
		ctx.sfx(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5f, 1f);
		player.setVelocity(player.getVelocity().x, 0.8, player.getVelocity().z);
		player.velocityModified = true;
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				player.removeStatusEffect(StatusEffects.LEVITATION);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, true));
				return true;
			}
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
			if (age % 40 == 20) {
				Sfx.play(world, player.getPos(), ModSounds.WATER_WHOOSH, 0.8f, 0.7f);
			}
			return false;
		});
	}

	/** Eight water tentacles that lash out at anything that comes close and block projectiles. */
	public static void octopusForm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = (int) (200 * ctx.power());
		Fx.attach(player, AttachedFxType.OCTOPUS, duration);
		Fx.splash(world, ctx.chest(), 1.4f);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 0, false, false, true));
		ctx.sfx(ModSounds.WATER_SURGE, 1.6f, 1.2f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= duration) {
				return true;
			}
			Vec3d center = player.getPos().add(0, 1, 0);
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(4),
				p -> p.getOwner() != player)) {
				Fx.splash(world, projectile.getPos(), 0.4f);
				projectile.discard();
			}
			if (age % 12 == 0) {
				List<LivingEntity> targets = ctx.targetsAround(center, 6);
				targets.sort(Comparator.comparingDouble(t -> t.squaredDistanceTo(center)));
				for (int i = 0; i < Math.min(2, targets.size()); i++) {
					LivingEntity target = targets.get(i);
					Vec3d tp = target.getPos().add(0, target.getHeight() / 2, 0);
					ctx.damage(target, Element.WATER, 4 * wetBonus(ctx));
					ctx.knockback(target, target.getPos().subtract(player.getPos()), 1.0, 0.4);
					Fx.line(world, center, tp, Colors.WATER, 2.5f);
					Fx.splash(world, tp, 0.6f);
					Sfx.play(world, tp, ModSounds.WHIP_CRACK, 1.2f, 1.1f);
					Sfx.play(world, tp, SoundEvents.ENTITY_PLAYER_SPLASH, 0.8f, 1.3f);
				}
			}
			if (age % 50 == 25) {
				Sfx.play(world, player.getPos(), ModSounds.WATER_WHOOSH, 0.8f, 0.9f);
			}
			return false;
		});
	}

	/** A massive wave that sweeps the battlefield. */
	public static void tidalWave(AbilityContext ctx) {
		Vec3d flat = ctx.flatLook();
		Vec3d spot = ctx.feet().add(flat.multiply(2));
		BlockPos ground = ctx.groundAt(spot.x, spot.y, spot.z, 2, 5);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : spot;
		TidalWaveEntity wave = new TidalWaveEntity(ModEntities.TIDAL_WAVE, ctx.world());
		wave.setup(ctx.player(), pos, flat, ctx.power() * (ctx.player().isTouchingWaterOrRain() ? 1.25f : 1f));
		ctx.world().spawnEntity(wave);
		Fx.splash(ctx.world(), pos.add(0, 1, 0), 1.8f);
		ctx.sfx(ModSounds.WATER_SURGE, 3f, 0.8f);
		ctx.sfx(SoundEvents.AMBIENT_UNDERWATER_EXIT, 2f, 0.5f);
	}

	/** Grabs every creature nearby by the water in its body, lifts them up and slams them down. */
	public static void bloodbending(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		List<LivingEntity> victims = ctx.targetsAround(ctx.chest(), 12 * ctx.power());
		if (victims.isEmpty()) {
			player.sendMessage(Text.translatable("message.avatarbending.no_target").formatted(Formatting.GRAY), true);
			ctx.fail();
			return;
		}
		boolean fullMoon = world.getMoonPhase() == 0 && world.isNight();
		float strength = fullMoon ? 1.5f : 1f;
		if (fullMoon) {
			player.sendMessage(Text.translatable("message.avatarbending.full_moon").formatted(Formatting.DARK_RED), true);
		}
		int duration = 60;
		for (LivingEntity victim : victims) {
			Fx.attach(victim, AttachedFxType.BLOODBENT, duration, player.getId());
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 10, false, false, true));
		}
		Fx.ring(world, ctx.feet().add(0, 0.1, 0), UP, Colors.BLOOD, 12f, 20);
		Fx.burst(world, ctx.hands(), Colors.BLOOD, 1.0f);
		ctx.sfx(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.2f, 0.5f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age % 15 == 0) {
				Sfx.play(world, player.getPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2f, 1.0f);
			}
			if (age < duration) {
				for (LivingEntity victim : victims) {
					if (!victim.isAlive()) {
						continue;
					}
					double lift = age < 15 ? 0.12 : (age % 20 < 10 ? 0.02 : -0.02);
					victim.setVelocity(0, lift, 0);
					victim.velocityModified = true;
					victim.fallDistance = 0;
				}
				return false;
			}
			for (LivingEntity victim : victims) {
				if (!victim.isAlive()) {
					continue;
				}
				ctx.damage(victim, Element.WATER, 10 * strength);
				victim.setVelocity(0, -1.6, 0);
				victim.velocityModified = true;
				Fx.groundShockwave(world, victim.getPos(), Colors.BLOOD, 3f);
			}
			Sfx.play(world, player.getPos(), ModSounds.QUAKE_BOOM, 2.5f, 1.1f);
			return true;
		});
	}

	/** A giant whirlpool that drags everything in, then erupts in a geyser. */
	public static void maelstrom(AbilityContext ctx) {
		HitResult hit = ctx.raycast(30);
		Vec3d point = hit.getPos();
		BlockPos ground = ctx.groundAt(point.x, point.y + 0.5, point.z, 2, 12);
		Vec3d pos = ground != null ? Vec3d.ofBottomCenter(ground) : point;
		MaelstromEntity maelstrom = new MaelstromEntity(ModEntities.MAELSTROM, ctx.world());
		maelstrom.setup(ctx.player(), pos, ctx.flatLook(), ctx.power());
		ctx.world().spawnEntity(maelstrom);
		Fx.splash(ctx.world(), pos.add(0, 0.5, 0), 2f);
		Fx.groundShockwave(ctx.world(), pos, Colors.WATER, (float) MaelstromEntity.RADIUS);
		Sfx.play(ctx.world(), pos, ModSounds.WATER_SURGE, 3f, 0.6f);
		ctx.sfx(ModSounds.CHARGE_MAGIC, 1.2f, 0.7f);
	}
}
