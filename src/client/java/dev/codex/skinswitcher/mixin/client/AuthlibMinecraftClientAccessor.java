package dev.codex.skinswitcher.mixin.client;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MinecraftClient.class, remap = false)
public interface AuthlibMinecraftClientAccessor {
    @Accessor(value = "accessToken", remap = false)
    String skinswitcher$getAccessToken();
}
