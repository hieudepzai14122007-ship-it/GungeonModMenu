package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.TempBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Avatar's meteor: a huge burning chunk of magma pulled down from the sky. Explodes without
 * breaking any blocks and leaves a short-lived ring of fire-like magma.
 */
public class MeteorEntity extends BlockProjectileEntity {
	public MeteorEntity(EntityType<? extends MeteorEntity> type, World world) {
		super(type, world);
		this.maxLife = 100;
	}

	@Override
	protected BlockState defaultBlock() {
		return Blocks.MAGMA_BLOCK.getDefaultState();
	}

	@Override
	protected Element element() {
		return Element.AVATAR;
	}

	@Override
	protected double gravity() {
		return 0.02;
	}

	@Override
	public boolean fullBright() {
		return true;
	}

	@Override
	public float spinSpeed() {
		return 6f;
	}

	@Override
	protected boolean canHit(Entity entity) {
		// The meteor ploughs through mobs and explodes on the ground.
		return false;
	}

	@Override
	protected void serverTick(ServerWorld world) {
		if (age % 4 == 0) {
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2f, 0.4f);
		}
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 1, 0.5, 1, 0);
		world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 200, 2.5, 1, 2.5, 0.25);
		world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5, pos.z, 60, 2, 0.5, 2, 0);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1, pos.z, 80, 2.5, 1.5, 2.5, 0.05);
		world.spawnParticles(AbilityContext.blockDust(Blocks.MAGMA_BLOCK.getDefaultState()), pos.x, pos.y + 0.5, pos.z, 120, 2, 0.5, 2, 0.4);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 4f, 0.5f);
		world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 3f, 0.6f);

		double radius = 7 * power;
		if (getOwner() instanceof ServerPlayerEntity player) {
			AbilityContext context = new AbilityContext(player, world, false);
			for (LivingEntity target : context.targetsAround(pos, radius)) {
				double falloff = 1 - Math.min(0.6, target.getPos().distanceTo(pos) / radius * 0.6);
				target.timeUntilRegen = 0;
				target.damage(AbilityContext.damageSource(world, Element.AVATAR, this, player), (float) (damage * power * falloff));
				target.setOnFireForTicks(120);
				Vec3d away = target.getPos().subtract(pos);
				away = away.lengthSquared() < 0.01 ? new Vec3d(0, 1, 0) : away.normalize();
				target.addVelocity(away.x * 1.6, 0.9, away.z * 1.6);
				target.velocityModified = true;
			}
		}
		// Glowing magma debris that disappears after a few seconds (never real fire, so nothing burns down).
		BlockPos center = BlockPos.ofFloored(pos);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 3.4 || random.nextInt(3) != 0) {
					continue;
				}
				for (int dy = 2; dy >= -3; dy--) {
					BlockPos p = center.add(dx, dy, dz);
					if (world.getBlockState(p).isSolidBlock(world, p) && world.getBlockState(p.up()).isAir()) {
						TempBlocks.place(world, p.up(), Blocks.MAGMA_BLOCK.getDefaultState(), 80 + random.nextInt(60));
						break;
					}
				}
			}
		}
	}

	@Override
	protected void expire(ServerWorld world) {
		impact(world, getPos());
	}

	@Override
	protected void clientTrail() {
		World world = getWorld();
		double y = getY() + getHeight() / 2;
		double r = getWidth() / 2;
		for (int i = 0; i < 10; i++) {
			world.addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * r * 2, y + (random.nextDouble() - 0.5) * r * 2,
				getZ() + (random.nextDouble() - 0.5) * r * 2, 0, 0.05, 0);
		}
		for (int i = 0; i < 4; i++) {
			world.addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5) * r, y + 0.5, getZ() + (random.nextDouble() - 0.5) * r, 0, 0.05, 0);
		}
		world.addParticle(ParticleTypes.LAVA, getX(), y, getZ(), 0, 0, 0);
	}
}
