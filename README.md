<p align="center">
  <img src="art/logo.png" alt="Instinct" width="800">
</p>

<p align="center"><strong>Worth raising.</strong></p>

<p align="center">Instinct — husbandry overhaul</p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/rfizzle/instinct"></a>
  <a href="https://github.com/rfizzle/instinct/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rfizzle/instinct?include_prereleases"></a>
  <a href="https://github.com/rfizzle/instinct/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/rfizzle/instinct/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://modrinth.com/mod/instinct-husbandry-overhaul"><img alt="Modrinth downloads" src="https://img.shields.io/modrinth/dt/x5aRPdcH?logo=modrinth&label=Modrinth&color=00AF5C"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/instinct-husbandry-overhaul"><img alt="CurseForge downloads" src="https://img.shields.io/curseforge/dt/1617731?logo=curseforge&label=CurseForge"></a>
</p>

A husbandry overhaul for Minecraft 1.21.1 (Fabric) — the animals you tame, breed, herd, and keep. Vanilla animals are disposable: pets sprint into lava, breeding is click-spam, and the optimal farm is 500 cows in a one-block pit. Instinct makes animals act like they want to live and makes time spent tending them pay — on the animals already in your world, vanilla or modded. Two blocks, four items, no new mobs, nothing to migrate.

## Download

