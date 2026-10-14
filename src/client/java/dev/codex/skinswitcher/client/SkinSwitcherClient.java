package dev.codex.skinswitcher.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.codex.skinswitcher.client.gui.SkinSwitcherScreen;
import dev.codex.skinswitcher.client.skin.LocalSkinState;
import dev.codex.skinswitcher.client.skin.SkinPresetStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class SkinSwitcherClient implements ClientModInitializer {
    private static final KeyMapping OPEN_SCREEN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.skinswitcher.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skinswitcher", "main"))
    ));

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                SkinPresetStore.activePreset().ifPresent(preset ->
                        LocalSkinState.load(preset, result -> {
                        })));

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                SkinPresetStore.activePreset().ifPresent(preset ->
                        LocalSkinState.load(preset, result -> {
                        })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SCREEN.consumeClick()) {
                client.setScreen(new SkinSwitcherScreen(client.screen));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                int width = 58;
                int height = 66;
                int x = Math.max(4, scaledWidth - width - 8);
                int y = Math.max(4, scaledHeight - height - 8);
                var accountSkin = client.getSkinManager().createLookup(client.getGameProfile(), false);
                Screens.getButtons(screen).add(new SkinPreviewMenuWidget(
                        x, y, width, height, client.getEntityModels(),
                        () -> LocalSkinState.apply(accountSkin.get()),
                        () -> client.setScreen(new SkinSwitcherScreen(screen))));
            } else if (screen instanceof PauseScreen) {
                int width = 58;
                int height = 66;
                int x = Math.max(4, scaledWidth - width - 8);
                int y = Math.max(4, scaledHeight - height - 8);
                var accountSkin = client.getSkinManager().createLookup(client.getGameProfile(), false);
                Screens.getButtons(screen).add(new SkinPreviewMenuWidget(
                        x, y, width, height, client.getEntityModels(),
                        () -> LocalSkinState.apply(accountSkin.get()),
                        () -> client.setScreen(new SkinSwitcherScreen(screen))));
            }
        });
    }
}
