package com.avatarbending.client;

import com.avatarbending.client.render.AvatarEyesFeatureRenderer;
import com.avatarbending.client.render.BlockProjectileRenderer;
import com.avatarbending.client.screen.BendingMenuScreen;
import com.avatarbending.client.screen.ChooseElementScreen;
import com.avatarbending.entity.ModEntities;
import com.avatarbending.item.ModItems;
import com.avatarbending.network.ModPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;

public class AvatarBendingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KeyBinds.init();

		EntityRendererRegistry.register(ModEntities.AIR_BLAST, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.FIRE_BLAST, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.WATER_BLAST, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TORNADO, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TIDAL_WAVE, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.BOULDER, BlockProjectileRenderer::new);
		EntityRendererRegistry.register(ModEntities.ICE_SHARD, BlockProjectileRenderer::new);
		EntityRendererRegistry.register(ModEntities.METEOR, BlockProjectileRenderer::new);

		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new AvatarEyesFeatureRenderer(playerRenderer));
			}
		});

		HudRenderCallback.EVENT.register(BendingHud::render);

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.BenderSyncPayload.ID,
			(payload, context) -> ClientBenderState.apply(payload));
		// On join this arrives while "Loading terrain" is still showing, so it is opened from the tick handler.
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenChooserPayload.ID,
			(payload, context) -> ClientBenderState.chooserRequested = true);
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.AvatarVisualPayload.ID,
			(payload, context) -> ClientBenderState.setAvatarVisual(payload.entityId(), payload.active()));
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.SpiritPopPayload.ID, (payload, context) -> {
			MinecraftClient client = context.client();
			client.gameRenderer.showFloatingItem(new ItemStack(ModItems.AVATAR_SPIRIT));
			if (client.player != null) {
				client.particleManager.addEmitter(client.player, ParticleTypes.TOTEM_OF_UNDYING, 40);
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientBenderState.clear());

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		if (ClientBenderState.chooserRequested) {
			if (ClientBenderState.hasBending()) {
				ClientBenderState.chooserRequested = false;
			} else if (client.currentScreen == null || client.currentScreen instanceof BendingMenuScreen) {
				ClientBenderState.chooserRequested = false;
				client.setScreen(new ChooseElementScreen());
			}
		}
		while (KeyBinds.CAST.wasPressed()) {
			if (ClientBenderState.hasBending()) {
				ClientPlayNetworking.send(ModPayloads.CastPayload.INSTANCE);
			} else {
				client.setScreen(new ChooseElementScreen());
			}
		}
		while (KeyBinds.NEXT_ABILITY.wasPressed()) {
			ClientPlayNetworking.send(new ModPayloads.CycleAbilityPayload(Screen.hasShiftDown() ? -1 : 1));
		}
		while (KeyBinds.SWITCH_ELEMENT.wasPressed()) {
			ClientPlayNetworking.send(ModPayloads.CycleElementPayload.INSTANCE);
		}
		while (KeyBinds.AVATAR_STATE.wasPressed()) {
			ClientPlayNetworking.send(ModPayloads.AvatarStateKeyPayload.INSTANCE);
		}
		while (KeyBinds.MENU.wasPressed()) {
			client.setScreen(ClientBenderState.hasBending() ? new BendingMenuScreen() : new ChooseElementScreen());
		}
	}
}
