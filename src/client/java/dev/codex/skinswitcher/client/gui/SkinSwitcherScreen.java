package dev.codex.skinswitcher.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.codex.skinswitcher.client.skin.LocalSkinState;
import dev.codex.skinswitcher.client.skin.SkinPreset;
import dev.codex.skinswitcher.client.skin.SkinPresetStore;
import dev.codex.skinswitcher.client.skin.SkinUploadService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.PlayerModelType;

public final class SkinSwitcherScreen extends Screen {
    private static final int COLOR_FRAME = 0xFFF0D28E;
    private static final int COLOR_BRASS = 0xFF9A7844;
    private static final int COLOR_MUTED_BRASS = 0xFF5D5038;
    private static final int COLOR_FRAME_FILL = 0xF2161817;
    private static final int COLOR_PANEL = 0xF51C1D19;
    private static final int COLOR_PANEL_HEADER = 0xFF24231E;
    private static final int COLOR_VIEWPORT = 0xFF111311;
    private static final int COLOR_CARD = 0xFF20211D;
    private static final int COLOR_CARD_HOVER = 0xFF2B2B24;
    private static final int COLOR_CARD_SELECTED = 0xFF332B1D;
    private static final int COLOR_TEXT = 0xFFF0E5CD;
    private static final int COLOR_MUTED_TEXT = 0xFFB7AA92;
    private static final int COLOR_SUCCESS = 0xFFA5CE85;
    private static final int COLOR_WARNING = 0xFFE0BE78;
    private static final int COLOR_ERROR = 0xFFE28C7D;

    private final Screen parent;
    private final Map<Path, BufferedImage> thumbnailCache = new HashMap<>();

    private List<SkinPreset> presets = List.of();
    private SkinPreset selected;
    private BufferedImage selectedImage;
    private PlayerSkin selectedPreviewSkin;
    private Identifier previewTextureId;
    private ClientAsset.Texture previewTexture;
    private Component status = Component.translatable("screens.skinswitcher.status.scaffold");
    private int statusColor = COLOR_MUTED_TEXT;
    private int firstVisiblePreset;
    private int rowButtonCount = 1;
    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;
    private int sectionY;
    private int sectionHeight;
    private int presetsX;
    private int presetsWidth;
    private int previewX;
    private int previewWidth;
    private int presetListX;
    private int presetListY;
    private int presetListWidth;
    private int presetListHeight;
    private int presetRowHeight = 48;
    private int presetRowGap = 6;
    private int scrollbarX;
    private int searchButtonX;
    private int searchButtonY;
    private int searchInputX;
    private int searchInputY;
    private int searchInputWidth;
    private int sortButtonX;
    private int previewWidgetX;
    private int previewWidgetY;
    private int previewWidgetWidth;
    private int previewWidgetHeight;
    private boolean presetsLoaded;
    private boolean uploading;
    private boolean renaming;
    private boolean searchOpen;
    private String searchQuery = "";
    private EditBox renameInput;
    private EditBox searchInput;
    private final List<PresetRowButton> presetRowButtons = new ArrayList<>();
    private double presetScrollOffset;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private boolean reorderMode;
    private SkinPreset dragPreset;
    private int dragTargetIndex = -1;
    private boolean dragTargetAfter;
    private boolean draggingPreset;
    private double dragStartX;
    private double dragStartY;
    private double dragGrabOffsetX;
    private double dragGrabOffsetY;
    private long lastDragAutoScrollAt;

