package dev.codex.skinswitcher.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/** A frameless menu wardrobe button with a player preview that follows the pointer. */
public final class SkinPreviewMenuWidget extends AbstractWidget {
    private static final int COLOR_LABEL = 0xFFE2D5B9;
    private static final int COLOR_LABEL_HOVER = 0xFFF0D28E;
    private static final float MENU_CENTER_YAW = 0.25f;
    private static final float MAX_POINTER_YAW = 0.30f;
    private static final float MAX_HEAD_PITCH = 0.22f;
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private final Supplier<PlayerSkin> skinSupplier;
    private final Runnable onPress;

    public SkinPreviewMenuWidget(int x, int y, int width, int height,
                                 net.minecraft.client.model.geom.EntityModelSet models,
                                 Supplier<PlayerSkin> skinSupplier, Runnable onPress) {
        super(x, y, width, height, Component.translatable("screens.skinswitcher.open"));
        this.wideModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.skinSupplier = skinSupplier;
        this.onPress = onPress;
        setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerSkin skin = skinSupplier.get();
        if (skin != null) {
            int labelHeight = 13;
            int modelHeight = Math.max(1, getHeight() - labelHeight);
            int modelY = getY() + labelHeight;
            PlayerModel model = skin.model() == PlayerModelType.SLIM ? slimModel : wideModel;
            Minecraft client = Minecraft.getInstance();
            double headCenterX = getX() + getWidth() / 2.0;
            double headCenterY = modelY + modelHeight * 0.12;
            double screenWidth = Math.max(1, client.getWindow().getGuiScaledWidth());
            double screenHeight = Math.max(1, client.getWindow().getGuiScaledHeight());
            float horizontalLook = clamp((float) ((mouseX - headCenterX) / (screenWidth * 0.42)), -1.0f, 1.0f);
            float verticalLook = clamp((float) ((mouseY - headCenterY) / (screenHeight * 0.30)), -1.0f, 1.0f);
            model.head.yRot = clamp(
                    MENU_CENTER_YAW - horizontalLook * MAX_POINTER_YAW,
                    -0.10f, 0.55f);
            model.head.xRot = verticalLook * MAX_HEAD_PITCH;
            float scale = 0.97f * modelHeight / 2.125f;
            graphics.submitSkinRenderState(model, skin.body().texturePath(), scale,
                    -5.0f, -30.0f, -1.0625f,
                    getX(), modelY, getRight(), getY() + getHeight());
        }

        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + 2,
                isHoveredOrFocused() ? COLOR_LABEL_HOVER : COLOR_LABEL);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_SPACE)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            onPress.run();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
