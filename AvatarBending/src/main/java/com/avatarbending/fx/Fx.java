package com.avatarbending.fx;

import com.avatarbending.bending.Element;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.network.ModPayloads.AttachFxPayload;
import com.avatarbending.network.ModPayloads.FxPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side entry point for visual effects. Each call sends one small packet to nearby players;
 * their clients render the effect (see ClientFx).
 */
public final class Fx {
	/** Players this far away still see big effects. */
	public static final double RANGE = 160;

	private Fx() {
	}

	public static void play(ServerWorld world, FxType type, Vec3d pos, Vec3d vec, float scale, int color, int data) {
		FxPayload payload = new FxPayload(type.ordinal(), pos.x, pos.y, pos.z, (float) vec.x, (float) vec.y, (float) vec.z,
			scale, color, data);
		for (ServerPlayerEntity player : PlayerLookup.around(world, pos, RANGE)) {
			ModPayloads.send(player, payload);
		}
	}

	public static void burst(ServerWorld world, Vec3d pos, int color, float scale) {
		play(world, FxType.BURST, pos, Vec3d.ZERO, scale, color, 0);
	}

	public static void ring(ServerWorld world, Vec3d pos, Vec3d axis, int color, float radius, int lifetime) {
		play(world, FxType.RING, pos, axis, radius, color, lifetime);
	}

	public static void groundShockwave(ServerWorld world, Vec3d pos, int color, float radius) {
		play(world, FxType.GROUND_SHOCKWAVE, pos, Vec3d.ZERO, radius, color, 0);
	}

	public static void beam(ServerWorld world, Vec3d from, Vec3d to, int color, float width, int lifetime) {
		play(world, FxType.BEAM, from, to.subtract(from), width, color, lifetime);
	}

	public static void lightning(ServerWorld world, Vec3d from, Vec3d to, int color, float intensity) {
		play(world, FxType.LIGHTNING, from, to.subtract(from), intensity, color, 0);
	}

	public static void explosion(ServerWorld world, Vec3d pos, @Nullable Element element, float scale) {
		int color = element == null ? Colors.WHITE : Colors.of(element);
		play(world, FxType.EXPLOSION, pos, Vec3d.ZERO, scale, color, element == null ? -1 : element.ordinal());
	}

	public static void charge(ServerWorld world, Vec3d pos, int color, float radius, int duration) {
		play(world, FxType.CHARGE, pos, Vec3d.ZERO, radius, color, duration);
	}

	public static void pillar(ServerWorld world, Vec3d base, int color, float height, int lifetime) {
		play(world, FxType.PILLAR, base, Vec3d.ZERO, height, color, lifetime);
	}

	public static void spiral(ServerWorld world, Vec3d base, int color, float radius, float height) {
		play(world, FxType.SPIRAL, base, Vec3d.ZERO, radius, color, Math.round(height * 10));
	}

	public static void windBurst(ServerWorld world, Vec3d pos, Vec3d direction, float scale) {
		play(world, FxType.WIND_BURST, pos, direction, scale, Colors.AIR, 0);
	}

	public static void splash(ServerWorld world, Vec3d pos, float scale) {
		play(world, FxType.SPLASH, pos, Vec3d.ZERO, scale, Colors.WATER, 0);
	}

	public static void frost(ServerWorld world, Vec3d pos, float scale) {
		play(world, FxType.FROST, pos, Vec3d.ZERO, scale, Colors.ICE, 0);
	}

	public static void debris(ServerWorld world, Vec3d pos, BlockState state, float scale) {
		play(world, FxType.DEBRIS, pos, Vec3d.ZERO, scale, Colors.EARTH, Block.getRawIdFromState(state));
	}

	public static void flames(ServerWorld world, Vec3d pos, int color, float scale) {
		play(world, FxType.FLAMES, pos, Vec3d.ZERO, scale, color, 0);
	}

	public static void sigil(ServerWorld world, Vec3d pos, int color, float radius, int duration) {
		play(world, FxType.SIGIL, pos, Vec3d.ZERO, radius, color, duration);
	}

	public static void flash(ServerWorld world, Vec3d pos, int color, float radius) {
		play(world, FxType.FLASH, pos, Vec3d.ZERO, radius, color, 0);
	}

	public static void shake(ServerWorld world, Vec3d pos, float intensity, int radius) {
		play(world, FxType.SHAKE, pos, Vec3d.ZERO, intensity, 0, radius);
	}

	public static void line(ServerWorld world, Vec3d from, Vec3d to, int color, float density) {
		play(world, FxType.LINE, from, to.subtract(from), density, color, 0);
	}

	public static void slash(ServerWorld world, Vec3d center, Vec3d forward, int color, float radius) {
		play(world, FxType.SLASH, center, forward, radius, color, 0);
	}

	public static void cone(ServerWorld world, Vec3d origin, Vec3d directionTimesLength, Element element, float spread) {
		play(world, FxType.CONE, origin, directionTimesLength, spread, Colors.of(element), element.ordinal());
	}

	public static void fireRing(ServerWorld world, Vec3d pos, int color, float radius, int ticks) {
		play(world, FxType.FIRE_RING, pos, Vec3d.ZERO, radius, color, ticks);
	}

	// ---------------------------------------------------------------- attached effects

	public static void attach(Entity entity, AttachedFxType type, int duration) {
		attach(entity, type, duration, 0, Vec3d.ZERO);
	}

	public static void attach(Entity entity, AttachedFxType type, int duration, int data) {
		attach(entity, type, duration, data, Vec3d.ZERO);
	}

	public static void attach(Entity entity, AttachedFxType type, int duration, int data, Vec3d anchor) {
		if (!(entity.getWorld() instanceof ServerWorld world)) {
			return;
		}
		AttachFxPayload payload = new AttachFxPayload(entity.getId(), type.ordinal(), duration, data,
			(float) anchor.x, (float) anchor.y, (float) anchor.z);
		for (ServerPlayerEntity player : PlayerLookup.around(world, entity.getPos(), RANGE)) {
			ModPayloads.send(player, payload);
		}
	}

	/** Stops an attached effect early. */
	public static void detach(Entity entity, AttachedFxType type) {
		attach(entity, type, 0, 0, Vec3d.ZERO);
	}
}