    public SkinSwitcherScreen(Screen parent) {
        super(Component.translatable("screens.skinswitcher.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!presetsLoaded) refreshPresetData(false);
        if (selected != null && selectedPreviewSkin == null) loadSelectedPreview();
        calculateLayout();

        clampFirstVisiblePreset();
        presetRowButtons.clear();
        for (int row = 0; row < rowButtonCount; row++) {
            int x = presetListX;
            int y = presetListY + row * (presetRowHeight + presetRowGap);
            PresetRowButton rowButton = new PresetRowButton(this, row, x, y,
                    presetListWidth, presetRowHeight);
            presetRowButtons.add(rowButton);
            this.addRenderableWidget(rowButton);
        }
        updatePresetRows();

        searchButtonX = presetsX + 12;
        searchButtonY = sectionY + sectionHeight - 25;
        sortButtonX = searchButtonX + 24;
        searchInputX = sortButtonX + 26;
        searchInputY = searchButtonY;
        searchInputWidth = Math.max(1, presetsX + presetsWidth - 12 - searchInputX);
        SearchToggleButton searchButton = new SearchToggleButton(this, searchButtonX, searchButtonY, 20, 18);
        searchButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screens.skinswitcher.search")));
        this.addRenderableWidget(searchButton);
        int sortButtonY = sectionY + sectionHeight - 25;
        SortMenuButton sortButton = new SortMenuButton(this, sortButtonX, sortButtonY, 20, 18,
                button -> toggleReorderMode());
        sortButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screens.skinswitcher.sort.open")));
        this.addRenderableWidget(sortButton);
        if (searchOpen) {
            int textInsetX = Math.min(4, Math.max(0, (searchInputWidth - 1) / 2));
            int textInsetY = Math.max(0, (18 - this.font.lineHeight) / 2);
            searchInput = new EditBox(this.font, searchInputX + textInsetX, searchInputY,
                    Math.max(1, searchInputWidth - textInsetX * 2), 18,
                    Component.translatable("screens.skinswitcher.search_hint")) {
                @Override
                public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(0.0F, (float) textInsetY);
                    try {
                        super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    } finally {
                        graphics.pose().popMatrix();
                    }
                }
            };
            searchInput.setMaxLength(48);
            searchInput.setValue(searchQuery);
            searchInput.setHint(Component.translatable("screens.skinswitcher.search_hint"));
            searchInput.setBordered(false);
            searchInput.setResponder(value -> {
                searchQuery = value;
                presetScrollOffset = 0;
                firstVisiblePreset = 0;
                updatePresetRows();
            });
            this.addRenderableWidget(searchInput);
            this.setFocused(searchInput);
            searchInput.setFocused(true);
        } else {
            searchInput = null;
        }

        if (selected != null) {
            Component modelLabel = modelLabel(selected.model());
            int badgeWidth = Math.max(54, this.font.width(modelLabel) + 16);
            int badgeX = previewX + previewWidth - badgeWidth - 10;
            this.addRenderableWidget(new WardrobeButton(badgeX, sectionY + 6, badgeWidth, 16,
                    modelLabel, false, button -> toggleModel()));
        }

        if (selectedPreviewSkin != null) {
            WardrobeSkinPreviewWidget previewWidget = new WardrobeSkinPreviewWidget(
                    previewWidgetX,
                    previewWidgetY,
                    previewWidgetWidth,
                    previewWidgetHeight,
                    Minecraft.getInstance().getEntityModels(),
                    () -> selectedPreviewSkin
            );
            this.addRenderableWidget(previewWidget);
        }

        int nameRowY = sectionY + 31;
        if (selected != null && renaming) {
            int gap = 4;
            int rowWidth = Math.min(previewWidth - 16, 330);
            int saveWidth = rowWidth >= 240 ? 54 : 40;
            int cancelWidth = rowWidth >= 240 ? 58 : 44;
            int inputWidth = Math.max(54, rowWidth - saveWidth - cancelWidth - gap * 2);
            int rowX = previewX + (previewWidth - (inputWidth + saveWidth + cancelWidth + gap * 2)) / 2;
            renameInput = new EditBox(this.font, rowX, nameRowY, inputWidth, 20,
                    Component.translatable("screens.skinswitcher.rename_hint"));
            renameInput.setMaxLength(48);
            renameInput.setValue(selected.name());
            renameInput.setHint(Component.translatable("screens.skinswitcher.rename_hint"));
            renameInput.setFocused(true);
            this.addRenderableWidget(renameInput);
            this.setFocused(renameInput);
            int saveX = rowX + inputWidth + gap;
            this.addRenderableWidget(new WardrobeButton(saveX, nameRowY, saveWidth, 20,
                    Component.translatable("screens.skinswitcher.save"), true, button -> renameSelected()));
            int cancelX = saveX + saveWidth + gap;
            this.addRenderableWidget(new WardrobeButton(cancelX, nameRowY, cancelWidth, 20,
                    Component.translatable("screens.skinswitcher.cancel"), false, button -> cancelRename()));
        } else if (selected != null) {
            this.addRenderableWidget(new PresetNameButton(this, selected.name(), previewX + 12,
                    nameRowY, previewWidth - 24));
        } else {
            renameInput = null;
        }

        int controlY = frameY + frameHeight - 53;
        int gap = 8;
        int[] widths = actionButtonWidths(frameWidth - 40, gap);
        int totalWidth = widths[0] + widths[1] + widths[2] + widths[3] + gap * 3;
        int controlX = frameX + (frameWidth - totalWidth) / 2;

        this.addRenderableWidget(new WardrobeButton(controlX, controlY, widths[0], 25,
                Component.translatable("screens.skinswitcher.folder"), false, button -> openFolder()));
        controlX += widths[0] + gap;
        this.addRenderableWidget(new WardrobeButton(controlX, controlY, widths[1], 25,
                Component.translatable("screens.skinswitcher.refresh"), false, button -> refreshPresets()));
        controlX += widths[1] + gap;
        WardrobeButton applyButton = new WardrobeButton(controlX, controlY, widths[2], 25,
                Component.translatable("screens.skinswitcher.apply"), false, button -> applyLocally());
        applyButton.active = selected != null;
        this.addRenderableWidget(applyButton);
        controlX += widths[2] + gap;
        WardrobeButton uploadButton = new WardrobeButton(controlX, controlY, widths[3], 25,
                Component.translatable("screens.skinswitcher.upload"), true, button -> upload());
        uploadButton.active = selected != null && !uploading;
        this.addRenderableWidget(uploadButton);

        this.addRenderableWidget(new WardrobeButton(frameX + frameWidth - 40, frameY + 12, 24, 22,
                Component.literal("×"), false, button -> onClose()));
    }

    private void calculateLayout() {
        frameWidth = Math.max(320, Math.min(1040, this.width - 36));
        frameHeight = Math.max(260, Math.min(700, this.height - 28));
        frameX = (this.width - frameWidth) / 2;
        frameY = (this.height - frameHeight) / 2;

        int contentX = frameX + 12;
        int contentWidth = frameWidth - 24;
        sectionY = frameY + 70;
        sectionHeight = frameHeight - 130;
        int panelGap = 12;
        presetsWidth = (int) ((contentWidth - panelGap) * 0.49f);
        previewWidth = contentWidth - panelGap - presetsWidth;
        presetsX = contentX;
        previewX = presetsX + presetsWidth + panelGap;

        presetListX = presetsX + 12;
        presetListY = sectionY + 32;
        presetListWidth = Math.max(52, presetsWidth - 36);
        presetListHeight = Math.max(1, sectionHeight - 72);
        presetRowGap = presetListHeight < 140 ? 4 : 6;
        int desiredRows = Math.max(1, Math.min(12, (presetListHeight + 40) / 48));
        int fittingRowHeight = (presetListHeight - presetRowGap * (desiredRows - 1)) / desiredRows;
        presetRowHeight = Math.max(20, Math.min(48, fittingRowHeight));
        int rowStride = presetRowHeight + presetRowGap;
        rowButtonCount = Math.max(1, (presetListHeight + rowStride - 1) / rowStride + 1);
        scrollbarX = presetsX + presetsWidth - 12;
        searchButtonX = presetsX + 12;
        searchButtonY = sectionY + sectionHeight - 25;
        sortButtonX = searchButtonX + 24;
        searchInputX = sortButtonX + 26;
        searchInputY = searchButtonY;
        searchInputWidth = Math.max(1, presetsX + presetsWidth - 12 - searchInputX);
        clampFirstVisiblePreset();

        previewWidgetX = previewX + 12;
        previewWidgetY = sectionY + 55;
        previewWidgetWidth = previewWidth - 24;
        previewWidgetHeight = Math.max(64, sectionHeight - 106);
    }

    private static int[] actionButtonWidths(int availableWidth, int gap) {
        int[] preferred = {104, 88, 154, 178};
        int totalPreferred = preferred[0] + preferred[1] + preferred[2] + preferred[3];
        int availableForButtons = Math.max(220, availableWidth - gap * 3);
        if (availableForButtons >= totalPreferred) return preferred;

        float scale = (float) availableForButtons / totalPreferred;
        int[] widths = new int[preferred.length];
        for (int i = 0; i < preferred.length; i++) {
            widths[i] = Math.max(54, Math.round(preferred[i] * scale));
        }
        return widths;
    }

    private double maxScrollOffset() {
        int rowStride = presetRowHeight + presetRowGap;
        double contentHeight = Math.max(0, filteredPresets().size() * rowStride - presetRowGap);
        return Math.max(0.0, contentHeight - presetListHeight);
    }

    private void clampFirstVisiblePreset() {
        presetScrollOffset = Math.max(0.0, Math.min(maxScrollOffset(), presetScrollOffset));
        firstVisiblePreset = Math.max(0, (int) Math.floor(presetScrollOffset
                / Math.max(1, presetRowHeight + presetRowGap)));
    }

    private void ensureSelectedPresetVisible() {
        if (presetListHeight <= 0) return;
        int selectedIndex = filteredPresets().indexOf(selected);
        if (selectedIndex < 0) return;
        int rowStride = presetRowHeight + presetRowGap;
        double rowTop = selectedIndex * (double) rowStride;
        double rowBottom = rowTop + presetRowHeight;
        if (rowTop < presetScrollOffset) {
            presetScrollOffset = rowTop;
        } else if (rowBottom > presetScrollOffset + presetListHeight) {
            presetScrollOffset = rowBottom - presetListHeight;
        }
        clampFirstVisiblePreset();
    }

    private List<SkinPreset> filteredPresets() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        var matching = presets.stream()
                .filter(preset -> query.isBlank()
                        || preset.name().toLowerCase(Locale.ROOT).contains(query));
        return matching.toList();
    }

