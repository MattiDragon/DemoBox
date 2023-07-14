package io.github.mattidragon.demobox.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.mattidragon.demobox.DemoBoxGame;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.plasmid.game.GameSpaceMetadata;
import xyz.nucleoid.plasmid.game.manager.ManagedGameSpace;

@Mixin(ManagedGameSpace.class)
public class ManagedGameSpaceMixin {
    @Shadow(remap = false) @Final private GameSpaceMetadata metadata;

    @ModifyExpressionValue(method = "onAddPlayer", at = @At(value = "INVOKE", target = "Lxyz/nucleoid/plasmid/game/GameTexts$Join;success(Lnet/minecraft/server/network/ServerPlayerEntity;)Lnet/minecraft/text/MutableText;"))
    private MutableText demobox$hideJoinMessageInDemo(MutableText original, ServerPlayerEntity player) {
        if (metadata.sourceConfig().type() == DemoBoxGame.TYPE) {
            return Text.translatable("demobox.demo.join", player.getDisplayName());
        }
        return original;
    }
}
