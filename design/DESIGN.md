# Instinct — Design Specification

> Husbandry Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Instinct makes the animals you tame, breed, and keep worth the keeping: pets that act like they want to live and grow harder to kill the longer they survive, and livestock whose bloodlines reward small, well-tended pastures over mass breeding pits. The name is the thesis — animals with a mind of their own. The visual language draws from **the pasture and the pack**: hay and wheat amber, meadow green, worn wood, and the warm glow of a well-kept farmstead at dusk.

### Tagline

*"Worth raising."*

### Motif

The motif is a **paw print** — the mark an animal leaves, glowing amber like a track pressed into warm earth. It appears in the logo, the mod icon, the 16×16 glyph, site headers, and flavor art. It never appears in another mod's assets, and no sibling motif (arch/compass, hourglass, treasure chest/key, market stall) is adjacent to it.

### Logo Description

**Full Logo (`art/logo.png`):** A large glowing amber paw print — one broad pad, four toes — inlaid in a weathered stone slab, radiating warm light, centered inside a dark stone brickwork frame. Leaf-green vines with small wheat sheaves climb the frame's inner edge; a hay bale and a coiled lead rest at the frame's base as supporting trinkets. Below, "INSTINCT" in a blocky pixel font on a stone banner, with "MINECRAFT HUSBANDRY OVERHAUL" as the subtitle line. Palette: amber glow (`#D9A441` → `#F2C14E`) on dark stone, leaf-green (`#7CB342`) accents only in the vines and sheaves.

**Icon (`art/icon-128.png`):** The glowing amber paw print isolated on its stone slab — high contrast, warm radiance against a dark/transparent background. Reads cleanly at 128×128.

**16×16 glyph (`art/hud-icon-16.png`):** A minimal pixel paw print — one pad, four toes — in wheat amber with an ink outline. Instinct has no HUD slot (§2), so the glyph serves Jade/WTHIT and recipe-viewer contexts only.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary surface | Dark Moss | `#141a0a` | Backgrounds, dark surfaces |
| Secondary surface | Deep Olive | `#232e10` | Mid-tones, card backgrounds |
| Accent 1 | Wheat Amber | `#D9A441` | Glows, highlights, headings, interactive elements |
| Accent 2 | Leaf Green | `#7CB342` | Pasture accents, grade/quality indicators, links |
| Bright | Bright Amber | `#F2C14E` | Hover states, heading gradient end, the paw's glow core |
| Bright green | Meadow | `#9CCC65` | Leaf-green hover states, prime-grade emphasis |

