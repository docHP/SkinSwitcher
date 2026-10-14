package dev.codex.skinswitcher.mixin.client;

import dev.codex.skinswitcher.client.skin.LocalSkinState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("TAIL"), cancellable = true)
    private void skinswitcher$applyLocalPreset(CallbackInfoReturnable<PlayerSkin> callback) {
        Minecraft client = Minecraft.getInstance();
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (client.player != null && player.getUUID().equals(client.player.getUUID())) {
            callback.setReturnValue(LocalSkinState.apply(callback.getReturnValue()));
        }
    }
}
