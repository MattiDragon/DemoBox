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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.plasmid.api.game.GameSpaceManager;
import xyz.nucleoid.plasmid.api.game.player.GamePlayerJoiner;
import xyz.nucleoid.plasmid.api.game.player.JoinIntent;
import xyz.nucleoid.plasmid.api.util.Scheduler;
import xyz.nucleoid.plasmid.impl.game.manager.GameSpaceManagerImpl;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static xyz.nucleoid.plasmid.impl.command.GameCommand.NOT_IN_GAME;

public class DemoBoxCommand {
    private static final SuggestionProvider<CommandSourceStack> STRUCTURE_SUGGESTION_PROVIDER = (context, builder) -> {
        StructureTemplateManager structureTemplateManager = context.getSource().getLevel().getStructureManager();
        return SharedSuggestionProvider.suggestResource(structureTemplateManager.listTemplates(), builder);
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
                            .then(argument("signPos", BlockPosArgument.blockPos())
                                    .then(buildArgTree(DemoBoxCommand::executeSign))))
            );
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> buildArgTree(CommandHandler handler) {
        return argument("template", ResourceLocationArgument.id())
                .suggests(STRUCTURE_SUGGESTION_PROVIDER)
                .executes(context -> handler.execute(context, ResourceLocationArgument.getId(context, "template"), new Vec3(0.5, 2, 0.5), List.of(), List.of()))
                .then(argument("pos", Vec3Argument.vec3())
                        .executes(context -> handler.execute(context, ResourceLocationArgument.getId(context, "template"), Vec3Argument.getVec3(context, "pos"), List.of(), List.of()))
                        .then(argument("setupFunction", FunctionArgument.functions())
                                .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                .executes(context -> handler.execute(context, ResourceLocationArgument.getId(context, "template"), Vec3Argument.getVec3(context, "pos"), FunctionArgument.getFunctions(context, "setupFunction"), List.of()))
                            .then(
                                argument("playerFunction", FunctionArgument.functions())
                                    .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                    .executes(context -> handler.execute(context, ResourceLocationArgument.getId(context, "template"), Vec3Argument.getVec3(context, "pos"), FunctionArgument.getFunctions(context, "setupFunction"), FunctionArgument.getFunctions(context, "playerFunction"))))));
    }

    // Joinked from GameCommand because brigadier can't deal with childless redirects
    private static int executeLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();

        var gameSpace = GameSpaceManagerImpl.get().byPlayer(player);
        if (gameSpace == null) {
            throw NOT_IN_GAME.create();
        }

        Scheduler.INSTANCE.submit(server -> {
            gameSpace.getPlayers().kick(player);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int executeSign(CommandContext<CommandSourceStack> context, ResourceLocation structure, Vec3 pos, Collection<CommandFunction<CommandSourceStack>> functions,  Collection<CommandFunction<CommandSourceStack>> playerFunctions) throws CommandSyntaxException {
        var signPos = BlockPosArgument.getLoadedBlockPos(context, "signPos");
        var source = context.getSource();
        var block = source.getLevel().getBlockState(signPos).getBlock();
        if (!(source.getLevel().getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
            source.sendFailure(Component.translatable("command.demobox.sign.missing"));
            return 0;
        }

        var command = "demobox open " + getCommandEnd(context);

        UnaryOperator<SignText> textChanger = text -> {
            var clickEvent = new ClickEvent.RunCommand(command);
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
        return 1;
    }

    /**
     * Extracts the argument text from the sign command for use within the sign text.
     */
    private static String getCommandEnd(CommandContext<CommandSourceStack> context) {
        var nodes = context.getNodes();
        ParsedCommandNode<CommandSourceStack> mainNode = null;
        for (var i = nodes.size() - 1; i >= 0; i--) {
            var node = nodes.get(i);
            if (node.getNode() instanceof LiteralCommandNode<CommandSourceStack> literal && literal.getLiteral().equals("sign")) {
                mainNode = nodes.get(i + 2);
                break;
            }
        }
        if (mainNode == null) {
            throw new IllegalStateException("Cannot find node in parsed command (weird hacks going on???)");
        }
        return context.getInput().substring(mainNode.getRange().getStart());
    }

    private static int executeOpen(CommandContext<CommandSourceStack> context, ResourceLocation structure, Vec3 pos, Collection<CommandFunction<CommandSourceStack>> functions, Collection<CommandFunction<CommandSourceStack>> playerFunctions) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();

        DemoBoxGame.open(new DemoBoxGame.Settings(structure, pos, functions.stream().map(CommandFunction::id).toList(), playerFunctions.stream().map(CommandFunction::id).toList()))
                .thenAcceptAsync(gameSpace -> {
                    var space = GameSpaceManager.get().byPlayer(player);
                    if (space != null) space.getPlayers().kick(player);

                    var results = GamePlayerJoiner.tryJoin(player, gameSpace, JoinIntent.PLAY);
                    if (results.error() != null) {
                        source.sendFailure(Component.translatable("command.demobox.open.fail"));
                    }
                }, player.getServer());
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface CommandHandler {
        int execute(CommandContext<CommandSourceStack> context, ResourceLocation structure, Vec3 pos, Collection<CommandFunction<CommandSourceStack>> functions, Collection<CommandFunction<CommandSourceStack>> playerFunctions) throws CommandSyntaxException;
    }
}
