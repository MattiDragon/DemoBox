package io.github.mattidragon.demobox;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.*;
import xyz.nucleoid.plasmid.api.game.config.CustomValuesConfig;
import xyz.nucleoid.plasmid.api.game.config.GameConfig;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.JoinOfferResult;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class DemoBoxGame {
    public static final GameType<Settings> TYPE = GameType.register(DemoBox.id("demo_box"), Settings.CODEC, DemoBoxGame::open);

    private final ServerLevel world;
    private final GameSpace gameSpace;
    private final Settings settings;

    public DemoBoxGame(ServerLevel world, GameSpace gameSpace, Settings settings) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.settings = settings;
    }

    public static CompletableFuture<GameSpace> open(Settings settings) {
        var config = new GameConfig<>(TYPE, null, null, null, null, CustomValuesConfig.empty(), settings);
        return GameSpaceManager.get().open(Holder.direct(config));
    }

    private static GameOpenProcedure open(GameOpenContext<Settings> context) {
       return context.openWithWorld(createWorldConfig(context.server().registryAccess()), (activity, world) -> {
           var instance = new DemoBoxGame(world, activity.getGameSpace(), context.config());
           instance.setup();
           activity.listen(GamePlayerEvents.OFFER, instance::onPlayerOffered);
           activity.listen(GamePlayerEvents.ACCEPT, instance::onPlayerAccepted);
           activity.listen(GamePlayerEvents.LEAVE, instance::onPlayerLeave);
           activity.listen(GamePlayerEvents.JOIN, instance::onPlayerJoin);
           activity.listen(GamePlayerEvents.JOIN_MESSAGE, instance::onJoinMessage);
           activity.listen(GamePlayerEvents.LEAVE_MESSAGE, instance::onLeaveMessage);
           activity.listen(PlayerDeathEvent.EVENT, (player, source) -> {
               instance.gameSpace.getPlayers().kick(player);
               player.sendSystemMessage(Component.translatable("demobox.demo.death").withStyle(ChatFormatting.RED));
               return EventResult.DENY;
           });
       });
    }

    private void setup() {
        world.getStructureManager()
                .get(settings.structureId)
                .ifPresent(template -> {
                    var size = template.getSize();
                    var pos = new BlockPos(size.getX() / -2, 1, size.getZ() / -2);
                    template.placeInWorld(world, pos, pos, new StructurePlaceSettings(), world.random, 0);
                });
        executeFunctions(settings.functions, null);
    }

    private void onPlayerLeave(ServerPlayer player) {
        if (gameSpace.getPlayers().stream().allMatch(player2 -> player2 != player)) {
            gameSpace.close(GameCloseReason.FINISHED);
        }
    }

    private void onPlayerJoin(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("demobox.info.1").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("demobox.info.2").withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.translatable("demobox.info.3").withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.translatable("demobox.info.4").withStyle(ChatFormatting.WHITE));
        executeFunctions(settings.playerFunctions, player);
    }

    private Component onJoinMessage(ServerPlayer player, @Nullable Component currentText, Component defaultText) {
        return Component.translatable("demobox.demo.join", player.getDisplayName()).withStyle(ChatFormatting.YELLOW);
    }

    private Component onLeaveMessage(ServerPlayer player, @Nullable Component currentText, Component defaultText) {
        return Component.translatable("demobox.demo.leave", player.getDisplayName()).withStyle(ChatFormatting.YELLOW);
    }

    private JoinOfferResult onPlayerOffered(JoinOffer offer) {
        return offer.accept();
    }

    private JoinAcceptorResult onPlayerAccepted(JoinAcceptor joinAcceptor) {
        return joinAcceptor.teleport(world, settings.playerPos);
    }

    private void executeFunctions(List<ResourceLocation> functions, Entity entity) {
        var server = world.getServer();
        var manager = server.getFunctions();
        for (var id : functions) {
            manager.get(id).ifPresentOrElse(
                function -> manager.execute(
                    function,
                    new CommandSourceStack(server, Vec3.ZERO, Vec2.ZERO, world, 2, "DemoBox Setup", Component.literal("DemoBox Setup"), server, entity).withSuppressedOutput()
                ),
                () -> DemoBox.LOGGER.warn("Missing function: {}", id)
            );
        }
    }

    @NotNull
    private static RuntimeWorldConfig createWorldConfig(RegistryAccess registryManager) {
        var worldConfig = new RuntimeWorldConfig();
        worldConfig.setFlat(true);
        var generatorConfig = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), registryManager.getOrThrow(Biomes.PLAINS), List.of());
        generatorConfig.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BARRIER));
        generatorConfig.updateLayers();
        worldConfig.setGenerator(new FlatLevelSource(generatorConfig));

        var disabledRules = Arrays.asList(GameRules.RULE_DAYLIGHT, GameRules.RULE_WEATHER_CYCLE, GameRules.RULE_DOMOBSPAWNING, GameRules.RULE_DO_PATROL_SPAWNING, GameRules.RULE_DOINSOMNIA, GameRules.RULE_DO_TRADER_SPAWNING);
        for (var booleanRuleKey : disabledRules) {
            worldConfig.setGameRule(booleanRuleKey, false);
        }
        worldConfig.setSeed(1);
        return worldConfig;
    }

    public record Settings(ResourceLocation structureId, Vec3 playerPos, List<ResourceLocation> functions, List<ResourceLocation> playerFunctions) {
        public static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("structureId").forGetter(Settings::structureId),
                Vec3.CODEC.fieldOf("playerPos").forGetter(Settings::playerPos),
                ResourceLocation.CODEC.listOf().fieldOf("functions").forGetter(Settings::functions),
                ResourceLocation.CODEC.listOf().fieldOf("playerFunctions").forGetter(Settings::playerFunctions)
        ).apply(instance, Settings::new));
    }
}
