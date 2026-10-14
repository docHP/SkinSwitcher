package dev.codex.skinswitcher.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class LocalSkinState {
    private static volatile ClientAsset.Texture activeTexture;
    private static volatile net.minecraft.world.entity.player.PlayerModelType activeModel;

    private LocalSkinState() {
    }

    public static void load(SkinPreset preset, Consumer<LoadResult> callback) {
        Minecraft client = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try (var input = Files.newInputStream(preset.file())) {
                return NativeImage.read(input);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }).whenComplete((image, error) -> client.execute(() -> {
            if (error != null) {
                callback.accept(LoadResult.FAILED);
                return;
            }

            try {
                Identifier id = Identifier.fromNamespaceAndPath("skinswitcher", "local/" + UUID.randomUUID());
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "Skin Switcher local preset", image);
                client.getTextureManager().register(id, dynamicTexture);
                ClientAsset.Texture previousTexture = activeTexture;
                activeTexture = new DynamicSkinTexture(id);
                activeModel = preset.model();
                if (previousTexture instanceof DynamicSkinTexture previous) {
                    client.getTextureManager().release(previous.id());
                }
                callback.accept(LoadResult.READY);
            } catch (RuntimeException exception) {
                image.close();
                callback.accept(LoadResult.FAILED);
            }
        }));
    }

    public static PlayerSkin apply(PlayerSkin original) {
        ClientAsset.Texture texture = activeTexture;
        var model = activeModel;
        if (texture == null || model == null || original == null) return original;
        return new PlayerSkin(texture, original.cape(), original.elytra(), model, false);
    }

    public enum LoadResult {
        READY,
        FAILED
    }

    private record DynamicSkinTexture(Identifier id) implements ClientAsset.Texture {
        @Override
        public Identifier texturePath() {
            return id;
        }
    }
}
