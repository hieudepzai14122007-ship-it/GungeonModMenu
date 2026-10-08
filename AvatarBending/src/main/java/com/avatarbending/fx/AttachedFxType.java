package com.avatarbending.fx;

/**
 * Visual effects that follow an entity for a while (shields, auras, beams, breath...).
 * Rendered every tick on each client, so they track the entity smoothly.
 */
public enum AttachedFxType {
	/** Spinning dome of wind. */
	WIND_SHIELD,
	/** Eight waving water tentacles. */
	OCTOPUS,
	/** Spinning ball of air under the feet. */
	AIR_SCOOTER,
	/** Rocks orbiting the body. */
	EARTH_ARMOR,
	/** Victim of bloodbending. data = caster entity id. */
	BLOODBENT,
	/** Glowing spirit body. */
	SPIRIT_FORM,
	/** Jets of flame from the feet. */
	FIRE_JET,
	/** Column of water under the feet. */
	WATER_SPOUT,
	/** Rising healing spiral. */
	HEALING,
	/** Energy gathering in front of the chest. data = color. */
	CHARGE_HANDS,
	/** Cone of blue fire along the look direction. data = color. */
	DRAGON_BREATH,
	/** Frost covering a frozen mob. */
	FROZEN,
	/** Four-color beam along the look direction. */
	ELEMENTAL_BEAM,
	/** Steel cable to an anchor point. anchor = target position. */
	METAL_CABLE,
	/** Beam of light to another entity. data = target entity id. */
	ENERGY_LINK,
	/** Four elemental rings orbiting the body. */
	ELEMENT_STORM,
	/** Wind gathering into a cannon in front of the hands. */
	AIR_CANNON_CHARGE;

	public static AttachedFxType byOrdinal(int ordinal) {
		AttachedFxType[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}
}
