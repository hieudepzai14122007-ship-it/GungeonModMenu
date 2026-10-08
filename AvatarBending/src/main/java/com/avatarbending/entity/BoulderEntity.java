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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A boulder ripped out of the ground. It first rises in front of the earthbender, then is hurled
 * where they are looking and shatters on impact, hurting everything nearby.
 */
public class BoulderEntity extends BlockProjectileEntity {
	private int raiseTicks;
	private float launchSpeed = 1.6f;

	public BoulderEntity(EntityType<? extends BoulderEntity> type, World world) {
		super(type, world);
		this.maxLife = 60;
	}

	public void setRaise(int raiseTicks, float launchSpeed) {
		this.raiseTicks = raiseTicks;
		this.launchSpeed = launchSpeed;
	}

	@Override
	protected Element element() {
		return Element.EARTH;
	}

	@Override
	protected double gravity() {
		return raiseTicks > 0 ? 0 : 0.035;
	}

	@Override
	public void tick() {
		if (!getWorld().isClient && raiseTicks > 0) {
			raiseTicks--;
			life = 0;
			if (raiseTicks == 0) {
				Entity owner = getOwner();
				Vec3d dir = owner != null ? owner.getRotationVec(1f) : getVelocity().normalize();
				if (owner instanceof ServerPlayerEntity player) {
					HitResult hit = new AbilityContext(player, (ServerWorld) getWorld(), false).raycast(48);
					Vec3d from = getPos().add(0, getHeight() / 2, 0);
					if (hit.getPos().squaredDistanceTo(from) > 1) {
						dir = hit.getPos().subtract(from).normalize();
					}
				}
				setVelocity(dir.multiply(launchSpeed));
				velocityModified = true;
				ServerWorld world = (ServerWorld) getWorld();
				Sfx.play(world, getPos(), ModSounds.AIR_WHOOSH, 1.2f, 0.55f);
				Sfx.play(world, getPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
			} else {
				setVelocity(0, 0.28, 0);
			}
		}
		super.tick();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return raiseTicks <= 0 && super.canHit(entity);
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (raiseTicks > 0) {
			// Still rising out of the ground: ignore the ground it came from.
			return;
		}
		super.onBlockHit(hit);
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		Vec3d push = getVelocity().normalize().multiply(1.2 * power);
		target.addVelocity(push.x, 0.45, push.z);
		target.velocityModified = true;
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.debris(world, pos, getBlockState(), 1.5f);
		Fx.groundShockwave(world, pos, Colors.EARTH, 3.5f);
		Sfx.play(world, pos, ModSounds.ROCK_IMPACT, 2.0f, 0.85f);
		Sfx.play(world, pos, getBlockState().getSoundGroup().getBreakSound(), 1.5f, 0.6f);
		if (getOwner() instanceof ServerPlayerEntity player) {
			AbilityContext context = new AbilityContext(player, world, false);
			for (LivingEntity nearby : context.targetsAround(pos, 2.5 * power)) {
				nearby.timeUntilRegen = 0;
				nearby.damage(AbilityContext.damageSource(world, Element.EARTH, this, player), damage * 0.4f * power);
				Vec3d away = nearby.getPos().subtract(pos).normalize();
				nearby.addVelocity(away.x * 0.6, 0.3, away.z * 0.6);
				nearby.velocityModified = true;
			}
		}
	}

	@Override
	protected void expire(ServerWorld world) {
		impact(world, getPos());
	}
}
