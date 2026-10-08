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

import java.util.List;

/**
 * Spellbook: browse every element's spells and click one to select it.
 */
public class BendingMenuScreen extends Screen {
	private static final int ROW_HEIGHT = 22;

	private Element tab;

	public BendingMenuScreen() {
		super(Text.translatable("screen.avatarbending.menu.title"));
		this.tab = ClientBenderState.active != null ? ClientBenderState.active : Element.AIR;
	}

	private int panelWidth() {
		return Math.min(340, width - 20);
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

		int left = panelLeft();
		int panelWidth = panelWidth();
		boolean unlocked = ClientBenderState.availableElements().contains(tab);
		List<Ability> abilities = Ability.forElement(tab);
		int top = listTop();
		int color = tab.color();

		for (int i = 0; i < abilities.size(); i++) {
			Ability ability = abilities.get(i);
			int y = top + i * ROW_HEIGHT;
			boolean selected = unlocked && ability == ClientBenderState.selectedAbility();
			boolean hovered = unlocked && mouseX >= left && mouseX < left + panelWidth && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			context.fill(left, y, left + panelWidth, y + ROW_HEIGHT - 2, selected ? BendingHud.withAlpha(color, 0x70) : (hovered ? 0x70303040 : 0x90101018));
			context.fill(left, y, left + 3, y + ROW_HEIGHT - 2, BendingHud.withAlpha(color, 0xFF));
			BendingHud.drawEmblem(context, tab, left + 6, y + 2, 16);
			Text name = Text.literal((i + 1) + ". ").append(ability.displayName());
			context.drawTextWithShadow(textRenderer, name.copy().formatted(unlocked ? Formatting.WHITE : Formatting.DARK_GRAY, Formatting.BOLD), left + 26, y + 2, 0xFFFFFFFF);
			Text stats = Text.translatable("screen.avatarbending.menu.stats", ability.chiCost(), String.format("%.1f", ability.cooldown() / 20f));
			context.drawTextWithShadow(textRenderer, stats, left + panelWidth - 6 - textRenderer.getWidth(stats), y + 2, 0xFF9AD0FF);
			String desc = textRenderer.trimToWidth(ability.description().getString(), panelWidth - 32);
			context.drawTextWithShadow(textRenderer, Text.literal(desc), left + 26, y + 11, unlocked ? 0xFFB8B8B8 : 0xFF5A5A5A);
			if (hovered) {
				setTooltip(textRenderer.wrapLines(ability.description(), 220));
			}
		}

		// Extra lines under the list, as many as fit above the Done button.
		int infoY = top + abilities.size() * ROW_HEIGHT + 4;
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
		int left = panelLeft();
		List<Ability> abilities = Ability.forElement(tab);
		for (int i = 0; i < abilities.size(); i++) {
			int y = listTop() + i * ROW_HEIGHT;
			if (mouseX >= left && mouseX < left + panelWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
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

	@Override
	public boolean shouldPause() {
		return false;
	}
}
