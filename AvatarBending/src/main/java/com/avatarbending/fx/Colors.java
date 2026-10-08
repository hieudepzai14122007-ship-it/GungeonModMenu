package com.avatarbending.fx;

/** Shared RGB palette for effects. */
public final class Colors {
	public static final int WHITE = 0xFFFFFF;
	public static final int AIR = 0xDDEEFF;
	public static final int AIR_ACCENT = 0xFFC870;
	public static final int WATER = 0x3FA9FF;
	public static final int WATER_DEEP = 0x1D63E6;
	public static final int ICE = 0xB8F4FF;
	public static final int EARTH = 0x9A6B3C;
	public static final int EARTH_GREEN = 0x5DBB3F;
	public static final int LAVA = 0xFF5A0A;
	public static final int FIRE = 0xFF7A1A;
	public static final int FIRE_CORE = 0xFFD86A;
	public static final int BLUE_FIRE = 0x4FA8FF;
	public static final int LIGHTNING = 0xA8D8FF;
	public static final int AVATAR = 0x7DF9FF;
	public static final int SPIRIT = 0xFFF4C8;
	public static final int BLOOD = 0xA0101A;
	public static final int METAL = 0xB8C0C8;
	public static final int HEAL = 0x7DFFC8;

	private Colors() {
	}

	public static int of(com.avatarbending.bending.Element element) {
		return switch (element) {
			case AIR -> AIR;
			case WATER -> WATER;
			case EARTH -> EARTH;
			case FIRE -> FIRE;
			case AVATAR -> AVATAR;
		};
	}
}
