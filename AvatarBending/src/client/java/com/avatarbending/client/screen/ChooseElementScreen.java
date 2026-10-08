package com.avatarbending.client.screen;

import com.avatarbending.bending.Element;
import com.avatarbending.client.BendingHud;
import com.avatarbending.network.ModPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * "Choose your element" screen shown the first time a player joins.
 */
public class ChooseElementScreen extends Screen {
	private static final int GAP = 6;

	private int ticks;
	private int cardWidth = 96;
	private int cardHeight = 150;

	public ChooseElementScreen() {
		super(Text.translatable("screen.avatarbending.choose.title"));
	}

	private int cardX(int index) {
		int total = 4 * cardWidth + 3 * GAP;
		return (width - total) / 2 + index * (cardWidth + GAP);
	}

	private int cardY(int index) {
		return Math.max(36, (height - cardHeight - 30) / 2 + 12);
	}

	@Override
	protected void init() {
		// Always one row of four cards, shrunk to fit small windows.
		cardWidth = Math.min(96, (width - 16 - 3 * GAP) / 4);
		cardHeight = Math.min(150, height - 74);
		List<Element> elements = Element.BENDABLE;
		for (int i = 0; i < elements.size(); i++) {
			Element element = elements.get(i);
			int x = cardX(i);
			int y = cardY(i);
			addDrawableChild(ButtonWidget.builder(Text.translatable("screen.avatarbending.choose.button", element.displayName()),
					button -> choose(element))
				.dimensions(x + 6, y + cardHeight - 26, cardWidth - 12, 20)
				.build());
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.avatarbending.choose.later"), button -> close())
			.dimensions(width / 2 - 60, height - 26, 120, 20)
			.build());
	}

	private void choose(Element element) {
		ClientPlayNetworking.send(new ModPayloads.ChooseElementPayload(element.ordinal()));
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f));
		}
		close();
	}

	@Override
	public void tick() {
		ticks++;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		float time = ticks + delta;
		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, cardY(0) - 30, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.avatarbending.choose.subtitle").formatted(Formatting.GRAY),
			width / 2, cardY(0) - 18, 0xFFFFFFFF);

		List<Element> elements = Element.BENDABLE;
		for (int i = 0; i < elements.size(); i++) {
			Element element = elements.get(i);
			int x = cardX(i);
			int y = cardY(i);
			boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
			int color = element.color();
			context.fill(x, y, x + cardWidth, y + cardHeight, 0xB0101018);
			int border = BendingHud.withAlpha(color, hovered ? 0xFF : 0x90);
			context.fill(x, y, x + cardWidth, y + 2, border);
			context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border);
			context.fill(x, y, x + 2, y + cardHeight, border);
			context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border);

			int size = hovered ? 52 : 48;
			float bob = hovered ? MathHelper.sin(time * 0.3f) * 2 : 0;
			BendingHud.drawEmblem(context, element, x + (cardWidth - size) / 2, (int) (y + 8 + (52 - size) / 2 + bob), size);
			context.drawCenteredTextWithShadow(textRenderer, element.displayName().copy().formatted(Formatting.BOLD), x + cardWidth / 2, y + 64, 0xFFFFFFFF);
			List<OrderedText> lines = textRenderer.wrapLines(element.description(), cardWidth - 8);
			int maxLines = Math.max(0, (cardHeight - 78 - 28) / 10);
			for (int l = 0; l < lines.size() && l < maxLines; l++) {
				context.drawCenteredTextWithShadow(textRenderer, lines.get(l), x + cardWidth / 2, y + 78 + l * 10, 0xFFB8B8B8);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		List<Element> elements = Element.BENDABLE;
		for (int i = 0; i < elements.size(); i++) {
			int x = cardX(i);
			int y = cardY(i);
			if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight) {
				choose(elements.get(i));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
