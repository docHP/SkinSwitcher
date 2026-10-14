# Skin Switcher

An independent Fabric client mod for Minecraft 1.21.11.

## Wardrobe

- Open the wardrobe from the title screen, pause menu, or default `O` key.
- The title and pause menus show the same frameless 3D skin preview in the bottom-right corner; its head follows the mouse, its “Skins” label sits above it, and clicking it opens the wardrobe.
- Browse local PNG presets in a vertical list that adjusts row size to the available GUI height, with overlaid pixel-art head thumbnails and a draggable 3D player preview.
- Scroll smoothly through the whole preset list or drag its scrollbar; the selected row stays highlighted.
- Select the bulleted-list button to enter reorder mode. Rows wiggle; drag a skin to the insertion line to move it. Your order is saved locally and retained across renames and model changes.
- Open the search field from the magnifying-glass button at the bottom of the preset list to filter by name.
- Click a preset name above the preview to rename it, or drop PNG skins onto the open wardrobe to import them for preview.
- **Apply Locally** changes the skin rendered by your client and remembers that preset.
- **Upload to Account** sends the selected skin to the signed-in Minecraft profile and reports the actual HTTP result.
- Authentication tokens are not stored or printed in logs.

Uploading changes the account skin. Reconnect so a server can load the updated profile skin for other players; no custom server component is required. The local preview does not change the account skin by itself.

## Skin files

Place 64x64 PNG skins in `config/skinswitcher/presets`. Files ending in `.slim.png` use the slim-arm model; other files use classic arms. Legacy 64x32 skins are accepted as classic.
Renaming a preset keeps its skin model. Dropped PNGs are copied into the presets folder; an existing name is kept by adding a number.

## Build

Use JDK 21 or newer and run `gradlew.bat build` on Windows. The distributable mod jar will be written under `build/libs`.

The project is still an early prototype. Verify it in a disposable world before relying on it during a live session.
