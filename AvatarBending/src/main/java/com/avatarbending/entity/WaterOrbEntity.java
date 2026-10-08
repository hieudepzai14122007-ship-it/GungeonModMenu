package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.ability.WaterAbilities;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Glacier Bomb: a glowing orb of water that bursts into a field of ice, freezing everything nearby.
 */
public class WaterOrbEntity extends BendingProjectileEntity {
	private boolean exploded;

	public WaterOrbEntity(EntityType<? extends WaterOrbEntity> type, World world) {
		super(type, world);
		this.maxLife = 60;
	}

	@Override
	protected Element element() {
		return Element.WATER;
	}

	@Override
	protected double gravity() {
		return 0.03;
	}

	@Override
	protected double drag() {
		return 0.99;
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (getWorld() instanceof ServerWorld world) {
			explode(world, hit.getPos());
		}
		discard();
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (getWorld() instanceof ServerWorld world) {
			explode(world, hit.getPos());
		}
		discard();
	}

	@Override
	protected void expire(ServerWorld world) {
		explode(world, getPos());
	}

	private void explode(ServerWorld world, Vec3d pos) {
		if (exploded) {
			return;
		}
		exploded = true;
		Fx.frost(world, pos, 2.2f);
		Fx.splash(world, pos, 1.2f);
		Fx.groundShockwave(world, pos, Colors.ICE, 5.5f);
		Sfx.play(world, pos, ModSounds.ICE_CRACK, 2.0f, 0.8f);
		Sfx.play(world, pos, ModSounds.ICE_CRACK, 2.0f, 1.25f);
		Sfx.play(world, pos, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
		Sfx.play(world, pos, ModSounds.WATER_WHOOSH, 1.2f, 0.7f);
		if (!(getOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		AbilityContext context = new AbilityContext(player, world, false);
		for (LivingEntity target : context.targetsAround(pos, 4.5 * power)) {
			target.timeUntilRegen = 0;
			target.damage(AbilityContext.damageSource(world, Element.WATER, this, player), damage * power);
			WaterAbilities.freeze(world, target, (int) (100 * power));
		}
		// A crown of ice spikes around the impact.
		for (int i = 0; i < 9; i++) {
			double a = i * Math.PI * 2 / 9 + random.nextDouble() * 0.3;
			double r = 2.6 + random.nextDouble();
			BlockPos ground = context.groundAt(pos.x + Math.cos(a) * r, pos.y, pos.z + Math.sin(a) * r, 2, 4);
			if (ground == null) {
				continue;
			}
			int height = 1 + random.nextInt(3);
			for (int h = 0; h < height; h++) {
				if (!context.placeTemp(ground.up(h), Blocks.PACKED_ICE.getDefaultState(), 120 + i * 3)) {
					break;
				}
			}
		}
	}
}
