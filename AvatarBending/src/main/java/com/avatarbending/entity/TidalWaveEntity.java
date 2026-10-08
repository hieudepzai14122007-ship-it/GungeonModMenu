package com.avatarbending.entity;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.bending.Element;
import com.avatarbending.fx.Fx;
import com.avatarbending.sound.ModSounds;
import com.avatarbending.sound.Sfx;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A wide wall of water that surges forward, sweeping mobs away and putting out fires.
 */
public class TidalWaveEntity extends GroundSpellEntity {
	public static final double HALF_WIDTH = 3.5;
	public static final double HEIGHT = 3.2;

	private final Set<UUID> hit = new HashSet<>();

	public TidalWaveEntity(EntityType<?> type, World world) {
		super(type, world);
		this.maxLife = 36;
		this.speed = 0.6;
	}

	public Vec3d perpendicular() {
		Vec3d dir = direction();
		return new Vec3d(-dir.z, 0, dir.x);
	}

	@Override
	protected void serverTick(ServerWorld world, AbilityContext context) {
		double halfWidth = HALF_WIDTH * scale();
		double height = HEIGHT * scale();
		Vec3d dir = direction();
		Vec3d perp = perpendicular();
		Box box = getBoundingBox().expand(halfWidth + 1, height, halfWidth + 1);
		for (LivingEntity target : context.targetsIn(box)) {
			Vec3d rel = target.getPos().subtract(getPos());
			double along = rel.dotProduct(dir);
			double side = rel.dotProduct(perp);
			if (Math.abs(along) > 1.6 || Math.abs(side) > halfWidth || rel.y < -1.5 || rel.y > height) {
				continue;
			}
			if (hit.add(target.getUuid())) {
				context.damage(target, Element.WATER, 6f * power);
				target.extinguish();
				Fx.splash(world, target.getPos().add(0, 1, 0), 0.8f);
			}
			push(target, dir.multiply(speed + 0.35).add(0, 0.3, 0));
		}
		for (double s = -halfWidth; s <= halfWidth; s += 1) {
			Vec3d p = getPos().add(perp.multiply(s));
			for (int dy = -1; dy <= 2; dy++) {
				BlockPos pos = BlockPos.ofFloored(p.x, p.y + dy, p.z);
				if (world.getBlockState(pos).getBlock() instanceof AbstractFireBlock) {
					world.removeBlock(pos, false);
				}
			}
		}
		if (age % 8 == 0) {
			Sfx.play(world, getPos(), ModSounds.WATER_WHOOSH, 2f, 0.6f);
		}
	}
}
