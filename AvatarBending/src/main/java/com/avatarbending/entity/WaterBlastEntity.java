package com.avatarbending.entity;

import com.avatarbending.bending.Element;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A spiraling whip of water. Knocks back, puts out fires and soaks the target.
 */
public class WaterBlastEntity extends BendingProjectileEntity {
	public WaterBlastEntity(EntityType<? extends WaterBlastEntity> type, World world) {
		super(type, world);
		this.maxLife = 26;
	}

	@Override
	protected Element element() {
		return Element.WATER;
	}

	@Override
	protected double gravity() {
		return 0.008;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.extinguish();
		Vec3d push = getVelocity().normalize().multiply(0.9 * power);
		target.addVelocity(push.x, 0.25, push.z);
		target.velocityModified = true;
		Sfx.play(world, target.getPos(), ModSounds.WHIP_CRACK, 1.0f, 1.2f);
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.splash(world, pos, 0.9f);
		Sfx.play(world, pos, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.6f, 1.3f);
	}

	@Override
	protected void expire(ServerWorld world) {
		Fx.splash(world, getPos(), 0.4f);
	}
}
