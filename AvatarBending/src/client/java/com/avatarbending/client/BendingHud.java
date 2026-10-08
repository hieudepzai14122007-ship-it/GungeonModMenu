package com.avatarbending.client;

import com.avatarbending.AvatarBending;
import com.avatarbending.bending.Ability;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.Element;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Top-left bending panel: element emblem, selected spell, chi bar and spell slots.
 * Also draws the glowing screen tint while in the Avatar State.
 */
public final class BendingHud {
	private static final int PANEL_WIDTH = 156;

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
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		TextRenderer text = client.textRenderer;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		float time = client.player.age + tickCounter.getTickDelta(false);

		if (ClientBenderState.avatarStateTicks > 0) {
			renderAvatarState(context, text, width, height, time);
		}

		if (!ClientBenderState.hasBending()) {
			Text hint = Text.translatable("hud.avatarbending.choose_hint", KeyBinds.MENU.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW));
			int w = text.getWidth(hint);
			context.fill(4, 4, 12 + w, 18, 0x90000000);
			context.drawTextWithShadow(text, hint, 8, 7, 0xFFFFFFFF);
			return;
		}

		Element element = ClientBenderState.active;
		Ability ability = ClientBenderState.selectedAbility();
		if (element == null || ability == null) {
			return;
		}
		int color = element.color();
		// Top-left corner: free of the hotbar, chat, effects and boss bars.
		int x = 6;
		int y = 8;

		// Panel background with an element-colored top edge.
		context.fill(x - 3, y - 3, x + PANEL_WIDTH, y + 62, 0x90000000);
		context.fill(x - 3, y - 4, x + PANEL_WIDTH, y - 3, withAlpha(color, 0xFF));

		drawEmblem(context, element, x, y, 24);
		context.drawTextWithShadow(text, element.displayName().copy().formatted(Formatting.BOLD), x + 28, y + 2, 0xFFFFFFFF);
		context.drawTextWithShadow(text, ability.displayName(), x + 28, y + 13, 0xFFFFFFFF);

		// Chi bar.
		int barY = y + 28;
		int barWidth = PANEL_WIDTH - 4;
		float chi = MathHelper.clamp(ClientBenderState.chi / BenderData.MAX_CHI, 0, 1);
		context.fill(x, barY, x + barWidth, barY + 7, 0xFF1A1A1A);
		int filled = Math.round(barWidth * chi);
		context.fillGradient(x, barY, x + filled, barY + 7, withAlpha(lighten(color), 0xFF), withAlpha(color, 0xFF));
		if (ClientBenderState.avatarStateTicks > 0) {
			int shine = (int) ((time * 4) % (barWidth + 20)) - 10;
			context.fill(x + Math.max(0, shine), barY, x + Math.min(barWidth, shine + 6), barY + 7, 0x80FFFFFF);
		}
		Text chiText = Text.translatable("hud.avatarbending.chi", Math.round(ClientBenderState.chi));
		context.drawTextWithShadow(text, chiText, x, barY + 9, 0xFFDDDDDD);
		Text keys = Text.translatable("hud.avatarbending.keys",
			KeyBinds.CAST.getBoundKeyLocalizedText(), KeyBinds.NEXT_ABILITY.getBoundKeyLocalizedText());
		context.drawTextWithShadow(text, keys, x + barWidth - text.getWidth(keys), barY + 9, 0xFF9A9A9A);

		// Spell slots with cooldown overlays.
		List<Ability> abilities = Ability.forElement(element);
		int slotY = y + 48;
		for (int i = 0; i < abilities.size(); i++) {
			int sx = x + i * 14;
			boolean selected = abilities.get(i) == ability;
			context.fill(sx, slotY, sx + 12, slotY + 12, selected ? 0xFFFFFFFF : 0xFF3A3A3A);
			context.fill(sx + 1, slotY + 1, sx + 11, slotY + 11, withAlpha(color, selected ? 0xFF : 0xB0));
			int pct = ClientBenderState.cooldownPercent(i);
			if (pct > 0) {
				int h = Math.max(1, Math.round(10 * pct / 100f));
				context.fill(sx + 1, slotY + 1, sx + 11, slotY + 1 + h, 0xC0000000);
			}
			context.drawText(text, String.valueOf(i + 1), sx + 4, slotY + 2, selected ? 0xFF000000 : 0xFFFFFFFF, false);
		}

		// Element switch / Avatar State status on the right of the slots.
		int infoX = x + abilities.size() * 14 + 4;
		if (ClientBenderState.suppressedTicks > 0) {
			context.drawTextWithShadow(text, Text.translatable("hud.avatarbending.suppressed", ClientBenderState.suppressedTicks / 20 + 1),
				infoX, slotY + 2, 0xFFC060FF);
		} else if (ClientBenderState.avatar) {
			Text status;
			if (ClientBenderState.avatarStateTicks > 0) {
				status = Text.translatable("hud.avatarbending.avatar_active");
			} else if (ClientBenderState.avatarStateCooldown > 0) {
				status = Text.translatable("hud.avatarbending.avatar_cooldown", ClientBenderState.avatarStateCooldown / 20 + 1);
			} else {
				status = Text.translatable("hud.avatarbending.avatar_ready", KeyBinds.AVATAR_STATE.getBoundKeyLocalizedText());
			}
			context.drawTextWithShadow(text, status, infoX, slotY + 2, 0xFF7DF9FF);
		}
	}

	private static void renderAvatarState(DrawContext context, TextRenderer text, int width, int height, float time) {
		float pulse = 0.5f + 0.5f * MathHelper.sin(time * 0.15f);
		int edge = (int) (60 + 50 * pulse);
		context.fill(0, 0, width, height, 0x147DF9FF);
		// Glowing vignette on every edge.
		context.fillGradient(0, 0, width, 40, withAlpha(0xBFFFFF, edge), 0x00BFFFFF);
		context.fillGradient(0, height - 40, width, height, 0x00BFFFFF, withAlpha(0xBFFFFF, edge));
		for (int i = 0; i < 30; i++) {
			int alpha = (int) (edge * (1 - i / 30f));
			context.fill(i, 0, i + 1, height, withAlpha(0xBFFFFF, alpha));
			context.fill(width - i - 1, 0, width - i, height, withAlpha(0xBFFFFF, alpha));
		}
		Text title = Text.translatable("hud.avatarbending.avatar_state", ClientBenderState.avatarStateTicks / 20 + 1)
			.formatted(Formatting.AQUA, Formatting.BOLD);
		context.getMatrices().push();
		float scale = 1.5f;
		context.getMatrices().translate(width / 2f, 8, 0);
		context.getMatrices().scale(scale, scale, 1);
		context.drawCenteredTextWithShadow(text, title, 0, 0, 0xFFFFFFFF);
		context.getMatrices().pop();
	}

	private static int lighten(int rgb) {
		int r = Math.min(255, ((rgb >> 16) & 0xFF) + 70);
		int g = Math.min(255, ((rgb >> 8) & 0xFF) + 70);
		int b = Math.min(255, (rgb & 0xFF) + 70);
		return (r << 16) | (g << 8) | b;
	}
}
