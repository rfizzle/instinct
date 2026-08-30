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
| Paw glyph 16×16 | `art/glyphs/paw-16.glyph` | `art/hud-icon-16.png` (Jade/WTHIT + recipe-viewer contexts; Instinct has no HUD slot — `DESIGN.md` §2) |

## In-game pixel art

| Asset | `.glyph` source | Final asset |
|---|---|---|
| Command whistle item sprite | `art/glyphs/command-whistle-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/command_whistle.png` |
| Vet kit item sprite | `art/glyphs/vet-kit-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/vet_kit.png` |
| Pedigree treat item sprite | `art/glyphs/pedigree-treat-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/pedigree_treat.png` |
| Keepsake collar item sprite | `art/glyphs/keepsake-collar-16.gen.py` → `art/glyphs/keepsake-collar-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/item/keepsake_collar.png` |
| Feeding trough block faces | `art/glyphs/feeding-trough-{top,side,bottom,inner}-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/block/feeding_trough_{top,side,bottom,inner}.png` |
| Kennel post side | `art/glyphs/kennel-post-16.gen.py` → `art/glyphs/kennel-post-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/block/kennel_post.png` |
| Kennel post cap | `art/glyphs/kennel-post-top-16.glyph` (16 native) | `src/main/resources/assets/instinct/textures/block/kennel_post_top.png` |

## Audio (.sfx — procedural synthesis)

| Asset | `.sfx` source | Final asset |
|---|---|---|
| Rank-up chime | `art/audio/rank-up.sfx` | `src/main/resources/assets/instinct/sounds/rank_up.ogg` |
| Revival shimmer | `art/audio/revive.sfx` | `src/main/resources/assets/instinct/sounds/revive.ogg` |
| Whistle — follow cue | `art/audio/whistle-follow.sfx` | `src/main/resources/assets/instinct/sounds/whistle_follow.ogg` |
| Whistle — stay cue | `art/audio/whistle-stay.sfx` | `src/main/resources/assets/instinct/sounds/whistle_stay.ogg` |
| Whistle — attack cue | `art/audio/whistle-attack.sfx` | `src/main/resources/assets/instinct/sounds/whistle_attack.ogg` |
| Whistle — herd cue | `art/audio/whistle-herd.sfx` | `src/main/resources/assets/instinct/sounds/whistle_herd.ogg` |
| Whistle — guard cue | `art/audio/whistle-guard.sfx` | `src/main/resources/assets/instinct/sounds/whistle_guard.ogg` |
