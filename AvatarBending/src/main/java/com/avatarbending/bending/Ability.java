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
 * Every bending spell: ten per element plus ten Avatar spells. Order inside an element is the
 * order players cycle through with the "next spell" key. Cooldowns are in ticks (20 = 1 second).
 */
public enum Ability {
	// Air
	AIR_BLAST(Element.AIR, "air_blast", 8, 10, true, AirAbilities::airBlast),
	AIR_BARRAGE(Element.AIR, "air_barrage", 20, 40, true, AirAbilities::airBarrage),
	AIR_BLADE(Element.AIR, "air_blade", 14, 30, true, AirAbilities::airBlade),
	AIR_LEAP(Element.AIR, "air_leap", 15, 40, false, AirAbilities::airLeap),
	AIR_SCOOTER(Element.AIR, "air_scooter", 20, 200, false, AirAbilities::airScooter),
	WIND_SHIELD(Element.AIR, "wind_shield", 25, 300, false, AirAbilities::windShield),
	VACUUM(Element.AIR, "vacuum", 30, 240, true, AirAbilities::vacuum),
	AIR_SPHERE(Element.AIR, "air_sphere", 25, 100, true, AirAbilities::airSphere),
	TORNADO(Element.AIR, "tornado", 40, 240, true, AirAbilities::tornado),
	AIR_CANNON(Element.AIR, "air_cannon", 50, 300, true, AirAbilities::airCannon),

	// Water
	WATER_WHIP(Element.WATER, "water_whip", 10, 12, true, WaterAbilities::waterWhip),
	ICE_SHARDS(Element.WATER, "ice_shards", 18, 30, true, WaterAbilities::iceShards),
	ICE_WAVE(Element.WATER, "ice_wave", 30, 120, true, WaterAbilities::iceWave),
	GLACIER_BOMB(Element.WATER, "glacier_bomb", 35, 200, true, WaterAbilities::glacierBomb),
	HEALING_WATERS(Element.WATER, "healing_waters", 35, 300, false, WaterAbilities::healingWaters),
	WATER_SPOUT(Element.WATER, "water_spout", 25, 200, false, WaterAbilities::waterSpout),
	OCTOPUS_FORM(Element.WATER, "octopus_form", 40, 400, true, WaterAbilities::octopusForm),
	TIDAL_WAVE(Element.WATER, "tidal_wave", 45, 240, true, WaterAbilities::tidalWave),
	BLOODBENDING(Element.WATER, "bloodbending", 50, 500, true, WaterAbilities::bloodbending),
	MAELSTROM(Element.WATER, "maelstrom", 60, 400, true, WaterAbilities::maelstrom),

	// Earth
	BOULDER_TOSS(Element.EARTH, "boulder_toss", 12, 20, true, EarthAbilities::boulderToss),
	ROCK_BARRAGE(Element.EARTH, "rock_barrage", 25, 80, true, EarthAbilities::rockBarrage),
	EARTH_SPIKES(Element.EARTH, "earth_spikes", 25, 80, true, EarthAbilities::earthSpikes),
	EARTH_WALL(Element.EARTH, "earth_wall", 20, 80, false, EarthAbilities::earthWall),
	EARTH_PILLAR(Element.EARTH, "earth_pillar", 15, 60, false, EarthAbilities::earthPillar),
	METAL_CABLES(Element.EARTH, "metal_cables", 15, 40, true, EarthAbilities::metalCables),
	EARTH_ARMOR(Element.EARTH, "earth_armor", 30, 400, false, EarthAbilities::earthArmor),
	SEISMIC_SLAM(Element.EARTH, "seismic_slam", 40, 200, true, EarthAbilities::seismicSlam),
	LAVABENDING(Element.EARTH, "lavabending", 45, 300, true, EarthAbilities::lavabending),
	FISSURE(Element.EARTH, "fissure", 60, 400, true, EarthAbilities::fissure),

	// Fire
	FIRE_BLAST(Element.FIRE, "fire_blast", 8, 8, true, FireAbilities::fireBlast),
	FIRE_FISTS(Element.FIRE, "fire_fists", 18, 30, true, FireAbilities::fireFists),
	FIRE_WHIP(Element.FIRE, "fire_whip", 20, 50, true, FireAbilities::fireWhip),
	FIRE_JET(Element.FIRE, "fire_jet", 18, 30, false, FireAbilities::fireJet),
	INFERNO_RING(Element.FIRE, "inferno_ring", 35, 160, true, FireAbilities::infernoRing),
	DRAGON_BREATH(Element.FIRE, "dragon_breath", 30, 200, true, FireAbilities::dragonBreath),
	FIRE_DRAGON(Element.FIRE, "fire_dragon", 45, 240, true, FireAbilities::fireDragon),
	COMBUSTION(Element.FIRE, "combustion", 45, 200, true, FireAbilities::combustion),
	LIGHTNING(Element.FIRE, "lightning", 60, 300, true, FireAbilities::lightning),
	FIRESTORM(Element.FIRE, "firestorm", 70, 400, true, FireAbilities::firestorm),

	// Avatar only
	ELEMENTAL_STORM(Element.AVATAR, "elemental_storm", 70, 600, true, AvatarAbilities::elementalStorm),
	ENERGYBENDING(Element.AVATAR, "energybending", 60, 400, true, AvatarAbilities::energybending),
	METEOR_STRIKE(Element.AVATAR, "meteor_strike", 80, 600, true, AvatarAbilities::meteorStrike),
	ELEMENTAL_BEAM(Element.AVATAR, "elemental_beam", 65, 400, true, AvatarAbilities::elementalBeam),
	FIRE_TORNADO(Element.AVATAR, "fire_tornado", 60, 400, true, AvatarAbilities::fireTornado),
	BLIZZARD(Element.AVATAR, "blizzard", 60, 500, true, AvatarAbilities::blizzard),
	VOLCANO(Element.AVATAR, "volcano", 75, 600, true, AvatarAbilities::volcano),
	SPIRIT_FORM(Element.AVATAR, "spirit_form", 40, 600, false, AvatarAbilities::spiritForm),
	RAAVAS_LIGHT(Element.AVATAR, "raavas_light", 50, 800, false, AvatarAbilities::raavasLight),
	AVATARS_WRATH(Element.AVATAR, "avatars_wrath", 100, 1200, true, true, AvatarAbilities::avatarsWrath);

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
	private final boolean avatarStateOnly;
	private final Caster caster;

	Ability(Element element, String id, int chiCost, int cooldown, boolean offensive, Caster caster) {
		this(element, id, chiCost, cooldown, offensive, false, caster);
	}

	Ability(Element element, String id, int chiCost, int cooldown, boolean offensive, boolean avatarStateOnly, Caster caster) {
		this.element = element;
		this.id = id;
		this.chiCost = chiCost;
		this.cooldown = cooldown;
		this.offensive = offensive;
		this.avatarStateOnly = avatarStateOnly;
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

	/** True for spells that can only be cast while in the Avatar State. */
	public boolean avatarStateOnly() {
		return avatarStateOnly;
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
