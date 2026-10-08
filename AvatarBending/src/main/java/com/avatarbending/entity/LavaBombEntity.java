package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A glob of molten rock (Lavabending and the Volcano). Splashes burning lava on impact and leaves
 * magma that cools back down. Real lava is never placed, so nothing burns or floods.
 */
public class LavaBombEntity extends BlockProjectileEntity {
	public LavaBombEntity(EntityType<? extends LavaBombEntity> type, World world) {
		super(type, world);
		this.maxLife = 90;
	}

	@Override
	protected BlockState defaultBlock() {
		return Blocks.MAGMA_BLOCK.getDefaultState();
	}

	@Override
	protected Element element() {
		return Element.EARTH;
	}

	@Override
	protected double gravity() {
		return 0.05;
	}

	@Override
	protected double drag() {
		return 0.99;
	}

	@Override
	public boolean fullBright() {
		return true;
	}

	@Override
	public float spinSpeed() {
		return 12f;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.setOnFireForTicks(100);
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.flames(world, pos, Colors.LAVA, 1.1f);
		Fx.debris(world, pos, Blocks.MAGMA_BLOCK.getDefaultState(), 0.5f);
		Sfx.play(world, pos, ModSounds.FIRE_EXPLOSION, 1.0f, 1.5f);
		Sfx.play(world, pos, SoundEvents.BLOCK_LAVA_POP, 1.5f, 0.8f);
		Sfx.play(world, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.2f);
		if (!(getOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		AbilityContext context = new AbilityContext(player, world, false);
		for (LivingEntity target : context.targetsAround(pos, 2.2)) {
			target.timeUntilRegen = 0;
			target.damage(AbilityContext.damageSource(world, Element.FIRE, this, player), damage * 0.6f * power);
			target.setOnFireForTicks(100);
		}
		for (int i = 0; i < 3; i++) {
			BlockPos ground = context.groundAt(pos.x + random.nextGaussian() * 0.8, pos.y + 0.5, pos.z + random.nextGaussian() * 0.8, 2, 3);
			if (ground != null) {
				context.placeTemp(ground, Blocks.MAGMA_BLOCK.getDefaultState(), 80 + random.nextInt(40));
			}
		}
	}
}
