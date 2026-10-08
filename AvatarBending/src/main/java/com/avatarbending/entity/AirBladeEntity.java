package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A wide crescent of razor wind that slices through every mob in its path.
 */
public class AirBladeEntity extends BendingProjectileEntity {
	private final Set<UUID> hit = new HashSet<>();

	public AirBladeEntity(EntityType<? extends AirBladeEntity> type, World world) {
		super(type, world);
		this.maxLife = 20;
	}

	@Override
	protected Element element() {
		return Element.AIR;
	}

	@Override
	protected boolean canHit(Entity entity) {
		return false;
	}

	@Override
	protected void serverTick(ServerWorld world) {
		if (!(getOwner() instanceof ServerPlayerEntity player)) {
			return;
		}
		AbilityContext context = new AbilityContext(player, world, false);
		Box box = getBoundingBox().stretch(getVelocity()).expand(0.4, 0.6, 0.4);
		for (LivingEntity target : context.targetsIn(box)) {
			if (!hit.add(target.getUuid())) {
				continue;
			}
			hitEntity(world, target);
			Vec3d push = getVelocity().normalize().multiply(0.6);
			target.addVelocity(push.x, 0.25, push.z);
			target.velocityModified = true;
			Fx.burst(world, target.getPos().add(0, target.getHeight() * 0.6, 0), Colors.AIR, 0.6f);
			Sfx.play(world, target.getPos(), ModSounds.AIR_SLASH, 0.8f, 1.5f);
		}
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.windBurst(world, pos, getVelocity().multiply(-1), 0.9f);
		Sfx.play(world, pos, ModSounds.AIR_SLASH, 0.7f, 0.8f);
	}

	@Override
	protected void expire(ServerWorld world) {
		Fx.windBurst(world, getPos(), getVelocity(), 0.6f);
	}
}
