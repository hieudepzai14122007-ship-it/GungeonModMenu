package com.avatarbending.bending;

import com.avatarbending.ability.AbilityContext;
import com.avatarbending.ability.AirAbilities;
import com.avatarbending.ability.AvatarAbilities;
import com.avatarbending.ability.EarthAbilities;
import com.avatarbending.ability.FireAbilities;
import com.avatarbending.ability.WaterAbilities;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every bending spell. Order inside an element is the order players cycle through with the
 * "next ability" key. Cooldowns are in ticks (20 ticks = 1 second).
 */
public enum Ability {
	// Air
	AIR_BLAST(Element.AIR, "air_blast", 8, 10, true, AirAbilities::airBlast),
	AIR_BARRAGE(Element.AIR, "air_barrage", 20, 40, true, AirAbilities::airBarrage),
	AIR_LEAP(Element.AIR, "air_leap", 15, 40, false, AirAbilities::airLeap),
	AIR_SCOOTER(Element.AIR, "air_scooter", 20, 200, false, AirAbilities::airScooter),
	TORNADO(Element.AIR, "tornado", 40, 240, true, AirAbilities::tornado),
	AIR_SPHERE(Element.AIR, "air_sphere", 25, 100, true, AirAbilities::airSphere),

	// Water
	WATER_WHIP(Element.WATER, "water_whip", 10, 12, true, WaterAbilities::waterWhip),
	ICE_SHARDS(Element.WATER, "ice_shards", 18, 30, true, WaterAbilities::iceShards),
	ICE_WAVE(Element.WATER, "ice_wave", 30, 120, true, WaterAbilities::iceWave),
	HEALING_WATERS(Element.WATER, "healing_waters", 35, 300, false, WaterAbilities::healingWaters),
	WATER_SPOUT(Element.WATER, "water_spout", 25, 200, false, WaterAbilities::waterSpout),
	TIDAL_WAVE(Element.WATER, "tidal_wave", 45, 240, true, WaterAbilities::tidalWave),

	// Earth
	BOULDER_TOSS(Element.EARTH, "boulder_toss", 12, 20, true, EarthAbilities::boulderToss),
	EARTH_SPIKES(Element.EARTH, "earth_spikes", 25, 80, true, EarthAbilities::earthSpikes),
	EARTH_WALL(Element.EARTH, "earth_wall", 20, 80, false, EarthAbilities::earthWall),
	EARTH_PILLAR(Element.EARTH, "earth_pillar", 15, 60, false, EarthAbilities::earthPillar),
	SEISMIC_SLAM(Element.EARTH, "seismic_slam", 40, 200, true, EarthAbilities::seismicSlam),
	EARTH_ARMOR(Element.EARTH, "earth_armor", 30, 400, false, EarthAbilities::earthArmor),

	// Fire
	FIRE_BLAST(Element.FIRE, "fire_blast", 8, 8, true, FireAbilities::fireBlast),
	FIRE_FISTS(Element.FIRE, "fire_fists", 18, 30, true, FireAbilities::fireFists),
	FIRE_JET(Element.FIRE, "fire_jet", 18, 30, false, FireAbilities::fireJet),
	INFERNO_RING(Element.FIRE, "inferno_ring", 35, 160, true, FireAbilities::infernoRing),
	COMBUSTION(Element.FIRE, "combustion", 45, 200, true, FireAbilities::combustion),
	LIGHTNING(Element.FIRE, "lightning", 60, 300, true, FireAbilities::lightning),

	// Avatar only
	ELEMENTAL_STORM(Element.AVATAR, "elemental_storm", 70, 600, true, AvatarAbilities::elementalStorm),
	ENERGYBENDING(Element.AVATAR, "energybending", 60, 400, true, AvatarAbilities::energybending),
	METEOR_STRIKE(Element.AVATAR, "meteor_strike", 80, 600, true, AvatarAbilities::meteorStrike);

	@FunctionalInterface
	public interface Caster {
		void cast(AbilityContext context);
	}

	private static final Map<Element, List<Ability>> BY_ELEMENT = new EnumMap<>(Element.class);

	static {
		for (Element element : Element.values()) {
			BY_ELEMENT.put(element, new ArrayList<>());
		}
		for (Ability ability : values()) {
			BY_ELEMENT.get(ability.element).add(ability);
		}
		BY_ELEMENT.replaceAll((element, list) -> Collections.unmodifiableList(list));
	}

	private final Element element;
	private final String id;
	private final int chiCost;
	private final int cooldown;
	private final boolean offensive;
	private final Caster caster;

	Ability(Element element, String id, int chiCost, int cooldown, boolean offensive, Caster caster) {
		this.element = element;
		this.id = id;
		this.chiCost = chiCost;
		this.cooldown = cooldown;
		this.offensive = offensive;
		this.caster = caster;
	}

	public Element element() {
		return element;
	}

	public String id() {
		return id;
	}

	public int chiCost() {
		return chiCost;
	}

	public int cooldown() {
		return cooldown;
	}

	/** True for spells that are meant to hurt something in front of the caster. */
	public boolean offensive() {
		return offensive;
	}

	public void cast(AbilityContext context) {
		caster.cast(context);
	}

	public Text displayName() {
		return Text.translatable("ability.avatarbending." + id);
	}

	public Text description() {
		return Text.translatable("ability.avatarbending." + id + ".desc");
	}

	public static List<Ability> forElement(Element element) {
		return BY_ELEMENT.get(element);
	}

	public static Ability byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
