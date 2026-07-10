# Instinct — Asset Manifest

> Where every committed asset lives: its source under `art/` (a re-renderable
> `.glyph` for pixel art, a `.sfx` for audio, or a `.png` master for generated
> hi-res art) and the final file it ships as. **`MISSING`** in the source column
> flags a pixel asset with no `.glyph` source yet — a candidate for the glyph
> pipeline (concord `design/DESIGN-SYSTEM.md` §8). Final paths are under
> `src/main/resources/` unless noted.

## Not yet created

| Asset | Intended source | Destination |
|---|---|---|
| Full logo | Gemini (prompt in `DESIGN.md` §4) | `art/logo.png` → `site/assets/logo.png` — (planned, README masthead + site hero) |
| Mod icon 128×128 | `/glyph` size ladder or Gemini | `art/icon-128.png` → `assets/instinct/icon.png`, `site/assets/icon.png` — (planned) |
| Paw glyph 16×16 | `/glyph` | `art/hud-icon-16.png` (+ `.glyph`) → Jade/WTHIT + recipe-viewer contexts — (planned) |
| Feeding trough block textures (top/side/bottom/inner) | `/glyph` | `assets/instinct/textures/block/feeding_trough_*.png` — (planned) |
| Command whistle item sprite | `/glyph` | `assets/instinct/textures/item/command_whistle.png` — (planned) |
| Vet kit item sprite | `/glyph` | `assets/instinct/textures/item/vet_kit.png` — (planned) |
| Pedigree treat item sprite | `/glyph` | `assets/instinct/textures/item/pedigree_treat.png` — (planned) |
| Whistle — follow cue | `/sfx` | `art/audio/whistle-follow.sfx` → `assets/instinct/sounds/whistle_follow.ogg` — (planned) |
| Whistle — stay cue | `/sfx` | `art/audio/whistle-stay.sfx` → `assets/instinct/sounds/whistle_stay.ogg` — (planned) |
| Whistle — attack cue | `/sfx` | `art/audio/whistle-attack.sfx` → `assets/instinct/sounds/whistle_attack.ogg` — (planned) |
| Rank-up chime | `/sfx` | `art/audio/rank-up.sfx` → `assets/instinct/sounds/rank_up.ogg` — (planned) |
| Revival shimmer | `/sfx` | `art/audio/revive.sfx` → `assets/instinct/sounds/revive.ogg` — (planned) |
| OG image 1200×630 | Gemini (logo on Ink) | `site/assets/og-image.png` — (planned) |
