package com.avatarbending.item;

import com.avatarbending.AvatarBending;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.network.ModPayloads;
import com.avatarbending.network.ModPayloads.SpiritPopPayload;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Avatar Spirit. Using it makes a bender the Avatar, master of all four elements.
 */
public class AvatarSpiritItem extends Item {
	public AvatarSpiritItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack, world.isClient);
		}
		BenderData data = BendingManager.get(player);
		if (data.isAvatar()) {
			player.sendMessage(Text.translatable("message.avatarbending.already_avatar").formatted(Formatting.AQUA), true);
			return TypedActionResult.fail(stack);
		}
		if (data.primary() == null) {
			player.sendMessage(Text.translatable("message.avatarbending.choose_first").formatted(Formatting.RED), true);
			ModPayloads.send(player, ModPayloads.OpenChooserPayload.INSTANCE);
			return TypedActionResult.fail(stack);
		}
		becomeAvatar(player);
		if (!player.isCreative()) {
			stack.decrement(1);
		}
		return TypedActionResult.consume(stack);
	}

	/** Turns a player into the Avatar with a big celebration. */
	public static void becomeAvatar(ServerPlayerEntity player) {
		BenderData data = BendingManager.get(player);
		data.setAvatar(true);
		data.setChi(BenderData.MAX_CHI);
		data.setActive(Element.AVATAR);
		data.setAbilityIndex(0);
		if (data.primary() == null) {
			data.setPrimary(Element.AIR);
		}

		ServerWorld world = player.getServerWorld();
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
			bolt.setCosmetic(true);
			world.spawnEntity(bolt);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.5f, 0.8f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1f, 1.3f);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2f, 1f);
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 150, 0.6, 1, 0.6, 0.6);
		world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 80, 0.3, 1.5, 0.3, 0.2);

		ModPayloads.send(player, SpiritPopPayload.INSTANCE);
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.avatarbending.avatar").formatted(Formatting.AQUA, Formatting.BOLD)));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.avatarbending.avatar.sub").formatted(Formatting.WHITE)));
		player.sendMessage(Text.translatable("message.avatarbending.became_avatar").formatted(Formatting.AQUA), false);

		BendingManager.unlockSpiritRecipe(player);
		AvatarBending.grantAdvancement(player, "bender");
		AvatarBending.grantAdvancement(player, "avatar");
		BendingManager.sync(player, true);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.avatarbending.avatar_spirit.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.avatarbending.avatar_spirit.tooltip2").formatted(Formatting.AQUA));
	}
}
