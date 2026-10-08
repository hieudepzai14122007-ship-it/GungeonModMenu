package com.avatarbending.client;

import com.avatarbending.AvatarBending;
import com.avatarbending.bending.Ability;
import com.avatarbending.bending.AvatarState;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.Element;
import com.avatarbending.client.fx.ScreenEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * A small, clean bending HUD in the bottom-right corner next to the hotbar: the element icon and
 * selected spell, a slim chi bar and one pip per spell. Falls back to the top-left corner when the
 * window is too narrow. Also draws a soft glow on the screen edges in the Avatar State.
 */
public final class BendingHud {
	private static final int MARGIN = 6;
	private static final int BAR_WIDTH = 90;
	/** Width kept free beside the hotbar; longer spell names are shortened to fit. */
	private static final int RESERVED_WIDTH = 108;
	private static final int AVATAR_COLOR = 0x7DF9FF;

	private BendingHud() {
	}

	public static Identifier emblem(Element element) {
		return AvatarBending.id("textures/gui/emblem_" + element.id() + ".png");
	}

	public static void drawEmblem(DrawContext context, Element element, int x, int y, int size) {
		RenderSystem.enableBlend();
		context.drawTexture(emblem(element), x, y, size, size, 0, 0, 64, 64, 64, 64);
		RenderSystem.disableBlend();
	}

	public static int withAlpha(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		float tickDelta = tickCounter.getTickDelta(false);
		ScreenEffects.renderFlash(context, tickDelta);
		if (client.options.hudHidden) {
			return;
		}
		TextRenderer text = client.textRenderer;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float time = client.player.age + tickDelta;

		if (ClientBenderState.avatarStateTicks > 0) {
			renderEdgeGlow(context, width, height, time);
		}

		if (!ClientBenderState.hasBending()) {
			Text hint = Text.translatable("hud.avatarbending.choose_hint", KeyBinds.MENU.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW));
			int w = text.getWidth(hint);
			int[] pos = anchor(client, width, height, w, 9);
			context.drawTextWithShadow(text, hint, pos[0], pos[1], 0xFFFFFFFF);
			return;
		}

		Element element = ClientBenderState.active;
		Ability ability = ClientBenderState.selectedAbility();
		if (element == null || ability == null) {
			return;
		}
		List<Ability> abilities = Ability.forElement(element);
		int index = Math.floorMod(ClientBenderState.ability, abilities.size());
		int color = element.color();
		boolean avatarState = ClientBenderState.avatarStateTicks > 0;
		boolean suppressed = ClientBenderState.suppressedTicks > 0;
		int cooldownPct = ClientBenderState.cooldownPercent(index);
		boolean enoughChi = avatarState || ClientBenderState.chi >= ability.chiCost();

		// Spell line: grayed out while it recharges, red when there is not enough chi.
		Text name;
		int nameColor;
		if (suppressed) {
			name = Text.translatable("hud.avatarbending.suppressed", ClientBenderState.suppressedTicks / 20 + 1);
			nameColor = 0xFFC060FF;
		} else {
			name = ability.displayName();
			nameColor = cooldownPct > 0 ? 0xFF8A8A8A : enoughChi ? 0xFFFFFFFF : 0xFFFF7070;
		}
		boolean showAvatarMark = ClientBenderState.avatar && !avatarState;
		int maxNameWidth = RESERVED_WIDTH - 13 - (showAvatarMark ? 9 : 0);
		if (text.getWidth(name) > maxNameWidth) {
			name = Text.literal(text.trimToWidth(name.getString(), maxNameWidth - text.getWidth("...")) + "...");
		}
		int lineWidth = 13 + text.getWidth(name) + (showAvatarMark ? 9 : 0);
		Text avatarLabel = avatarState
			? Text.translatable("hud.avatarbending.avatar_state", ClientBenderState.avatarStateTicks / 20 + 1)
			: null;
		int boxWidth = Math.max(BAR_WIDTH, Math.max(lineWidth, avatarLabel == null ? 0 : text.getWidth(avatarLabel)));
		int boxHeight = (avatarLabel == null ? 0 : 11) + 11 + 3 + 3 + 2;
		int[] pos = anchor(client, width, height, boxWidth, boxHeight);
		boolean rightAligned = pos[0] > width / 2;
		int x = pos[0];
		int y = pos[1];

		if (avatarLabel != null) {
			int lx = rightAligned ? x + boxWidth - text.getWidth(avatarLabel) : x;
			context.drawTextWithShadow(text, avatarLabel, lx, y, withAlpha(AVATAR_COLOR, 0xFF));
			y += 11;
		}

		// Icon (darkened from the top while the spell recharges) and spell name.
		int lineX = rightAligned ? x + boxWidth - lineWidth : x;
		drawEmblem(context, element, lineX, y - 1, 10);
		if (cooldownPct > 0 && !suppressed) {
			int h = Math.max(1, Math.round(10 * cooldownPct / 100f));
			context.fill(lineX, y - 1, lineX + 10, y - 1 + h, 0xA0000000);
		}
		int textX = lineX + 13;
		context.drawTextWithShadow(text, name, textX, y, nameColor);
		textX += text.getWidth(name);
		if (showAvatarMark) {
			boolean ready = ClientBenderState.avatarStateCooldown <= 0;
			drawDiamond(context, textX + 4, y + 1, ready ? withAlpha(AVATAR_COLOR, 0xFF) : 0xFF505050);
		}
		y += 11;

