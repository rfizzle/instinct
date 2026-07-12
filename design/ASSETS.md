# Instinct — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source column
> flags a pixel asset with no `.glyph` source yet — a candidate for the glyph
> pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths are under
> `src/main/resources/` unless noted.

## Branding masters

| Asset | Source | Final / derived copies |
|---|---|---|
| Full logo | `art/logo.png` — `.png` master (Gemini; prompt in `DESIGN.md` §4) | `site/assets/logo.png` (1600w web copy, README masthead embeds the master), `site/assets/og-image.png` (1200×630 on Ink) |
| Mod icon | `art/glyphs/icon.gen.py` → `art/glyphs/icon.glyph` (32 native) | `art/icon-128.png` (`--scale-to 128`) → `src/main/resources/assets/instinct/icon.png`, `site/assets/icon.png`; `art/icon-512.png` (`--scale-to 512`, store master) |
| Paw glyph 16×16 | `art/glyphs/paw-16.glyph` | `art/hud-icon-16.png` (Jade/WTHIT + recipe-viewer contexts) |

## Item & block sprites

| Asset | Source | Final / derived copies |
|---|---|---|
| Pedigree treat item sprite | `art/glyphs/pedigree-treat-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/pedigree_treat.png` |
| Vet kit item sprite | `art/glyphs/vet-kit-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/vet_kit.png` |
| Feeding trough block faces (top/side/bottom/inner) | `art/glyphs/feeding-trough-{top,side,bottom,inner}-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/block/feeding_trough_{top,side,bottom,inner}.png` |

## Audio cues

| Asset | Source | Final / derived copies |
|---|---|---|
| Revival shimmer | `art/audio/revive.sfx` | `src/main/resources/assets/instinct/sounds/revive.ogg` |

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Command whistle item sprite | `/glyph` | `assets/instinct/textures/item/command_whistle.png` — (planned) |
| Whistle — follow cue | `/sfx` | `art/audio/whistle-follow.sfx` → `assets/instinct/sounds/whistle_follow.ogg` — (planned) |
| Whistle — stay cue | `/sfx` | `art/audio/whistle-stay.sfx` → `assets/instinct/sounds/whistle_stay.ogg` — (planned) |
| Whistle — attack cue | `/sfx` | `art/audio/whistle-attack.sfx` → `assets/instinct/sounds/whistle_attack.ogg` — (planned) |
