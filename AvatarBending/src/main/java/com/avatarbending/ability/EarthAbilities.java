package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.entity.BoulderEntity;
import com.avatarbending.entity.ModEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class EarthAbilities {
	private static final BlockState SPIKE_TIP = Blocks.POINTED_DRIPSTONE.getDefaultState()
		.with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
		.with(PointedDripstoneBlock.THICKNESS, Thickness.TIP);

	private EarthAbilities() {
	}

	/** Rips a boulder from the ground and hurls it. */
	public static void boulderToss(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BlockState ground = ctx.groundBlock();
		if (ground.getRenderType() != BlockRenderType.MODEL || ground.hasBlockEntity()) {
			ground = AbilityContext.earthMaterial(ground);
		}
		Vec3d flat = ctx.flatLook();
		Vec3d spot = player.getPos().add(flat.multiply(1.8));
		BlockPos groundPos = ctx.groundAt(spot.x, spot.y, spot.z, 1, 3);
		double y = groundPos != null ? groundPos.getY() - 0.8 : player.getY();
		BoulderEntity boulder = new BoulderEntity(ModEntities.BOULDER, ctx.world());
		boulder.setBlockState(ground);
		boulder.setup(player, new Vec3d(spot.x, y, spot.z), new Vec3d(0, 0.28, 0), 9f, ctx.power());
		boulder.setRaise(7, 1.7f);
		ctx.world().spawnEntity(boulder);
		ctx.particles(AbilityContext.blockDust(ground), new Vec3d(spot.x, y + 1, spot.z), 30, 0.5, 0.15);
		ctx.particles(new BlockStateParticleEffect(ParticleTypes.DUST_PILLAR, ground), new Vec3d(spot.x, y + 1, spot.z), 10, 0.4, 0.1);
		ctx.sound(ground.getSoundGroup().getBreakSound(), 1.5f, 0.6f);
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND, 0.8f, 0.8f);
	}

	/** A line of rock spikes that erupts from the ground and launches mobs. */
	public static void earthSpikes(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = ctx.flatLook();
		Vec3d origin = player.getPos();
		BlockState material = AbilityContext.earthMaterial(ctx.groundBlock());
		int length = (int) (12 * ctx.power());
		Set<UUID> hit = new HashSet<>();
		double[] lastY = {origin.y};
		int[] misses = {0};
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND, 1f, 0.7f);
		EffectScheduler.schedule(age -> {
			int step = age + 2;
			if (step > length) {
				return true;
			}
			Vec3d p = origin.add(dir.multiply(step));
			BlockPos ground = ctx.groundAt(p.x, lastY[0], p.z, 2, 4);
			if (ground == null) {
				return ++misses[0] > 3;
			}
			misses[0] = 0;
			lastY[0] = ground.getY();
			BlockState groundState = world.getBlockState(ground.down());
			Vec3d center = Vec3d.ofBottomCenter(ground);
			for (LivingEntity target : ctx.targetsAround(center.add(0, 0.5, 0), 1.8)) {
				if (hit.add(target.getUuid())) {
					ctx.damage(target, Element.EARTH, 7);
					target.setVelocity(target.getVelocity().x * 0.3, 1.0, target.getVelocity().z * 0.3);
					target.velocityModified = true;
				}
			}
			int duration = 80 + step * 3;
			int height = step % 2 == 0 ? 2 : 1;
			boolean placed = true;
			for (int h = 0; h < height && placed; h++) {
				placed = ctx.placeTemp(ground.up(h), material, duration);
			}
			if (placed) {
				ctx.placeTemp(ground.up(height), SPIKE_TIP, duration - 1);
			}
			world.spawnParticles(AbilityContext.blockDust(groundState), center.x, center.y + 0.5, center.z, 15, 0.4, 0.5, 0.4, 0.15);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.DUST_PILLAR, groundState), center.x, center.y, center.z, 6, 0.3, 0.1, 0.3, 0.1);
			ctx.soundAt(center, groundState.getSoundGroup().getBreakSound(), 1f, 0.6f);
			if (step % 3 == 0) {
				ctx.soundAt(center, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND, 0.8f, 0.8f);
			}
			return false;
		});
	}

	/** Raises a wall of earth in front of the bender. */
	public static void earthWall(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = ctx.flatLook();
		Vec3d side = new Vec3d(-dir.z, 0, dir.x);
		BlockState material = AbilityContext.earthMaterial(ctx.groundBlock());
		int halfWidth = ctx.avatarState() ? 3 : 2;
		int height = ctx.avatarState() ? 4 : 3;
		int duration = 200;
		Vec3d front = player.getPos().add(dir.multiply(3));
		BlockPos[] columns = new BlockPos[halfWidth * 2 + 1];
		boolean any = false;
		for (int i = -halfWidth; i <= halfWidth; i++) {
			Vec3d p = front.add(side.multiply(i));
			columns[i + halfWidth] = ctx.groundAt(p.x, player.getY(), p.z, 2, 4);
			any |= columns[i + halfWidth] != null;
		}
		if (!any) {
			ctx.fail();
			return;
		}
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.6f);
		EffectScheduler.schedule(age -> {
			if (age % 2 != 0) {
				return false;
			}
			int row = age / 2;
			if (row >= height) {
				return true;
			}
			for (BlockPos column : columns) {
				if (column == null) {
					continue;
				}
				BlockPos pos = column.up(row);
				if (ctx.placeTemp(pos, material, duration - row * 2)) {
					world.spawnParticles(AbilityContext.blockDust(material), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
				}
			}
			BlockPos mid = columns[halfWidth] != null ? columns[halfWidth].up(row) : player.getBlockPos();
			ctx.soundAt(Vec3d.ofCenter(mid), material.getSoundGroup().getPlaceSound(), 1.5f, 0.6f);
			return false;
		});
	}

	/** A pillar of rock shoots up beneath the bender, launching them into the sky. */
	public static void earthPillar(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		BlockPos ground = ctx.groundAt(player.getX(), player.getY(), player.getZ(), 0, 3);
		if (ground == null) {
			ctx.fail();
			player.sendMessage(Text.translatable("message.avatarbending.need_ground"), true);
			return;
		}
		BlockState material = AbilityContext.earthMaterial(world.getBlockState(ground.down()));
		int height = (int) (5 * ctx.power());
		player.setVelocity(player.getVelocity().x, 1.2 + 0.1 * (height - 5), player.getVelocity().z);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(80);
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
		ctx.particles(AbilityContext.blockDust(material), Vec3d.ofBottomCenter(ground), 30, 0.6, 0.2);
		EffectScheduler.schedule(1, age -> {
			if (age >= height) {
				return true;
			}
			BlockPos pos = ground.up(age);
			// Never trap the player inside their own pillar.
			if (!player.getBoundingBox().intersects(new Box(pos))) {
				TempBlocks.place(world, pos, material, 160 - age * 2);
				world.spawnParticles(AbilityContext.blockDust(material), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.5, 0.3, 0.5, 0.1);
				ctx.soundAt(Vec3d.ofCenter(pos), material.getSoundGroup().getPlaceSound(), 1f, 0.7f);
			}
			return false;
		});
	}

	/** Slam the ground: a shockwave ring launches everything around you. Slams down first if you are in the air. */
	public static void seismicSlam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BlockPos below = ctx.groundAt(player.getX(), player.getY(), player.getZ(), 0, 2);
		if (player.isOnGround() || below != null) {
			quake(ctx, 1f);
			return;
		}
		// In the air: dive down and quake on landing. Higher dives make bigger quakes.
		double startY = player.getY();
		player.setVelocity(player.getVelocity().x * 0.2, -2.2, player.getVelocity().z * 0.2);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(100);
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_AIR, 1.2f, 0.7f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age > 80) {
				return true;
			}
			ctx.world().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 3, 0.2, 0.5, 0.2, 0.01);
			if (player.isOnGround() || player.isTouchingWater()) {
				float bonus = (float) Math.min(1.0, Math.max(0, startY - player.getY()) / 20.0);
				quake(ctx, 1f + bonus);
				return true;
			}
			player.setVelocity(player.getVelocity().x, Math.min(player.getVelocity().y, -1.6), player.getVelocity().z);
			player.velocityModified = true;
			return false;
		});
	}

	private static void quake(AbilityContext ctx, float scale) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d origin = player.getPos();
		double maxRadius = 9 * ctx.power() * scale;
		Set<UUID> hit = new HashSet<>();
		BlockState groundState = ctx.groundBlock();
		ctx.sound(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
		ctx.sound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.2f, 0.6f);
		ctx.particles(new BlockStateParticleEffect(ParticleTypes.DUST_PILLAR, groundState), origin, 40, 1.2, 0.3);
		EffectScheduler.schedule(age -> {
			double radius = 1 + age * 1.1;
			if (radius > maxRadius) {
				return true;
			}
			int points = (int) (radius * 7);
			for (int i = 0; i < points; i++) {
				double angle = (Math.PI * 2 * i) / points;
				double x = origin.x + Math.cos(angle) * radius;
				double z = origin.z + Math.sin(angle) * radius;
				BlockPos ground = ctx.groundAt(x, origin.y, z, 2, 3);
				if (ground == null) {
					continue;
				}
				BlockState state = world.getBlockState(ground.down());
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.DUST_PILLAR, state), x, ground.getY() + 0.1, z, 1, 0.1, 0, 0.1, 0.1);
				if (i % 3 == 0) {
					world.spawnParticles(AbilityContext.blockDust(state), x, ground.getY() + 0.3, z, 2, 0.2, 0.2, 0.2, 0.2);
				}
			}
			for (LivingEntity target : ctx.targetsAround(origin, radius + 0.5)) {
				double dx = target.getX() - origin.x;
				double dz = target.getZ() - origin.z;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist < radius - 1.6 || Math.abs(target.getY() - origin.y) > 3 || !hit.add(target.getUuid())) {
					continue;
				}
				ctx.damage(target, Element.EARTH, 7 * scale);
				Vec3d out = dist < 0.01 ? Vec3d.ZERO : new Vec3d(dx / dist, 0, dz / dist);
				target.setVelocity(out.x * 0.6, 1.0 + 0.2 * scale, out.z * 0.6);
				target.velocityModified = true;
			}
			if (age % 2 == 0) {
				ctx.soundAt(origin, SoundEvents.BLOCK_STONE_BREAK, 1.2f, 0.5f);
			}
			return false;
		});
	}

	/** Coats the bender in rock armor. */
	public static void earthArmor(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int duration = (int) (600 * ctx.power());
		BlockState material = ctx.groundBlock();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 1, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, 1, false, false, true));
		ctx.sound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.5f, 0.7f);
		ctx.sound(material.getSoundGroup().getPlaceSound(), 1.5f, 0.6f);
		// Rocks fly in from the ground and wrap around the bender.
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age < 12) {
				double radius = 3.0 - age * 0.22;
				for (int i = 0; i < 10; i++) {
					double angle = i * (Math.PI / 5) + age * 0.3;
					double y = player.getY() + (i % 4) * 0.5;
					world.spawnParticles(AbilityContext.blockDust(material), player.getX() + Math.cos(angle) * radius, y,
						player.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
				}
				return false;
			}
			if (age == 12) {
				world.spawnParticles(AbilityContext.blockDust(material), player.getX(), player.getY() + 1, player.getZ(), 50, 0.35, 0.7, 0.35, 0.05);
				ctx.soundAt(player.getPos(), SoundEvents.BLOCK_STONE_PLACE, 1.5f, 0.5f);
			}
			if (age % 10 == 0 && player.hasStatusEffect(StatusEffects.RESISTANCE)) {
				world.spawnParticles(AbilityContext.blockDust(material), player.getX(), player.getY() + 1, player.getZ(), 3, 0.3, 0.6, 0.3, 0);
			}
			return age >= duration || !player.hasStatusEffect(StatusEffects.RESISTANCE);
		});
	}
}
