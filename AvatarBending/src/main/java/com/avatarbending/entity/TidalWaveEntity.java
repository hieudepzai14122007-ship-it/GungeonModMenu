package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A wide wall of water that surges forward, sweeping mobs away and putting out fires.
 */
public class TidalWaveEntity extends GroundSpellEntity {
	public static final double HALF_WIDTH = 3.5;
	public static final double HEIGHT = 3.2;

	private final Set<UUID> hit = new HashSet<>();

	public TidalWaveEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 36;
		this.speed = 0.6;
	}

	private Vec3d perpendicular() {
		Vec3d dir = direction();
		return new Vec3d(-dir.z, 0, dir.x);
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		double halfWidth = HALF_WIDTH * scale();
		double height = HEIGHT * scale();
		Vec3d dir = direction();
		Vec3d perp = perpendicular();
		Box box = getBoundingBox().expand(halfWidth + 1, height, halfWidth + 1);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d rel = target.getPos().subtract(getPos());
			double along = rel.dotProduct(dir);
			double side = rel.dotProduct(perp);
			if (Math.abs(along) > 1.6 || Math.abs(side) > halfWidth || rel.y < -1.5 || rel.y > height) {
				continue;
			}
			if (hit.add(target.getUuid())) {
				context.damage(target, Element.WATER, 6f * power);
				target.extinguish();
			}
			push(target, dir.multiply(speed + 0.35).add(0, 0.3, 0));
		}
		// Douse fires along the wave's front.
		for (double s = -halfWidth; s <= halfWidth; s += 1) {
			Vec3d p = getPos().add(perp.multiply(s));
			for (int dy = -1; dy <= 2; dy++) {
				BlockPos pos = BlockPos.ofFloored(p.x, p.y + dy, p.z);
				if (world.getBlockState(pos).getBlock() instanceof AbstractFireBlock) {
					world.removeBlock(pos, false);
					world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
				}
			}
		}
		if (age % 6 == 0) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 2f, 0.6f);
		}
		if (age == 1) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 2.5f, 0.5f);
		}
	}

	@Override
	protected void clientParticles() {
		World world = getWorld();
		float scale = scale();
		double halfWidth = HALF_WIDTH * scale;
		double height = HEIGHT * scale;
		// Wave grows in, then collapses at the end of its life.
		double grow = Math.min(1.0, age / 6.0) * Math.min(1.0, (maxLife - age) / 6.0 + 0.3);
		double h = height * grow;
		Vec3d dir = direction();
		Vec3d perp = perpendicular();
		for (double s = -halfWidth; s <= halfWidth; s += 0.45) {
			double edge = 1 - Math.pow(Math.abs(s) / halfWidth, 4);
			double columnHeight = h * edge;
			for (double y = 0; y < columnHeight; y += 0.5) {
				double curl = (y / Math.max(0.1, columnHeight)) * 0.8;
				Vec3d p = getPos().add(perp.multiply(s)).add(dir.multiply(curl));
				world.addParticle(ParticleTypes.SPLASH, p.x, getY() + y, p.z, dir.x * 0.2, 0.05, dir.z * 0.2);
				if (random.nextInt(3) == 0) {
					world.addParticle(ParticleTypes.BUBBLE_POP, p.x, getY() + y, p.z, 0, 0, 0);
				}
			}
			// Foamy crest.
			Vec3d crest = getPos().add(perp.multiply(s)).add(dir.multiply(0.9));
			world.addParticle(ParticleTypes.CLOUD, crest.x, getY() + columnHeight, crest.z, dir.x * 0.1, 0.02, dir.z * 0.1);
			if (random.nextInt(2) == 0) {
				world.addParticle(ParticleTypes.FALLING_WATER, crest.x, getY() + columnHeight, crest.z, 0, 0, 0);
			}
		}
	}
}
