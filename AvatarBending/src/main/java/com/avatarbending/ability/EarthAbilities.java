package com.avatarbending.ability;

import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.entity.BoulderEntity;
import com.avatarbending.entity.LavaBombEntity;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.entity.RockEntity;
import com.avatarbending.fx.AttachedFxType;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EarthAbilities {
	private static final BlockState SPIKE_TIP = Blocks.POINTED_DRIPSTONE.getDefaultState()
		.with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
		.with(PointedDripstoneBlock.THICKNESS, Thickness.TIP);

	private EarthAbilities() {
	}

	/** Rock torn from the ground under the player: grass and dirt become clumps of earth. */
	private static BlockState rockMaterial(AbilityContext ctx) {
		BlockState ground = ctx.groundBlock();
		if (ground.isIn(BlockTags.DIRT) || ground.isOf(Blocks.FARMLAND) || ground.isOf(Blocks.DIRT_PATH)) {
			return Blocks.COARSE_DIRT.getDefaultState();
		}
		if (ground.getRenderType() != BlockRenderType.MODEL || ground.hasBlockEntity()) {
			ground = AbilityContext.earthMaterial(ground);
		}
		return ground;
	}

	/** Rips a boulder from the ground and hurls it. */
	public static void boulderToss(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BlockState ground = rockMaterial(ctx);
		Vec3d flat = ctx.flatLook();
		Vec3d spot = player.getPos().add(flat.multiply(1.8));
		BlockPos groundPos = ctx.groundAt(spot.x, spot.y, spot.z, 1, 3);
		double y = groundPos != null ? groundPos.getY() - 0.8 : player.getY();
		BoulderEntity boulder = new BoulderEntity(ModEntities.BOULDER, ctx.world());
		boulder.setBlockState(ground);
		boulder.setup(player, new Vec3d(spot.x, y, spot.z), new Vec3d(0, 0.28, 0), 9f, ctx.power());
		boulder.setRaise(7, 1.7f);
		ctx.world().spawnEntity(boulder);
		Vec3d surface = new Vec3d(spot.x, y + 0.9, spot.z);
		Fx.debris(ctx.world(), surface, ground, 1.0f);
		Fx.groundShockwave(ctx.world(), surface, Colors.EARTH, 2.5f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.3f, 1.3f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND, 0.9f, 0.8f);
	}

	/** Six rocks rise and orbit the earthbender, then fire one after another. */
	public static void rockBarrage(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		BlockState material = rockMaterial(ctx);
		int count = 6;
		for (int i = 0; i < count; i++) {
			RockEntity rock = new RockEntity(ModEntities.ROCK, world);
			rock.setBlockState(material);
			double ang = i * Math.PI * 2 / count;
			Vec3d start = player.getPos().add(Math.cos(ang) * 1.8, 0.1, Math.sin(ang) * 1.8);
			rock.setup(player, start, Vec3d.ZERO, 4.5f, ctx.power());
			rock.setOrbit(i, count, 16 + i * 3);
			world.spawnEntity(rock);
		}
		Fx.debris(world, ctx.feet().add(0, 0.3, 0), material, 1.2f);
		Fx.groundShockwave(world, ctx.feet(), Colors.EARTH, 3f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.4f, 1.2f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND, 1f, 1.1f);
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
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.5f, 1.0f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND, 1f, 0.7f);
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
			Fx.debris(world, center.add(0, 0.5, 0), groundState, 0.6f);
			if (step % 2 == 0) {
				Sfx.play(world, center, ModSounds.ROCK_IMPACT, 1.1f, 0.9f + world.random.nextFloat() * 0.3f);
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
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.6f, 0.9f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.6f);
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
				ctx.placeTemp(column.up(row), material, duration - row * 2);
			}
			BlockPos mid = columns[halfWidth] != null ? columns[halfWidth].up(row) : player.getBlockPos();
			Fx.debris(world, Vec3d.ofCenter(mid), material, 0.9f);
			Sfx.play(world, Vec3d.ofCenter(mid), material.getSoundGroup().getPlaceSound(), 1.5f, 0.6f);
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
		Fx.groundShockwave(world, Vec3d.ofBottomCenter(ground), Colors.EARTH, 3.5f);
		Fx.debris(world, Vec3d.ofBottomCenter(ground), material, 1.2f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.3f, 1.4f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
		EffectScheduler.schedule(1, age -> {
			if (age >= height) {
				return true;
			}
			BlockPos pos = ground.up(age);
			if (!player.getBoundingBox().intersects(new Box(pos))) {
				TempBlocks.place(world, pos, material, 160 - age * 2);
			}
			return false;
		});
	}

	/**
	 * Metalbending: shoots a steel cable. Hook a block to pull yourself to it, or hook a creature to
	 * yank it towards you.
	 */
	public static void metalCables(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HitResult hit = ctx.raycast(32);
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			Fx.attach(player, AttachedFxType.METAL_CABLE, 12, 0, target.getPos().add(0, target.getHeight() / 2, 0));
			ctx.damage(target, Element.EARTH, 3);
			ctx.sfx(ModSounds.METAL_ZING, 1.3f, 1.0f);
			ctx.sfx(SoundEvents.BLOCK_CHAIN_PLACE, 1.2f, 0.8f);
			EffectScheduler.schedule(age -> {
				if (!target.isAlive() || age >= 10) {
					return true;
				}
				Vec3d pull = player.getPos().subtract(target.getPos());
				if (pull.length() < 2) {
					return true;
				}
				target.setVelocity(pull.normalize().multiply(1.0).add(0, 0.15, 0));
				target.velocityModified = true;
				return false;
			});
			return;
		}
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
			player.sendMessage(Text.translatable("message.avatarbending.no_anchor").formatted(Formatting.GRAY), true);
			ctx.fail();
			return;
		}
		Vec3d anchor = blockHit.getPos();
		Fx.attach(player, AttachedFxType.METAL_CABLE, 30, 0, anchor);
		Fx.burst(world, anchor, Colors.METAL, 0.6f);
		BendingManager.get(player).grantFallImmunity(100);
		ctx.sfx(ModSounds.METAL_ZING, 1.4f, 1.0f);
		ctx.sfx(SoundEvents.BLOCK_CHAIN_PLACE, 1.2f, 0.8f);
		Sfx.play(world, anchor, SoundEvents.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age >= 30) {
				return true;
			}
			Vec3d to = anchor.subtract(player.getPos().add(0, 1, 0));
			if (to.length() < 1.8) {
				Fx.detach(player, AttachedFxType.METAL_CABLE);
				return true;
			}
			player.setVelocity(to.normalize().multiply(1.15).add(0, 0.12, 0));
			player.velocityModified = true;
			player.fallDistance = 0;
			return false;
		});
	}

	/** Coats the bender in rock armor. */
	public static void earthArmor(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int duration = (int) (600 * ctx.power());
		BlockState material = ctx.groundBlock();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 1, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, 1, false, false, true));
		Fx.attach(player, AttachedFxType.EARTH_ARMOR, duration);
		Fx.debris(ctx.world(), ctx.chest(), material, 1.4f);
		Fx.groundShockwave(ctx.world(), ctx.feet(), Colors.EARTH, 3f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.4f, 1.1f);
		ctx.sfx(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.5f, 0.7f);
		ctx.sfx(material.getSoundGroup().getPlaceSound(), 1.5f, 0.6f);
	}

	/** Slam the ground: a shockwave ring launches everything around you. Slams down first if you are in the air. */
	public static void seismicSlam(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		BlockPos below = ctx.groundAt(player.getX(), player.getY(), player.getZ(), 0, 2);
		if (player.isOnGround() || below != null) {
			quake(ctx, 1f);
			return;
		}
		double startY = player.getY();
		player.setVelocity(player.getVelocity().x * 0.2, -2.2, player.getVelocity().z * 0.2);
		player.velocityModified = true;
		BendingManager.get(player).grantFallImmunity(100);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_AIR, 1.2f, 0.7f);
		ctx.sfx(ModSounds.AIR_WHOOSH, 1.2f, 0.6f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved() || age > 80) {
				return true;
			}
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
		Fx.groundShockwave(world, origin, Colors.EARTH, (float) maxRadius);
		Fx.debris(world, origin.add(0, 0.3, 0), ctx.groundBlock(), 1.6f * scale);
		Fx.shake(world, origin, 3f * scale, 40);
		ctx.sfx(ModSounds.QUAKE_BOOM, 3f, 1.0f);
		ctx.sfx(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.5f);
		EffectScheduler.schedule(age -> {
			double radius = 1 + age * 1.1;
			if (radius > maxRadius) {
				return true;
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
				Fx.debris(world, target.getPos(), world.getBlockState(target.getBlockPos().down()), 0.6f);
			}
			return false;
		});
	}

	/** Lavabending: hurls a stream of molten rock that splashes burning lava. */
	public static void lavabending(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Fx.flames(world, ctx.hands(), Colors.LAVA, 0.8f);
		ctx.sfx(SoundEvents.ITEM_BUCKET_FILL_LAVA, 1.2f, 0.8f);
		ctx.sfx(ModSounds.ROCK_RUMBLE, 1.2f, 1.4f);
		EffectScheduler.schedule(age -> {
			if (!player.isAlive() || player.isRemoved()) {
				return true;
			}
			if (age % 3 == 0) {
				Vec3d look = ctx.look();
				Vec3d dir = AbilityContext.rotateY(look, (world.random.nextDouble() - 0.5) * 10).add(0, 0.12, 0).normalize();
				LavaBombEntity bomb = new LavaBombEntity(ModEntities.LAVA_BOMB, world);
				bomb.setup(player, ctx.hands(), dir.multiply(1.45), 10f, ctx.power());
				world.spawnEntity(bomb);
				Fx.flames(world, ctx.hands(), Colors.LAVA, 0.35f);
				Sfx.play(world, player.getPos(), ModSounds.FIRE_WHOOSH, 1.0f, 0.7f);
			}
			return age >= 12;
		});
	}

	/**
	 * The ground tears open in a long crack that swallows enemies, then slams shut with crushing
	 * force. The ground always closes back exactly as it was.
	 */
	public static void fissure(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d dir = ctx.flatLook();
		Vec3d side = new Vec3d(-dir.z, 0, dir.x);
		Vec3d origin = player.getPos();
		int length = (int) (18 * ctx.power());
		int openTicks = length + 2;
		int closeAt = openTicks + 40;
		List<BlockPos> removed = new ArrayList<>();
		double[] lastY = {origin.y};
		ctx.sfx(ModSounds.ROCK_RUMBLE, 3f, 0.6f);
		ctx.sfx(ModSounds.QUAKE_BOOM, 2f, 1.3f);
		Fx.shake(world, origin, 2f, 40);
		EffectScheduler.schedule(age -> {
			if (age < openTicks) {
				int step = age + 2;
				if (step <= length) {
					Vec3d p = origin.add(dir.multiply(step));
					BlockPos ground = ctx.groundAt(p.x, lastY[0], p.z, 2, 4);
					if (ground != null) {
						lastY[0] = ground.getY();
						for (int w = -1; w <= 1; w++) {
							BlockPos column = BlockPos.ofFloored(Vec3d.ofBottomCenter(ground).add(side.multiply(w)));
							for (int d = 1; d <= 3; d++) {
								BlockPos pos = column.down(d);
								if (TempBlocks.remove(world, pos, closeAt - age + 60)) {
									removed.add(pos);
								}
							}
						}
						Fx.debris(world, Vec3d.ofBottomCenter(ground), world.getBlockState(ground.down(4)), 0.8f);
						if (step % 3 == 0) {
							Sfx.play(world, Vec3d.ofBottomCenter(ground), ModSounds.ROCK_IMPACT, 1.2f, 0.7f);
						}
					}
				}
				return false;
			}
			if (age == closeAt) {
				Box area = new Box(origin, origin.add(dir.multiply(length + 1))).expand(2, 0, 2).stretch(0, -5, 0).stretch(0, 4, 0);
				for (LivingEntity target : ctx.targetsIn(area)) {
					Vec3d rel = target.getPos().subtract(origin);
					double along = rel.dotProduct(dir);
					double across = Math.abs(rel.dotProduct(side));
					if (along < 1 || along > length + 1 || across > 2) {
						continue;
					}
					ctx.damage(target, Element.EARTH, 16);
					target.setVelocity(0, 1.3, 0);
					target.velocityModified = true;
					target.fallDistance = 0;
				}
				Vec3d mid = origin.add(dir.multiply(length / 2.0));
				Fx.shake(world, mid, 4f, 50);
				for (int i = 2; i <= length; i += 3) {
					Fx.debris(world, origin.add(dir.multiply(i)).add(0, 0.3, 0), world.getBlockState(BlockPos.ofFloored(origin.add(dir.multiply(i))).down()), 1.0f);
				}
				Fx.groundShockwave(world, mid, Colors.EARTH, length / 2f);
				Sfx.play(world, mid, ModSounds.QUAKE_BOOM, 4f, 0.7f);
				Sfx.play(world, mid, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, 0.6f);
				return false;
			}
			if (age == closeAt + 3) {
				for (int i = removed.size() - 1; i >= 0; i--) {
					TempBlocks.restore(world, removed.get(i));
				}
				return true;
			}
			return age > closeAt + 3;
		});
	}
}
