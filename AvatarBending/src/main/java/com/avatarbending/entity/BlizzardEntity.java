package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.ability.WaterAbilities;
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
 * A howling snowstorm. Everything inside slowly freezes, then the storm locks it all in ice.
 */
public class BlizzardEntity extends GroundSpellEntity {
	public static final double RADIUS = 10;

	public BlizzardEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 120;
		this.speed = 0;
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		if (age % 10 == 0) {
			for (LivingEntity target : context.targetsAround(getPos(), RADIUS * scale())) {
				context.damage(target, Element.WATER, 2f * power);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
				target.setFrozenTicks(Math.min(target.getFrozenTicks() + 40, target.getMinFreezeDamageTicks() + 60));
			}
		}
		if (age % 40 == 0) {
			Sfx.play(world, getPos(), ModSounds.WIND_HOWL, 4f, 1.15f);
		}
		if (age % 13 == 0) {
			Sfx.play(world, getPos().add(random.nextGaussian() * 4, 1, random.nextGaussian() * 4), ModSounds.ICE_CRACK, 1.5f, 0.9f);
		}
	}

	@Override
	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
		Fx.frost(world, getPos().add(0, 1, 0), 3.5f);
		Fx.groundShockwave(world, getPos(), Colors.ICE, (float) (RADIUS * scale()));
		Sfx.play(world, getPos(), ModSounds.ICE_CRACK, 3f, 0.7f);
		Sfx.play(world, getPos(), ModSounds.ICE_CRACK, 3f, 1.1f);
		if (context == null) {
			return;
		}
		for (LivingEntity target : context.targetsAround(getPos(), RADIUS * scale())) {
			context.damage(target, Element.WATER, 6f * power);
			WaterAbilities.freeze(world, target, 100);
		}
	}
}
