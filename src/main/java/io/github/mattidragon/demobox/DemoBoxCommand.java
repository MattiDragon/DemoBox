package io.github.mattidragon.demobox;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.plasmid.api.game.player.GamePlayerJoiner;
import xyz.nucleoid.plasmid.api.game.player.JoinIntent;
import xyz.nucleoid.plasmid.api.util.Scheduler;
import xyz.nucleoid.plasmid.impl.game.manager.GameSpaceManagerImpl;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static xyz.nucleoid.plasmid.impl.command.GameCommand.NOT_IN_GAME;

public class DemoBoxCommand {
    private static final SuggestionProvider<ServerCommandSource> STRUCTURE_SUGGESTION_PROVIDER = (context, builder) -> {
        StructureTemplateManager structureTemplateManager = context.getSource().getWorld().getStructureTemplateManager();
        return CommandSource.suggestIdentifiers(structureTemplateManager.streamTemplates(), builder);
    };

    public static void register() {
        // We need to run after plasmid to be able to redirect to their commands
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, DemoBox.id("after"));
        CommandRegistrationCallback.EVENT.register(DemoBox.id("after"), (dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("demobox")
                    .requires(Permissions.require("demobox", true))
                    .then(literal("leave")
                            .requires(Permissions.require("demobox.leave", true))
                            .executes(DemoBoxCommand::executeLeave))
                    .then(literal("open")
                            .requires(Permissions.require("demobox.open", 2))
                            .then(buildArgTree(DemoBoxCommand::executeOpen)))
                    .then(literal("sign")
                            .requires(Permissions.require("demobox.sign", 2))
                            .then(argument("signPos", BlockPosArgumentType.blockPos())
                                    .then(buildArgTree(DemoBoxCommand::executeSign))))
            );
        });
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Identifier> buildArgTree(CommandHandler handler) {
        return argument("template", IdentifierArgumentType.identifier())
                .suggests(STRUCTURE_SUGGESTION_PROVIDER)
                .executes(context -> handler.execute(context, IdentifierArgumentType.getIdentifier(context, "template"), new Vec3d(0.5, 2, 0.5), List.of(), List.of()))
                .then(argument("pos", Vec3ArgumentType.vec3())
                        .executes(context -> handler.execute(context, IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), List.of(), List.of()))
                        .then(argument("setupFunction", CommandFunctionArgumentType.commandFunction())
                                .suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                .executes(context -> handler.execute(context, IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), CommandFunctionArgumentType.getFunctions(context, "setupFunction"), List.of()))
                            .then(
                                argument("playerFunction", CommandFunctionArgumentType.commandFunction())
                                    .suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                    .executes(context -> handler.execute(context, IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), CommandFunctionArgumentType.getFunctions(context, "setupFunction"), CommandFunctionArgumentType.getFunctions(context, "playerFunction"))))));
    }

    // Joinked from GameCommand because brigadier can't deal with childless redirects
    private static int executeLeave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrThrow();

        var gameSpace = GameSpaceManagerImpl.get().byPlayer(player);
        if (gameSpace == null) {
            throw NOT_IN_GAME.create();
        }

        Scheduler.INSTANCE.submit(server -> {
            gameSpace.getPlayers().kick(player);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeSign(CommandContext<ServerCommandSource> context, Identifier structure, Vec3d pos, Collection<CommandFunction<ServerCommandSource>> functions,  Collection<CommandFunction<ServerCommandSource>> playerFunctions) throws CommandSyntaxException {
        var signPos = BlockPosArgumentType.getLoadedBlockPos(context, "signPos");
        var source = context.getSource();
        var block = source.getWorld().getBlockState(signPos).getBlock();
        if (!(source.getWorld().getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
            source.sendError(Text.translatable("command.demobox.sign.missing"));
            return 0;
        }

        var command = "demobox open " + getCommandEnd(context);

        UnaryOperator<SignText> textChanger = text -> {
            var clickEvent = new ClickEvent.RunCommand(command);
            Text[] texts;
            if (block instanceof HangingSignBlock || block instanceof WallHangingSignBlock) {
                texts = new Text[]{
                        Text.translatable("demobox.hanging_sign.line1").formatted(Formatting.GREEN)
                                .styled(style -> style.withClickEvent(clickEvent)),
                        Text.translatable("demobox.hanging_sign.line2").formatted(Formatting.GREEN),
                        Text.translatable("demobox.hanging_sign.line3").formatted(Formatting.GREEN),
                        ScreenTexts.EMPTY
                };
            } else {
                texts = new Text[]{
                        ScreenTexts.EMPTY,
                        Text.translatable("demobox.sign.line1").formatted(Formatting.GREEN)
                                .styled(style -> style.withClickEvent(clickEvent)),
                        Text.translatable("demobox.sign.line2").formatted(Formatting.GREEN),
                        ScreenTexts.EMPTY
                };
            }
            return new SignText(
                    texts,
                    texts,
                    DyeColor.LIME,
                    true
            );
        };
        sign.changeText(textChanger, true);
        sign.changeText(textChanger, false);
        sign.setWaxed(true);

        source.sendFeedback(() -> Text.translatable("command.demobox.sign.success"), true);
        return 1;
    }

    /**
     * Extracts the argument text from the sign command for use within the sign text.
     */
    private static String getCommandEnd(CommandContext<ServerCommandSource> context) {
        var nodes = context.getNodes();
        ParsedCommandNode<ServerCommandSource> mainNode = null;
        for (var i = nodes.size() - 1; i >= 0; i--) {
            var node = nodes.get(i);
            if (node.getNode() instanceof LiteralCommandNode<ServerCommandSource> literal && literal.getLiteral().equals("sign")) {
                mainNode = nodes.get(i + 2);
                break;
            }
        }
        if (mainNode == null) {
            throw new IllegalStateException("Cannot find node in parsed command (weird hacks going on???)");
        }
        return context.getInput().substring(mainNode.getRange().getStart());
    }

    private static int executeOpen(CommandContext<ServerCommandSource> context, Identifier structure, Vec3d pos, Collection<CommandFunction<ServerCommandSource>> functions, Collection<CommandFunction<ServerCommandSource>> playerFunctions) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrThrow();

        DemoBoxGame.open(new DemoBoxGame.Settings(structure, pos, functions.stream().map(CommandFunction::id).toList(), playerFunctions.stream().map(CommandFunction::id).toList()))
                .thenAcceptAsync(gameSpace -> {
                    var space = GameSpaceManager.get().byPlayer(player);
                    if (space != null) space.getPlayers().kick(player);

                    var results = GamePlayerJoiner.tryJoin(player, gameSpace, JoinIntent.PLAY);
                    if (results.error() != null) {
                        source.sendError(Text.translatable("command.demobox.open.fail"));
                    }
                }, player.getServer());
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface CommandHandler {
        int execute(CommandContext<ServerCommandSource> context, Identifier structure, Vec3d pos, Collection<CommandFunction<ServerCommandSource>> functions, Collection<CommandFunction<ServerCommandSource>> playerFunctions) throws CommandSyntaxException;
    }
}