| [Modrinth](https://modrinth.com/mod/instinct-husbandry-overhaul) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/instinct-husbandry-overhaul) | [GitHub Releases](https://github.com/rfizzle/instinct/releases) | [Website](https://instinct.rfizzle.com) | [Report an issue](https://github.com/rfizzle/instinct/issues) |
| --- | --- | --- | --- | --- |

---

## Features

### Pets That Look Before They Leap

Tamed wolves, cats, and parrots treat lava, fire, and cactus as walls when they pathfind. A lit creeper sends them to a 4-block berth at 1.4× speed — a sitting pet stands, steps clear, and sits back down at its new spot. The follow-owner teleport is suppressed while you are falling more than 3 blocks, swimming in lava, or gliding, so your wolf never materializes into the hole you are falling down.

Your own damage never lands on your own pets — the arrow that overshoots, the splash potion, the TNT. Other players' hits are untouched; this is about your aim, not invulnerability. And a shoulder parrot finally stays put through jumps, short falls, and scratches, coming down on a deliberate double-tap sneak instead of the first time you hop a fence.

### Veteran Pets

Every pet counts the in-game days since you tamed it, including days spent waiting at home. At **10, 30, and 60 days** it becomes **Seasoned**, **Veteran**, **Venerable** — each rank +1 heart of max health and +1 attack damage, to +3/+3.

Each rank also teaches it something an old animal would know. A Seasoned pet warns you in its own voice when a monster sets its eye on you. A Veteran ducks your sweeping blade. A Venerable one steadies the young: pets near it accrue days 25% faster, whoever they belong to. Crouch-look reads any pet's days and rank.

### Quality Genetics

Livestock carry a bloodline grade — **ordinary → sturdy → prime**. Breed within 8 blocks of a hay bale (or trough-fed within the last day) and the offspring has a 50% chance to climb a grade; breed in a crush of more than 12 animals and it risks slipping one. A prime cow drops +2 beef and +1 leather; prime sheep shear +2 wool, prime chickens lay 20% faster.

Newborns of graded stock also inherit a **perk** — *hardy*, *fleet*, *fertile*, or *placid* — biased toward the parents' perks when the pen is well fed, so a tended bloodline stabilizes line by line. A **Pedigree Treat** guarantees the next offspring is born prime.

### Flocking, Herding & Droving

Hold wheat, carrots, or seeds and the matching animals form a flock behind you — 15% faster than vanilla, holding a loose 2-block spacing instead of shoving into your back. Lead three or more and up to two of your following pets fall in behind on their own, each getting behind a straggler and pressing it toward you. Pets press, never bite: a driven animal is never hurt and never panics.

Flocks swim rivers in formation instead of scattering at the bank, and a following pet takes your boat's spare seat, stepping back out when you land.

### Predator Watch

Leave a pet on Stay near your livestock and it watches the fence line. A fox creeping in on the chickens or a wild wolf stalking the sheep finds its hunt broken and itself driven back from the pasture. Any pet guards — a cat turns a fox as a wolf does. A predator with no stationed pet nearby behaves exactly as vanilla.

### The Feeding Trough

A craftable wooden block that holds a stack of feed and breeds the herd while you are away. Hay bales convert to 9 wheat on insert; hoppers fill it, comparators read it. It self-limits — past 16 animals in radius it keeps feeding babies but stops starting new pairs, so a passive farm cannot quietly become a lag machine.

### The Command Whistle

Copper and bone. It speaks to every tamed pet within 20 blocks.

| Input | Order |
| --- | --- |
| Left-click | Toggle the whole pack between Stay and Follow |
| Right-click a mob | Every combat-capable pet stands and attacks it |
| Right-click your own livestock | **Round-up** — pets drive that animal's nearby herd home to you |
| Sneak + right-click a spot | **Guard** — the pack holds that ground and engages hostiles that wander in |
| Sneak + right-click a kennel post | Assign it as the pack's home |
| Sneak + left-click | **Lost-pet census** — every bonded pet beyond earshot, with distance and bearing, or the dimension for one you left in the Nether |

### Downed, Not Dead

Lethal damage **downs** a pet instead of killing it: helpless, ignored by monsters, and waiting indefinitely across saves and logouts. Revive it with a golden apple or a **Vet Kit** and it stands at half health with regeneration, at the cost of one veterancy rank. A tamed **mount** — the whole horse family — goes down the same way, with no rank to lose.

A downed cat, parrot, or pup can be scooped up and carried clear of the fire. Carrying takes no inventory slot but slows you, so a rescue under fire still costs something.

Fire, lava, and the void remain final — and a pet lost that way leaves a **keepsake collar**, engraved with its name and the rank it earned.

### The Kennel Post

Place a kennel post where your animals belong and point the whistle at it: every pet in earshot takes it as home. From then on a Stay order sends your homed pets walking back to their post to settle, instead of dropping them wherever you happened to be standing. A pet that goes down beside its post recovers on its own over about five minutes — no item spent, no rank lost.

### Modded Animals Welcome

Any modded animal that behaves like a vanilla one joins automatically: a tameable otter reads as a pet, a breedable critter reads as livestock. Mods and datapacks opt species in or out with one tag entry, add their feed to the trough, and give their species proper product yields with one small data file — no code, no dependency. Server owners get the final word by name in the config. Species without curated data still work: drop bonuses mirror the animal's own loot table.

### Advancements

**Instinct** (root) · **Old Friend** — raise a pet to Venerable · **Best in Show** — breed a prime animal · **Back from the Brink** — revive a downed pet · **Pack Leader** — command ten pets with a single whistle.

---

## Installation

**Requirements:** Minecraft 1.21.1, Fabric Loader 0.16.10+, Fabric API, Java 21

Drop the jar into `mods/` on both server and client. Config generates at `config/instinct.json` on first launch — hot-reload it with `/instinct reload`.

**Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) for an in-game settings screen; [Jade](https://modrinth.com/mod/jade) or [WTHIT](https://modrinth.com/mod/wthit) to read an animal's grade, perk, veterancy, and downed status off your crosshair.

---

## Commands

| Command | Perm | Description |
| --- | --- | --- |
| `/instinct info` | 0 | How the animal on your crosshair resolved: its coverage sets, grade, perk, and veterancy |
| `/instinct set veterancy <days>` | 2 | Set the accrued days of the pet on your crosshair (0–100000) |
| `/instinct set grade <grade>` | 2 | Set the bloodline grade of the animal on your crosshair |
| `/instinct reload` | 2 | Re-read `config/instinct.json` without a restart |

[Full command reference →](https://instinct.rfizzle.com/commands.html)

---

## Configuration

Every feature is independently tunable in `config/instinct.json` (62 options), with no restart needed. Sections: `Animal Coverage`, `Self-Preservation`, `Veterancy`, `Genetics`, `Flocking & Herding`, `Feeding Trough`, `Command Whistle`, `Kennel Post`, `Downed & Revival`, `Predator Watch`, `Inspection`.

Each system has its own `enable*` toggle, so any part of the mod can be switched off without touching the rest. Out-of-range values are clamped on load with a log line saying what changed, and a config file written by an older version gains the keys it predates on the next start.

[Full config reference →](https://instinct.rfizzle.com/config.html)

---

## Building from Source

```sh
./gradlew build                     # produces build/libs/instinct-<version>.jar
./gradlew test                      # unit tests
./gradlew runGametest               # Fabric gametest suite
./gradlew runDatagen                # regenerate src/main/generated/
./gradlew verifyDatagenIdempotent   # assert the committed generated tree is current
```

`make help` lists the shortcuts.

### Releasing

The pushed `v*` tag is the source of truth — the release workflow derives the version from it. Just tag and push:

```sh
make release VERSION=1.2.3             # tags v1.2.3 and pushes it (triggers the release)
make release VERSION=1.2.3 NO_PUSH=1   # create the tag locally only
```

---

## For Mod Developers

Instinct exposes a stable, read-only API in `com.rfizzle.instinct.api`, following the
[Concord API Standard](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Use it as a soft dependency: compile against the mod with `modCompileOnly` and guard
every call with `FabricLoader.isModLoaded("instinct")`. Everything outside the `api`
package is internal and may change in any release.

**The stable surface**

- `InstinctAPI.isPet(EntityType<?>)` / `InstinctAPI.isLivestock(EntityType<?>)` /
  `InstinctAPI.isMount(EntityType<?>)` — set membership after full Animal Coverage
  resolution (mounts are the horse family by default, extensible by tag and config:
  self-preservation and downed only)
- `InstinctAPI.getGrade(Animal)` / `InstinctAPI.getPerk(Animal)` — bloodline grade
  and birth perk (`ORDINARY`/`NONE` for untracked animals)
- `InstinctAPI.getVeterancyDays(TamableAnimal)` / `InstinctAPI.getVeterancyRank(TamableAnimal)` —
  accrued days and derived rank 0–3
- `InstinctAPI.setVeterancyRateProvider(ToDoubleFunction<TamableAnimal>)` — multiplies live
  veterancy accrual (error-isolated: non-finite, non-positive, or throwing providers count as
  1.0; last registration wins)
- `InstinctAPI.isDowned(LivingEntity)` — downed state
- `InstinctAPI.isTroughFed(Animal)` — trough-fed within the last 24000 ticks

**Events** — Fabric `Event` objects, fired server-side:

- `InstinctAnimalBredCallback(parentA, parentB, child, grade)` — after a newborn's bloodline
  grade resolves at breeding
- `InstinctAnimalDownedCallback(animal, source)` — when a pet or mount enters the downed state,
  with the lethal damage source that downed it
- `InstinctAnimalRevivedCallback(animal, reviver, item)` — on every path back up: an item revival
  in the field, and a pet's own recovery at a kennel post. The recovery path passes a null
  `reviver` and an empty `item`, so null-check the player

A listener that throws is caught, logged once at `WARN`, and skipped — it can never break the
host operation, and never denies the listeners registered after it their call.

An animal mod needs **no code at all** to opt in or out. Ship entries in your own data:

| Tag / data file | What it gets you |
| --- | --- |
| `#instinct:pets` / `#instinct:livestock` / `#instinct:mounts` (+ `_exclude`) | Entity-type tags — opt species explicitly into or out of the three coverage sets |
| `#instinct:carryable` | Small pets a rescuer can pick up and carry while downed |
| `#instinct:predators` (+ `_exclude`) | Species a stationed pet turns off the pasture |
| `#instinct:trough_food` | Your feed items become trough-accepted |
| `#instinct:revive_items` | Your item revives downed pets |
| `#instinct:mirror_products` | Non-edible items the generic drop-bonus fallback may duplicate |
| `data/<ns>/instinct/products/*.json` | One file per species: primary/secondary products for grade drop bonuses. Unknown ids skip harmlessly, so rows for absent mods are safe to ship |

### Gradle Setup

```gradle
repositories {
    // Sibling jars resolve from GitHub Releases through an artifact-only `rfizzle:` ivy
    // repo while the Modrinth projects are not publicly resolvable. See API-STANDARD §4.
    ivy {
        name = 'GitHubReleases'
        url = 'https://github.com'
        patternLayout {
            artifact '/[organisation]/[module]/releases/download/v[revision]/[module]-[revision].jar'
        }
        metadataSources { artifact() }
        content { includeGroup 'rfizzle' }
    }
}

dependencies {
    modCompileOnly "rfizzle:instinct:<version>"
}
```

### Usage Example

```java
if (FabricLoader.getInstance().isModLoaded("instinct")) {
    boolean livestock = com.rfizzle.instinct.api.InstinctAPI.isLivestock(EntityType.COW);
}
```

---

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a modular collection of system overhauls.
Install any, combine all.

Two siblings add optional touches, and neither is required: with
[Tribulation](https://github.com/rfizzle/tribulation), days survived at difficulty tier 3+
count double toward veterancy; with [Prosperity](https://github.com/rfizzle/prosperity),
outlands- and depths-tier chests occasionally hold a Vet Kit or a Pedigree Treat.

---

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Instinct is not
affiliated with Mojang Studios or Microsoft.
