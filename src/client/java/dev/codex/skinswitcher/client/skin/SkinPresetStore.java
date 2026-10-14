package dev.codex.skinswitcher.client.skin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.PlayerModelType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SkinPresetStore {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("skinswitcher");
    private static final Path PRESETS = ROOT.resolve("presets");
    private static final Path ACTIVE_PRESET = ROOT.resolve("active-preset.txt");
    private static final Path PRESET_ORDER = ROOT.resolve("preset-order.txt");

    private SkinPresetStore() {
    }

    public static Path presetsDirectory() {
        return PRESETS;
    }

    public static List<SkinPreset> load() throws IOException {
        Files.createDirectories(PRESETS);
        Map<String, Integer> savedOrder = readOrderPositions();

        try (var files = Files.list(PRESETS)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .map(SkinPresetStore::toPreset)
                    .flatMap(Optional::stream)
                    .sorted(Comparator
                            .comparingInt((SkinPreset preset) -> savedOrder.getOrDefault(
                                    preset.file().getFileName().toString(), Integer.MAX_VALUE))
                            .thenComparing(preset -> preset.name().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public static void saveOrder(List<SkinPreset> presets) throws IOException {
        List<String> fileNames = presets.stream()
                .map(preset -> preset.file().getFileName().toString())
                .distinct()
                .toList();
        writeOrderEntries(fileNames);
    }

    public static Optional<SkinPreset> activePreset() {
        if (!Files.isRegularFile(ACTIVE_PRESET)) return Optional.empty();

        try {
            String fileName = Files.readString(ACTIVE_PRESET, StandardCharsets.UTF_8).trim();
            if (fileName.isEmpty() || Path.of(fileName).getNameCount() != 1) return Optional.empty();
            Path expected = PRESETS.resolve(fileName).normalize();
            if (!expected.getParent().equals(PRESETS.normalize())) return Optional.empty();
            return toPreset(expected);
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static void select(SkinPreset preset) throws IOException {
        Files.createDirectories(ROOT);
        Files.writeString(ACTIVE_PRESET, preset.file().getFileName().toString(), StandardCharsets.UTF_8);
    }

    public static Optional<SkinPreset> importFile(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)
                || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            return Optional.empty();
        }
        Optional<SkinPreset> validated = toPreset(source);
        if (validated.isEmpty()) return Optional.empty();

        Path absoluteSource = source.toAbsolutePath().normalize();
        if (absoluteSource.getParent().equals(PRESETS.toAbsolutePath().normalize())) return validated;

        Files.createDirectories(PRESETS);
        SkinPreset sourcePreset = validated.get();
        String sourceName = source.getFileName().toString();
        String baseName = sourceName.substring(0, sourceName.length() - 4).trim();
        if (sourcePreset.model() == PlayerModelType.SLIM) baseName = stripSlimSuffix(baseName);
        baseName = sanitizeImportName(baseName);
        if (baseName.isBlank()) baseName = "Skin";
        String suffix = sourcePreset.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM
                ? ".slim.png" : ".png";

        for (int number = 1; ; number++) {
            String numberedName = number == 1 ? baseName : baseName + " (" + number + ")";
            Path destination = PRESETS.resolve(numberedName + suffix);
            try {
                Files.copy(source, destination);
                return toPreset(destination);
            } catch (FileAlreadyExistsException ignored) {
                // Keep both skins if a dropped file has a name already in the wardrobe.
            }
        }
    }

    public static SkinPreset rename(SkinPreset preset, String requestedName) throws IOException {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".png")) {
            name = name.substring(0, name.length() - 4).trim();
        }
        if (preset.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM
                && name.toLowerCase(Locale.ROOT).endsWith(".slim")) {
            name = name.substring(0, name.length() - 5).trim();
        }
        if (name.isBlank() || name.length() > 48 || name.endsWith(".") || name.endsWith(" ")
                || name.matches(".*[\\\\/:*?\"<>|].*")
                || name.chars().anyMatch(Character::isISOControl)
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])")) {
            throw new IllegalArgumentException("Invalid preset name");
        }

        String suffix = preset.model() == PlayerModelType.SLIM
                ? ".slim.png" : ".png";
        Path source = preset.file().toAbsolutePath().normalize();
        Path destination = source.resolveSibling(name + suffix);
        if (source.equals(destination)) return toPreset(source).orElse(preset);
        if (Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());

        return movePresetFile(source, destination);
    }

    public static SkinPreset setModel(SkinPreset preset, PlayerModelType model) throws IOException {
        if (preset.model() == model) return preset;
        String suffix = model == PlayerModelType.SLIM ? ".slim.png" : ".png";
        Path source = preset.file().toAbsolutePath().normalize();
        Path destination = source.resolveSibling(preset.name() + suffix);
        if (source.equals(destination)) return toPreset(source).orElse(preset);
        if (Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());
        return movePresetFile(source, destination);
    }

    private static SkinPreset movePresetFile(Path source, Path destination) throws IOException {
        String activeFile = null;
        if (Files.isRegularFile(ACTIVE_PRESET)) {
            try {
                activeFile = Files.readString(ACTIVE_PRESET, StandardCharsets.UTF_8).trim();
            } catch (IOException ignored) {
                // Renaming a preset should still work if the remembered-selection file is unreadable.
            }
        }
        boolean wasActive = source.getFileName().toString().equals(activeFile);
        List<String> originalOrder = null;
        if (Files.isRegularFile(PRESET_ORDER)) {
            try {
                originalOrder = readOrderEntries();
            } catch (IOException ignored) {
                // A damaged order file should not prevent renaming or changing the model.
            }
        }
        Files.move(source, destination);
        try {
            if (originalOrder != null) {
                List<String> updatedOrder = new ArrayList<>(originalOrder);
                int oldIndex = updatedOrder.indexOf(source.getFileName().toString());
                if (oldIndex >= 0) updatedOrder.set(oldIndex, destination.getFileName().toString());
                else updatedOrder.add(destination.getFileName().toString());
                writeOrderEntries(updatedOrder);
            }
            if (wasActive) {
                Files.writeString(ACTIVE_PRESET, destination.getFileName().toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            if (originalOrder != null) {
                try {
                    writeOrderEntries(originalOrder);
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            try {
                Files.move(destination, source);
            } catch (IOException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            if (wasActive) {
                try {
                    Files.writeString(ACTIVE_PRESET, source.getFileName().toString(), StandardCharsets.UTF_8);
                } catch (IOException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }
        return toPreset(destination).orElseThrow(() -> new IOException("Renamed skin could not be loaded"));
    }

    private static Map<String, Integer> readOrderPositions() {
        try {
            List<String> entries = readOrderEntries();
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) positions.putIfAbsent(entries.get(i), i);
            return positions;
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private static List<String> readOrderEntries() throws IOException {
        if (!Files.isRegularFile(PRESET_ORDER)) return List.of();
        return Files.readAllLines(PRESET_ORDER, StandardCharsets.UTF_8).stream()
                .filter(name -> !name.isBlank())
                .toList();
    }

    private static void writeOrderEntries(List<String> fileNames) throws IOException {
        Files.createDirectories(ROOT);
        Path temporary = ROOT.resolve("preset-order.txt.tmp");
        try {
            Files.write(temporary, fileNames, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, PRESET_ORDER, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, PRESET_ORDER, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sanitizeImportName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static Optional<SkinPreset> toPreset(Path file) {
        try {
            long fileSize = Files.size(file);
            if (fileSize <= 0 || fileSize > 2_000_000) return Optional.empty();
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) return Optional.empty();
            int width = image.getWidth();
            int height = image.getHeight();
            if (width != 64 || (height != 64 && height != 32)) return Optional.empty();

            String fileName = file.getFileName().toString();
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            var model = hasSlimSuffix(lowerName) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            String displayName = fileName.substring(0, fileName.length() - 4);
            if (model == PlayerModelType.SLIM) displayName = stripSlimSuffix(displayName);
            return Optional.of(new SkinPreset(file.toAbsolutePath().normalize(), displayName, model));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean hasSlimSuffix(String lowerFileName) {
        return lowerFileName.endsWith(".slim.png") || lowerFileName.endsWith("_slim.png")
                || lowerFileName.endsWith("-slim.png");
    }

    private static String stripSlimSuffix(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".slim") || lowerName.endsWith("_slim") || lowerName.endsWith("-slim")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }
}
