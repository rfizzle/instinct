#!/usr/bin/env python3
"""Generate the kennel post's two 16x16 glyph specs (SPEC §9).

  kennel-post-16.glyph      — the post's wood: vertical oak grain with dark grooves,
                              matching the feeding trough's oak ramp so the two blocks
                              read as one set.
  kennel-post-top-16.glyph  — the cap's top face: the same oak boards with a small dark
                              paw carved into the middle, so a placed post reads as "home".

Run from the repo root:  python3 art/glyphs/kennel-post-16.gen.py
then render with the glyph tool (see the commands printed at the end).
"""
from pathlib import Path

N = 16
OAK_LIGHT, OAK_MID, OAK_KNOT, OAK_GROOVE = "a", "b", "c", "g"
PAW = "p"  # darker carved paw on the cap

LEGEND = {
    "a": "#b8955c",
    "b": "#a37d47",
    "c": "#895f34",
    "g": "#5f4526",
    "p": "#4a3520",
}

# Deterministic knot positions so the grain looks worked, not noisy.
POST_KNOTS = {(8, 2), (13, 5), (4, 9), (12, 11), (7, 13)}
GROOVE_COLS = {3, 11}


def post_pixel(x, y):
    if x in GROOVE_COLS:
        return OAK_GROOVE
    if (x, y) in POST_KNOTS:
        return OAK_KNOT
    # Two-tone plank grain: alternate light/mid on a diagonal so boards read vertical.
    return OAK_LIGHT if (x + (y // 2)) % 3 else OAK_MID


# A compact paw carved into the cap: one pad, four toes.
PAW_PIXELS = {
    (7, 6), (8, 6),            # toes (top row)
    (5, 7), (10, 7),           # outer toes
    (6, 9), (7, 9), (8, 9), (9, 9),   # pad top
    (6, 10), (7, 10), (8, 10), (9, 10),  # pad body
    (7, 11), (8, 11),          # pad base
}


def top_pixel(x, y):
    if x == 0 or x == N - 1 or y == 0 or y == N - 1:
        return OAK_GROOVE  # the worn rim, matching the trough top
    if (x, y) in PAW_PIXELS:
        return PAW
    return OAK_LIGHT if (x + (y // 2)) % 3 else OAK_MID


def render(name, header, pixel):
    lines = [f"# {header}", "size: 16", "", "legend:"]
    used = sorted({pixel(x, y) for y in range(N) for x in range(N)})
    for token in used:
        lines.append(f"  {token} {LEGEND[token]}")
    lines.append("")
    lines.append("frame:")
    for y in range(N):
        lines.append("  " + "".join(pixel(x, y) for x in range(N)))
    out = Path(__file__).with_name(name)
    out.write_text("\n".join(lines) + "\n")
    print("wrote", out)


render("kennel-post-16.glyph",
       "Instinct 16x16 kennel post — the post's wood: vertical oak grain with dark grooves.",
       post_pixel)
render("kennel-post-top-16.glyph",
       "Instinct 16x16 kennel post — cap top: oak boards with a small paw carved in the middle.",
       top_pixel)

print()
print("render:")
print("  python3 .ai/skills/mc-textures/scripts/glyph.py art/glyphs/kennel-post-16.glyph "
      "-o src/main/resources/assets/instinct/textures/block/kennel_post.png --no-preview")
print("  python3 .ai/skills/mc-textures/scripts/glyph.py art/glyphs/kennel-post-top-16.glyph "
      "-o src/main/resources/assets/instinct/textures/block/kennel_post_top.png --no-preview")
