package com.avatarbending.bending;

import com.avatarbending.AvatarBending;
import com.avatarbending.ability.AbilityContext;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.network.ModPayloads.BenderSyncPayload;
import com.avatarbending.network.ModPayloads.OpenChooserPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side brain of the mod: validates casts, regenerates chi, applies passives and keeps
 * each client's HUD in sync.
 */
public final class BendingManager {
	public static final float CHI_REGEN_PER_TICK = 0.35f;

	private static final Map<UUID, BenderSyncPayload> LAST_SYNC = new HashMap<>();

	private BendingManager() {
	}

	public static BenderData get(PlayerEntity player) {
		return player.getAttachedOrCreate(AvatarBending.BENDER);
	}

	// ---------------------------------------------------------------- ticking

	public static void tickAll(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tick(player);
		}
	}

	public static void tick(ServerPlayerEntity player) {
		BenderData data = get(player);
		data.tickTimers();

		if (data.hasBending() && player.isAlive()) {
			if (data.inAvatarState()) {
				data.setChi(BenderData.MAX_CHI);
			} else {
				data.setChi(data.chi() + CHI_REGEN_PER_TICK);
			}
			if (player.age % 40 == 0) {
				applyPassives(player, data);
			}
		}

		AvatarState.tick(player, data);
		sync(player, false);
	}

	private static void applyPassives(ServerPlayerEntity player, BenderData data) {
		if (data.suppressedTicks() > 0) {
			return;
		}
		boolean air = data.canUse(Element.AIR);
		boolean water = data.canUse(Element.WATER);
		boolean earth = data.canUse(Element.EARTH);
		boolean fire = data.canUse(Element.FIRE);
		if (air) {
			passive(player, StatusEffects.SPEED, 0);
		}
		if (water && player.isTouchingWater()) {
			passive(player, StatusEffects.DOLPHINS_GRACE, 0);
			passive(player, StatusEffects.WATER_BREATHING, 0);
		}
		if (earth) {
			passive(player, StatusEffects.HASTE, 0);
		}
		if (fire) {
			passive(player, StatusEffects.FIRE_RESISTANCE, 0);
		}
	}

	private static void passive(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		// Never shorten or weaken a stronger effect from a potion or beacon.
		if (current != null && (current.getAmplifier() > amplifier || current.getDuration() > 100)) {
			return;
		}
		player.addStatusEffect(new StatusEffectInstance(effect, 100, amplifier, true, false, false));
	}

	// ---------------------------------------------------------------- casting

	/** Casts the player's selected ability, if they have the chi and it is off cooldown. */
	public static boolean tryCast(ServerPlayerEntity player) {
		BenderData data = get(player);
		Ability ability = data.selectedAbility();
		if (ability == null) {
			ModPayloads.send(player, OpenChooserPayload.INSTANCE);
			return false;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (data.suppressedTicks() > 0) {
			player.sendMessage(Text.translatable("message.avatarbending.suppressed", data.suppressedTicks() / 20 + 1)
				.formatted(Formatting.DARK_PURPLE), true);
			return false;
		}
		int cooldown = data.cooldown(ability);
		if (cooldown > 0) {
			player.sendMessage(Text.translatable("message.avatarbending.cooldown", ability.displayName(),
				String.format("%.1f", cooldown / 20f)).formatted(Formatting.GRAY), true);
			return false;
		}
		boolean free = data.inAvatarState() || player.getAbilities().creativeMode;
		if (!free && data.chi() < ability.chiCost()) {
			player.sendMessage(Text.translatable("message.avatarbending.no_chi").formatted(Formatting.RED), true);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
				SoundCategory.PLAYERS, 0.4f, 1.6f);
			return false;
		}
		if (!cast(player, ability)) {
			return false;
		}
		if (!free) {
			data.setChi(data.chi() - ability.chiCost());
		}
		int cd = data.inAvatarState() ? ability.cooldown() / 4 : ability.cooldown();
		data.setCooldown(ability, cd);
		sync(player, false);
		return true;
	}

	/**
	 * Runs an ability's effect with no chi or cooldown checks. Returns false if the ability failed
	 * (for example, nothing to target).
	 */
	public static boolean cast(ServerPlayerEntity player, Ability ability) {
		BenderData data = get(player);
		AbilityContext context = new AbilityContext(player, player.getServerWorld(), data.inAvatarState());
		try {
			ability.cast(context);
			return !context.failed();
		} catch (RuntimeException e) {
			AvatarBending.LOGGER.error("Ability {} failed for {}", ability.id(), player.getName().getString(), e);
			return false;
		}
	}

	// ---------------------------------------------------------------- selection

	public static void cycleAbility(ServerPlayerEntity player, int direction) {
		BenderData data = get(player);
		Element element = data.active();
		if (element == null) {
			return;
		}
		data.setAbilityIndex(Math.floorMod(data.abilityIndex() + direction, Ability.forElement(element).size()));
		Ability ability = data.selectedAbility();
		if (ability != null) {
			player.sendMessage(Text.empty().append(ability.displayName().copy().formatted(element.formatting(), Formatting.BOLD)), true);
		}
		player.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.3f, 1.8f);
		sync(player, false);
	}

	public static void cycleElement(ServerPlayerEntity player) {
		BenderData data = get(player);
		List<Element> elements = data.availableElements();
		if (elements.size() < 2) {
			if (data.hasBending()) {
				player.sendMessage(Text.translatable("message.avatarbending.only_avatar_switch").formatted(Formatting.GRAY), true);
			}
			return;
		}
		Element current = data.active();
		int index = elements.indexOf(current);
		Element next = elements.get((index + 1) % elements.size());
		data.setActive(next);
		data.setAbilityIndex(0);
		player.sendMessage(Text.translatable("message.avatarbending.element_switched", next.displayName().copy().formatted(Formatting.BOLD)), true);
		player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f);
		sync(player, false);
	}

	public static void selectAbility(ServerPlayerEntity player, Element element, int index) {
		BenderData data = get(player);
		if (!data.canUse(element)) {
			return;
		}
		List<Ability> abilities = Ability.forElement(element);
		if (index < 0 || index >= abilities.size()) {
			return;
		}
		data.setActive(element);
		data.setAbilityIndex(index);
		sync(player, false);
	}

	// ---------------------------------------------------------------- progression

	/**
	 * Gives the player their first element. Players can only choose once unless {@code force} is set
	 * (used by operator commands).
	 */
	public static boolean chooseElement(ServerPlayerEntity player, Element element, boolean force) {
		BenderData data = get(player);
		if (!Element.BENDABLE.contains(element)) {
			return false;
		}
		if (data.primary() != null && !force) {
			player.sendMessage(Text.translatable("message.avatarbending.already_chosen").formatted(Formatting.RED), false);
			return false;
		}
		data.setPrimary(element);
		data.setActive(element);
		data.setAbilityIndex(0);
		data.setChi(BenderData.MAX_CHI);
		data.clearCooldowns();

		player.sendMessage(Text.translatable("message.avatarbending.chosen", element.displayName().copy().formatted(Formatting.BOLD)), false);
		player.sendMessage(Text.translatable("message.avatarbending.controls").formatted(Formatting.GRAY), false);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1f, 1.2f);
		AbilityContext context = new AbilityContext(player, player.getServerWorld(), false);
		context.elementBurst(element, player.getPos().add(0, 1, 0), 60);

		unlockSpiritRecipe(player);
		AvatarBending.grantAdvancement(player, "bender");
		sync(player, true);
		return true;
	}

	public static void unlockSpiritRecipe(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		Optional<RecipeEntry<?>> recipe = server.getRecipeManager().get(AvatarBending.id("avatar_spirit"));
		recipe.ifPresent(entry -> player.unlockRecipes(List.of(entry)));
	}

	public static void reset(ServerPlayerEntity player) {
		BenderData data = get(player);
		if (data.inAvatarState()) {
			AvatarState.exit(player, data, false);
		}
		data.reset();
		sync(player, true);
	}

	// ---------------------------------------------------------------- Avatar State

	public static void toggleAvatarState(ServerPlayerEntity player) {
		BenderData data = get(player);
		if (!data.isAvatar()) {
			player.sendMessage(Text.translatable("message.avatarbending.not_avatar").formatted(Formatting.GRAY), true);
			return;
		}
		if (data.inAvatarState()) {
			AvatarState.exit(player, data, true);
			return;
		}
		if (data.avatarStateCooldown() > 0 && !player.getAbilities().creativeMode) {
			player.sendMessage(Text.translatable("message.avatarbending.avatar_state_cooldown",
				data.avatarStateCooldown() / 20 + 1).formatted(Formatting.GRAY), true);
			return;
		}
		AvatarState.enter(player, data);
	}

	// ---------------------------------------------------------------- sync

	public static void onJoin(ServerPlayerEntity player) {
		LAST_SYNC.remove(player.getUuid());
		BenderData data = get(player);
		if (!data.inAvatarState() && data.grantedFlight()) {
			AvatarState.revokeFlight(player, data);
		}
		sync(player, true);
		AvatarState.broadcastVisual(player, data.inAvatarState());
		if (!data.hasBending()) {
			ModPayloads.send(player, OpenChooserPayload.INSTANCE);
		} else {
			unlockSpiritRecipe(player);
		}
	}

	public static void onLeave(ServerPlayerEntity player) {
		LAST_SYNC.remove(player.getUuid());
	}

	public static void sync(ServerPlayerEntity player, boolean force) {
		if (player.networkHandler == null || !ServerPlayNetworking.canSend(player, BenderSyncPayload.ID)) {
			return;
		}
		BenderData data = get(player);
		Element active = data.active();
		int[] cooldowns;
		if (active == null) {
			cooldowns = new int[0];
		} else {
			List<Ability> abilities = Ability.forElement(active);
			cooldowns = new int[abilities.size()];
			for (int i = 0; i < abilities.size(); i++) {
				Ability ability = abilities.get(i);
				int remaining = data.cooldown(ability);
				cooldowns[i] = remaining <= 0 ? 0 : Math.max(1, Math.round(100f * remaining / Math.max(1, ability.cooldown())));
			}
		}
		BenderSyncPayload payload = new BenderSyncPayload(
			data.primary() == null ? -1 : data.primary().ordinal(),
			data.isAvatar(),
			active == null ? -1 : active.ordinal(),
			data.abilityIndex(),
			Math.round(data.chi() * 10f) / 10f,
			data.avatarStateTicks(),
			data.avatarStateCooldown(),
			data.suppressedTicks(),
			cooldowns);
		BenderSyncPayload last = LAST_SYNC.get(player.getUuid());
		if (!force && payload.sameAs(last)) {
			return;
		}
		LAST_SYNC.put(player.getUuid(), payload);
		ServerPlayNetworking.send(player, payload);
	}
}
