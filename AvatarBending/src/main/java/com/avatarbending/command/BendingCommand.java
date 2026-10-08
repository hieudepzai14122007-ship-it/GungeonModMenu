package com.avatarbending.command;

import com.avatarbending.bending.Ability;
import com.avatarbending.bending.AvatarState;
import com.avatarbending.bending.BenderData;
import com.avatarbending.bending.BendingManager;
import com.avatarbending.bending.Element;
import com.avatarbending.item.AvatarSpiritItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /bending choose|info|avatar|reset|set|state|refill|cast
 */
public final class BendingCommand {
	private static final SuggestionProvider<ServerCommandSource> ELEMENTS = (context, builder) ->
		CommandSource.suggestMatching(Element.BENDABLE.stream().map(Element::id), builder);
	private static final SuggestionProvider<ServerCommandSource> ABILITIES = (context, builder) ->
		CommandSource.suggestMatching(java.util.Arrays.stream(Ability.values()).map(Ability::id), builder);

	private BendingCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("bending")
			.then(literal("choose")
				.then(argument("element", StringArgumentType.word()).suggests(ELEMENTS)
					.executes(ctx -> choose(ctx, ctx.getSource().getPlayerOrThrow(), false))))
			.then(literal("info")
				.executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
				.then(argument("player", EntityArgumentType.player())
					.executes(ctx -> info(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
			.then(literal("avatar").requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> avatar(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
				.then(argument("player", EntityArgumentType.player())
					.executes(ctx -> avatar(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
			.then(literal("reset").requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> reset(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
				.then(argument("player", EntityArgumentType.player())
					.executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
			.then(literal("set").requires(source -> source.hasPermissionLevel(2))
				.then(argument("player", EntityArgumentType.player())
					.then(argument("element", StringArgumentType.word()).suggests(ELEMENTS)
						.executes(ctx -> choose(ctx, EntityArgumentType.getPlayer(ctx, "player"), true)))))
			.then(literal("state").requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> state(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
			.then(literal("refill").requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> refill(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
			.then(literal("cast").requires(source -> source.hasPermissionLevel(2))
				.then(argument("ability", StringArgumentType.word()).suggests(ABILITIES)
					.executes(ctx -> cast(ctx, ctx.getSource().getPlayerOrThrow()))
					.then(argument("player", EntityArgumentType.player())
						.executes(ctx -> cast(ctx, EntityArgumentType.getPlayer(ctx, "player")))))));
	}

	private static int choose(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, boolean force) {
		String id = StringArgumentType.getString(ctx, "element").toLowerCase(Locale.ROOT);
		Element element = Element.byId(id);
		if (element == null || !Element.BENDABLE.contains(element)) {
			ctx.getSource().sendError(Text.translatable("command.avatarbending.unknown_element", id));
			return 0;
		}
		if (!BendingManager.chooseElement(player, element, force)) {
			return 0;
		}
		if (force) {
			ctx.getSource().sendFeedback(() -> Text.translatable("command.avatarbending.set", player.getDisplayName(), element.displayName()), true);
		}
		return 1;
	}

	private static int info(ServerCommandSource source, ServerPlayerEntity player) {
		BenderData data = BendingManager.get(player);
		if (!data.hasBending()) {
			source.sendFeedback(() -> Text.translatable("command.avatarbending.info.none", player.getDisplayName()), false);
			return 0;
		}
		MutableText elements = Text.empty();
		for (Element element : data.availableElements()) {
			if (!elements.getSiblings().isEmpty()) {
				elements.append(Text.literal(", ").formatted(Formatting.GRAY));
			}
			elements.append(element.displayName());
		}
		source.sendFeedback(() -> Text.translatable("command.avatarbending.info", player.getDisplayName(), elements,
			Math.round(data.chi()), data.isAvatar() ? Text.translatable("gui.yes") : Text.translatable("gui.no")), false);
		Ability selected = data.selectedAbility();
		if (selected != null) {
			source.sendFeedback(() -> Text.translatable("command.avatarbending.info.selected", selected.displayName()), false);
		}
		return 1;
	}

	private static int avatar(ServerCommandSource source, ServerPlayerEntity player) {
		if (BendingManager.get(player).isAvatar()) {
			source.sendError(Text.translatable("message.avatarbending.already_avatar"));
			return 0;
		}
		AvatarSpiritItem.becomeAvatar(player);
		source.sendFeedback(() -> Text.translatable("command.avatarbending.avatar", player.getDisplayName()), true);
		return 1;
	}

	private static int reset(ServerCommandSource source, ServerPlayerEntity player) {
		BendingManager.reset(player);
		source.sendFeedback(() -> Text.translatable("command.avatarbending.reset", player.getDisplayName()), true);
		return 1;
	}

	private static int state(ServerCommandSource source, ServerPlayerEntity player) throws CommandSyntaxException {
		BenderData data = BendingManager.get(player);
		if (!data.isAvatar()) {
			source.sendError(Text.translatable("message.avatarbending.not_avatar"));
			return 0;
		}
		data.setAvatarStateCooldown(0);
		if (!data.inAvatarState()) {
			AvatarState.enter(player, data);
		}
		return 1;
	}

	/** Casts any spell as the player, for free (handy for command blocks and adventure maps). */
	private static int cast(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		String id = StringArgumentType.getString(ctx, "ability").toLowerCase(Locale.ROOT);
		for (Ability ability : Ability.values()) {
			if (ability.id().equals(id)) {
				return BendingManager.cast(player, ability) ? 1 : 0;
			}
		}
		ctx.getSource().sendError(Text.translatable("command.avatarbending.unknown_ability", id));
		return 0;
	}

	private static int refill(ServerCommandSource source, ServerPlayerEntity player) {
		BenderData data = BendingManager.get(player);
		data.setChi(BenderData.MAX_CHI);
		data.clearCooldowns();
		data.setAvatarStateCooldown(0);
		BendingManager.sync(player, true);
		source.sendFeedback(() -> Text.translatable("command.avatarbending.refill"), false);
		return 1;
	}
}
