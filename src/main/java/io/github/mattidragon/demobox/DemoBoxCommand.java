package io.github.mattidragon.demobox;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.IntegerSuggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.command.GameCommand;
import xyz.nucleoid.plasmid.game.manager.GameSpaceManager;
import xyz.nucleoid.plasmid.game.player.GamePlayerJoiner;
import xyz.nucleoid.plasmid.util.Scheduler;

import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

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
                    .requires(source -> Permissions.check(source, "demobox", true))
                    .then(literal("leave")
                            .requires(source -> Permissions.check(source, "demobox.leave", true))
                            .executes(DemoBoxCommand::leaveGame))
                    .then(literal("open")
                            .requires(source -> Permissions.check(source, "demobox.open", 2))
                            .then(argument("template", IdentifierArgumentType.identifier())
                                    .suggests(STRUCTURE_SUGGESTION_PROVIDER)
                                    .executes(context -> execute(context.getSource(), IdentifierArgumentType.getIdentifier(context, "template"), new Vec3d(0.5, 2, 0.5), List.of(), List.of(), context))
                                    .then(argument("pos", Vec3ArgumentType.vec3())
                                            .executes(context -> execute(context.getSource(), IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), List.of(), List.of(), context))
                                            .then(argument("setupFunction", CommandFunctionArgumentType.commandFunction())
                                                    .suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                                    .executes(context -> execute(context.getSource(), IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), CommandFunctionArgumentType.getFunctions(context, "setupFunction"), List.of(), context))
                                                    .then(argument("onJoinFunction", CommandFunctionArgumentType.commandFunction())
                                                            .suggests(FunctionCommand.SUGGESTION_PROVIDER)
                                                            .executes(context -> execute(context.getSource(), IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), CommandFunctionArgumentType.getFunctions(context, "setupFunction"), CommandFunctionArgumentType.getFunctions(context, "onJoinFunction"), context))
                                                            .then(argument("permissionLevel", IntegerArgumentType.integer(0, 4))
                                                                    .executes(context -> execute(context.getSource(), IdentifierArgumentType.getIdentifier(context, "template"), Vec3ArgumentType.getVec3(context, "pos"), CommandFunctionArgumentType.getFunctions(context, "setupFunction"), CommandFunctionArgumentType.getFunctions(context, "onJoinFunction"), IntegerArgumentType.getInteger(context, "permissionLevel"))))))))));

        });
    }

    // Joinked from GameCommand because brigadier can't deal with childless redirects
    private static int leaveGame(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrThrow();

        var gameSpace = GameSpaceManager.get().byPlayer(player);
        if (gameSpace == null) {
            throw GameCommand.NOT_IN_GAME.create();
        }

        Scheduler.INSTANCE.submit(server -> {
            gameSpace.getPlayers().kick(player);
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int execute(ServerCommandSource source, Identifier structure, Vec3d pos, Collection<CommandFunction<ServerCommandSource>> functions, Collection<CommandFunction<ServerCommandSource>> onJoinFunctions, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrThrow();
        var server = player.getServer();
        return execute(source, structure, pos, functions, onJoinFunctions, server.getPermissionLevel(player.getGameProfile()));
    }
    private static int execute(ServerCommandSource source, Identifier structure, Vec3d pos, Collection<CommandFunction<ServerCommandSource>> functions, Collection<CommandFunction<ServerCommandSource>> onJoinFunctions, int permissionLevel) throws CommandSyntaxException {
        var player = source.getPlayerOrThrow();

        DemoBoxGame.open(new DemoBoxGame.Settings(structure, pos, functions.stream().map(CommandFunction::id).toList(), onJoinFunctions.stream().map(CommandFunction::id).toList(), permissionLevel))
                .thenAcceptAsync(gameSpace -> {
                    var space = GameSpaceManager.get().byPlayer(player);
                    if (space != null) space.getPlayers().kick(player);

                    var results = GamePlayerJoiner.tryJoin(player, gameSpace);
                    if (results.globalError != null || !results.playerErrors.isEmpty()) {
                        source.sendError(Text.translatable("command.demobox.open.fail"));
                    }
                }, player.getServer());
        return Command.SINGLE_SUCCESS;
    }
}