    private void updatePresetRows() {
        List<SkinPreset> filtered = filteredPresets();
        clampFirstVisiblePreset();
        int rowStride = presetRowHeight + presetRowGap;
        int rowOffset = (int) Math.round(presetScrollOffset - firstVisiblePreset * (double) rowStride);
        for (int slot = 0; slot < presetRowButtons.size(); slot++) {
            int index = firstVisiblePreset + slot;
            presetRowButtons.get(slot).setY(presetListY + slot * rowStride - rowOffset);
            presetRowButtons.get(slot).setPreset(index < filtered.size() ? filtered.get(index) : null);
        }
    }

    private void toggleReorderMode() {
        reorderMode = !reorderMode;
        if (!reorderMode) {
            dragPreset = null;
            draggingPreset = false;
            dragTargetIndex = -1;
            dragTargetAfter = false;
        }
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        if (!searchOpen) {
            searchQuery = "";
            presetScrollOffset = 0;
            firstVisiblePreset = 0;
            ensureSelectedPresetVisible();
        }
        rebuildWidgets();
    }

    private void activatePresetRow(int slot) {
        if (dragPreset != null || slot < 0 || slot >= presetRowButtons.size()) return;
        SkinPreset preset = presetRowButtons.get(slot).preset;
        if (preset != null) selectPreset(preset);
    }

