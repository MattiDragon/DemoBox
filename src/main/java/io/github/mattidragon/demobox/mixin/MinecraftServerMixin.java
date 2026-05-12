package io.github.mattidragon.demobox.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import io.github.mattidragon.demobox.DemoBox;
import io.github.mattidragon.demobox.DemoBoxClickActions;
import io.github.mattidragon.demobox.DemoBoxConfigs;
import io.github.mattidragon.demobox.DemoConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Shadow
	public abstract SavedDataStorage getDataStorage();

	@Shadow
	public abstract PlayerList getPlayerList();

	@Inject(method = "handleCustomClickAction", at = @At("HEAD"))
	private void onHandleCustomClickAction(Identifier id, Optional<Tag> payload, CallbackInfo ci) {
		if (id.equals(DemoBoxClickActions.CREATE_UPDATE_DEMO)) {
			handleCreateUpdate(payload);
		}
	}

	@Unique
	private void handleCreateUpdate(Optional<Tag> payload) {
		if (payload.isEmpty()) {
			DemoBox.LOGGER.error("Received create/update demo click action with no payload");
			return;
		}
		var tag = payload.get();
		var ops = NbtOps.INSTANCE;

		var playerResult = UUIDUtil.CODEC.fieldOf("player").codec().decode(ops, tag).map(Pair::getFirst);
		var idResult = Identifier.CODEC.fieldOf("id").codec().decode(ops, tag).map(Pair::getFirst);
		var configResult = DemoConfig.DIALOG_CODEC.decode(ops, tag).map(Pair::getFirst);

		if (playerResult instanceof DataResult.Error<UUID> error) {
			DemoBox.LOGGER.error("Failed to decode player UUID from click action payload: {}", error.message());
			return;
		}
		if (idResult instanceof DataResult.Error<Identifier> error) {
			DemoBox.LOGGER.error("Failed to decode demo config ID from click action payload: {}", error.message());
			return;
		}
		if (configResult instanceof DataResult.Error<DemoConfig> error) {
			DemoBox.LOGGER.error("Failed to decode demo config from click action payload: {}", error.message());
			playerResult.result().map(getPlayerList()::getPlayer)
				.ifPresent(player -> player.sendSystemMessage(Component.translatable("demobox.config.invalid", error.message())));
			return;
		}

		getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE)
			.createOrUpdateConfig(idResult.getOrThrow(), configResult.getOrThrow());
	}
}
