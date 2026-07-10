# Instinct — Husbandry Overhaul

**_Worth raising._**

**Also on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/instinct-husbandry-overhaul)
and [GitHub Releases](https://github.com/rfizzle/instinct/releases).**
Visit the [website](https://instinct.rfizzle.com) for the full feature list,
config reference, and command guide.

---

Instinct is a husbandry overhaul for **Minecraft 1.21.1 (Fabric)** — the
animals you tame, breed, herd, and keep. Vanilla animals are disposable: pets
sprint into lava, breeding is click-spam, and the optimal farm is 500 cows in a
one-block pit. Instinct makes animals act like they want to live and makes time
spent tending them pay — on the animals already in your world, vanilla or
modded.

**In development.** The design and full behavioral spec are committed and
features are being built against them; this page describes the first release.

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- Every feature independently tunable through `config/instinct.json` —
  hot-reload with `/instinct reload`.
- MIT licensed.

## Features

- **Pets that look before they leap** — tamed wolves, cats, and parrots path
  around lava and cacti, keep a 4-block berth from ignited creepers, and
  refuse to teleport to you while you're falling or swimming in lava.
- **Veteran pets** — pets gain a rank at 10/30/60 days survived: +1 heart and
  +1 attack damage each, up to +3/+3. Crouch-look reads any pet's days.
- **Quality genetics** — well-fed, uncrowded breeding raises bloodlines
  (ordinary → sturdy → prime). A prime cow drops +2 beef and +1 leather;
  overcrowded pits degrade. Three tended generations beat three hundred
  crammed ones.
- **Flocking & herding** — held food gathers a flock 15% faster with 2-block
  spacing. Herding, not shoving.
- **The feeding trough** — one wooden block that feeds and breeds the herd
  while you're away, with a population cap so the farm never becomes a lag
  machine.
- **The command whistle** — copper and bone; one press toggles every pet
  within 20 blocks between Stay and Follow, right-click sends the pack after
  a target.
- **Downed, not dead** — lethal damage downs a pet instead of killing it;
  revive with a golden apple or a Vet Kit at the cost of one veterancy rank.
  Fire, lava, and the void remain final.
- **Modded animals welcome** — tameable/breedable modded animals are covered
  automatically; mods and packs deepen support with one-file tags and data.

## Part of Concord

Part of **[Concord](https://concord.rfizzle.com)** — a modular collection of
system overhauls. Install any, combine all.

**Enhanced by** (all optional, never required): **Mercantile** — butchers sell
Vet Kits and Pedigree Treats at high reputation · **Tribulation** — veterancy
accrues double at difficulty tier 3+ · **Prosperity** — Vet Kits and Pedigree
Treats in far-tier chests · **Cultivation** — farm produce feeds the trough ·
**Distillation** — a brewed remedy revives downed pets · **Respite** — swift
nights age pets on the same clock.
