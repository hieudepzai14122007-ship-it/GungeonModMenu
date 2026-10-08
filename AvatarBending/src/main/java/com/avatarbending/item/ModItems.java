package com.avatarbending.item;

import com.avatarbending.AvatarBending;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	public static final Item AVATAR_SPIRIT = Registry.register(Registries.ITEM, AvatarBending.id("avatar_spirit"),
		new AvatarSpiritItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.TOTEM_OF_UNDYING, AVATAR_SPIRIT));
	}
}