		// Slim bar: chi normally, the remaining Avatar State time while it is active.
		int barX = rightAligned ? x + boxWidth - BAR_WIDTH : x;
		context.fill(barX, y, barX + BAR_WIDTH, y + 3, 0xA0000000);
		if (avatarState) {
			float left = MathHelper.clamp(ClientBenderState.avatarStateTicks / (float) AvatarState.DURATION, 0, 1);
			int filled = Math.round(BAR_WIDTH * left);
			context.fill(barX, y, barX + filled, y + 3, withAlpha(AVATAR_COLOR, 0xFF));
			int shine = (int) ((time * 3) % (BAR_WIDTH + 16)) - 8;
			int s0 = Math.max(0, shine);
			int s1 = Math.min(filled, shine + 5);
			if (s1 > s0) {
				context.fill(barX + s0, y, barX + s1, y + 3, 0xC0FFFFFF);
			}
		} else {
			float chi = MathHelper.clamp(ClientBenderState.chi / BenderData.MAX_CHI, 0, 1);
			int filled = Math.round(BAR_WIDTH * chi);
			context.fill(barX, y, barX + filled, y + 3, withAlpha(color, 0xFF));
			context.fill(barX, y, barX + filled, y + 1, withAlpha(lighten(color), 0xFF));
			// Tick mark showing how much chi the selected spell needs.
			int cost = Math.round(BAR_WIDTH * MathHelper.clamp(ability.chiCost() / BenderData.MAX_CHI, 0, 1));
			context.fill(barX + cost, y - 1, barX + cost + 1, y + 4, enoughChi ? 0xB0FFFFFF : 0xFFFF5050);
		}
		y += 3 + 3;

		// One pip per spell: white = selected, colored = ready, dark = recharging.
		int n = abilities.size();
		int pipWidth = Math.max(2, (BAR_WIDTH + 2) / n - 2);
		int pipsWidth = n * (pipWidth + 2) - 2;
		int pipX = rightAligned ? x + boxWidth - pipsWidth : x;
		for (int i = 0; i < n; i++) {
			int px = pipX + i * (pipWidth + 2);
			int pipColor;
			if (i == index) {
				pipColor = 0xFFFFFFFF;
			} else if (ClientBenderState.cooldownPercent(i) > 0) {
				pipColor = 0xFF3A3A3A;
			} else {
				pipColor = withAlpha(color, 0xC0);
			}
			context.fill(px, y, px + pipWidth, y + 2, pipColor);
		}
	}

	/**
	 * Top-left corner of a box of the given size: bottom-right next to the hotbar when there is room,
	 * otherwise the top-left corner of the screen.
	 */
	private static int[] anchor(MinecraftClient client, int width, int height, int boxWidth, int boxHeight) {
		// Right edge of the hotbar, plus the off-hand slot or attack indicator when they sit on that side.
		int hotbarRight = width / 2 + 91 + 4;
		if (client.options.getMainArm().getValue() == Arm.LEFT) {
			hotbarRight += 29;
		} else if (client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
			hotbarRight += 24;
		}
		if (width - MARGIN - Math.max(boxWidth, RESERVED_WIDTH) >= hotbarRight) {
			return new int[] {width - MARGIN - boxWidth, height - MARGIN - boxHeight};
		}
		return new int[] {MARGIN, MARGIN};
	}

	private static void drawDiamond(DrawContext context, int x, int y, int color) {
		context.fill(x + 2, y, x + 3, y + 1, color);
		context.fill(x + 1, y + 1, x + 4, y + 2, color);
		context.fill(x, y + 2, x + 5, y + 3, color);
		context.fill(x + 1, y + 3, x + 4, y + 4, color);
		context.fill(x + 2, y + 4, x + 3, y + 5, color);
	}

	/** Soft glow on the screen edges while in the Avatar State. */
	private static void renderEdgeGlow(DrawContext context, int width, int height, float time) {
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 0.15f);
		int alpha = (int) (30 + 34 * pulse);
		int depth = 18;
		context.fillGradient(0, 0, width, depth, withAlpha(0xBFFFFF, alpha), 0x00BFFFFF);
		context.fillGradient(0, height - depth, width, height, 0x00BFFFFF, withAlpha(0xBFFFFF, alpha));
		for (int i = 0; i < depth; i++) {
			int a = (int) (alpha * (1 - i / (float) depth));
			context.fill(i, 0, i + 1, height, withAlpha(0xBFFFFF, a));
			context.fill(width - i - 1, 0, width - i, height, withAlpha(0xBFFFFF, a));
		}
	}

	private static int lighten(int rgb) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
		int g = Math.min(255, ((rgb >> 8) & 0xFF) + 70);
		int b = Math.min(255, (rgb & 0xFF) + 70);
		return (r << 16) | (g << 8) | b;
	}
}
