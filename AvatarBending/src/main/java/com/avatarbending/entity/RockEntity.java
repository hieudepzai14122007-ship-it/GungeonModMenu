package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Rock Barrage: a rock that first orbits its earthbender, then shoots at the crosshair.
 */
public class RockEntity extends BlockProjectileEntity {
	private static final TrackedData<Integer> ORBIT = DataTracker.registerData(RockEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public RockEntity(EntityType<? extends RockEntity> type, World world) {
		super(type, world);
		this.maxLife = 60;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ORBIT, 0);
	}

	/** Packs orbit slot, rock count and launch tick so clients animate the same orbit. */
	public void setOrbit(int index, int count, int launchAt) {
		dataTracker.set(ORBIT, (launchAt << 16) | ((index & 0xFF) << 8) | (count & 0xFF));
	}

	private int launchAt() {
		return dataTracker.get(ORBIT) >>> 16;
	}

	public boolean orbiting() {
		return age < launchAt() && getOwner() != null;
	}

	@Override
	protected Element element() {
		return Element.EARTH;
	}

	@Override
	protected double gravity() {
		return orbiting() ? 0 : 0.012;
	}

	@Override
	public void tick() {
		Entity owner = getOwner();
		int launchAt = launchAt();
		if (owner != null && age < launchAt) {
			int packed = dataTracker.get(ORBIT);
			int i = (packed >> 8) & 0xFF;
			int n = Math.max(1, packed & 0xFF);
			double ang = age * 0.28 + i * Math.PI * 2 / n;
			double lift = Math.min(1.0, age / 6.0);
			Vec3d target = owner.getPos().add(Math.cos(ang) * 1.8, 0.4 + 1.1 * lift + MathHelper.sin(age * 0.3f + i) * 0.2, Math.sin(ang) * 1.8);
			setVelocity(target.subtract(getPos()));
			life = 0;
		}
		if (!getWorld().isClient && owner != null && age == launchAt) {
			Vec3d dir = owner.getRotationVec(1f);
			if (owner instanceof ServerPlayerEntity player) {
				HitResult hit = new AbilityContext(player, (ServerWorld) getWorld(), false).raycast(48);
				Vec3d from = getPos().add(0, getHeight() / 2, 0);
				if (hit.getPos().squaredDistanceTo(from) > 1) {
					dir = hit.getPos().subtract(from).normalize();
				}
			}
			setVelocity(dir.multiply(2.2));
			velocityModified = true;
			Sfx.play((ServerWorld) getWorld(), getPos(), ModSounds.AIR_WHOOSH, 0.9f, 0.7f);
		}
		super.tick();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return !orbiting() && super.canHit(entity);
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (orbiting()) {
			return;
		}
		super.onBlockHit(hit);
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		Vec3d push = getVelocity().normalize().multiply(0.5);
		target.addVelocity(push.x, 0.2, push.z);
		target.velocityModified = true;
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		Fx.debris(world, pos, getBlockState(), 0.6f);
		Sfx.play(world, pos, ModSounds.ROCK_IMPACT, 1.0f, 1.3f);
	}

	@Override
	public float spinSpeed() {
		return 20f;
	}
}
