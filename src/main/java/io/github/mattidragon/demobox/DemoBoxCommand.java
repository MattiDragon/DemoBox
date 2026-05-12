package io.github.mattidragon.demobox;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Util;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.plasmid.api.game.player.GamePlayerJoiner;
import xyz.nucleoid.plasmid.api.game.player.JoinIntent;
import xyz.nucleoid.plasmid.api.util.Scheduler;
import xyz.nucleoid.plasmid.impl.game.manager.GameSpaceManagerImpl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static xyz.nucleoid.plasmid.impl.command.GameCommand.NOT_IN_GAME;

public class DemoBoxCommand {
	private static final SuggestionProvider<CommandSourceStack> DEMO_SUGGESTION_PROVIDER = (context, builder) -> {
		var configs = context.getSource().getServer().getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE);
		return SharedSuggestionProvider.suggestResource(configs.getConfigs().keySet(), builder);
	};

	public static void register() {
		// We need to run after plasmid to be able to redirect to their commands
		CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, DemoBox.id("after"));
		CommandRegistrationCallback.EVENT.register(DemoBox.id("after"), (dispatcher, _, _) -> {
			dispatcher.register(literal("demobox")
				.requires(Permissions.require("demobox", true))
				.then(literal("create")
					.requires(Permissions.require("demobox.edit", PermissionLevel.GAMEMASTERS))
					.executes(DemoBoxCommand::executeCreate))
				.then(literal("edit")
					.requires(Permissions.require("demobox.edit", PermissionLevel.GAMEMASTERS))
					.then(argument("id", IdentifierArgument.id())
						.suggests(DEMO_SUGGESTION_PROVIDER)
						.executes(DemoBoxCommand::executeEdit)))
				.then(literal("delete")
					.requires(Permissions.require("demobox.edit", PermissionLevel.GAMEMASTERS))
					.then(argument("id", IdentifierArgument.id())
						.suggests(DEMO_SUGGESTION_PROVIDER)
						.executes(DemoBoxCommand::executeDelete)))

				.then(literal("leave")
					.requires(Permissions.require("demobox.leave", true))
					.executes(DemoBoxCommand::executeLeave))
				.then(literal("open")
					.requires(Permissions.require("demobox.open", PermissionLevel.GAMEMASTERS))
					.then(argument("id", IdentifierArgument.id())
						.suggests(DEMO_SUGGESTION_PROVIDER)
						.executes(DemoBoxCommand::executeOpenNamed)))
				.then(literal("sign")
					.requires(Permissions.require("demobox.sign", PermissionLevel.GAMEMASTERS))
					.then(argument("signPos", BlockPosArgument.blockPos())
						.then(argument("id", IdentifierArgument.id())
							.suggests(DEMO_SUGGESTION_PROVIDER)
							.executes(DemoBoxCommand::executeSignNamed))))
			);
		});
	}

	private static int executeCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var player = context.getSource().getPlayerOrException();

		var dialog = new ConfirmationDialog(
			new CommonDialogData(
				Component.translatable("demobox.dialog.create.title"),
				Optional.empty(),
				true,
				true,
				DialogAction.CLOSE,
				List.of(),
				List.of(
					new Input("id", new TextInput(
						200,
						Component.translatable("demobox.dialog.field.id"),
						true,
						"",
						128,
						Optional.empty())),
					new Input("structure", new TextInput(200,
						Component.translatable("demobox.dialog.field.structure"),
						true,
						"",
						128,
						Optional.empty())),
					new Input("spawn_pos", new TextInput(200,
						Component.translatable("demobox.dialog.field.pos"),
						true,
						"0 2 0",
						32,
						Optional.empty())),
					new Input("setup_commands", new TextInput(200,
						Component.translatable("demobox.dialog.field.setup_commands"),
						true,
						"",
						1024,
						Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.empty())))),
					new Input("join_commands", new TextInput(200,
						Component.translatable("demobox.dialog.field.join_commands"),
						true,
						"",
						1024,
						Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.empty()))))
				)
			),
			new ActionButton(new CommonButtonData(CommonComponents.GUI_DONE, 100), Optional.of(new CustomAll(
				DemoBoxClickActions.CREATE_UPDATE_DEMO,
				Optional.of(Util.make(new CompoundTag(), tag ->
					tag.put("player", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, player.getUUID()).getOrThrow())))
			))),
			new ActionButton(new CommonButtonData(CommonComponents.GUI_CANCEL, 100), Optional.empty())
		);
		player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
		return Command.SINGLE_SUCCESS;
	}

	private static int executeEdit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var id = IdentifierArgument.getId(context, "id");
		var source = context.getSource();
		var configs = source.getServer().getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE);
		var config = configs.getConfig(id);
		if (config == null) {
			source.sendFailure(Component.translatable("demobox.demo.not_found", id.toString()));
			return 0;
		}

		var player = source.getPlayerOrException();

		var dialog = new ConfirmationDialog(
			new CommonDialogData(
				Component.translatable("demobox.dialog.edit.title"),
				Optional.empty(),
				true,
				true,
				DialogAction.CLOSE,
				List.of(),
				List.of(
					new Input("structure", new TextInput(200,
						Component.translatable("demobox.dialog.field.structure"),
						true,
						config.structure().toString(),
						128,
						Optional.empty())),
					new Input("spawn_pos", new TextInput(200,
						Component.translatable("demobox.dialog.field.pos"),
						true,
						"%s %s %s".formatted(config.spawnPos().x, config.spawnPos().y, config.spawnPos().z),
						32,
						Optional.empty())),
					new Input("setup_commands", new TextInput(200,
						Component.translatable("demobox.dialog.field.setup_commands"),
						true,
						config.setupCommands(),
						1024,
						Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.empty())))),
					new Input("join_commands", new TextInput(200,
						Component.translatable("demobox.dialog.field.join_commands"),
						true,
						config.joinCommands(),
						1024,
						Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.empty()))))
				)
			),
			new ActionButton(new CommonButtonData(CommonComponents.GUI_DONE, 100), Optional.of(new CustomAll(
				DemoBoxClickActions.CREATE_UPDATE_DEMO,
				Optional.of(Util.make(new CompoundTag(), tag -> {
					tag.put("player", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, player.getUUID()).getOrThrow());
					tag.putString("id", id.toString());
				}))
			))),
			new ActionButton(new CommonButtonData(CommonComponents.GUI_CANCEL, 100), Optional.empty())
		);
		player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
		return Command.SINGLE_SUCCESS;
	}

	private static int executeDelete(CommandContext<CommandSourceStack> context) {
		var id = IdentifierArgument.getId(context, "id");
		var source = context.getSource();
		var configs = source.getServer().getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE);
		var removed = configs.removeConfig(id);

		if (removed) {
			source.sendSuccess(() -> Component.translatable("demobox.demo.deleted", id.toString()), true);
			return Command.SINGLE_SUCCESS;
		} else {
			source.sendFailure(Component.translatable("demobox.demo.not_found", id.toString()));
			return 0;
		}
	}

	private static int executeOpenNamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var id = IdentifierArgument.getId(context, "id");
		var source = context.getSource();
		var player = source.getPlayerOrException();
		var configs = source.getServer().getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE);
		var config = configs.getConfig(id);
		if (config == null) {
			source.sendFailure(Component.translatable("demobox.demo.not_found", id.toString()));
			return 0;
		}

		DemoBoxGame.open(config).thenAcceptAsync(gameSpace -> {
			var space = GameSpaceManager.get().byPlayer(player);
			if (space != null) space.getPlayers().kick(player);

			var results = GamePlayerJoiner.tryJoin(player, gameSpace, JoinIntent.PLAY);
			if (results.error() != null) {
				source.sendFailure(Component.translatable("command.demobox.open.fail"));
			}
		}, source.getServer());

		return Command.SINGLE_SUCCESS;
	}

	private static int executeSignNamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var id = IdentifierArgument.getId(context, "id");
		var source = context.getSource();

		var signPos = BlockPosArgument.getLoadedBlockPos(context, "signPos");
		var block = source.getLevel().getBlockState(signPos).getBlock();
		if (!(source.getLevel().getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
			source.sendFailure(Component.translatable("command.demobox.sign.missing"));
			return 0;
		}

		UnaryOperator<SignText> textChanger = _ -> {
			var clickEvent = new ClickEvent.RunCommand("demobox open " + id);
			Component[] texts;
			if (block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock) {
				texts = new Component[]{
					Component.translatable("demobox.hanging_sign.line1").withStyle(ChatFormatting.GREEN)
						.withStyle(style -> style.withClickEvent(clickEvent)),
					Component.translatable("demobox.hanging_sign.line2").withStyle(ChatFormatting.GREEN),
					Component.translatable("demobox.hanging_sign.line3").withStyle(ChatFormatting.GREEN),
					CommonComponents.EMPTY
				};
			} else {
				texts = new Component[]{
					CommonComponents.EMPTY,
					Component.translatable("demobox.sign.line1").withStyle(ChatFormatting.GREEN)
						.withStyle(style -> style.withClickEvent(clickEvent)),
					Component.translatable("demobox.sign.line2").withStyle(ChatFormatting.GREEN),
					CommonComponents.EMPTY
				};
			}
			return new SignText(
				texts,
				texts,
				DyeColor.LIME,
				true
			);
		};
		sign.updateText(textChanger, true);
		sign.updateText(textChanger, false);
		sign.setWaxed(true);

		source.sendSuccess(() -> Component.translatable("command.demobox.sign.success"), true);

		return Command.SINGLE_SUCCESS;
	}

	// Joinked from GameCommand because brigadier can't deal with childless redirects
	private static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		var source = context.getSource();
		var player = source.getPlayerOrException();

		var gameSpace = GameSpaceManagerImpl.get().byPlayer(player);
		if (gameSpace == null) {
			throw NOT_IN_GAME.create();
		}

		Scheduler.INSTANCE.submit(_ -> {
			gameSpace.getPlayers().kick(player);
		});

		return Command.SINGLE_SUCCESS;
	}

	@FunctionalInterface
	private interface CommandHandler {
		int execute(CommandContext<CommandSourceStack> context, Identifier structure, Vec3 pos, Collection<CommandFunction<CommandSourceStack>> functions, Collection<CommandFunction<CommandSourceStack>> playerFunctions) throws CommandSyntaxException;
	}
}
