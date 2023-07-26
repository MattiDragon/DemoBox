package io.github.mattidragon.demobox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import org.jetbrains.annotations.NotNull;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.config.CustomValuesConfig;
import xyz.nucleoid.plasmid.game.config.GameConfig;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.manager.GameSpaceManager;
import xyz.nucleoid.plasmid.game.manager.ManagedGameSpace;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DemoBoxGame {
    public static final GameType<Settings> TYPE = GameType.register(DemoBox.id("demo_box"), Settings.CODEC, DemoBoxGame::open);

    private final ServerWorld world;
    private final GameSpace gameSpace;
    private final Settings settings;

    public DemoBoxGame(ServerWorld world, GameSpace gameSpace, Settings settings) {
        this.world = world;
        this.gameSpace = gameSpace;
        this.settings = settings;
    }

    public static CompletableFuture<ManagedGameSpace> open(Settings settings) {
        var config = new GameConfig<>(null, TYPE, null, null, null, null, CustomValuesConfig.empty(), settings);
        return GameSpaceManager.get().open(config);
    }

    private static GameOpenProcedure open(GameOpenContext<Settings> context) {
       return context.openWithWorld(createWorldConfig(context.server().getRegistryManager()), (activity, world) -> {
           var instance = new DemoBoxGame(world, activity.getGameSpace(), context.config());
           instance.setup();
           activity.listen(GamePlayerEvents.OFFER, instance::onPlayerOffered);
           activity.listen(GamePlayerEvents.LEAVE, instance::onPlayerLeave);
       });
    }

    private void setup() {
        world.getStructureTemplateManager()
                .getTemplate(settings.structureId)
                .ifPresent(template -> {
                    var size = template.getSize();
                    var pos = new BlockPos(size.getX() / -2, 1, size.getZ() / -2);
                    template.place(world, pos, pos, new StructurePlacementData(), world.random, 0);
                });
        var server = world.getServer();
        var manager = server.getCommandFunctionManager();
        for (var id : settings.functions) {
            manager.getFunction(id).ifPresentOrElse(function -> manager.execute(function, new ServerCommandSource(server, Vec3d.ZERO, Vec2f.ZERO, world, 2, "DemoBox Setup", Text.literal("DemoBox Setup"), server, null).withSilent()),
                    () -> DemoBox.LOGGER.warn("Missing function: {}", id));
        }
    }

    private void onPlayerLeave(ServerPlayerEntity player) {
        if (gameSpace.getPlayers().stream().allMatch(player2 -> player2 != player)) {
            gameSpace.close(GameCloseReason.FINISHED);
        }
    }

    @NotNull
    private PlayerOfferResult onPlayerOffered(PlayerOffer offer) {
        return offer.accept(world, settings.playerPos);
    }

    @NotNull
    private static RuntimeWorldConfig createWorldConfig(DynamicRegistryManager registryManager) {
        var worldConfig = new RuntimeWorldConfig();
        worldConfig.setFlat(true);
        var generatorConfig = new FlatChunkGeneratorConfig(Optional.of(RegistryEntryList.of()), registryManager.get(RegistryKeys.BIOME).entryOf(BiomeKeys.PLAINS), List.of());
        generatorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.BARRIER));
        generatorConfig.updateLayerBlocks();
        worldConfig.setGenerator(new FlatChunkGenerator(generatorConfig));

        var disabledRules = Arrays.asList(GameRules.DO_DAYLIGHT_CYCLE, GameRules.DO_WEATHER_CYCLE, GameRules.DO_MOB_SPAWNING, GameRules.DO_PATROL_SPAWNING, GameRules.DO_INSOMNIA, GameRules.DO_TRADER_SPAWNING);
        for (var booleanRuleKey : disabledRules) {
            worldConfig.setGameRule(booleanRuleKey, false);
        }
        worldConfig.setSeed(1);
        return worldConfig;
    }

    public record Settings(Identifier structureId, Vec3d playerPos, List<Identifier> functions) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("structureId").forGetter(Settings::structureId),
                Vec3d.CODEC.fieldOf("playerPos").forGetter(Settings::playerPos),
                Identifier.CODEC.listOf().fieldOf("functions").forGetter(Settings::functions)
        ).apply(instance, Settings::new));
    }
}