Shared neutrals (text and surfaces) follow the standard tokens as-is — `--color-bone`, `--color-ash`, `--color-smoke`, `--color-ink`, `--color-card`, `--color-elevated` — see concord [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §1.

**Pairing-rule clearance (DESIGN-SYSTEM §2/§7):** the signature pair is the reserved Husbandry row, amber-with-leaf. Amber sits near Meridian's and Prosperity's golds, but neither pair matches: gold-with-violet reads Meridian, gold-with-cyan reads Prosperity, amber-with-leaf reads Instinct. Leaf Green (`#7CB342`, yellow-green) is distinct from Mercantile's blue-green emeralds (`#50C878`/`#6DDB94`). No collision with Tribulation (crimson/ember) or the reserved Apothecary (magenta/copper), Tempest (blue/white), and Stratum (grey/copper-orange) rows. Surfaces are a dark moss/olive tint in the standard value range, distinct from Prosperity's warm bronze (`#1a1408`/`#2e2510`).

### Typography

- **Headings:** pixel/blocky display treatment in the amber gradient (`#D9A441` → `#F2C14E`), with the shared 4s brightness pulse.
- Everything else is the standard (DESIGN-SYSTEM §3); in-game is the vanilla font, always.

---

## 2. HUD Decision

**No slot, by design.** Instinct carries no persistent ambient state a player needs while walking around — veterancy, bloodline grades, and trough status are properties of specific animals and blocks, not of the player's moment-to-moment situation. That information lives in crouch-inspect action-bar lines (SPEC §2/§3), Jade/WTHIT tooltips on animals and the feeding trough, and item tooltips. Whistle feedback is a transient ✦ action-bar line, not a HUD element. No `isHudVisible()`/`getHudHeight()` accessors are published — those are for HUD-bearing mods (concord [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md)).

---

## 3. Assets

The full asset manifest — every `.glyph`/`.sfx` source under `art/`, the final path it ships as, and what is still planned — lives in [`ASSETS.md`](ASSETS.md).

Asset-family judgments (the suite stance: custom where it earns its place, vanilla where vanilla is right):

- **Feeding trough block** — custom textures (top/side/bottom/inner): a new block deserves its own face; worn oak planks with a hay-lined interior, glyph-pipeline pixel art.
- **Items (command whistle, vet kit, pedigree treat)** — custom 16×16 item sprites: copper-and-bone whistle, cloth-wrapped kit with a leaf-green cross, amber-glazed treat.
- **Animals** — vanilla textures untouched, always. Instinct's whole stance is that vanilla's animals are the cast; grades and ranks show through inspection lines and tooltips, never through retextures.
- **Particles** — vanilla particles only (hearts, smoke, happy villager green); Instinct's effects are small and vanilla's set already reads correctly.
- **Sounds** — custom synthesized cues for the whistle's three commands and the two milestone moments (rank-up, revival), where an identity earns its place; everything organic (eating, whines, block placement) stays vanilla. Triggers and subtitles in SPEC; sources in `ASSETS.md`.

---

## 4. Generation Prompts

**Full logo (Gemini):**

> Pixel art logo for a Minecraft mod named "INSTINCT". A dark stone brickwork frame surrounds a weathered stone slab bearing a large glowing paw print (one broad pad, four toes) in warm wheat-amber (#D9A441) with a bright amber core (#F2C14E), radiating soft light. Leaf-green (#7CB342) vines with small wheat sheaves climb the inner edge of the frame; a small pixel hay bale and a coiled rope lead sit at the base. Below the emblem, "INSTINCT" in a blocky pixel font on a carved stone banner, with "MINECRAFT HUSBANDRY OVERHAUL" in smaller pixel type underneath. Dark background (#0a0a0a), moody farmstead-at-dusk lighting, 16-bit pixel art style, no anti-aliasing.

**128×128 icon (Gemini or `/glyph` ladder):**

> Pixel art icon: a glowing wheat-amber (#D9A441) paw print with a bright core (#F2C14E) inlaid in a dark weathered stone slab, soft warm radiance, dark transparent-friendly background, crisp 16-bit pixel style, legible at 128×128.

Pixel-art sources for the glyph, block, and item textures are `.glyph` files under `art/` — referenced in `ASSETS.md`, never duplicated here.

---

## 5. Image References

Reference and exploration images live in `art/exploration/` (generation candidates, rejected variants, palette studies). Useful vanilla reference: hay bale and oak plank textures (the trough's material anchors), wolf/cat models at rest (downed pose framing), and the vanilla breeding heart particle (the visual register Instinct's moments sit beside).

---

## 6. Website & Listing Brand Notes

Content lives elsewhere — page copy in `site/pages/*.json` rendered by the shared Concord template at `instinct.rfizzle.com`, store copy in `site/listing-modrinth.md` / `site/listing-curseforge.md`, release notes in `changelogs/`. This section is brand only.

- **Accent usage:** amber (`#D9A441` → `#F2C14E`) carries headings, hero glow, and interactive elements; leaf green (`#7CB342`) is the secondary — links, grade badges, feature-card borders. Body text and surfaces stay on the shared neutrals over the moss/olive tinted surfaces.
- **Hero:** the full logo over the dark field, tagline beneath, version/loader badges.
- **Gallery art direction** (1920×1080, vanilla or a light shader): a wolf pack pathing around a lava lake; a herd flocking in a neat line behind a player holding wheat; a fenced pasture with a feeding trough and hearts; the whistle moment — a dozen wolves turning at once; a downed wolf being revived with a vet kit.
- **OG image:** full logo on Ink at 1200×630; `<title>` = `Instinct — Worth raising.`; meta description = tagline + one mechanical sentence.

---

## 7. Concord Context

Instinct owns the husbandry silo: the animals you tame, breed, herd, and keep — their survival, lineage, and yield. It does not touch villagers (Mercantile), difficulty or hostile mobs (Tribulation), container loot (Prosperity), or enchantments (Meridian).

Against its siblings' signatures, amber-with-leaf reads warm and pastoral where Meridian's gold-with-violet reads arcane, Prosperity's gold-with-cyan reads treasure, Mercantile's emerald reads commerce, and Tribulation's crimson reads threat.

Suite references: concord [`VISION.md`](../../concord/VISION.md), [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md), [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md), [`API-STANDARD.md`](../../concord/API-STANDARD.md).
