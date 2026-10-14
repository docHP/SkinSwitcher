package dev.codex.skinswitcher.client.skin;

import net.minecraft.world.entity.player.PlayerModelType;

import java.nio.file.Path;

public record SkinPreset(Path file, String name, PlayerModelType model) {
}
