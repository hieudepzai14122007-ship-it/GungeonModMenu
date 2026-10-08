package com.avatarbending.client;

import com.avatarbending.bending.Ability;
import com.avatarbending.bending.Element;
import com.avatarbending.network.ModPayloads.BenderSyncPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The local player's bending state as last sent by the server, plus which players are in the
 * Avatar State (for the glowing-eyes effect).
 */
public final class ClientBenderState {
	@Nullable
	public static Element primary;
	public static boolean avatar;
	@Nullable
	public static Element active;
	public static int ability;
	public static float chi = 100;
	public static int avatarStateTicks;
	public static int avatarStateCooldown;
	public static int suppressedTicks;
	public static int[] cooldowns = new int[0];
	/** The server asked to show the element chooser; opened once no other screen is in the way. */
	public static boolean chooserRequested;

	private static final Set<Integer> AVATAR_STATE_ENTITIES = new HashSet<>();

	private ClientBenderState() {
	}

	public static void apply(BenderSyncPayload payload) {
		primary = Element.byOrdinal(payload.primary());
		avatar = payload.avatar();
		active = Element.byOrdinal(payload.active());
		ability = payload.ability();
		chi = payload.chi();
		avatarStateTicks = payload.avatarStateTicks();
		avatarStateCooldown = payload.avatarStateCooldown();
		suppressedTicks = payload.suppressedTicks();
		cooldowns = payload.cooldowns();
	}

	public static boolean hasBending() {
		return primary != null || avatar;
	}

	public static List<Element> availableElements() {
		List<Element> list = new ArrayList<>();
		if (avatar) {
			list.addAll(Element.BENDABLE);
			list.add(Element.AVATAR);
		} else if (primary != null) {
			list.add(primary);
		}
		return list;
	}

	@Nullable
	public static Ability selectedAbility() {
		if (active == null) {
			return null;
		}
		List<Ability> list = Ability.forElement(active);
		return list.isEmpty() ? null : list.get(Math.floorMod(ability, list.size()));
	}

	/** Remaining cooldown of the active element's ability {@code index}, as a percentage. */
	public static int cooldownPercent(int index) {
		return index >= 0 && index < cooldowns.length ? cooldowns[index] : 0;
	}

	public static boolean isInAvatarState(int entityId) {
		return AVATAR_STATE_ENTITIES.contains(entityId);
	}

	public static void setAvatarVisual(int entityId, boolean active) {
		if (active) {
			AVATAR_STATE_ENTITIES.add(entityId);
		} else {
			AVATAR_STATE_ENTITIES.remove(entityId);
		}
	}

	public static void clear() {
		primary = null;
		avatar = false;
		active = null;
		ability = 0;
		chi = 100;
		avatarStateTicks = 0;
		avatarStateCooldown = 0;
		suppressedTicks = 0;
		cooldowns = new int[0];
		chooserRequested = false;
		AVATAR_STATE_ENTITIES.clear();
	}
}
