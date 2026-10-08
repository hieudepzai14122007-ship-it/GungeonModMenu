package com.avatarbending.bending;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

import java.util.List;

/**
 * The four bendable elements, plus {@link #AVATAR} which holds the Avatar-only spells.
 */
public enum Element {
	AIR("air", 0xDCEBFF, Formatting.WHITE),
	WATER("water", 0x2E9BFF, Formatting.AQUA),
	EARTH("earth", 0x5DBB3F, Formatting.GREEN),
	FIRE("fire", 0xFF5A1F, Formatting.RED),
	AVATAR("avatar", 0x7DF9FF, Formatting.LIGHT_PURPLE);

	/** The elements a player can choose as their first element. */
	public static final List<Element> BENDABLE = List.of(AIR, WATER, EARTH, FIRE);

	private final String id;
	private final int color;
	private final Formatting formatting;

	Element(String id, int color, Formatting formatting) {
		this.id = id;
		this.color = color;
		this.formatting = formatting;
	}

	public String id() {
		return id;
	}

	/** RGB color used for the HUD, the chi bar and dust particles. */
	public int color() {
		return color;
	}

	public Formatting formatting() {
		return formatting;
	}

	public Vector3f colorVector() {
		return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
	}

	public String translationKey() {
		return "element.avatarbending." + id;
	}

	public Text displayName() {
		return Text.translatable(translationKey()).formatted(formatting);
	}

	public Text description() {
		return Text.translatable(translationKey() + ".desc");
	}

	public static Element byId(String id) {
		for (Element element : values()) {
			if (element.id.equals(id)) {
				return element;
			}
		}
		return null;
	}

	public static Element byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
