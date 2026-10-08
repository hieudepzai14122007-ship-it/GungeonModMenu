package com.avatarbending.test;

import com.avatarbending.bending.Ability;
import com.avatarbending.bending.AvatarState;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.effect.EffectScheduler;
import com.avatarbending.item.AvatarSpiritItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side smoke tests: every spell is cast by a fake player at an iron golem. The tests fail
 * if a spell throws, if an offensive spell does no damage, or if temporary blocks are left behind.
 * Run with {@code ./gradlew runGametest}.
 */
public class BendingGameTests implements FabricGameTest {
	private static final int SPACING = 70;
	private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 4);
	private static final BlockPos TARGET_POS = new BlockPos(5, 1, 4);

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "air", tickLimit = 600)
	public void airSpells(TestContext ctx) {
		runElement(ctx, Element.AIR);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "water", tickLimit = 600)
	public void waterSpells(TestContext ctx) {
		runElement(ctx, Element.WATER);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "earth", tickLimit = 600)
	public void earthSpells(TestContext ctx) {
		runElement(ctx, Element.EARTH);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire", tickLimit = 600)
	public void fireSpells(TestContext ctx) {
		runElement(ctx, Element.FIRE);
	}

	// Sky access: the meteor falls from 30 blocks up and would hit the test's barrier ceiling.
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "avatar", tickLimit = 600, skyAccess = true)
	public void avatarSpells(TestContext ctx) {
		runElement(ctx, Element.AVATAR);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "progression", tickLimit = 200)
	public void chiCooldownsAndAvatarState(TestContext ctx) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		BenderData data = BendingManager.get(player);
		ctx.assertTrue(!data.hasBending(), "new players start without bending");

		ctx.assertTrue(BendingManager.chooseElement(player, Element.FIRE, false), "choosing an element works");
		ctx.assertTrue(!BendingManager.chooseElement(player, Element.WATER, false), "players can only choose once");
		ctx.assertTrue(data.primary() == Element.FIRE, "primary element is fire");
		ctx.assertTrue(data.availableElements().equals(List.of(Element.FIRE)), "non-avatars only have one element");

		data.setChi(BenderData.MAX_CHI);
		data.setAbilityIndex(0);
		ctx.assertTrue(data.selectedAbility() == Ability.FIRE_BLAST, "first fire ability is Fire Blast");
		ctx.assertTrue(BendingManager.tryCast(player), "cast with full chi works");
		ctx.assertTrue(Math.abs(data.chi() - (BenderData.MAX_CHI - Ability.FIRE_BLAST.chiCost())) < 0.01f, "casting costs chi");
		ctx.assertTrue(!BendingManager.tryCast(player), "abilities have a cooldown");
		data.clearCooldowns();
		data.setChi(1);
		ctx.assertTrue(!BendingManager.tryCast(player), "casting needs chi");

		BendingManager.cycleAbility(player, 1);
		ctx.assertTrue(data.selectedAbility() == Ability.FIRE_FISTS, "cycling selects the next ability");
		BendingManager.cycleElement(player);
		ctx.assertTrue(data.active() == Element.FIRE, "non-avatars cannot switch elements");

		AvatarSpiritItem.becomeAvatar(player);
		ctx.assertTrue(data.isAvatar(), "the Avatar Spirit makes you the Avatar");
		ctx.assertTrue(data.availableElements().size() == 5, "the Avatar has every element and the Avatar spells");
		BendingManager.cycleElement(player);
		ctx.assertTrue(data.active() == Element.AIR, "the Avatar can switch elements");

		AvatarState.enter(player, data);
		ctx.assertTrue(data.inAvatarState(), "Avatar State starts");
		data.setChi(0);
		data.clearCooldowns();
		ctx.assertTrue(BendingManager.tryCast(player), "spells are free in the Avatar State");
		data.setAvatarStateTicks(3);
		ctx.runAtTick(20, () -> {
			ctx.assertTrue(!data.inAvatarState(), "Avatar State ends");
			ctx.assertTrue(data.avatarStateCooldown() > 0, "Avatar State has a cooldown");
			BendingManager.reset(player);
			ctx.assertTrue(!data.hasBending(), "reset removes bending");
			ctx.complete();
		});
	}

	private static ServerPlayerEntity player(TestContext ctx) {
		// Spells reach well outside the 8x8 test area: keep the chunks around it ticking.
		BlockPos center = ctx.getAbsolutePos(PLAYER_POS);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				ctx.getWorld().setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, true);
			}
		}
		@SuppressWarnings("removal")
		ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		// Survival rules so spells cost chi (the mock player only pretends to be creative).
		player.changeGameMode(GameMode.SURVIVAL);
		placePlayer(ctx, player);
		return player;
	}

	private static void placePlayer(TestContext ctx, ServerPlayerEntity player) {
		Vec3d pos = ctx.getAbsolute(Vec3d.ofBottomCenter(PLAYER_POS));
		Vec3d target = ctx.getAbsolute(Vec3d.ofBottomCenter(TARGET_POS));
		float yaw = (float) Math.toDegrees(Math.atan2(target.z - pos.z, target.x - pos.x)) - 90f;
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
		player.setHeadYaw(yaw);
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0;
	}

	private static void floor(TestContext ctx) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static IronGolemEntity freshTarget(TestContext ctx, IronGolemEntity current) {
		IronGolemEntity golem = current;
		if (golem == null || !golem.isAlive()) {
			golem = ctx.spawnMob(EntityType.IRON_GOLEM, TARGET_POS);
			golem.setAiDisabled(true);
			golem.setPersistent();
		}
		Vec3d pos = ctx.getAbsolute(Vec3d.ofBottomCenter(TARGET_POS));
		golem.refreshPositionAndAngles(pos.x, pos.y, pos.z, 90, 0);
		golem.setVelocity(Vec3d.ZERO);
		golem.setHealth(golem.getMaxHealth());
		golem.clearStatusEffects();
		golem.extinguish();
		golem.setFrozenTicks(0);
		golem.timeUntilRegen = 0;
		return golem;
	}

	private void runElement(TestContext ctx, Element element) {
		floor(ctx);
		ServerPlayerEntity player = player(ctx);
		BenderData data = BendingManager.get(player);
		data.setPrimary(Element.FIRE);
		data.setAvatar(true);
		int errorsBefore = EffectScheduler.errorCount();
		List<Ability> abilities = Ability.forElement(element);
		List<String> problems = new ArrayList<>();
		IronGolemEntity[] target = new IronGolemEntity[1];

		for (int i = 0; i < abilities.size(); i++) {
			Ability ability = abilities.get(i);
			int start = 5 + i * SPACING;
			ctx.runAtTick(start, () -> {
				placePlayer(ctx, player);
				target[0] = freshTarget(ctx, target[0]);
				if (!BendingManager.cast(player, ability)) {
					problems.add(ability.id() + " failed to cast");
				}
			});
			ctx.runAtTick(start + SPACING - 2, () -> {
				LivingEntity golem = target[0];
				boolean hurt = golem == null || !golem.isAlive() || golem.getHealth() < golem.getMaxHealth();
				if (ability.offensive() && !hurt) {
					problems.add(ability.id() + " did no damage");
				}
			});
		}

		if (element == Element.EARTH) {
			int wallIndex = abilities.indexOf(Ability.EARTH_WALL);
			BlockPos wallBlock = new BlockPos(4, 1, 2);
			ctx.runAtTick(5 + wallIndex * SPACING + 12, () -> {
				if (ctx.getBlockState(wallBlock).isAir()) {
					problems.add("earth_wall placed no block at " + wallBlock);
				}
			});
		}

		int end = 5 + abilities.size() * SPACING + 5;
		ctx.runAtTick(end, () -> {
			if (element == Element.EARTH && !ctx.getBlockState(new BlockPos(4, 1, 2)).isAir()) {
				problems.add("earth_wall blocks did not disappear");
			}
			if (EffectScheduler.errorCount() != errorsBefore) {
				problems.add((EffectScheduler.errorCount() - errorsBefore) + " spell effect(s) crashed (see log)");
			}
			if (!problems.isEmpty()) {
				ctx.throwGameTestException(element.id() + ": " + String.join("; ", problems));
			}
			ctx.complete();
		});
	}
}
