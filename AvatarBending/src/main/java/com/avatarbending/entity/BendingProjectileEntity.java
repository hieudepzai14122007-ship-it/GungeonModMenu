package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Base class for bending projectiles. Moves in a straight (or slightly falling) line, hits the first
 * block or valid target and expires after {@link #maxLife} ticks. Trails are spawned on the client
 * every tick for smooth visuals.
 */
public abstract class BendingProjectileEntity extends ProjectileEntity {
	protected int life;
	protected int maxLife = 40;
	protected float damage = 4f;
	protected float power = 1f;

	protected BendingProjectileEntity(EntityType<? extends BendingProjectileEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	public void setup(LivingEntity owner, Vec3d pos, Vec3d velocity, float damage, float power) {
		setOwner(owner);
		setPosition(pos.x, pos.y, pos.z);
		setVelocity(velocity);
		ProjectileUtil.setRotationFromVelocity(this, 1f);
		this.damage = damage;
		this.power = power;
	}

	protected abstract Element element();

	/** Downward acceleration per tick. */
	protected double gravity() {
		return 0;
	}

	protected double drag() {
		return 1.0;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();

		if (!world.isClient) {
			HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
			if (hit.getType() != HitResult.Type.MISS && !isRemoved()) {
				onCollision(hit);
				if (isRemoved()) {
					return;
				}
			}
			serverTick((ServerWorld) world);
		}

		Vec3d velocity = getVelocity();
		setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
		setVelocity(velocity.multiply(drag()).add(0, -gravity(), 0));
		ProjectileUtil.setRotationFromVelocity(this, 0.5f);

		if (world.isClient) {
			clientTrail();
		} else if (++life > maxLife) {
			expire((ServerWorld) world);
			discard();
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		Entity owner = getOwner();
		if (entity == owner || entity instanceof BendingProjectileEntity) {
			return false;
		}
		if (owner != null && entity instanceof TameableEntity tameable && owner instanceof LivingEntity living && tameable.isOwner(living)) {
			return false;
		}
		if (entity instanceof PlayerEntity player && player.isSpectator()) {
			return false;
		}
		return entity instanceof LivingEntity && super.canHit(entity);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (getWorld() instanceof ServerWorld world && hit.getEntity() instanceof LivingEntity target) {
			hitEntity(world, target);
			impact(world, hit.getPos());
		}
		discard();
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		if (getWorld() instanceof ServerWorld world) {
			impact(world, hit.getPos());
		}
		discard();
	}

	/** Damages a directly hit target. Override to add extra effects. */
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		target.timeUntilRegen = 0;
		target.damage(AbilityContext.damageSource(world, element(), this, getOwner()), damage * power);
	}

	/** Visual/sound effect where the projectile hits something. */
	protected void impact(ServerWorld world, Vec3d pos) {
	}

	/** Called every server tick while flying. */
	protected void serverTick(ServerWorld world) {
	}

	/** Called when the projectile runs out of range without hitting anything. */
	protected void expire(ServerWorld world) {
	}

	/** Spawns trail particles on the client. */
	protected abstract void clientTrail();

	protected ServerPlayerEntity ownerPlayer() {
		return getOwner() instanceof ServerPlayerEntity player ? player : null;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 128 * 128;
	}
}
