package dev.codex.skinswitcher.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

/** Draggable player preview with a small default look toward the character's right. */
final class WardrobeSkinPreviewWidget extends AbstractWidget {
    private static final float DEFAULT_ROTATION_X = -5.0f;
    private static final float DEFAULT_ROTATION_Y = -30.0f;

    private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private final Supplier<PlayerSkin> skinSupplier;
    private float rotationX = DEFAULT_ROTATION_X;
    private float rotationY = DEFAULT_ROTATION_Y;

    WardrobeSkinPreviewWidget(int x, int y, int width, int height,
                              EntityModelSet models, Supplier<PlayerSkin> skinSupplier) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.wideModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.skinSupplier = skinSupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerSkin skin = skinSupplier.get();
        if (skin == null) return;

        PlayerModel model = skin.model() == PlayerModelType.SLIM ? slimModel : wideModel;
        model.head.yRot = 0.0f;
        model.head.xRot = 0.0f;
        float scale = 0.97f * getHeight() / 2.125f;
        graphics.submitSkinRenderState(model, skin.body().texturePath(), scale,
                rotationX, rotationY, -1.0625f,
                getX(), getY(), getRight(), getBottom());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        rotationX = Mth.clamp(rotationX - (float) dragY * 2.5f, -50.0f, 50.0f);
        rotationY += (float) dragX * 2.5f;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
