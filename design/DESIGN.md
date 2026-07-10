# Instinct — Design Specification

> Husbandry Overhaul for Minecraft 1.21.1 Fabric

---

## 1. Brand Identity

### Narrative

Instinct makes the animals you tame, breed, and keep worth the keeping: pets that act like they want to live and grow harder to kill the longer they survive, and livestock whose bloodlines reward small, well-tended pastures over mass breeding pits. The name is the thesis — animals with a mind of their own. The visual language draws from **the hearth-side of husbandry**: breeding hearts, tanned hide and leather, worn wood, and lantern-lit stables at dusk.

### Tagline

*"Worth raising."*

### Motif

The motif is a **paw print** — the mark an animal leaves, glowing warm rose like the breeding hearts vanilla already taught players to love. It appears in the logo, the mod icon, the 16×16 glyph, site headers, and flavor art. It never appears in another mod's assets, and no sibling motif (arch/compass, hourglass, treasure chest/key, market stall, lantern, alembic still, Cultivation's sheaf) is adjacent to it.

### Logo Description

**Full Logo (`art/logo.png`):** A large glowing rose paw print — one broad pad, four toes with claw marks — pressed into a weathered circular stone slab, rimmed by a stitched russet leather collar with a hanging buckle strap, over dark umber stone brickwork under a thin strip of starry night. A coiled rope lead hangs from the collar's left; a hay bale and a small bone rest at the base where a carved stone ribbon banner carries "INSTINCT" in blocky russet pixel type, with "MINECRAFT HUSBANDRY OVERHAUL" beneath. Wall lanterns and hanging chains flank the brickwork. Palette: rose glow (`#E5709B` → `#F5A8C8`) radiating from the paw, hide russet (`#B8622B`) in the collar, rope, and lettering.

**Icon (`art/icon-128.png`):** The glowing rose paw print isolated on its stone slab — high contrast, warm radiance against a dark/transparent background. Reads cleanly at 128×128.

**16×16 glyph (`art/hud-icon-16.png`):** A minimal pixel paw print — one pad, four toes — in heart rose with an ink outline. Instinct has no HUD slot (§2), so the glyph serves Jade/WTHIT and recipe-viewer contexts only.

### Color Palette

| Role | Color | Hex | Usage |
|------|-------|-----|-------|
| Primary surface | Dark Umber | `#1a120a` | Backgrounds, dark surfaces |
| Secondary surface | Deep Hide | `#2e2210` | Mid-tones, card backgrounds |
| Accent 1 | Heart Rose | `#E5709B` | Glows, highlights, headings, interactive elements |
| Accent 2 | Hide Russet | `#B8622B` | Leather accents, grade/quality indicators, links |
| Bright | Rose Glow | `#F5A8C8` | Hover states, heading gradient end, the paw's glow core |
| Bright warm | Tanned Hide | `#D98A4A` | Russet hover states, prime-grade emphasis |

Shared neutrals (text and surfaces) follow the standard tokens as-is — `--color-bone`, `--color-ash`, `--color-smoke`, `--color-ink`, `--color-card`, `--color-elevated` — see concord [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md) §1.

**Pairing-rule clearance (DESIGN-SYSTEM §2/§7):** rose-with-russet shares no accent with any member or reserved row. Heart Rose is the suite's first pink — nothing adjacent anywhere. Hide Russet's nearest neighbors are Distillation's Copper (`#E77C56`, a light salmon paired with magenta) and the reserved Stratum Copper Orange (`#E07A3F`, paired with grey); Instinct's russet is darker leather-brown and its rose partner disambiguates the pair at a glance. The old reserved Husbandry pair (Wheat Amber/Leaf Green) belongs to Cultivation, the field half of the split silo. Surfaces are a dark umber tint in the standard value range — redder than Prosperity's bronze (`#1a1408`/`#2e2510`) and warmer than Cultivation's loam (`#101a0a`/`#1c2e10`).

### Typography

- **Headings:** pixel/blocky display treatment in the rose gradient (`#E5709B` → `#F5A8C8`), with the shared 4s brightness pulse.
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

