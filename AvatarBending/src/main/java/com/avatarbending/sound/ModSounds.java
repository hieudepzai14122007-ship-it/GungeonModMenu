package com.avatarbending.sound;

import com.avatarbending.AvatarBending;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Custom sound effects. They are synthesized by tools/gen_sounds.py (no third-party audio).
 */
public final class ModSounds {
	public static final SoundEvent AIR_WHOOSH = register("air_whoosh");
	public static final SoundEvent AIR_BLAST = register("air_blast");
	public static final SoundEvent AIR_SLASH = register("air_slash");
	public static final SoundEvent WIND_HOWL = register("wind_howl");
	public static final SoundEvent FIRE_WHOOSH = register("fire_whoosh");
	public static final SoundEvent FIRE_ROAR = register("fire_roar");
	public static final SoundEvent FIRE_EXPLOSION = register("fire_explosion");
	public static final SoundEvent ELECTRIC_CHARGE = register("electric_charge");
	public static final SoundEvent LIGHTNING_STRIKE = register("lightning_strike");
	public static final SoundEvent WATER_WHOOSH = register("water_whoosh");
	public static final SoundEvent WATER_SURGE = register("water_surge");
	public static final SoundEvent ICE_CRACK = register("ice_crack");
	public static final SoundEvent ROCK_RUMBLE = register("rock_rumble");
	public static final SoundEvent ROCK_IMPACT = register("rock_impact");
	public static final SoundEvent QUAKE_BOOM = register("quake_boom");
	public static final SoundEvent CHARGE_MAGIC = register("charge_magic");
	public static final SoundEvent SPIRIT_CHIME = register("spirit_chime");
	public static final SoundEvent AVATAR_STATE = register("avatar_state");
	public static final SoundEvent AVATAR_PULSE = register("avatar_pulse");
	public static final SoundEvent ENERGY_BEAM = register("energy_beam");
	public static final SoundEvent METAL_ZING = register("metal_zing");
	public static final SoundEvent WHIP_CRACK = register("whip_crack");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		return Registry.register(Registries.SOUND_EVENT, AvatarBending.id(name), SoundEvent.of(AvatarBending.id(name)));
	}

	public static void register() {
		// Static initializer does the work.
	}
}
