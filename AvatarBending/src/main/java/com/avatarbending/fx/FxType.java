package com.avatarbending.fx;

/**
 * Positional visual effects. The server sends one small packet and every nearby client renders
 * the full effect locally (hundreds of particles, screen shake and flashes).
 * <p>Packet fields: position, a vector (direction, axis or end offset), scale, color, data.
 */
public enum FxType {
	/** Glowing burst of light and sparks. scale = size. */
	BURST,
	/** Expanding ring. vec = ring axis, scale = radius, data = lifetime ticks. */
	RING,
	/** Ring racing along the ground with dust and screen shake. scale = radius. */
	GROUND_SHOCKWAVE,
	/** Straight energy beam. vec = end - start, scale = width, data = lifetime ticks. */
	BEAM,
	/** Branching lightning bolt with flash and shake. vec = end - start, scale = intensity. */
	LIGHTNING,
	/** Element-themed explosion. scale = size, data = element ordinal (or -1). */
	EXPLOSION,
	/** Energy gathering into a point. scale = radius, data = duration ticks. */
	CHARGE,
	/** Pillar of light reaching into the sky. scale = height, data = lifetime ticks. */
	PILLAR,
	/** Glowing spiral rising around a point. scale = radius, data = height in tenths. */
	SPIRAL,
	/** Gust of wind. vec = direction, scale = size. */
	WIND_BURST,
	/** Water explosion. scale = size. */
	SPLASH,
	/** Ice explosion. scale = size. */
	FROST,
	/** Flying rock debris. scale = size, data = block state id. */
	DEBRIS,
	/** Burst of fire. scale = size. */
	FLAMES,
	/** Rotating magic circle on the ground. scale = radius, data = duration ticks. */
	SIGIL,
	/** Screen flash for nearby players. scale = radius. */
	FLASH,
	/** Camera shake for nearby players. scale = intensity, data = radius. */
	SHAKE,
	/** Dotted glowing line. vec = end - start, scale = density. */
	LINE,
	/** Crescent slash arc. vec = forward direction, scale = radius. */
	SLASH,
	/** Spray of particles in a cone. vec = direction * length, scale = spread. data = element ordinal. */
	CONE,
	/** Ring of flames racing outwards along the ground. scale = radius, data = ticks to reach full size. */
	FIRE_RING;

	public static FxType byOrdinal(int ordinal) {
		FxType[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}
}
