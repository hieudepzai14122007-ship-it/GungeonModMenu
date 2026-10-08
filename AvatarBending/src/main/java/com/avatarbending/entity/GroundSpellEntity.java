package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A spell that travels along the ground (tornadoes, tidal waves). It is only a marker: all
 * visuals are particles and all effects are applied by the server.
 */
public abstract class GroundSpellEntity extends Entity {
	private static final TrackedData<Float> SCALE = DataTracker.registerData(GroundSpellEntity.class, TrackedDataHandlerRegistry.FLOAT);

	@Nullable
	private UUID ownerUuid;
	@Nullable
	private ServerPlayerEntity cachedOwner;
	protected int maxLife = 100;
	protected double speed = 0.25;
	protected float power = 1f;

	protected GroundSpellEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public void setup(ServerPlayerEntity owner, Vec3d pos, Vec3d direction, float power) {
		this.ownerUuid = owner.getUuid();
		this.cachedOwner = owner;
		this.power = power;
		setPosition(pos);
		float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
		setYaw(yaw);
		prevYaw = yaw;
		dataTracker.set(SCALE, power);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SCALE, 1f);
	}

	/** Visual size multiplier (bigger in the Avatar State). */
	public float scale() {
		return dataTracker.get(SCALE);
	}

	public Vec3d direction() {
		float rad = getYaw() * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(-MathHelper.sin(rad), 0, MathHelper.cos(rad));
	}

	@Nullable
	public ServerPlayerEntity owner() {
		if (cachedOwner != null && !cachedOwner.isRemoved()) {
			return cachedOwner;
		}
		if (ownerUuid != null && getWorld() instanceof ServerWorld world) {
			cachedOwner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
			return cachedOwner;
		}
		return null;
	}

	@Nullable
	protected AbilityContext context() {
		ServerPlayerEntity owner = owner();
		return owner == null ? null : new AbilityContext(owner, (ServerWorld) getWorld(), false);
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			clientParticles();
			return;
		}
		ServerWorld world = (ServerWorld) getWorld();
		AbilityContext context = context();
		if (context == null || age > maxLife) {
			finish(world, context);
			discard();
			return;
		}
		Vec3d next = getPos().add(direction().multiply(speed));
		setPosition(next.x, followGround(world, next), next.z);
		serverTick(world, context);
	}

	/** Keeps the spell on the ground: climbs small steps and drops down ledges. */
	private double followGround(ServerWorld world, Vec3d pos) {
		BlockPos base = BlockPos.ofFloored(pos.x, pos.y + 0.5, pos.z);
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos p = base.up(dy);
			if (!world.getBlockState(p).isSolidBlock(world, p)
				&& world.getBlockState(p.down()).isSolidBlock(world, p.down())) {
				return p.getY();
			}
		}
		return pos.y;
	}

	protected abstract void serverTick(ServerWorld world, AbilityContext context);

	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
	}

	protected abstract void clientParticles();

	protected static void push(LivingEntity target, Vec3d velocity) {
		target.setVelocity(velocity);
		target.velocityModified = true;
		target.fallDistance = 0;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 128 * 128;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
