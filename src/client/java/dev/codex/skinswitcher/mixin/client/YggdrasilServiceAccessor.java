package dev.codex.skinswitcher.mixin.client;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = YggdrasilUserApiService.class, remap = false)
public interface YggdrasilServiceAccessor {
    @Accessor(value = "minecraftClient", remap = false)
    MinecraftClient skinswitcher$getMinecraftClient();
}
