package com.avatarbending.sound;

import com.avatarbending.effect.EffectScheduler;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/**
 * Plays layered sound effects. Volume above 1 makes a sound audible from further away
 * (16 blocks per 1.0 volume).
 */
public final class Sfx {
	private Sfx() {
	}

	public static void play(ServerWorld world, Vec3d pos, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.PLAYERS, volume, vary(world, pitch));
	}

	public static void play(ServerWorld world, Vec3d pos, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
		play(world, pos, sound.value(), volume, pitch);
	}

	/** Plays a sound after {@code delay} ticks. */
	public static void later(ServerWorld world, int delay, Vec3d pos, SoundEvent sound, float volume, float pitch) {
		EffectScheduler.later(delay, () -> play(world, pos, sound, volume, pitch));
	}

	/** Small random pitch change so repeated casts never sound identical. */
	public static float vary(ServerWorld world, float pitch) {
		return Math.max(0.5f, Math.min(2.0f, pitch * (0.94f + world.random.nextFloat() * 0.12f)));
	}
}
