package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A giant whirlpool that drags everything into its center, then erupts in a geyser.
 */
public class MaelstromEntity extends GroundSpellEntity {
	public static final double RADIUS = 7;

	public MaelstromEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 120;
		this.speed = 0;
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		double radius = RADIUS * scale();
		for (LivingEntity target : context.targetsAround(getPos(), radius + 1)) {
			Vec3d toCenter = new Vec3d(getX() - target.getX(), 0, getZ() - target.getZ());
			double dist = toCenter.length();
			Vec3d inward = dist < 0.01 ? Vec3d.ZERO : toCenter.multiply(1 / dist);
			Vec3d tangent = new Vec3d(-inward.z, 0, inward.x);
			push(target, inward.multiply(0.1 + 0.02 * dist).add(tangent.multiply(0.3)).add(0, -0.05, 0));
			if (age % 10 == 0) {
				context.damage(target, Element.WATER, 2f * power);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2));
			}
		}
		if (age % 40 == 0) {
			Sfx.play(world, getPos(), ModSounds.WATER_SURGE, 3f, 0.7f);
			Sfx.play(world, getPos(), ModSounds.WIND_HOWL, 1.5f, 0.6f);
		}
	}

	@Override
	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
		Vec3d center = getPos();
		Fx.pillar(world, center, Colors.WATER, 14f, 24);
		Fx.splash(world, center.add(0, 1, 0), 3f);
		Fx.groundShockwave(world, center, Colors.WATER, (float) (RADIUS * scale()));
		Sfx.play(world, center, ModSounds.WATER_SURGE, 4f, 1.3f);
		Sfx.play(world, center, ModSounds.AIR_BLAST, 2f, 1.4f);
		if (context == null) {
			return;
		}
		for (LivingEntity target : context.targetsAround(center, RADIUS * scale() + 1)) {
			context.damage(target, Element.WATER, 8f * power);
			push(target, new Vec3d(0, 1.6, 0));
		}
	}
}