    private void refreshPresetData(boolean preserveSelection) {
        Path previous = preserveSelection && selected != null ? selected.file() : null;
        try {
            presets = SkinPresetStore.load();
            thumbnailCache.clear();
            selected = previous == null ? null : presets.stream()
                    .filter(preset -> preset.file().equals(previous))
                    .findFirst().orElse(null);
            if (selected == null) {
                Path activeFile = SkinPresetStore.activePreset().map(SkinPreset::file).orElse(null);
                selected = activeFile == null ? null : presets.stream()
                        .filter(preset -> preset.file().equals(activeFile))
                        .findFirst().orElse(null);
            }
            if (selected == null && !presets.isEmpty()) selected = presets.get(0);
            presetsLoaded = true;
            ensureSelectedPresetVisible();

            if (selected == null) {
                releasePreviewTexture();
                selectedImage = null;
                status = Component.translatable("screens.skinswitcher.status.empty");
                statusColor = COLOR_WARNING;
            } else {
                loadSelectedPreview();
                setPreviewingStatus();
            }
        } catch (IOException | RuntimeException exception) {
            presets = List.of();
            selected = null;
            selectedImage = null;
            releasePreviewTexture();
            presetsLoaded = true;
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private void refreshPresets() {
        presetScrollOffset = 0;
        firstVisiblePreset = 0;
        renaming = false;
        renameInput = null;
        refreshPresetData(true);
        rebuildWidgets();
    }

    private void cancelRename() {
        renaming = false;
        renameInput = null;
        rebuildWidgets();
    }

    private void beginRename() {
        renaming = true;
        rebuildWidgets();
    }

    private void toggleModel() {
        if (selected == null) return;
        PlayerModelType targetModel = selected.model() == PlayerModelType.SLIM
                ? PlayerModelType.WIDE : PlayerModelType.SLIM;
        try {
            SkinPreset updated = SkinPresetStore.setModel(selected, targetModel);
            presets = SkinPresetStore.load();
            selected = presets.stream().filter(preset -> preset.file().equals(updated.file()))
                    .findFirst().orElse(updated);
            ensureSelectedPresetVisible();
            loadSelectedPreview();
            status = Component.translatable("screens.skinswitcher.status.model_changed",
                    modelLabel(targetModel));
            statusColor = COLOR_SUCCESS;
            rebuildWidgets();
        } catch (FileAlreadyExistsException exception) {
            status = Component.translatable("screens.skinswitcher.status.model_name_exists");
            statusColor = COLOR_ERROR;
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private static Component modelLabel(PlayerModelType model) {
        return Component.translatable(model == PlayerModelType.SLIM
                ? "screens.skinswitcher.model.slim" : "screens.skinswitcher.model.classic");
    }

    private void renameSelected() {
        if (selected == null || renameInput == null) return;
        try {
            SkinPreset renamed = SkinPresetStore.rename(selected, renameInput.getValue());
            presets = SkinPresetStore.load();
            selected = presets.stream().filter(preset -> preset.file().equals(renamed.file()))
                    .findFirst().orElse(renamed);
            ensureSelectedPresetVisible();
            loadSelectedPreview();
            status = Component.translatable("screens.skinswitcher.status.renamed", selected.name());
            statusColor = COLOR_SUCCESS;
            renaming = false;
            renameInput = null;
            rebuildWidgets();
        } catch (FileAlreadyExistsException exception) {
            status = Component.translatable("screens.skinswitcher.status.name_exists");
            statusColor = COLOR_ERROR;
        } catch (IllegalArgumentException exception) {
            status = Component.translatable("screens.skinswitcher.status.invalid_name");
            statusColor = COLOR_ERROR;
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        int imported = 0;
        java.util.ArrayList<SkinPreset> importedPresets = new java.util.ArrayList<>();
        for (Path path : paths) {
            try {
                SkinPreset importedPreset = SkinPresetStore.importFile(path).orElse(null);
                if (importedPreset != null) {
                    imported++;
                    importedPresets.add(importedPreset);
                }
            } catch (IOException | RuntimeException ignored) {
                // Continue importing any other valid dropped skin files.
            }
        }
        if (imported == 0) {
            status = Component.translatable("screens.skinswitcher.status.drop_invalid");
            statusColor = COLOR_ERROR;
            return;
        }

        Path previous = selected == null ? null : selected.file();
        try {
            presets = SkinPresetStore.load();
            SkinPreset firstImported = null;
            for (SkinPreset importedPreset : importedPresets) {
                firstImported = presets.stream().filter(preset -> preset.file().equals(importedPreset.file()))
                        .findFirst().orElse(null);
                if (firstImported != null) break;
            }
            selected = firstImported != null ? firstImported : presets.stream()
                    .filter(preset -> previous != null && preset.file().equals(previous))
                    .findFirst().orElse(presets.isEmpty() ? null : presets.get(0));
            searchQuery = "";
            ensureSelectedPresetVisible();
            renaming = false;
            renameInput = null;
            if (selected != null) loadSelectedPreview();
            status = Component.translatable("screens.skinswitcher.status.dropped", imported);
            statusColor = COLOR_SUCCESS;
            rebuildWidgets();
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!renaming && maxScrollOffset() > 0
                && mouseX >= presetListX - 4 && mouseX <= scrollbarX + 6
                && mouseY >= presetListY && mouseY < presetListY + presetListHeight) {
            presetScrollOffset -= verticalAmount * (presetRowHeight + presetRowGap) * 0.75;
            clampFirstVisiblePreset();
            updatePresetRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        dragPreset = null;
        dragTargetIndex = -1;
        draggingPreset = false;
        // Handle the compact footer controls before child-widget dispatch. The preset
        // rows are clipped visually, but their widgets can extend below the list and
        // intercept clicks near these controls on some GUI scales.
        if (button == 0 && mouseY >= searchButtonY && mouseY < searchButtonY + 18) {
            if (mouseX >= searchButtonX && mouseX < searchButtonX + 20) {
                toggleSearch();
                return true;
            }
            if (mouseX >= sortButtonX && mouseX < sortButtonX + 20) {
                toggleReorderMode();
                return true;
            }
        }
        if (button == 0 && !renaming && maxScrollOffset() > 0
                && mouseX >= scrollbarX - 4 && mouseX <= scrollbarX + 6
                && mouseY >= presetListY && mouseY < presetListY + presetListHeight) {
            draggingScrollbar = true;
            scrollbarDragOffset = mouseY >= scrollbarThumbY() && mouseY < scrollbarThumbY() + scrollbarThumbHeight()
                    ? (int) mouseY - scrollbarThumbY() : scrollbarThumbHeight() / 2;
            if (mouseY < scrollbarThumbY() || mouseY >= scrollbarThumbY() + scrollbarThumbHeight()) {
                scrollToScrollbar(mouseY - scrollbarDragOffset);
            }
            return true;
        }
        if (button == 0) {
            int index = presetRowIndexAt(mouseX, mouseY);
            List<SkinPreset> filtered = filteredPresets();
            if (reorderMode && index >= 0 && index < filtered.size()) {
                dragPreset = filtered.get(index);
                dragTargetIndex = index;
                dragTargetAfter = false;
                dragStartX = mouseX;
                dragStartY = mouseY;
                int rowStride = presetRowHeight + presetRowGap;
                double rowTop = presetListY + index * (double) rowStride - presetScrollOffset;
                dragGrabOffsetX = mouseX - presetListX;
                dragGrabOffsetY = mouseY - rowTop;
                lastDragAutoScrollAt = 0;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        return handled || dragPreset != null;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && draggingScrollbar) {
            scrollToScrollbar(mouseY - scrollbarDragOffset);
            return true;
        }
        if (button == 0 && dragPreset != null) {
            double deltaX = mouseX - dragStartX;
            double deltaY = mouseY - dragStartY;
            if (!draggingPreset && deltaX * deltaX + deltaY * deltaY >= 25) {
                draggingPreset = true;
            }
            if (draggingPreset) updateDragTarget(mouseY);
        }
        boolean handled = super.mouseDragged(event, dragX, dragY);
        return handled || draggingPreset;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        boolean handled = super.mouseReleased(event);
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (button != 0 || dragPreset == null) return handled;

        SkinPreset clickedPreset = dragPreset;
        boolean wasDragging = draggingPreset;
        if (wasDragging) {
            updateDragTarget(mouseY);
            savePresetReorder(clickedPreset);
        } else if (mouseX >= presetListX && mouseX < presetListX + presetListWidth
                && mouseY >= presetListY && mouseY < presetListY + presetListHeight) {
            selectPreset(clickedPreset);
        }
        dragPreset = null;
        dragTargetIndex = -1;
        dragTargetAfter = false;
        draggingPreset = false;
        return true;
    }

    private int presetRowIndexAt(double mouseX, double mouseY) {
        if (mouseX < presetListX || mouseX >= presetListX + presetListWidth
                || mouseY < presetListY || mouseY >= presetListY + presetListHeight) return -1;
        int contentY = (int) Math.floor(mouseY - presetListY + presetScrollOffset);
        int rowStride = presetRowHeight + presetRowGap;
        int index = contentY / rowStride;
        return contentY % rowStride < presetRowHeight && index < filteredPresets().size() ? index : -1;
    }

    private void updateDragTarget(double mouseY) {
        List<SkinPreset> filtered = filteredPresets();
        if (filtered.isEmpty()) return;

        int direction = mouseY < presetListY + 8 ? -1
                : mouseY > presetListY + presetListHeight - 8 ? 1 : 0;
        long now = Util.getMillis();
        if (direction != 0 && now - lastDragAutoScrollAt >= 120) {
            presetScrollOffset += direction * (double) (presetRowHeight + presetRowGap);
            clampFirstVisiblePreset();
            updatePresetRows();
            lastDragAutoScrollAt = now;
        }

        int contentY = Math.max(0, (int) Math.floor(mouseY - presetListY + presetScrollOffset));
        int rowStride = presetRowHeight + presetRowGap;
        dragTargetIndex = Math.min(filtered.size() - 1, contentY / rowStride);
        int withinRow = contentY % rowStride;
        dragTargetAfter = withinRow >= presetRowHeight / 2;
    }

    private int scrollbarThumbHeight() {
        double contentHeight = Math.max(1.0,
                filteredPresets().size() * (double) (presetRowHeight + presetRowGap) - presetRowGap);
        return Math.min(presetListHeight, Math.max(18,
                (int) Math.round(presetListHeight * (double) presetListHeight / contentHeight)));
    }

    private int scrollbarThumbY() {
        int travel = Math.max(0, presetListHeight - scrollbarThumbHeight());
        double maximum = maxScrollOffset();
        return presetListY + (maximum <= 0 ? 0
                : (int) Math.round(travel * presetScrollOffset / maximum));
    }

    private void scrollToScrollbar(double thumbTop) {
        int travel = presetListHeight - scrollbarThumbHeight();
        if (travel <= 0) return;
        double progress = (thumbTop - presetListY) / travel;
        presetScrollOffset = Mth.clamp((float) (progress * maxScrollOffset()),
                0.0f, (float) maxScrollOffset());
        clampFirstVisiblePreset();
        updatePresetRows();
    }

    private void savePresetReorder(SkinPreset movedPreset) {
        List<SkinPreset> filtered = filteredPresets();
        if (dragTargetIndex < 0 || dragTargetIndex >= filtered.size()) return;
        SkinPreset targetPreset = filtered.get(dragTargetIndex);
        int sourceIndex = presets.indexOf(movedPreset);
        int targetIndex = presets.indexOf(targetPreset);
        if (sourceIndex < 0 || targetIndex < 0) return;

        int insertionIndex = targetIndex + (dragTargetAfter ? 1 : 0);
        if (sourceIndex < insertionIndex) insertionIndex--;
        if (sourceIndex == insertionIndex) return;

        List<SkinPreset> reordered = new ArrayList<>(presets);
        reordered.remove(sourceIndex);
        insertionIndex = Math.max(0, Math.min(reordered.size(), insertionIndex));
        reordered.add(insertionIndex, movedPreset);
        try {
            SkinPresetStore.saveOrder(reordered);
            presets = List.copyOf(reordered);
            ensureSelectedPresetVisible();
            updatePresetRows();
            status = Component.translatable("screens.skinswitcher.status.reordered", movedPreset.name());
            statusColor = COLOR_SUCCESS;
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private void selectPreset(SkinPreset preset) {
        if (selected != null && selected.file().equals(preset.file())) return;
        selected = preset;
        loadSelectedPreview();
        setPreviewingStatus();
        if (renaming) {
            renaming = false;
            renameInput = null;
        }
        rebuildWidgets();
    }

    private void setPreviewingStatus() {
        if (selected == null) return;
        status = Component.translatable("screens.skinswitcher.status.previewing", selected.name());
        statusColor = COLOR_MUTED_TEXT;
    }

    private void loadSelectedPreview() {
        releasePreviewTexture();
        selectedImage = null;
        selectedPreviewSkin = null;
        if (selected == null) return;

        NativeImage nativeImage = null;
        try {
            selectedImage = ImageIO.read(selected.file().toFile());
            if (selectedImage == null) throw new IOException("Unsupported PNG image");

            try (InputStream input = Files.newInputStream(selected.file())) {
                nativeImage = NativeImage.read(input);
            }

            Identifier id = Identifier.fromNamespaceAndPath("skinswitcher", "preview/" + UUID.randomUUID());
            var dynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(
                    () -> "Skin Switcher wardrobe preview", nativeImage);
            nativeImage = null;
            Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
            previewTextureId = id;
            previewTexture = new PreviewTexture(id);
            selectedPreviewSkin = PlayerSkin.insecure(previewTexture, null, null, selected.model());
        } catch (IOException | RuntimeException exception) {
            if (nativeImage != null) nativeImage.close();
            selectedImage = null;
            selectedPreviewSkin = null;
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private void releasePreviewTexture() {
        if (previewTextureId != null && Minecraft.getInstance() != null) {
            Minecraft.getInstance().getTextureManager().release(previewTextureId);
        }
        previewTextureId = null;
        previewTexture = null;
        selectedPreviewSkin = null;
    }

    private BufferedImage thumbnail(SkinPreset preset) {
        return thumbnailCache.computeIfAbsent(preset.file(), path -> {
            try {
                return ImageIO.read(path.toFile());
            } catch (IOException ignored) {
                return null;
            }
        });
    }

    private void applyLocally() {
        if (selected == null) return;
        try {
            SkinPresetStore.select(selected);
            status = Component.translatable("screens.skinswitcher.status.applying");
            statusColor = COLOR_WARNING;
            LocalSkinState.load(selected, result -> {
                if (result == LocalSkinState.LoadResult.READY) {
                    status = Component.translatable("screens.skinswitcher.status.applied", selected.name());
                    statusColor = COLOR_SUCCESS;
                } else {
                    status = Component.translatable("screens.skinswitcher.status.read_error");
                    statusColor = COLOR_ERROR;
                }
            });
        } catch (IOException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private void upload() {
        if (selected == null || uploading) return;
        SkinPreset uploadingPreset = selected;
        uploading = true;
        status = Component.translatable("screens.skinswitcher.status.uploading");
        statusColor = COLOR_WARNING;
        rebuildWidgets();

        SkinUploadService.upload(uploadingPreset).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            uploading = false;
            if (error != null) {
                status = Component.translatable("screens.skinswitcher.status.upload_network_error", "RequestFailed");
                statusColor = COLOR_ERROR;
            } else {
                switch (result.outcome()) {
                    case ACCEPTED -> {
                        status = Component.translatable("screens.skinswitcher.status.upload_success");
                        statusColor = COLOR_SUCCESS;
                    }
                    case REJECTED -> {
                        status = Component.translatable("screens.skinswitcher.status.upload_failure", result.statusCode());
                        statusColor = COLOR_ERROR;
                    }
                    case NO_ACCOUNT -> {
                        status = Component.translatable("screens.skinswitcher.status.no_account");
                        statusColor = COLOR_WARNING;
                    }
                    case INVALID_FILE -> {
                        status = Component.translatable("screens.skinswitcher.status.invalid_skin");
                        statusColor = COLOR_ERROR;
                    }
                    case NETWORK_ERROR -> {
                        status = Component.translatable("screens.skinswitcher.status.upload_network_error", result.safeErrorType());
                        statusColor = COLOR_ERROR;
                    }
                }
            }
            if (Minecraft.getInstance().screen == this) rebuildWidgets();
        }));
    }

    private void openFolder() {
        try {
            Files.createDirectories(SkinPresetStore.presetsDirectory());
            Util.getPlatform().openFile(SkinPresetStore.presetsDirectory().toFile());
        } catch (IOException | RuntimeException exception) {
            status = Component.translatable("screens.skinswitcher.status.read_error");
            statusColor = COLOR_ERROR;
        }
    }

    private void drawFrame(GuiGraphics graphics) {
        graphics.fill(frameX, frameY, frameX + frameWidth, frameY + frameHeight, COLOR_BRASS);
        graphics.fill(frameX + 1, frameY + 1, frameX + frameWidth - 1, frameY + frameHeight - 1, COLOR_FRAME_FILL);
        graphics.fill(frameX + 3, frameY + 3, frameX + frameWidth - 3, frameY + frameHeight - 3, COLOR_PANEL);
        graphics.fill(frameX + 14, frameY + 61, frameX + frameWidth - 14, frameY + 62, COLOR_MUTED_BRASS);

        int corner = 5;
        graphics.fill(frameX + 3, frameY + 3, frameX + 3 + corner, frameY + 4, COLOR_FRAME);
        graphics.fill(frameX + 3, frameY + 3, frameX + 4, frameY + 3 + corner, COLOR_FRAME);
        graphics.fill(frameX + frameWidth - 3 - corner, frameY + 3, frameX + frameWidth - 3, frameY + 4, COLOR_FRAME);
        graphics.fill(frameX + frameWidth - 4, frameY + 3, frameX + frameWidth - 3, frameY + 3 + corner, COLOR_FRAME);
        graphics.fill(frameX + 3, frameY + frameHeight - 4, frameX + 3 + corner, frameY + frameHeight - 3, COLOR_FRAME);
        graphics.fill(frameX + 3, frameY + frameHeight - 3 - corner, frameX + 4, frameY + frameHeight - 3, COLOR_FRAME);
        graphics.fill(frameX + frameWidth - 3 - corner, frameY + frameHeight - 4, frameX + frameWidth - 3, frameY + frameHeight - 3, COLOR_FRAME);
        graphics.fill(frameX + frameWidth - 4, frameY + frameHeight - 3 - corner, frameX + frameWidth - 3, frameY + frameHeight - 3, COLOR_FRAME);
    }

    private void drawSectionPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_MUTED_BRASS);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_PANEL);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 27, COLOR_PANEL_HEADER);
        graphics.fill(x + 9, y + 27, x + width - 9, y + 28, COLOR_MUTED_BRASS);
    }

    private void drawHeadThumbnail(GuiGraphics graphics, BufferedImage image, int x, int y, int size) {
        if (image == null) return;
        int scale = Math.max(1, size / 8);
        int actualSize = scale * 8;
        int originX = x + (size - actualSize) / 2;
        int originY = y + (size - actualSize) / 2;
        drawHeadLayer(graphics, image, originX, originY, scale, 8, 8);
        drawHeadLayer(graphics, image, originX, originY, scale, 40, 8);
    }

    private void drawHeadLayer(GuiGraphics graphics, BufferedImage image,
                               int originX, int originY, int scale, int sourceX, int sourceY) {
        for (int y = 0; y < 8; y++) {
            int x = 0;
            while (x < 8) {
                int color = image.getRGB(sourceX + x, sourceY + y);
                if ((color >>> 24) == 0) {
                    x++;
                    continue;
                }
                int runEnd = x + 1;
                while (runEnd < 8 && image.getRGB(sourceX + runEnd, sourceY + y) == color) runEnd++;
                graphics.fill(originX + x * scale, originY + y * scale,
                        originX + runEnd * scale, originY + (y + 1) * scale, color);
                x = runEnd;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawFrame(graphics);
        drawSectionPanel(graphics, presetsX, sectionY, presetsWidth, sectionHeight);
        drawSectionPanel(graphics, previewX, sectionY, previewWidth, sectionHeight);

        Font font = this.font;
        graphics.drawString(font, Component.translatable("screens.skinswitcher.eyebrow"),
                frameX + 20, frameY + 10, COLOR_FRAME, false);
        graphics.drawString(font, this.title, frameX + 20, frameY + 24, COLOR_TEXT, false);
        graphics.drawString(font, Component.translatable("screens.skinswitcher.subtitle"),
                frameX + 20, frameY + 45, COLOR_MUTED_TEXT, false);

        graphics.drawString(font, Component.translatable("screens.skinswitcher.presets"),
                presetsX + 12, sectionY + 10, COLOR_TEXT, false);
        List<SkinPreset> filtered = filteredPresets();
        if (!filtered.isEmpty()) {
            Component count = Component.translatable("screens.skinswitcher.preset_count", filtered.size());
            graphics.drawString(font, count, presetsX + presetsWidth - font.width(count) - 14,
                    sectionY + 10, COLOR_MUTED_TEXT, false);
        }
        if (filtered.isEmpty() && !searchQuery.isBlank()) {
            graphics.drawCenteredString(font, Component.translatable("screens.skinswitcher.no_matches"),
                    presetsX + presetsWidth / 2, presetListY + presetListHeight / 2 - 4, COLOR_MUTED_TEXT);
        }
        if (maxScrollOffset() > 0) {
            graphics.fill(scrollbarX, presetListY, scrollbarX + 3,
                    presetListY + presetListHeight, COLOR_CARD);
            int thumbHeight = scrollbarThumbHeight();
            int thumbY = scrollbarThumbY();
            graphics.fill(scrollbarX - 1, thumbY, scrollbarX + 4,
                    thumbY + thumbHeight, COLOR_BRASS);
        }
        if (searchOpen) {
            graphics.fill(searchInputX - 1, searchInputY - 1,
                    searchInputX + searchInputWidth + 1, searchInputY + 19, COLOR_MUTED_BRASS);
            graphics.fill(searchInputX, searchInputY,
                    searchInputX + searchInputWidth, searchInputY + 18, COLOR_VIEWPORT);
        }

        graphics.drawString(font, Component.translatable("screens.skinswitcher.preview"),
                previewX + 12, sectionY + 10, COLOR_TEXT, false);
        graphics.fill(previewX + 8, sectionY + 30, previewX + previewWidth - 8,
                sectionY + sectionHeight - 30, COLOR_VIEWPORT);
        if (selected == null) {
            graphics.drawWordWrap(font, Component.translatable("screens.skinswitcher.empty"),
                    previewX + 24, sectionY + 78, previewWidth - 48, COLOR_MUTED_TEXT);
        } else {
            if (!renaming) graphics.drawCenteredString(font,
                    Component.translatable("screens.skinswitcher.rename_hint_line"),
                    previewX + previewWidth / 2, sectionY + sectionHeight - 25, COLOR_MUTED_TEXT);
            graphics.drawCenteredString(font, Component.translatable("screens.skinswitcher.drag_to_rotate"),
                    previewX + previewWidth / 2, sectionY + sectionHeight - 14, COLOR_MUTED_TEXT);
        }

        graphics.drawCenteredString(font, status, this.width / 2,
                frameY + frameHeight - 21, statusColor);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderDraggedPreset(graphics, mouseX, mouseY);
    }

    private void renderDraggedPreset(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!draggingPreset || dragPreset == null) return;

        int x = (int) Math.round(mouseX - dragGrabOffsetX);
        int y = (int) Math.round(mouseY - dragGrabOffsetY);
        int right = x + presetListWidth;
        int bottom = y + presetRowHeight;
        graphics.fill(x + 3, y + 4, right + 3, bottom + 4, 0x80000000);
        graphics.fill(x, y, right, bottom, COLOR_FRAME);
        graphics.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFF332B1D);

        int size = Math.min(32, presetRowHeight - 10);
        int headX = x + 8;
        int headY = y + (presetRowHeight - size) / 2;
        graphics.fill(headX - 2, headY - 2, headX + size + 2, headY + size + 2, COLOR_VIEWPORT);
        drawHeadThumbnail(graphics, thumbnail(dragPreset), headX, headY, size);

        Font font = Minecraft.getInstance().font;
        String name = PresetRowButton.fitName(font, dragPreset.name(), presetListWidth - size - 34);
        graphics.drawString(font, name, headX + size + 10,
                y + (presetRowHeight - 9) / 2, COLOR_FRAME, false);
        if (selected != null && selected.file().equals(dragPreset.file())) {
            graphics.drawCenteredString(font, "\u2713", right - 10,
                    y + (presetRowHeight - 9) / 2, COLOR_FRAME);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        releasePreviewTexture();
        super.removed();
    }

    private static final class PreviewTexture implements ClientAsset.Texture {
        private final Identifier id;

        private PreviewTexture(Identifier id) {
            this.id = id;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public Identifier texturePath() {
            return id;
        }
    }

    private static final class WardrobeButton extends Button {
        private final boolean primary;

        private WardrobeButton(int x, int y, int width, int height, Component message,
                               boolean primary, Button.OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            boolean highlighted = isHoveredOrFocused();
            int edge = !active ? COLOR_MUTED_BRASS : primary ? COLOR_FRAME : COLOR_MUTED_BRASS;
            int fill = !active ? 0xFF292925 : primary
                    ? (highlighted ? 0xFFAA813B : 0xFF80612D)
                    : (highlighted ? 0xFF34332B : 0xFF252622);
            int text = active ? COLOR_TEXT : COLOR_MUTED_TEXT;
            graphics.fill(x, y, right, bottom, edge);
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);
            if (highlighted && active) graphics.renderOutline(x, y, getWidth(), getHeight(), COLOR_FRAME);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    x + getWidth() / 2, y + (getHeight() - 9) / 2, text);
        }
    }

    private static final class PresetNameButton extends Button {
        private PresetNameButton(SkinSwitcherScreen screen, String name, int x, int y, int width) {
            super(x, y, width, 20, Component.literal(name), button -> screen.beginRename(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            String name = PresetRowButton.fitName(font, getMessage().getString(), getWidth() - 12);
            int color = isHoveredOrFocused() ? COLOR_FRAME : COLOR_TEXT;
            graphics.drawCenteredString(font, name, getX() + getWidth() / 2, getY() + 5, color);
            if (isHoveredOrFocused()) {
                int textWidth = font.width(name);
                int underlineX = getX() + (getWidth() - textWidth) / 2;
                graphics.fill(underlineX, getY() + 17, underlineX + textWidth, getY() + 18, COLOR_FRAME);
            }
        }
    }

    private static final class SearchToggleButton extends Button {
        private final SkinSwitcherScreen screen;

        private SearchToggleButton(SkinSwitcherScreen screen, int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatable("screens.skinswitcher.search"),
                    button -> screen.toggleSearch(), DEFAULT_NARRATION);
            this.screen = screen;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean highlighted = isHoveredOrFocused();
            graphics.fill(x, y, x + getWidth(), y + getHeight(),
                    highlighted ? COLOR_FRAME : COLOR_MUTED_BRASS);
            graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1,
                    highlighted ? COLOR_CARD_HOVER : COLOR_CARD);
            if (screen.searchOpen) {
                graphics.drawCenteredString(Minecraft.getInstance().font, "×",
                        x + getWidth() / 2, y + 4, COLOR_TEXT);
            } else {
                graphics.drawCenteredString(Minecraft.getInstance().font, "\uD83D\uDD0D",
                        x + getWidth() / 2, y + 4, COLOR_TEXT);
            }
        }
    }

    private static final class SortMenuButton extends Button {
        private final SkinSwitcherScreen screen;

        private SortMenuButton(SkinSwitcherScreen screen, int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height,
                    Component.translatable("screens.skinswitcher.sort.open"), onPress, DEFAULT_NARRATION);
            this.screen = screen;
        }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean active = screen.reorderMode;
            graphics.fill(x, y, getRight(), getBottom(),
                    active || isHoveredOrFocused() ? COLOR_FRAME : COLOR_MUTED_BRASS);
            graphics.fill(x + 1, y + 1, getRight() - 1, getBottom() - 1,
                    active ? COLOR_CARD_SELECTED : isHoveredOrFocused() ? COLOR_CARD_HOVER : COLOR_CARD);
            for (int row = 0; row < 3; row++) {
                int lineY = y + 3 + row * 5;
                graphics.fill(x + 4, lineY, x + 6, lineY + 2, COLOR_FRAME);
                graphics.fill(x + 9, lineY, x + 16, lineY + 1, COLOR_TEXT);
            }
        }
    }

    private static final class PresetRowButton extends AbstractWidget {
        private final SkinSwitcherScreen screen;
        private final int slot;
        private SkinPreset preset;

        private PresetRowButton(SkinSwitcherScreen screen, int slot,
                                int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
            this.screen = screen;
            this.slot = slot;
        }

        private void setPreset(SkinPreset preset) {
            this.preset = preset;
            this.active = preset != null;
            this.setMessage(preset == null ? Component.empty() : Component.literal(preset.name()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.enableScissor(screen.presetListX, screen.presetListY,
                    screen.presetListX + screen.presetListWidth,
                    screen.presetListY + screen.presetListHeight);
            renderRowContents(graphics, mouseX, mouseY);
            graphics.disableScissor();
        }

        private void renderRowContents(GuiGraphics graphics, int mouseX, int mouseY) {
            if (preset == null) return;
            int presetIndex = screen.filteredPresets().indexOf(preset);
            boolean lifted = screen.draggingPreset && screen.dragPreset != null
                    && screen.dragPreset.file().equals(preset.file());
            double phase = (Util.getMillis() % 10_000L) * 0.018 + presetIndex * 1.7;
            int wiggleX = screen.reorderMode && !lifted ? (int) Math.round(Math.sin(phase) * 1.5) : 0;
            int wiggleY = screen.reorderMode && !lifted ? (int) Math.round(Math.cos(phase) * 0.6) : 0;
            int x = getX();
            int y = getY();
            int right = x + getWidth();
            int bottom = y + getHeight();
            boolean selected = screen.selected != null && screen.selected.file().equals(preset.file());
            boolean pointerInsideViewport = mouseX >= screen.presetListX
                    && mouseX < screen.presetListX + screen.presetListWidth
                    && mouseY >= screen.presetListY
                    && mouseY < screen.presetListY + screen.presetListHeight;
            boolean highlighted = isFocused() || (isHovered() && pointerInsideViewport);
            if (lifted) {
                graphics.fill(x, y, right, bottom, COLOR_MUTED_BRASS);
                graphics.fill(x + 2, y + 2, right - 2, bottom - 2, COLOR_VIEWPORT);
                graphics.renderOutline(x + 2, y + 2, getWidth() - 4, getHeight() - 4, COLOR_BRASS);
            } else {
                int edge = selected ? COLOR_FRAME : highlighted ? COLOR_BRASS : COLOR_MUTED_BRASS;
                int fill = selected ? COLOR_CARD_SELECTED : highlighted ? COLOR_CARD_HOVER : COLOR_CARD;
                graphics.fill(x, y, right, bottom, edge);
                graphics.fill(x + 1, y + 1, right - 1, bottom - 1, fill);

                int size = Math.min(32, getHeight() - 10);
                int contentX = x + wiggleX;
                int contentY = y + wiggleY;
                int headX = contentX + 8;
                int headY = contentY + (getHeight() - size) / 2;
                graphics.fill(headX - 2, headY - 2, headX + size + 2, headY + size + 2, COLOR_VIEWPORT);
                BufferedImage image = screen.thumbnail(preset);
                // Draw the base face first, then the transparent hat/outer-head layer over it.
                screen.drawHeadThumbnail(graphics, image, headX, headY, size);

                Font font = Minecraft.getInstance().font;
                String name = fitName(font, preset.name(), getWidth() - size - 34);
                graphics.drawString(font, name, headX + size + 10,
                        contentY + (getHeight() - 9) / 2, selected ? COLOR_FRAME : COLOR_TEXT, false);
                if (selected) {
                    graphics.drawCenteredString(font, "\u2713", right - 10 + wiggleX,
                            contentY + (getHeight() - 9) / 2, COLOR_FRAME);
                }
            }
            if (screen.draggingPreset && screen.dragTargetIndex == presetIndex) {
                int markerY = screen.dragTargetAfter
                        ? bottom + screen.presetRowGap / 2 - 1
                        : y - screen.presetRowGap / 2 - 1;
                markerY = Mth.clamp(markerY, screen.presetListY,
                        screen.presetListY + screen.presetListHeight - 2);
                graphics.fill(x + 4, markerY, right - 4, markerY + 2, COLOR_FRAME);
            }
        }

        private static String fitName(Font font, String name, int width) {
            if (font.width(name) <= width) return name;
            String shortened = name;
            while (!shortened.isEmpty() && font.width(shortened + "…") > width) {
                shortened = shortened.substring(0, shortened.length() - 1);
            }
            return shortened.isEmpty() ? "…" : shortened + "…";
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!active || event.button() != 0
                    || event.x() < screen.presetListX
                    || event.x() >= screen.presetListX + screen.presetListWidth
                    || event.y() < screen.presetListY
                    || event.y() >= screen.presetListY + screen.presetListHeight
                    || !isMouseOver(event.x(), event.y())) return false;
            setFocused(true);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (!isFocused()) return false;
            setFocused(false);
            if (event.button() == 0 && !screen.reorderMode
                    && event.x() >= screen.presetListX
                    && event.x() < screen.presetListX + screen.presetListWidth
                    && event.y() >= screen.presetListY
                    && event.y() < screen.presetListY + screen.presetListHeight
                    && isMouseOver(event.x(), event.y())) {
                screen.activatePresetRow(slot);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