> Pixel art logo for a Minecraft mod named "INSTINCT". A circular stone medallion rimmed in stitched russet leather (#B8622B) over dark umber stone brickwork holds a weathered stone slab bearing a large glowing paw print (one broad pad, four toes) in warm heart-rose (#E5709B) with a bright core (#F5A8C8), radiating soft light. A coiled rope lead and leather straps wrap the medallion's rim; a small pixel hay bale and a bone sit at its base. Below the emblem, "INSTINCT" in a blocky pixel font on a carved stone banner, with "MINECRAFT HUSBANDRY OVERHAUL" in smaller pixel type underneath. Dark background (#0a0a0a), moody stable-at-dusk lighting, 16-bit pixel art style, no anti-aliasing.

**128×128 icon (Gemini or `/glyph` ladder):**

> Pixel art icon: a glowing heart-rose (#E5709B) paw print with a bright core (#F5A8C8) inlaid in a dark weathered stone slab, soft warm radiance, dark transparent-friendly background, crisp 16-bit pixel style, legible at 128×128.

Pixel-art sources for the glyph, block, and item textures are `.glyph` files under `art/` — referenced in `ASSETS.md`, never duplicated here.

---

## 5. Image References

Reference and exploration images live in `art/exploration/` (generation candidates, rejected variants, palette studies). Useful vanilla reference: hay bale and oak plank textures (the trough's material anchors), wolf/cat models at rest (downed pose framing), and the vanilla breeding heart particle (the visual register Instinct's moments sit beside).

---

## 6. Website & Listing Brand Notes

Content lives elsewhere — page copy in `site/pages/*.json` rendered by the shared Concord template at `instinct.rfizzle.com`, store copy in `site/listing-modrinth.md` / `site/listing-curseforge.md`, release notes in `changelogs/`. This section is brand only.

- **Accent usage:** rose (`#E5709B` → `#F5A8C8`) carries headings, hero glow, and interactive elements; hide russet (`#B8622B`) is the secondary — links, grade badges, feature-card borders. Body text and surfaces stay on the shared neutrals over the umber tinted surfaces.
- **Hero:** the full logo over the dark field, tagline beneath, version/loader badges.
- **Gallery art direction** (1920×1080, vanilla or a light shader): a wolf pack pathing around a lava lake; a herd flocking in a neat line behind a player holding wheat; a fenced pasture with a feeding trough and hearts; the whistle moment — a dozen wolves turning at once; a downed wolf being revived with a vet kit.
- **OG image:** full logo on Ink at 1200×630; `<title>` = `Instinct — Worth raising.`; meta description = tagline + one mechanical sentence.

---

## 7. Concord Context

Instinct owns the animal half of the husbandry silo: the animals you tame, breed, herd, and keep — their survival, lineage, and yield. The field half — crops, soil, food values, cooking — is Cultivation's; the boundary contract is that Instinct never changes what grows or what food does when eaten, and Cultivation never changes what animals do or drop. Instinct also does not touch villagers (Mercantile), difficulty or hostile mobs (Tribulation), container loot (Prosperity), enchantments (Meridian), potions and remedies (Distillation), or sleep and the day-night rhythm (Respite).

Against its siblings' signatures, rose-with-russet reads hearth and hide where Cultivation's amber-with-leaf reads field and harvest, Meridian's gold-with-violet reads arcane, Prosperity's gold-with-cyan reads treasure, Mercantile's emerald reads commerce, Tribulation's crimson reads threat, Respite's candle-with-indigo reads night, and Distillation's magenta-with-copper reads alchemy. The tagline pair is deliberate kin: Cultivation's "Worth growing." and Instinct's "Worth raising." are the two halves of one husbandry promise.

Suite references: concord [`VISION.md`](../../concord/VISION.md), [`design/DESIGN-SYSTEM.md`](../../concord/design/DESIGN-SYSTEM.md), [`HUD-STANDARD.md`](../../concord/HUD-STANDARD.md), [`API-STANDARD.md`](../../concord/API-STANDARD.md).
