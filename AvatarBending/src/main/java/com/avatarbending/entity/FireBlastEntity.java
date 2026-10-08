package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A blast of fire that sets its target ablaze. Never sets blocks on fire.
 * Styles: normal, blue (Azula-style) and falling fire meteors (Firestorm) that explode.
 */
public class FireBlastEntity extends BendingProjectileEntity {
	public static final int STYLE_NORMAL = 0;
	public static final int STYLE_BLUE = 1;
	public static final int STYLE_METEOR = 2;
	private static final TrackedData<Integer> STYLE = DataTracker.registerData(FireBlastEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public FireBlastEntity(EntityType<? extends FireBlastEntity> type, World world) {
		super(type, world);
		this.maxLife = 30;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STYLE, STYLE_NORMAL);
	}

	public int style() {
		return dataTracker.get(STYLE);
	}

	public void setStyle(int style) {
		dataTracker.set(STYLE, style);
		if (style == STYLE_METEOR) {
			this.maxLife = 80;
		}
	}

	public int color() {
		return style() == STYLE_BLUE ? Colors.BLUE_FIRE : Colors.FIRE;
	}

	@Override
	protected Element element() {
		return Element.FIRE;
	}

	@Override
	protected double gravity() {
		return style() == STYLE_METEOR ? 0.04 : 0;
	}

	@Override
	protected void hitEntity(ServerWorld world, LivingEntity target) {
		super.hitEntity(world, target);
		target.setOnFireForTicks((int) (80 * power));
		Vec3d push = getVelocity().normalize().multiply(0.5);
		target.addVelocity(push.x, 0.2, push.z);
		target.velocityModified = true;
	}

	@Override
	protected void impact(ServerWorld world, Vec3d pos) {
		if (style() == STYLE_METEOR) {
			Fx.flames(world, pos, Colors.FIRE, 1.4f);
			Fx.groundShockwave(world, pos, Colors.FIRE, 3.5f);
			Sfx.play(world, pos, ModSounds.FIRE_EXPLOSION, 1.6f, 1.2f);
			if (getOwner() instanceof ServerPlayerEntity player) {
				AbilityContext context = new AbilityContext(player, world, false);
				for (LivingEntity target : context.targetsAround(pos, 2.8)) {
					target.timeUntilRegen = 0;
					target.damage(AbilityContext.damageSource(world, Element.FIRE, this, player), 5f * power);
					target.setOnFireForTicks(80);
				}
			}
			return;
		}
		Fx.flames(world, pos, color(), 0.8f);
		Sfx.play(world, pos, ModSounds.FIRE_EXPLOSION, 0.6f, 1.6f);
	}

	@Override
	protected void expire(ServerWorld world) {
		Fx.flames(world, getPos(), color(), 0.4f);
	}
}
