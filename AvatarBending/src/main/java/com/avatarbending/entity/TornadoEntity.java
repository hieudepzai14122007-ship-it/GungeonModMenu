package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A roaring tornado that wanders forward, sucking mobs in, spinning them up into the sky and
 * flinging them out when it dies down. The Avatar's version is made of fire.
 */
public class TornadoEntity extends GroundSpellEntity {
	public static final double HEIGHT = 9;
	public static final double RADIUS = 3.5;
	private static final TrackedData<Boolean> FIERY = DataTracker.registerData(TornadoEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	public TornadoEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 160;
		this.speed = 0.2;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(FIERY, false);
	}

	public boolean fiery() {
		return dataTracker.get(FIERY);
	}

	public void setFiery(boolean fiery) {
		dataTracker.set(FIERY, fiery);
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		double radius = RADIUS * scale();
		double height = HEIGHT * scale();
		boolean fire = fiery();
		Box box = new Box(getX() - radius, getY() - 1, getZ() - radius, getX() + radius, getY() + height, getZ() + radius);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d toCenter = new Vec3d(getX() - target.getX(), 0, getZ() - target.getZ());
			double dist = toCenter.length();
			if (dist > radius) {
				continue;
			}
			Vec3d inward = dist < 0.01 ? Vec3d.ZERO : toCenter.multiply(1 / dist);
			Vec3d tangent = new Vec3d(-inward.z, 0, inward.x);
			double heightIn = target.getY() - getY();
			double lift = heightIn < height - 1 ? 0.22 : -0.05;
			Vec3d velocity = inward.multiply(0.12 + dist * 0.03).add(tangent.multiply(0.45)).add(direction().multiply(speed)).add(0, lift, 0);
			push(target, velocity);
			if (age % 15 == 0) {
				context.damage(target, fire ? Element.FIRE : Element.AIR, (fire ? 3f : 2f) * power);
				if (fire) {
					target.setOnFireForTicks(80);
				}
			}
		}
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
			Vec3d toCenter = new Vec3d(getX() - item.getX(), 0, getZ() - item.getZ());
			item.setVelocity(toCenter.multiply(0.1).add(new Vec3d(-toCenter.z, 0, toCenter.x).normalize().multiply(0.3)).add(0, 0.15, 0));
			item.velocityModified = true;
		}
		if (age % 30 == 0) {
			Sfx.play(world, getPos(), ModSounds.WIND_HOWL, 3f, fire ? 0.6f : 0.75f);
			if (fire) {
				Sfx.play(world, getPos(), ModSounds.FIRE_ROAR, 2.5f, 0.7f);
			}
		}
		if (age % 20 == 10) {
			Sfx.play(world, getPos(), SoundEvents.ENTITY_BREEZE_WHIRL, 2.5f, 0.5f);
		}
		if (age % 25 == 0) {
			Fx.shake(world, getPos(), 0.8f, 20);
		}
	}

	@Override
	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
		Fx.windBurst(world, getPos().add(0, 1, 0), new Vec3d(0, 1, 0), 2.5f);
		if (fiery()) {
			Fx.flames(world, getPos().add(0, 1, 0), com.avatarbending.fx.Colors.FIRE, 2f);
		}
		Sfx.play(world, getPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST, 2f, 0.6f);
		Sfx.play(world, getPos(), ModSounds.AIR_BLAST, 2f, 0.9f);
		if (context == null) {
			return;
		}
		double radius = RADIUS * scale() + 1;
		Box box = new Box(getX() - radius, getY() - 1, getZ() - radius, getX() + radius, getY() + HEIGHT * scale() + 2, getZ() + radius);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d away = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
			away = away.lengthSquared() < 0.01 ? direction() : away.normalize();
			push(target, away.multiply(1.4).add(0, 0.6, 0));
		}
	}
}
