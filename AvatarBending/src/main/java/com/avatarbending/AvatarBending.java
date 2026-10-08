package com.avatarbending;

import com.avatarbending.bending.AvatarState;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.command.BendingCommand;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.effect.TempBlocks;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.fx.ModParticles;
import com.avatarbending.item.ModItems;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvatarBending implements ModInitializer {
	public static final String MOD_ID = "avatarbending";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@SuppressWarnings("UnstableApiUsage")
	public static final AttachmentType<BenderData> BENDER = AttachmentRegistry.<BenderData>builder()
		.persistent(BenderData.CODEC)
		.copyOnDeath()
		.initializer(BenderData::new)
		.buildAndRegister(id("bender"));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static RegistryKey<DamageType> damageType(Element element) {
		String name = switch (element) {
			case AIR -> "airbending";
			case WATER -> "waterbending";
			case EARTH -> "earthbending";
			case FIRE -> "firebending";
			case AVATAR -> "avatar";
		};
		return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id(name));
	}

	public static void grantAdvancement(ServerPlayerEntity player, String name) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		AdvancementEntry advancement = server.getAdvancementLoader().get(id(name));
		if (advancement != null) {
			player.getAdvancementTracker().grantCriterion(advancement, "granted");
		}
	}

	@Override
	public void onInitialize() {
		ModSounds.register();
		ModParticles.register();
		ModItems.register();
		ModEntities.register();
		ModPayloads.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BendingCommand.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BendingManager.tickAll(server);
			EffectScheduler.tick();
			TempBlocks.tick(server);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BendingManager.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BendingManager.onLeave(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			BenderData data = BendingManager.get(newPlayer);
			data.setAvatarStateTicks(0);
			data.setGrantedFlight(false);
			data.setChi(BenderData.MAX_CHI);
			BendingManager.sync(newPlayer, true);
			AvatarState.broadcastVisual(newPlayer, false);
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			BenderData data = BendingManager.get(player);
			if (source.isIn(DamageTypeTags.IS_FALL) && (data.fallImmune() || (data.canUse(Element.AIR) && data.suppressedTicks() <= 0))) {
				return false;
			}
			// The Avatar State awakens when the Avatar is in mortal danger.
			if (data.isAvatar() && !data.inAvatarState() && data.avatarStateCooldown() <= 0
				&& player.getHealth() - amount < AvatarState.AUTO_TRIGGER_HEALTH && player.getHealth() > 0) {
				AvatarState.enter(player, data);
			}
			return true;
		});

		EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
			if (tracked instanceof ServerPlayerEntity player && BendingManager.get(player).inAvatarState()) {
				ModPayloads.send(viewer, new ModPayloads.AvatarVisualPayload(player.getId(), true));
			}
		});

		// Breaking a bending block just makes it vanish (no free blocks).
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient && TempBlocks.isTemp(world, pos)) {
				TempBlocks.restore(world, pos);
				return false;
			}
			return true;
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TempBlocks.restoreAll();
			EffectScheduler.clear();
		});

		LOGGER.info("Avatar Bending loaded: Water. Earth. Fire. Air.");
	}
}
