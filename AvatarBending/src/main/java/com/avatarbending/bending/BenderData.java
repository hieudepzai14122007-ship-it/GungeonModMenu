package com.avatarbending.bending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player bending state, stored as a Fabric data attachment so it survives saving and death.
 * Cooldowns and other short timers are transient and reset when the player logs out.
 */
public final class BenderData {
	public static final float MAX_CHI = 100f;

	public static final Codec<BenderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("primary", "").forGetter(d -> d.primary == null ? "" : d.primary.id()),
		Codec.BOOL.optionalFieldOf("avatar", false).forGetter(d -> d.avatar),
		Codec.STRING.optionalFieldOf("active", "").forGetter(d -> d.active == null ? "" : d.active.id()),
		Codec.INT.optionalFieldOf("ability", 0).forGetter(d -> d.abilityIndex),
		Codec.FLOAT.optionalFieldOf("chi", MAX_CHI).forGetter(d -> d.chi),
		Codec.INT.optionalFieldOf("avatar_state", 0).forGetter(d -> d.avatarStateTicks),
		Codec.INT.optionalFieldOf("avatar_cooldown", 0).forGetter(d -> d.avatarStateCooldown),
		Codec.BOOL.optionalFieldOf("granted_flight", false).forGetter(d -> d.grantedFlight)
	).apply(instance, BenderData::new));

	@Nullable
	private Element primary;
	private boolean avatar;
	@Nullable
	private Element active;
	private int abilityIndex;
	private float chi = MAX_CHI;
	private int avatarStateTicks;
	private int avatarStateCooldown;
	private boolean grantedFlight;

	// Transient state.
	private final Map<Ability, Integer> cooldowns = new EnumMap<>(Ability.class);
	private int fallImmuneTicks;
	private int suppressedTicks;
	private int spiritTicks;

	public BenderData() {
	}

	private BenderData(String primary, boolean avatar, String active, int abilityIndex, float chi,
		int avatarStateTicks, int avatarStateCooldown, boolean grantedFlight) {
		this.primary = Element.byId(primary);
		this.avatar = avatar;
		this.active = Element.byId(active);
		this.abilityIndex = abilityIndex;
		this.chi = chi;
		this.avatarStateTicks = avatarStateTicks;
		this.avatarStateCooldown = avatarStateCooldown;
		this.grantedFlight = grantedFlight;
		if (this.primary == Element.AVATAR) {
			this.primary = null;
		}
		if (this.active == null) {
			this.active = this.primary;
		}
	}

	public boolean hasBending() {
		return primary != null || avatar;
	}

	@Nullable
	public Element primary() {
		return primary;
	}

	public void setPrimary(@Nullable Element primary) {
		this.primary = primary;
	}

	public boolean isAvatar() {
		return avatar;
	}

	public void setAvatar(boolean avatar) {
		this.avatar = avatar;
	}

	/** Elements (and the Avatar spell tab) this player can use right now. */
	public List<Element> availableElements() {
		List<Element> list = new ArrayList<>();
		if (avatar) {
			list.addAll(Element.BENDABLE);
			list.add(Element.AVATAR);
		} else if (primary != null) {
			list.add(primary);
		}
		return list;
	}

	public boolean canUse(Element element) {
		return availableElements().contains(element);
	}

	@Nullable
	public Element active() {
		if (active != null && canUse(active)) {
			return active;
		}
		return primary != null ? primary : (avatar ? Element.AIR : null);
	}

	public void setActive(Element active) {
		this.active = active;
	}

	public int abilityIndex() {
		Element element = active();
		if (element == null) {
			return 0;
		}
		int size = Ability.forElement(element).size();
		return Math.floorMod(abilityIndex, size);
	}

	public void setAbilityIndex(int abilityIndex) {
		this.abilityIndex = abilityIndex;
	}

	@Nullable
	public Ability selectedAbility() {
		Element element = active();
		return element == null ? null : Ability.forElement(element).get(abilityIndex());
	}

	public float chi() {
		return chi;
	}

	public void setChi(float chi) {
		this.chi = Math.max(0f, Math.min(MAX_CHI, chi));
	}

	public int avatarStateTicks() {
		return avatarStateTicks;
	}

	public void setAvatarStateTicks(int ticks) {
		this.avatarStateTicks = Math.max(0, ticks);
	}

	public boolean inAvatarState() {
		return avatarStateTicks > 0;
	}

	public int avatarStateCooldown() {
		return avatarStateCooldown;
	}

	public void setAvatarStateCooldown(int ticks) {
		this.avatarStateCooldown = Math.max(0, ticks);
	}

	public boolean grantedFlight() {
		return grantedFlight;
	}

	public void setGrantedFlight(boolean grantedFlight) {
		this.grantedFlight = grantedFlight;
	}

	public int cooldown(Ability ability) {
		return cooldowns.getOrDefault(ability, 0);
	}

	public void setCooldown(Ability ability, int ticks) {
		if (ticks <= 0) {
			cooldowns.remove(ability);
		} else {
			cooldowns.put(ability, ticks);
		}
	}

	public void clearCooldowns() {
		cooldowns.clear();
	}

	public void tickTimers() {
		cooldowns.replaceAll((ability, ticks) -> ticks - 1);
		cooldowns.values().removeIf(ticks -> ticks <= 0);
		if (fallImmuneTicks > 0) {
			fallImmuneTicks--;
		}
		if (suppressedTicks > 0) {
			suppressedTicks--;
		}
		if (spiritTicks > 0) {
			spiritTicks--;
		}
		if (avatarStateCooldown > 0 && avatarStateTicks <= 0) {
			avatarStateCooldown--;
		}
	}

	public boolean fallImmune() {
		return fallImmuneTicks > 0;
	}

	public void grantFallImmunity(int ticks) {
		fallImmuneTicks = Math.max(fallImmuneTicks, ticks);
	}

	/** Ticks left during which this player's bending is blocked (by an enemy Avatar's energybending). */
	public int suppressedTicks() {
		return suppressedTicks;
	}

	public void setSuppressedTicks(int ticks) {
		suppressedTicks = Math.max(0, ticks);
	}

	/** Ticks left in Spirit Form (flight is granted while this is above zero). */
	public int spiritTicks() {
		return spiritTicks;
	}

	public void setSpiritTicks(int ticks) {
		spiritTicks = Math.max(0, ticks);
	}

	/** True while something (Avatar State, Spirit Form) should let this player fly. */
	public boolean wantsFlight() {
		return avatarStateTicks > 0 || spiritTicks > 0;
	}

	public void reset() {
		primary = null;
		avatar = false;
		active = null;
		abilityIndex = 0;
		chi = MAX_CHI;
		avatarStateTicks = 0;
		avatarStateCooldown = 0;
		cooldowns.clear();
		suppressedTicks = 0;
		spiritTicks = 0;
	}
}
