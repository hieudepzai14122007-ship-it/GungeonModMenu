package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Colors;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A temporary volcano that bursts out of the ground and rains lava bombs everywhere. The mound
 * sinks back into the ground when the eruption ends.
 */
public class VolcanoEntity extends GroundSpellEntity {
	private boolean built;

	public VolcanoEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 110;
		this.speed = 0;
	}

	public Vec3d crater() {
		return getPos().add(0, 3.2, 0);
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		if (!built) {
			built = true;
			buildMound(context);
			Fx.groundShockwave(world, getPos(), Colors.LAVA, 8f);
			Fx.debris(world, getPos().add(0, 1, 0), Blocks.BLACKSTONE.getDefaultState(), 2.5f);
			Fx.shake(world, getPos(), 3f, 40);
			Sfx.play(world, getPos(), ModSounds.ROCK_RUMBLE, 4f, 0.6f);
			Sfx.play(world, getPos(), ModSounds.QUAKE_BOOM, 4f, 0.8f);
		}
		if (age > 10 && age % 4 == 0) {
			ServerPlayerEntity owner = owner();
			if (owner != null) {
				LavaBombEntity bomb = new LavaBombEntity(ModEntities.LAVA_BOMB, world);
				double angle = random.nextDouble() * Math.PI * 2;
				double out = 0.25 + random.nextDouble() * 0.45;
				Vec3d velocity = new Vec3d(Math.cos(angle) * out, 0.9 + random.nextDouble() * 0.5, Math.sin(angle) * out);
				bomb.setup(owner, crater(), velocity, 8f, power);
				world.spawnEntity(bomb);
			}
		}
		if (age % 10 == 0) {
			for (LivingEntity target : context.targetsAround(getPos().add(0, 1, 0), 4.5)) {
				context.damage(target, Element.FIRE, 3f * power);
				target.setOnFireForTicks(60);
			}
		}
		if (age % 30 == 15) {
			Sfx.play(world, crater(), ModSounds.FIRE_ROAR, 3f, 0.6f);
			Fx.flames(world, crater(), Colors.LAVA, 1.8f);
		}
	}

	private void buildMound(AbilityContext context) {
		BlockPos base = getBlockPos();
		BlockState rock = Blocks.BLACKSTONE.getDefaultState();
		BlockState magma = Blocks.MAGMA_BLOCK.getDefaultState();
		for (int y = 0; y < 3; y++) {
			int r = 3 - y;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r + 0.3 || (y == 2 && d < 0.5)) {
						continue;
					}
					boolean rim = d > r - 0.8;
					context.placeTemp(base.add(dx, y, dz), rim && y < 2 ? rock : magma, maxLife + 40 - y * 2);
				}
			}
		}
	}

	@Override
	protected void finish(ServerWorld world, @Nullable AbilityContext context) {
		Fx.explosion(world, crater(), Element.FIRE, 3f);
		Fx.shake(world, getPos(), 3f, 40);
		Sfx.play(world, crater(), ModSounds.FIRE_EXPLOSION, 4f, 0.7f);
		if (context == null) {
			return;
		}
		for (LivingEntity target : context.targetsAround(crater(), 6)) {
			context.damage(target, Element.FIRE, 10f * power);
			target.setOnFireForTicks(100);
			Vec3d away = target.getPos().subtract(getPos()).normalize();
			push(target, away.multiply(1.2).add(0, 0.8, 0));
		}
	}
}
