package com.avatarbending.client.screen;

import com.avatarbending.bending.Ability;
import com.avatarbending.bending.Element;
import com.avatarbending.client.BendingHud;
import com.avatarbending.client.ClientBenderState;
import com.avatarbending.client.KeyBinds;
import com.avatarbending.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;

import java.util.List;

/**
 * Spellbook: browse every element's spells (two columns) and click one to select it.
 */
public class BendingMenuScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int COLUMN_GAP = 6;

	private Element tab;

	public BendingMenuScreen() {
		super(Text.translatable("screen.avatarbending.menu.title"));
		this.tab = ClientBenderState.active != null ? ClientBenderState.active : Element.AIR;
	}

	private int panelWidth() {
		return Math.min(420, width - 20);
	}

	private int columnWidth() {
		return (panelWidth() - COLUMN_GAP) / 2;
	}

	private static int rows(int count) {
		return (count + 1) / 2;
	}

	/** Left edge of spell {@code i}'s cell (spells 1-5 on the left, 6-10 on the right). */
	private int cellX(int i, int count) {
		return panelLeft() + (i < rows(count) ? 0 : columnWidth() + COLUMN_GAP);
	}

	private int cellY(int i, int count) {
		return listTop() + (i % rows(count)) * ROW_HEIGHT;
	}

	private int panelLeft() {
		return (width - panelWidth()) / 2;
	}

	private int listTop() {
		return 44;
	}

	@Override
	protected void init() {
		List<Element> available = ClientBenderState.availableElements();
		Element[] tabs = Element.values();
		int tabWidth = Math.min(64, (panelWidth() - 4 * 4) / tabs.length);
		int total = tabs.length * tabWidth + (tabs.length - 1) * 4;
		int x = (width - total) / 2;
		for (Element element : tabs) {
			boolean unlocked = available.contains(element);
			Text label = unlocked ? element.displayName() : Text.translatable(element.translationKey()).formatted(Formatting.DARK_GRAY);
			ButtonWidget button = ButtonWidget.builder(label, b -> {
					tab = element;
					clearAndInit();
				})
				.dimensions(x, 20, tabWidth, 18)
				.build();
			button.active = unlocked && element != tab;
			if (!unlocked) {
				button.setTooltip(Tooltip.of(Text.translatable("screen.avatarbending.menu.locked")));
			}
			addDrawableChild(button);
			x += tabWidth + 4;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
			.dimensions(width / 2 - 50, height - 23, 100, 20)
			.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, 7, 0xFFFFFFFF);

		int panelWidth = panelWidth();
		boolean unlocked = ClientBenderState.availableElements().contains(tab);
		List<Ability> abilities = Ability.forElement(tab);
		int top = listTop();
		int color = tab.color();

		int cellWidth = columnWidth();
		for (int i = 0; i < abilities.size(); i++) {
			Ability ability = abilities.get(i);
			int x = cellX(i, abilities.size());
			int y = cellY(i, abilities.size());
			boolean selected = unlocked && ability == ClientBenderState.selectedAbility();
			boolean hovered = unlocked && mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			context.fill(x, y, x + cellWidth, y + ROW_HEIGHT - 2, selected ? BendingHud.withAlpha(color, 0x70) : (hovered ? 0x70303040 : 0x90101018));
			context.fill(x, y, x + 2, y + ROW_HEIGHT - 2, BendingHud.withAlpha(color, 0xFF));
			BendingHud.drawEmblem(context, tab, x + 5, y + 3, 16);
			Text chi = Text.translatable("screen.avatarbending.menu.chi", ability.chiCost());
			Text cooldown = Text.translatable("screen.avatarbending.menu.cooldown", formatSeconds(ability.cooldown()));
			int statsWidth = Math.max(textRenderer.getWidth(chi), textRenderer.getWidth(cooldown));
			int textWidth = cellWidth - 25 - statsWidth - 6;
			Text name = Text.literal((i + 1) + ". ").append(ability.displayName())
				.formatted(unlocked ? Formatting.WHITE : Formatting.DARK_GRAY, Formatting.BOLD);
			context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(name, textWidth)), x + 25, y + 2,
				0xFFFFFFFF);
			String desc = textRenderer.trimToWidth(ability.description().getString(), textWidth);
			context.drawTextWithShadow(textRenderer, Text.literal(desc), x + 25, y + 12, unlocked ? 0xFFB8B8B8 : 0xFF5A5A5A);
			int statsX = x + cellWidth - 4;
			context.drawTextWithShadow(textRenderer, chi, statsX - textRenderer.getWidth(chi), y + 2, 0xFF9AD0FF);
			context.drawTextWithShadow(textRenderer, cooldown, statsX - textRenderer.getWidth(cooldown), y + 12, 0xFF8A8A8A);
			if (ability.avatarStateOnly()) {
				context.fill(x + cellWidth - 2, y, x + cellWidth, y + ROW_HEIGHT - 2, 0xFF7DF9FF);
			}
			if (hovered) {
				List<net.minecraft.text.OrderedText> lines = new java.util.ArrayList<>(
					textRenderer.wrapLines(ability.displayName().copy().formatted(Formatting.BOLD), 220));
				lines.addAll(textRenderer.wrapLines(ability.description(), 220));
				if (ability.avatarStateOnly()) {
					lines.addAll(textRenderer.wrapLines(Text.translatable("screen.avatarbending.menu.avatar_state_only")
						.formatted(Formatting.AQUA), 220));
				}
				setTooltip(lines);
			}
		}

		// Extra lines under the list, as many as fit above the Done button.
		int infoY = top + rows(abilities.size()) * ROW_HEIGHT + 4;
		int limit = height - 28;
		List<Text> info = new java.util.ArrayList<>();
		if (!ClientBenderState.avatar) {
			info.add(Text.translatable("screen.avatarbending.menu.become_avatar").formatted(Formatting.AQUA));
			info.add(Text.translatable("screen.avatarbending.menu.recipe").formatted(Formatting.GRAY));
		}
		info.add(Text.translatable("screen.avatarbending.menu.controls",
			KeyBinds.CAST.getBoundKeyLocalizedText(), KeyBinds.NEXT_ABILITY.getBoundKeyLocalizedText(),
			KeyBinds.SWITCH_ELEMENT.getBoundKeyLocalizedText(), KeyBinds.AVATAR_STATE.getBoundKeyLocalizedText()));
		outer:
		for (Text line : info) {
			for (var wrapped : textRenderer.wrapLines(line, panelWidth)) {
				if (infoY + 9 > limit) {
					break outer;
				}
				context.drawCenteredTextWithShadow(textRenderer, wrapped, width / 2, infoY, 0xFFDDDDDD);
				infoY += 10;
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (!ClientBenderState.availableElements().contains(tab)) {
			return false;
		}
		List<Ability> abilities = Ability.forElement(tab);
		for (int i = 0; i < abilities.size(); i++) {
			int x = cellX(i, abilities.size());
			int y = cellY(i, abilities.size());
			if (mouseX >= x && mouseX < x + columnWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
				ClientPlayNetworking.send(new ModPayloads.SelectAbilityPayload(tab.ordinal(), i));
				// Update locally right away so the highlight moves instantly.
				ClientBenderState.active = tab;
				ClientBenderState.ability = i;
				if (client != null) {
					client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (KeyBinds.MENU.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private static String formatSeconds(int ticks) {
		float seconds = ticks / 20f;
		return seconds == Math.round(seconds) ? String.valueOf(Math.round(seconds)) : String.format("%.1f", seconds);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
