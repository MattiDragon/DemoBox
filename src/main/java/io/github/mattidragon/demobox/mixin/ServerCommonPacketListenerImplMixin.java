package io.github.mattidragon.demobox.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import io.github.mattidragon.demobox.DemoBox;
import io.github.mattidragon.demobox.DemoBoxClickActions;
import io.github.mattidragon.demobox.DemoBoxConfigs;
import io.github.mattidragon.demobox.DemoConfig;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {
	@Shadow
	@Final
	protected MinecraftServer server;

	@Inject(method = "handleCustomClickAction", at = @At("TAIL"))
	private void onHandleCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		if (packet.id().equals(DemoBoxClickActions.CREATE_UPDATE_DEMO)) {
			//noinspection ConstantValue
			if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) {
				return;
			}
			if (Permissions.check(gameListener.player, "demobox.edit", PermissionLevel.GAMEMASTERS)) {
				handleCreateUpdate(packet.payload(), gameListener.player);
			} else {
				DemoBox.LOGGER.warn("Player {} attempted to create/update a demo config without permission", gameListener.player.getName().getString());
			}
		}
	}

	@Unique
	private void handleCreateUpdate(Optional<Tag> payload, ServerPlayer player) {
		if (payload.isEmpty()) {
			DemoBox.LOGGER.error("Received create/update demo click action with no payload");
			return;
		}
		var tag = payload.get();
		var ops = NbtOps.INSTANCE;

		var idResult = Identifier.CODEC.fieldOf("id").codec().decode(ops, tag).map(Pair::getFirst);
		var configResult = DemoConfig.DIALOG_CODEC.decode(ops, tag).map(Pair::getFirst);

		if (idResult instanceof DataResult.Error<Identifier> error) {
			DemoBox.LOGGER.error("Failed to decode demo config ID from click action payload: {}", error.message());
			return;
		}
		if (configResult instanceof DataResult.Error<DemoConfig> error) {
			DemoBox.LOGGER.error("Failed to decode demo config from click action payload: {}", error.message());
			player.sendSystemMessage(Component.translatable("demobox.config.invalid", error.message()));
			return;
		}

		server.getDataStorage().computeIfAbsent(DemoBoxConfigs.TYPE)
			.createOrUpdateConfig(idResult.getOrThrow(), configResult.getOrThrow());
	}
}
