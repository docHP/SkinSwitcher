package dev.codex.skinswitcher.mixin.client;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("userApiService")
    UserApiService skinswitcher$getUserApiService();
}
