package com.avatarbending.network;

import com.avatarbending.AvatarBending;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * All custom packets. Client-to-server packets only carry key presses; the server validates everything.
 */
public final class ModPayloads {
	private ModPayloads() {
	}

	private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
		return new CustomPayload.Id<>(AvatarBending.id(path));
	}

	// ---------------------------------------------------------------- client -> server

	public record CastPayload() implements CustomPayload {
		public static final CastPayload INSTANCE = new CastPayload();
		public static final Id<CastPayload> ID = id("cast");
		public static final PacketCodec<RegistryByteBuf, CastPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record CycleAbilityPayload(int direction) implements CustomPayload {
		public static final Id<CycleAbilityPayload> ID = id("cycle_ability");
		public static final PacketCodec<RegistryByteBuf, CycleAbilityPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.VAR_INT, CycleAbilityPayload::direction, CycleAbilityPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record CycleElementPayload() implements CustomPayload {
		public static final CycleElementPayload INSTANCE = new CycleElementPayload();
		public static final Id<CycleElementPayload> ID = id("cycle_element");
		public static final PacketCodec<RegistryByteBuf, CycleElementPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record AvatarStateKeyPayload() implements CustomPayload {
		public static final AvatarStateKeyPayload INSTANCE = new AvatarStateKeyPayload();
		public static final Id<AvatarStateKeyPayload> ID = id("avatar_state_key");
		public static final PacketCodec<RegistryByteBuf, AvatarStateKeyPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ChooseElementPayload(int element) implements CustomPayload {
		public static final Id<ChooseElementPayload> ID = id("choose_element");
		public static final PacketCodec<RegistryByteBuf, ChooseElementPayload> CODEC =
			PacketCodec.tuple(PacketCodecs.VAR_INT, ChooseElementPayload::element, ChooseElementPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record SelectAbilityPayload(int element, int index) implements CustomPayload {
		public static final Id<SelectAbilityPayload> ID = id("select_ability");
		public static final PacketCodec<RegistryByteBuf, SelectAbilityPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SelectAbilityPayload::element,
			PacketCodecs.VAR_INT, SelectAbilityPayload::index,
			SelectAbilityPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ---------------------------------------------------------------- server -> client

	/**
	 * Full HUD state of the receiving player. {@code cooldowns} holds the remaining cooldown of each
	 * ability of the active element as a percentage (0 = ready).
	 */
	public record BenderSyncPayload(int primary, boolean avatar, int active, int ability, float chi,
		int avatarStateTicks, int avatarStateCooldown, int suppressedTicks, int[] cooldowns) implements CustomPayload {
		public static final Id<BenderSyncPayload> ID = id("bender_sync");
		public static final PacketCodec<RegistryByteBuf, BenderSyncPayload> CODEC =
			CustomPayload.codecOf(BenderSyncPayload::write, BenderSyncPayload::read);

		private void write(PacketByteBuf buf) {
			buf.writeVarInt(primary);
			buf.writeBoolean(avatar);
			buf.writeVarInt(active);
			buf.writeVarInt(ability);
			buf.writeFloat(chi);
			buf.writeVarInt(avatarStateTicks);
			buf.writeVarInt(avatarStateCooldown);
			buf.writeVarInt(suppressedTicks);
			buf.writeIntArray(cooldowns);
		}

		private static BenderSyncPayload read(PacketByteBuf buf) {
			return new BenderSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
				buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readIntArray());
		}

		/** Value comparison (records compare arrays by reference). */
		public boolean sameAs(BenderSyncPayload other) {
			return other != null && primary == other.primary && avatar == other.avatar && active == other.active
				&& ability == other.ability && Float.compare(chi, other.chi) == 0
				&& avatarStateTicks == other.avatarStateTicks && avatarStateCooldown == other.avatarStateCooldown
				&& suppressedTicks == other.suppressedTicks && java.util.Arrays.equals(cooldowns, other.cooldowns);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record OpenChooserPayload() implements CustomPayload {
		public static final OpenChooserPayload INSTANCE = new OpenChooserPayload();
		public static final Id<OpenChooserPayload> ID = id("open_chooser");
		public static final PacketCodec<RegistryByteBuf, OpenChooserPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Tells clients that a player entered or left the Avatar State (glowing eyes and tattoos). */
	public record AvatarVisualPayload(int entityId, boolean active) implements CustomPayload {
		public static final Id<AvatarVisualPayload> ID = id("avatar_visual");
		public static final PacketCodec<RegistryByteBuf, AvatarVisualPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, AvatarVisualPayload::entityId,
			PacketCodecs.BOOL, AvatarVisualPayload::active,
			AvatarVisualPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Plays the totem-style "item pop" animation of the Avatar Spirit on the receiving client. */
	public record SpiritPopPayload() implements CustomPayload {
		public static final SpiritPopPayload INSTANCE = new SpiritPopPayload();
		public static final Id<SpiritPopPayload> ID = id("spirit_pop");
		public static final PacketCodec<RegistryByteBuf, SpiritPopPayload> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Sends a packet only if the player's client has this mod installed. */
	public static void send(ServerPlayerEntity player, CustomPayload payload) {
		if (player.networkHandler != null && ServerPlayNetworking.canSend(player, payload.getId())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CastPayload.ID, CastPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CycleAbilityPayload.ID, CycleAbilityPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CycleElementPayload.ID, CycleElementPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AvatarStateKeyPayload.ID, AvatarStateKeyPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ChooseElementPayload.ID, ChooseElementPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectAbilityPayload.ID, SelectAbilityPayload.CODEC);

		PayloadTypeRegistry.playS2C().register(BenderSyncPayload.ID, BenderSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenChooserPayload.ID, OpenChooserPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AvatarVisualPayload.ID, AvatarVisualPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpiritPopPayload.ID, SpiritPopPayload.CODEC);

		// Handlers run on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(CastPayload.ID,
			(payload, context) -> BendingManager.tryCast(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(CycleAbilityPayload.ID,
			(payload, context) -> BendingManager.cycleAbility(context.player(), payload.direction() < 0 ? -1 : 1));
		ServerPlayNetworking.registerGlobalReceiver(CycleElementPayload.ID,
			(payload, context) -> BendingManager.cycleElement(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AvatarStateKeyPayload.ID,
			(payload, context) -> BendingManager.toggleAvatarState(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ChooseElementPayload.ID, (payload, context) -> {
			Element element = Element.byOrdinal(payload.element());
			if (element != null && Element.BENDABLE.contains(element)) {
				BendingManager.chooseElement(context.player(), element, false);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SelectAbilityPayload.ID, (payload, context) -> {
			Element element = Element.byOrdinal(payload.element());
			if (element != null) {
				BendingManager.selectAbility(context.player(), element, payload.index());
			}
		});
	}
}
