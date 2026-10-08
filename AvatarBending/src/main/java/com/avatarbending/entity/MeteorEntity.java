package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Avatar's meteor: a huge burning chunk of magma pulled down from the sky. Explodes without
 * breaking any blocks and scatters glowing magma debris that cools down after a few seconds.
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
		if (age % 6 == 0) {
			Sfx.play(world, getPos(), ModSounds.FIRE_ROAR, 3f, 0.55f);
		}
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.explosion(world, pos, Element.FIRE, 3.5f);
		Fx.groundShockwave(world, pos, Colors.LAVA, 11f);
		Fx.flash(world, pos, 0xFFB060, 40);
		Fx.shake(world, pos, 5f, 70);
		Sfx.play(world, pos, ModSounds.FIRE_EXPLOSION, 5f, 0.6f);
		Sfx.play(world, pos, ModSounds.QUAKE_BOOM, 5f, 0.8f);
		Sfx.play(world, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 3f, 0.6f);

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
}
