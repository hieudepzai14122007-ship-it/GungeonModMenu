package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A roaring tornado that wanders forward, sucking mobs in, spinning them up into the sky and
 * flinging them out when it dies down.
 */
public class TornadoEntity extends GroundSpellEntity {
	public static final double HEIGHT = 9;
	public static final double RADIUS = 3.5;

	public TornadoEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 160;
		this.speed = 0.2;
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		double radius = RADIUS * scale();
		double height = HEIGHT * scale();
		Box box = new Box(getX() - radius, getY() - 1, getZ() - radius, getX() + radius, getY() + height, getZ() + radius);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d toCenter = new Vec3d(getX() - target.getX(), 0, getZ() - target.getZ());
			double dist = toCenter.length();
			if (dist > radius) {
				continue;
			}
			Vec3d inward = dist < 0.01 ? Vec3d.ZERO : toCenter.multiply(1 / dist);
			Vec3d tangent = new Vec3d(-inward.z, 0, inward.x);
			double heightIn = target.getY() - getY();
			double lift = heightIn < height - 1 ? 0.22 : -0.05;
			Vec3d velocity = inward.multiply(0.12 + dist * 0.03).add(tangent.multiply(0.45)).add(direction().multiply(speed)).add(0, lift, 0);
			push(target, velocity);
			if (age % 15 == 0) {
				context.damage(target, Element.AIR, 2f * power);
			}
		}
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
			Vec3d toCenter = new Vec3d(getX() - item.getX(), 0, getZ() - item.getZ());
			item.setVelocity(toCenter.multiply(0.1).add(new Vec3d(-toCenter.z, 0, toCenter.x).normalize().multiply(0.3)).add(0, 0.15, 0));
			item.velocityModified = true;
		}
		if (age % 20 == 0) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_WHIRL, SoundCategory.PLAYERS, 2.5f, 0.5f);
		}
		if (age % 12 == 0) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.PLAYERS, 2f, 0.6f);
		}
		// Debris ripped from the ground.
		BlockPos ground = getBlockPos().down();
		BlockState groundState = world.getBlockState(ground);
		if (!groundState.isAir() && age % 2 == 0) {
			world.spawnParticles(AbilityContext.blockDust(groundState), getX(), getY() + 0.3, getZ(), 6, radius * 0.4, 0.2, radius * 0.4, 0.15);
		}
	}

	@Override
	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
		world.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 2f, 0.6f);
		if (context == null) {
			return;
		}
		double radius = RADIUS * scale() + 1;
		Box box = new Box(getX() - radius, getY() - 1, getZ() - radius, getX() + radius, getY() + HEIGHT * scale() + 2, getZ() + radius);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d away = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
			away = away.lengthSquared() < 0.01 ? direction() : away.normalize();
			push(target, away.multiply(1.4).add(0, 0.6, 0));
		}
	}

	@Override
	protected void clientParticles() {
		World world = getWorld();
		float scale = scale();
		double height = HEIGHT * scale;
		float t = age * 0.45f;
		for (double h = 0; h < height; h += 0.45) {
			double radius = (0.35 + h * 0.33) * scale;
			for (int j = 0; j < 2; j++) {
				double angle = t + h * 0.8 + j * Math.PI;
				double x = getX() + Math.cos(angle) * radius;
				double z = getZ() + Math.sin(angle) * radius;
				double vx = -Math.sin(angle) * 0.15;
				double vz = Math.cos(angle) * 0.15;
				world.addParticle(ParticleTypes.CLOUD, x, getY() + h, z, vx, 0.04, vz);
			}
			if (random.nextInt(4) == 0) {
				double angle = random.nextDouble() * Math.PI * 2;
				world.addParticle(ParticleTypes.WHITE_ASH, getX() + Math.cos(angle) * radius, getY() + h, getZ() + Math.sin(angle) * radius, 0, 0.1, 0);
			}
		}
		if (age % 4 == 0) {
			world.addParticle(ParticleTypes.GUST, getX(), getY() + 0.5, getZ(), 0, 0, 0);
		}
		world.addParticle(ParticleTypes.SMALL_GUST, getX() + (random.nextDouble() - 0.5) * 2, getY() + random.nextDouble() * height,
			getZ() + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
		// Swirling dust ring at the base.
		for (int i = 0; i < 3; i++) {
			double angle = t * 1.6 + i * (Math.PI * 2 / 3);
			double r = 1.6 * scale;
			world.addParticle(ParticleTypes.POOF, getX() + Math.cos(angle) * r, getY() + 0.1, getZ() + Math.sin(angle) * r,
				-Math.sin(angle) * 0.1, 0.02, Math.cos(angle) * 0.1);
		}
	}
}
