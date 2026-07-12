# Instinct — Feature Spec

Minecraft 1.21.1 Fabric mod. Husbandry overhaul.

**Architectural philosophy:** Augment, never replace. Instinct never registers replacement entity types, never swaps or subclasses vanilla animals, and never rewrites a vanilla AI brain — all behavior changes are *additional* goals, goal wrappers, and pathfinding penalties injected into vanilla mobs at load, and all persistent state (veterancy, bloodline grade, downed status, trough-fed recency) rides **persistent Fabric data attachments** on the vanilla entities (the same `AttachmentType` mechanism the rest of the Concord suite uses). Remove Instinct and every animal is a byte-compatible vanilla animal plus inert attachment data. New registrations are limited to one block (feeding trough), three items (command whistle, vet kit, pedigree treat), six sounds, and the block entity behind the trough. All gameplay decisions run server-side; the client receives only display state (downed pose flag, trough fill).

**Asset philosophy:** Vanilla animals keep vanilla looks, always — grades and ranks surface through inspection lines and tooltips, never retextures. Custom pixel art (glyph pipeline — `/glyph`, `mc-textures` skill, concord `design/DESIGN-SYSTEM.md` §8, `.glyph` sources beside masters) covers only what Instinct adds: trough block faces, the three item sprites, and the 16×16 Jade glyph. Particles are vanilla (`heart`, `smoke`, `happy_villager`). Sounds stay vanilla where the cue is organic (eating, whines, block fill, a pet's warning growl); the whistle's four commands and the two milestone moments (rank-up, revival) are custom synthesized cues via the `/sfx` pipeline (§9), each with a subtitle.

---

## Animal Coverage — the pets and livestock sets

Every feature below applies to one of two membership sets, resolved per entity type. The goal: a modded animal that behaves like a vanilla one gets Instinct's treatment automatically, with no code from its author — and every automatic decision can be overridden by that author, a pack maker, or a server owner.

**The two sets:**
- **Pets** — self-preservation (§1), veterancy (§2), herding work (§4/§6), the whistle (§6), downed & revival (§7). Each feature additionally requires the individual animal to be tamed.
- **Livestock** — genetics (§3), flocking & being herded (§4/§6), the trough (§5).

**Resolution order** (first match wins), per entity type:

1. **Config** — `petsExclude` / `livestockExclude`, then `petsInclude` / `livestockInclude`. The server owner has the last word.
2. **Tags** — `#instinct:pets_exclude` / `#instinct:livestock_exclude`, then `#instinct:pets` / `#instinct:livestock`. Entity-type tags are ordinary data: a third-party animal mod ships a one-file tag entry in Instinct's namespace, a pack maker uses a datapack — no compile-time dependency, no API call, nothing to guard.
3. **Heuristic** (when `autoDetectAnimals` is true, the default) — a tamable animal type (`TamableAnimal`) is a **pet**; any other breedable animal type (`Animal`) is **livestock**. A type that qualifies as a pet is never heuristically livestock.

Instinct ships its vanilla membership as default tag contents, overridable like any tag: `#instinct:pets` = wolf, cat, parrot; `#instinct:livestock` = cow, sheep, pig, chicken, rabbit, goat; `#instinct:livestock_exclude` = the horse family (horse, donkey, mule, camel, llama, trader llama, skeleton horse, zombie horse) — vanilla horses already have their own bred-stat inheritance, and grafting grades onto it would double-dip.

**Graceful degradation.** Membership guarantees the *state* features — veterancy days, grades, perks, downed, whistle selection — because they run on attachments and attribute modifiers every living entity has. The *behavior* features attach best-effort: hazard maluses and the creeper berth apply to any pathfinding animal; teleport refusal gates the vanilla follow-owner behavior, so pets using a custom follow goal keep their own teleport rules; flocking upgrades only the vanilla tempt behavior and leaves a custom modded tempt goal untouched rather than broken. A feature that cannot attach simply doesn't — never a crash, never an altered vanilla-mod behavior.

**Genetics product data.** Death-drop bonuses (§3) need each species' products. That table is data, not code: Instinct reads `data/<namespace>/instinct/products/*.json` (reloadable with `/reload`), one entry per species:

```json
{
  "entity": "minecraft:cow",
  "primary": "minecraft:beef",
  "primary_cooked": "minecraft:cooked_beef",
  "secondary": "minecraft:leather"
}
```

- `primary_cooked` and `secondary` are optional; `"special": "wool_coat"` replaces `secondary` for the sheep coat-color case.
- Instinct ships the vanilla rows in-jar; a mod or pack adds rows for its own species the same way; on duplicate entity ids, standard datapack ordering wins.
- Rows naming an unknown entity or item id (their mod absent) are skipped and logged once at debug — curated rows for popular animal mods may ship in Instinct's own data and stay inert until that mod is present.

**Mirror fallback.** A livestock species with no product row still carries full genetics (grades, perks, inheritance, treat); when `enableGenericDropMirror` is true its death-drop bonus mirrors the animal's own loot: candidate stacks are the actual death drops whose item is edible or in `#instinct:mirror_products`; the largest candidate stack is treated as the primary product (sturdy +1, prime +2), the second-largest as secondary (sturdy 50% +1, prime +1). No candidates → no bonus. Deterministic given the drop roll.

**Item tags.** `#instinct:trough_food` — what the trough accepts (§5); ships with wheat, carrot, potato, beetroot, and the six vanilla seeds. `#instinct:mirror_products` — non-edible items the mirror fallback may duplicate; ships with leather, feather, rabbit hide, and `#minecraft:wool`. `#instinct:revive_items` — items that revive a downed pet (§7); ships with the golden apples and the vet kit.

---

## 1. Pet Self-Preservation

Tamed pets stop dying to terrain and to their owner's misadventures.

### Problem

Vanilla tamed wolves and cats pathfind through lava and cacti, sit next to hissing creepers, and teleport to an owner who is mid-fall or swimming in lava — the pet's death is usually the owner's pathing, not the owner's mistake. This kills the long-lived pets that §2 (Veterancy) exists to reward.

### Behavior

Applies to every tamed animal in the **pets set** (Animal Coverage). Three independent protections:

**1. Hazard-aware pathing.** The pet's navigation treats lava, fire, and cactus path nodes as impassable — it paths around them or, if no safe route exists, stays put rather than walking through. This affects navigation only; combat targeting, sitting, and all other AI are untouched.

**2. Creeper berth.** When a creeper's fuse is ignited (swelling, by player flint-and-steel, or trigger) within `creeperBerthBlocks + 3` blocks of the pet, the pet immediately moves away at 1.4× speed until it is at least `creeperBerthBlocks` (default 4) blocks from that creeper, then resumes its previous behavior.
- A **sitting** pet stands, steps clear to the berth distance, and **sits again** at its new position. Stay means stay — minus the blast radius.
- A pet currently attacking the igniting creeper breaks off its attack for the duration of the fuse; if the fuse resets (creeper survives, player leaves swell radius), the pet may re-engage.
- The berth check runs against every ignited creeper in range; the flee vector points away from the nearest one.

**3. Teleport refusal.** The follow-owner teleport (vanilla: owner more than 12 blocks away) is suppressed while any of these is true of the owner:
- fall distance greater than `teleportSuppressFallDistance` (default 3.0 blocks),
- in lava,
- gliding with elytra.

While suppressed, the pet continues normal follow pathing (it will still *walk* toward the owner along safe ground). Teleporting resumes the tick the owner is on solid ground, out of lava, and not gliding. The teleport destination rules themselves stay vanilla.

### Sitting and commanded state

Protections 1 and 2 apply regardless of sit state (a sitting pet flees a fuse, then re-sits). Protection 3 is inherently about following pets. Downed pets (§7) have no AI and are exempt from all three.

### Multiplayer

Per-pet, evaluated against the pet's own owner only. Another player falling past your wolf changes nothing.

### Failure paths

If the pet is already standing in a hazard (spawned there, pushed there), hazard-aware pathing does not trap it: escaping a damaging node to a safe node is always permitted; only *entering* hazard nodes is blocked. If every path out crosses lava, the pet stays put and vanilla damage/death (or §7 Downed) proceeds.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableSelfPreservation` | bool | `true` | |
| `creeperBerthBlocks` | int | `4` | 2–8 |
| `teleportSuppressFallDistance` | double | `3.0` | 0.5–10.0 |

### Implementation Notes

- Goal injection on `ServerEntityEvents.ENTITY_LOAD`: if the entity's type resolves into the pets set, set pathfinding maluses (`PathType.LAVA`, `PathType.DAMAGE_FIRE`, `PathType.DAMAGE_OTHER` → `-1.0`) and add a `CreeperBerthGoal` (priority 1, above sit) implemented as a targeted flee with a re-sit memory (was-sitting flag captured on trigger, restored on completion).
- Malus application is idempotent (setting the same malus twice is harmless), so re-loads are safe.
- Teleport suppression: a mixin gating the vanilla follow-owner goal's teleport step on the owner-state predicate above. The predicate lives in one helper (`SelfPreservation.ownerUnsafeToJoin(owner)`) so the goal mixin stays a two-line guard. Modded pets running the vanilla goal get this free; custom follow goals are left alone (Animal Coverage → graceful degradation).
- Escape-vs-enter asymmetry comes free with maluses: maluses affect node *cost evaluation* for nodes being entered; the current node is not re-evaluated. No extra code, but the gametest below pins it.

---

## 2. Pet Veterancy

Pets quietly grow stronger the longer they survive.

### Problem

A hundred-day-old wolf and a freshly tamed one are identical in vanilla; nothing rewards keeping a pet alive, so pets are treated as disposable. VISION.md promises "an old wolf is meaningfully stronger than a fresh tame."

### Behavior

Applies to every tamed animal in the **pets set** (Animal Coverage).

**Accrual.** Each pet tracks **accrued days**: in-game days elapsed since taming, counted by world game time (a pet left sitting at home in an unloaded chunk still accrues — it survived). Accrual is computed lazily: on a 200-tick server cadence while loaded, and on load/inspect/rank-check, the pet adds `(now − lastAccrualGameTime) / 24000 × rate` to `accruedDays`, where `rate` is 1.0 unless a veterancy-rate provider (§Public API) returns a different multiplier. Provider rates apply only to loaded, live accrual; gaps spent unloaded always accrue at 1.0.

**Ranks.** Crossing a threshold in `veterancyThresholdDays` (default `[10, 30, 60]`) grants a rank, up to rank 3:

| Rank | Name | Days (default) | Bonus (cumulative) |
|---|---|---|---|
| 0 | — | 0 | none |
| 1 | Seasoned | 10 | +2.0 max health, +1.0 attack damage |
| 2 | Veteran | 30 | +4.0 max health, +2.0 attack damage |
| 3 | Venerable | 60 | +6.0 max health, +3.0 attack damage |

Per-rank increments are `healthPerRank` (default 2.0) and `damagePerRank` (default 1.0). Pets without an attack attribute (parrots, many modded companions) receive the health bonus only.

**Rank-up moment.** The tick a rank is gained (pet loaded): the pet is healed by the health increment (no phantom empty hearts), 7 `heart` particles burst over it, the custom rank-up cue plays at the pet, and the owner — if online and within 32 blocks — sees the ✦ action-bar line: `✦ <name> has grown stronger.` (`<name>` = custom name, else species name). If the rank is crossed while unloaded/offline, the attribute update applies silently on next load; the moment is not queued.

**Rank behaviors.** While `enableRankBehaviors` is true (default), each rank also grants one learned behavior, cumulative. Behaviors follow the derived rank exactly: a demoted pet (threshold change, revival penalty §7) loses the behaviors above its new rank.

| Rank | Behavior |
|---|---|
| 1 Seasoned | **Warning** — the pet warns its owner of hostile attention |
| 2 Veteran | **Knows your swing** — the owner's sweep attacks no longer strike the pet |
| 3 Venerable | **Mentor** — nearby lower-rank pets accrue veterancy 25% faster |

**Warning (rank 1+).** Evaluated on a 40-tick server cadence per online owner with loaded rank-1+ pets: for each monster within `warningRadiusBlocks` (default 16) of such a pet whose current attack target is that pet's owner, and which no pet of that owner has warned about in the last 300 ticks — the nearest eligible pet warns: it faces the threat and plays its species' own aggression or hurt sound (wolf growl, cat hiss, parrot's threat imitation — always the entity's own vanilla voice, volume 1.0). No text, no particles; the pet's voice *is* the message. A sitting pet warns without standing; a downed pet never warns. One warning per threat per owner per 300 ticks, however many pets qualify.

**Knows your swing (rank 2+).** The owner's sweeping-edge area damage skips their own rank-2+ pets. Direct hits are unchanged — the pet learned to duck the arc, not to be immune to its owner. Other players' sweeps, and all other damage, are unaffected.

**Mentor (rank 3).** While a rank-3 pet is loaded, alive, and not downed, every pets-set tamed animal of rank 0–2 within `mentorRadiusBlocks` (default 16) accrues veterancy at ×(1 + `mentorRateBonus`) (default 0.25). Non-stacking: any number of mentors in range yields one bonus. Composes multiplicatively with the veterancy-rate provider (§Public API) — e.g. Tribulation's 2.0 × the mentor's 1.25 = 2.5. Like provider rates, it applies to live loaded accrual only; unloaded gaps stay at 1.0. Owners may differ — any rank-3 pet steadies any lower-rank pet.

**Inspection.** While `enableInspection` is true: a crouching owner whose crosshair rests on their own pet within 8 blocks receives an action-bar line: `✦ <name> has seen <days> days.` at rank 0, or `✦ <name> has seen <days> days — <rank name>.` at rank 1+. Emitted once per crosshair acquisition (re-emitted only after the crosshair leaves the pet or crouch is released). Server-computed, action bar only, nothing persistent on screen.

### Rank loss

Revival from the downed state costs one rank when `downedRankPenalty` is true — specified in §7.

### Edge cases

- **Attribute idempotency:** bonuses are applied as fixed-id attribute modifiers (`instinct:veterancy_health`, `instinct:veterancy_attack`) whose value is recomputed from current rank — re-applying replaces, never stacks. A config change to `healthPerRank` retro-applies on each pet's next load.
- **Offspring:** veterancy is individual; bred pets (or any new tame) start at 0 accrued days.
- **Untamed:** if a pet somehow becomes untamed, accrual stops and bonuses are removed; the attachment is retained (re-taming the same entity resumes from prior days).
- **Threshold config changes:** rank is always derived from `accruedDays` against the *current* threshold list — shortening thresholds can promote pets on next load; lengthening can demote (attributes follow).
- **Vanilla health cap:** max-health bonus stacks with vanilla healing behavior; current health is clamped to new max on demotion.
- **Warning without a voice:** a modded species with no registered hurt/ambient sound warns silently (faces the threat only) — never a crash, never a substituted foreign sound.
- **Warning vs. engaged threats:** the warning keys on the monster's *target*, not on combat state — a threat the owner is already fighting still gets its one warning, then goes quiet for 300 ticks. Cheap and predictable beats clever here.
- **Pets without an attack attribute** (parrots, many modded companions) get every rank behavior — warning, sweep-safety, and mentor are all non-combat. Only the damage bonus skips them.

### Multiplayer

Per-pet, owner-agnostic accrual (game time is global). The inspection line answers only the pet's owner; other players see nothing. Warnings fire per owner against that owner's own threats; the sweep guard applies only between a pet and its own owner. The mentor aura is deliberately cross-owner — a server's shared kennel benefits everyone's pups equally.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableVeterancy` | bool | `true` | |
| `veterancyThresholdDays` | int list | `[10, 30, 60]` | ascending, 1–3 entries, each 1–1000 |
| `healthPerRank` | double | `2.0` | 0.0–20.0 |
| `damagePerRank` | double | `1.0` | 0.0–10.0 |
| `enableRankBehaviors` | bool | `true` | warning / sweep-safety / mentor |
| `warningRadiusBlocks` | int | `16` | 8–24 |
| `mentorRadiusBlocks` | int | `16` | 8–32 |
| `mentorRateBonus` | double | `0.25` | 0.0–1.0 |
| `downedRankPenalty` | bool | `true` | (see §7) |
| `enableInspection` | bool | `true` | (shared with §3) |

### Implementation Notes

- Attachment `VeterancyData { double accruedDays; long lastAccrualGameTime; }` on the entity, persistent, codec-backed. Absent attachment ⇒ initialized at first accrual check after taming (`accruedDays = 0`).
- Accrual cadence rides a 200-tick modulo in a `ServerLivingEntityEvents`-adjacent per-entity tick hook or a lightweight server tick scan of tracked tamed entities; either is acceptable, but the accrual *math* lives in one pure helper (`Veterancy.accrue(data, now, rate)`) for unit testing.
- Rank derivation is pure: `Veterancy.rankFor(days, thresholds)`.
- Inspection detection: server-side, on the crouching player's tick — a 8-block raycast for the crosshair entity; per-player "last inspected entity" field provides the once-per-acquisition edge.
- The rate provider hook is a single registered `ToDoubleFunction<TamableAnimal>` defaulting to `1.0`; non-finite or non-positive returns are clamped to 1.0 (error isolation per API-STANDARD).
- Warning: rides a 40-tick server sweep over online players with rank-1+ pets loaded (`mc-tick-work` discipline — one AABB monster query per such owner per sweep, radius `warningRadiusBlocks` + pet spread); the warned-threat dedupe is a per-owner transient map of entity id → expiry tick, cleared on `SERVER_STOPPED`. Facing uses the pet's look control only — no goal injection needed.
- Knows your swing: a mixin filtering the sweep-AoE victim collection in `Player#attack` — skip entities that resolve to a pets-set tamed animal owned by the attacker at rank ≥ 2. One predicate helper (`Veterancy.ducksSweep(pet, attacker)`), two-line guard.
- Mentor: resolved inside the existing accrual pass — when accruing a rank 0–2 pet, one AABB query for a qualifying rank-3 pet within `mentorRadiusBlocks`, result folded into the same `rate` passed to `Veterancy.accrue(data, now, rate)`. The 200-tick accrual cadence bounds the query cost.

---

## 3. Quality Genetics

Well-tended breeding produces better animals; crowded pits produce worse ones.

### Problem

Vanilla breeding output is identical regardless of care — the optimal farm is 500 cows in a 1×1 hole. Nothing rewards the pasture over the crusher, and the crusher is also a server's worst lag source.

### Behavior

Applies to every animal in the **livestock set** (Animal Coverage).

**Grades.** Every covered animal carries a bloodline grade: **ordinary (0)**, **sturdy (1)**, or **prime (2)**. Naturally spawned, spawn-egg, and converted-from-nothing animals are ordinary. The grade is hidden state, surfaced only by inspection (below), Jade/WTHIT, and `/instinct info`.

**Inheritance at breeding.** When two covered animals produce offspring:

1. Base grade = `floor((gradeA + gradeB) / 2)`.
2. **Well-fed** if, at the moment of breeding, either: a hay bale block is within `hayRadiusBlocks` (default 8, spherical) of either parent, **or** either parent was trough-fed (§5) within the last 24000 ticks.
3. **Crowded** if more than `crowdingThreshold` (default 12) covered animals (any species, adults and babies, the parents included) are within `crowdingRadiusBlocks` (default 8, spherical) of the breeding pair's midpoint.
4. Resolution:
   - well-fed and not crowded → `gradeUpgradeChance` (default 0.5) to gain +1 grade (capped at prime);
   - crowded and not well-fed → `gradeDowngradeChance` (default 0.5) to lose 1 grade (floored at ordinary);
   - both or neither → base grade unchanged.
5. **Pedigree treat override:** if either parent carries the treat flag (below), the offspring is born **prime**, the flag is cleared from that parent, and steps 1–4 are skipped.

**Birth perks.** A newborn of grade 1+ is born with exactly one perk; grade-0 newborns get none. The pool:

| Perk | Effect |
|---|---|
| **Hardy** | +1.0 max health × grade |
| **Fleet** | +4% movement speed × grade |
| **Fertile** | breeding cooldown −15% × grade (the animal's own post-breed cooldown; prime fertile = −30%) |
| **Placid** | the panic sprint from damage or startle is suppressed (binary, all grades) — unless the animal is on fire or in lava, where flight overrides calm |

The perk and grade are permanent and survive growing up.

**Perk inheritance.** The newborn's perk is rolled from the parents' perks — but the bias applies only when the breeding is **well-fed** (rule 2 above); a breeding that is not well-fed rolls uniformly at random from the pool. Tended pastures are how bloodlines stabilize:

- both parents share perk P → P at **80%**, else uniform among the other three;
- parents carry different perks P and Q → **40% / 40%**, uniform 20%;
- exactly one parent has a perk P → P at **50%**, uniform 50%;
- neither parent has a perk → uniform.

An uncovered or grade-0 parent counts as perkless. The pedigree treat forces the *grade* (rule 5); the perk still rolls by these rules.

**Yield — death drops.** When a covered animal dies and drops its products (any death that produces loot, regardless of killer), bonus drops are added after the vanilla roll (Looting and sibling effects included, untouched). Products come from the species' product-data row (Animal Coverage); species without a row use the mirror fallback. The shipped vanilla rows:

| Species | Primary product | Secondary product |
|---|---|---|
| Cow | beef (steak if the vanilla drop was cooked) | leather |
| Pig | porkchop (cooked in kind) | — |
| Sheep | mutton (cooked in kind) | wool of its coat color |
| Chicken | chicken (cooked in kind) | feather |
| Rabbit | rabbit (cooked in kind) | rabbit hide |
| Goat | — | — |

- **Sturdy:** +1 primary; 50% chance of +1 secondary.
- **Prime:** +2 primary; +1 secondary.
- Goats have no vanilla products; their genetics carry only the birth perk and bloodline value.
- Baby animals drop nothing bonus (vanilla babies drop nothing).

**Yield — renewables.**
- Shearing a sheep yields +1 wool at sturdy, +2 at prime (on top of the vanilla 1–3).
- Chicken egg-lay interval is reduced 10% at sturdy, 20% at prime.

**Pedigree treat.** New item `instinct:pedigree_treat`, stack size 16. Crafted shapeless: 1 golden carrot + 1 hay bale + 1 honey bottle → 1 treat (bottle returned). Using it on an adult covered animal consumes the treat, plays the eat sound with `happy_villager` particles, and sets a persistent flag: that animal's **next** offspring is born prime (resolution rule 5). Feeding a second treat to the same animal before it breeds does nothing and is refused (hand swing, no consume, no message). The flag survives save/load and shows in `/instinct info`.

**Inspection.** Same crouch-look mechanism as §2, for covered animals within 8 blocks (any player, not just an owner — livestock have no owner): `✦ A sturdy cow — hardy.` / `✦ A prime sheep — placid.` (grade, then perk — the read a breeder culls by). Ordinary animals produce no line (silence is the baseline).

### Edge cases

- **Mixed pairs:** breeding always uses both parents' grades; a wild-caught ordinary partner drags the base grade down — deliberate: bloodlines take upkeep.
- **Conversions:** where vanilla copies entity data across a conversion (mooshroom sheared → cow), the grade and perk copy with it.
- **Uncovered partners:** if only one parent's type is in the livestock set (membership edited mid-world, or a cross-mod pairing where one side is excluded), the missing grade counts as 0.
- **Hay bale detection** is by block state in the scan radius; hay in item frames or inventories does not count.
- **Fertile scope:** fertile scales only the vanilla post-breed love cooldown (each parent's own, by its own perk and grade); it never touches the chicken egg timer (that is grade's renewable, above) or baby growth.
- **Placid under drive:** being pressed by a herding pet (§4/§6) is not damage and startles nothing — but when a drive takes fire, placid animals hold the line while the rest panic-sprint and straggle; drive assist recovers the stragglers. Placid is the drover's perk.
- **Placid panic coverage:** panic suppression swaps the exact-class vanilla `PanicGoal`, so it reaches cattle, pigs, sheep, and chickens. Rabbits panic through a `PanicGoal` subclass and goats through a brain behavior — neither is swapped (the same discipline that never touches a modded panic goal), so a placid rabbit or goat still flees but carries the grade, bloodline value, and perk as state.
- **Zombification, lightning:** covered species have no such conversions in vanilla; no behavior specified.

### Multiplayer

Grade and perk are entity state, visible and beneficial to every player equally. Breeding attribution does not matter; there is no per-player component.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableGenetics` | bool | `true` | |
| `enableGenericDropMirror` | bool | `true` | mirror fallback for species without a product row |
| `hayRadiusBlocks` | int | `8` | 2–16 |
| `crowdingThreshold` | int | `12` | 4–64 |
| `crowdingRadiusBlocks` | int | `8` | 4–16 |
| `gradeUpgradeChance` | double | `0.5` | 0.0–1.0 |
| `gradeDowngradeChance` | double | `0.5` | 0.0–1.0 |

`enableGenetics = false` freezes grades (no inheritance rolls, no bonus yields, no treat effect); existing attachment data is retained untouched.

### Implementation Notes

- Attachment `GeneticsData { int grade; Perk perk; boolean primeNextOffspring; long lastTroughFeedTime; }` on the entity, persistent. Absent ⇒ ordinary/no perk.
- Inheritance hook: mixin at `Animal#spawnChildFromBreeding` (after the child exists, before `finalizeSpawnChildFromBreeding` completes), computing grade via a pure helper `Genetics.resolveGrade(baseA, baseB, wellFed, crowded, random)` and the perk via `Genetics.resolvePerk(perkA, perkB, wellFed, random)` — both unit-testable with a seeded random.
- Hardy and fleet are fixed-id attribute modifiers (`instinct:genetic_health` ADD_VALUE, `instinct:genetic_speed` MULTIPLY_BASE), applied once at birth and re-asserted idempotently on load.
- Fertile: scale the post-breed love cooldown at the `spawnChildFromBreeding` site (each parent's reset scaled by its own perk × grade).
- Placid: the exact-class vanilla `PanicGoal` on covered animals is swapped for a perk-aware subclass that stands down unless the animal is on fire or in lava — the same swap discipline as §4's tempt swap (a modded `PanicGoal` subclass is never touched; disabled or perkless ⇒ vanilla behavior exactly).
- Death drops: `ServerLivingEntityEvents.AFTER_DEATH` spawns the bonus `ItemEntity`s beside vanilla loot; the species→product table is a `SimpleSynchronousResourceReloadListener` over `instinct/products/*.json` (Animal Coverage), falling back to the drop mirror; cooked-in-kind mirrors whether the vanilla roll was cooked (entity on fire at death).
- Shear bonus: mixin at the sheep shear drop site; egg interval: scale `eggTime` when (re)rolled, guarded by grade.
- `InstinctAnimalBredCallback` (§Public API) fires after grade resolution with parents, child, and final grade.

---

## 4. Flocking & Herding

Lured animals follow like a herd, not a mosh pit.

### Problem

Vanilla food-luring makes every animal race to occupy the player's exact position — they shove the player, block movement, and jostle each other. Moving a herd any distance is slower and more frustrating than boating them one by one.

### Behavior

Applies to every animal in the **livestock set** (Animal Coverage) whose tempt items include the held food. While an animal's tempt target is a player:

1. **Speed:** the tempt movement speed is the vanilla tempt speed × `flockSpeedMultiplier` (default 1.15).
2. **Spacing:** each tempted animal steers to keep at least `flockSpacingBlocks` (default 2.0) from every other currently-tempted animal — a gentle separation adjustment blended into its path, not a hard stop.
3. **Standoff:** a tempted animal stops approaching at 2.5 blocks from the player and holds position (facing the player) while the food stays held; it resumes when the player moves away.

Baby animals keep their vanilla follow-parent behavior underneath; leads override temptation entirely (vanilla rule, untouched).

**Drive assist.** While `enableHerding` is true: when a player is tempting at least 3 covered animals (a **drive**), up to `herdingMaxPets` (default 2) of that player's pets work the drive automatically. A working pet is pets-set, tamed by the driving player, following (not sitting), not downed, not in combat, not fleeing a creeper (§1 wins), and within 12 blocks of the player.

1. A **straggler** is a covered animal whose tempt target is the player but which is more than 8 blocks from the player.
2. A working pet claims the nearest unclaimed straggler (a transient claim with a 200-tick expiry — same shape as the trough's pathing claim, §5), paths to a point 2 blocks behind it on the straggler→player axis (recomputed at most every 10 ticks), and holds there.
3. A **pressed** animal moves toward the player at 1.2× its normal speed while its presser is within 3 blocks of that behind-point; the press and the claim end when the animal is within 5 blocks of the player, the claim expires, or the drive ends.
4. With no stragglers, working pets resume normal follow.

The pet presses; it never attacks, and being pressed is not damage — pressed animals never panic from it. Any pets-set species herds: herding is follow-work, not combat, so a cat drives cows as well as a wolf does. §6's round-up order reuses this exact press mechanic with a whistle trigger.

### Edge cases

- **Multiple luring players:** vanilla target selection (nearest qualifying player) is unchanged; the flock splits by target, and spacing applies within each flock. Drive assist follows the split — each player's own pets work each player's own drive.
- **Doorways and chokepoints:** spacing is a steering preference, not a collision rule — animals still funnel through 1-wide gaps single-file.
- **The trough (§5)** never tempts; flocking applies only to player-held food.
- **Straggler behind a wall:** if the working pet cannot path to the behind-point within its claim window, the claim expires and the straggler becomes claimable again (or is simply left — the drive never stalls waiting on one animal).
- **`enableFlocking = false`, `enableHerding = true`:** drive assist requires a tempt flock, so it never activates; the whistle round-up (§6) still works.

### Multiplayer

Server-side movement adjustment; every player experiences the same herd shape. Drive assist uses only the driving player's own pets; two players driving herds side by side each get their own pets' help and never each other's.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableFlocking` | bool | `true` | |
| `flockSpeedMultiplier` | double | `1.15` | 1.0–1.5 |
| `flockSpacingBlocks` | double | `2.0` | 1.0–4.0 |
| `enableHerding` | bool | `true` | drive assist + round-up (§6) |
| `herdingMaxPets` | int | `2` | 1–4 |

### Implementation Notes

- On entity load, an exact-class vanilla `TemptGoal` on covered animals is replaced with a `FlockingTemptGoal` subclass (same priority, same tempt items via the entity's own food predicate) adding the speed factor, the separation vector (computed against other flock members within 2× spacing, capped contribution), the 2.5-block standoff, and a widened tempt range of 16 blocks (vanilla is 10) so a lagging flock member stays tempted while a drive moves — so while flocking is on, held food gathers matching animals from 16 blocks rather than vanilla's 10. A modded `TemptGoal` *subclass* is never swapped — the animal keeps its custom behavior untouched (Animal Coverage → graceful degradation).
- Goal replacement is the only pattern where Instinct swaps rather than adds — always a vanilla goal for a *subclass* of itself (tempt here; panic for §3's placid perk), preserving all vanilla semantics when the feature toggles off mid-world (disabled ⇒ the subclass behaves exactly as the vanilla goal).
- Drive assist is two injected goals plus math: a `HerdWorkGoal` on pets (idle unless its owner is driving; computes the behind-point, holds the claim) and a press response on the claimed straggler via a transient high-priority move impulse (no persistent state — the claim map lives server-side, cleared on `SERVER_STOPPED`). The behind-point and straggler selection are pure helpers (`Herding.pressPoint(straggler, player)`, `Herding.stragglersOf(flock, player)`) for unit testing.
- **Feel over choreography:** gametests assert outcomes (the herd converges within the time budget), never appearance; the looks-right bar — arcs, pacing, no jitter — lives in manual testing. If pet positioning proves janky, the pressure valve is staging: the pet holds a fixed rear point and only the straggler's hustle carries the read. Drive assist is the core promise; round-up (§6) is the detachable extension.

---

## 5. Feeding Trough

Passive, low-lag farm automation in one wooden block.

### Problem

Vanilla breeding requires a player to click every animal every cooldown. Automation is impossible without contraptions, so "automated" farms become the crushers §3 penalizes.

### Behavior

**The block.** `instinct:feeding_trough` — a wooden trough, hopper-shaped profile without the spout, hay-lined when filled. Crafted (shaped, 1 result):

```
P _ P
P F P
```

`P` = any item in `#minecraft:planks`, `F` = any item in `#c:wooden_fences`. Mineable with an axe (wood tool tier, no tool required to drop). Not waterloggable. Flammable like planks.

**Storage.** One internal stack of a single accepted item type, capacity 64. Accepted: any item in `#instinct:trough_food` — shipped contents are wheat, carrot, potato, beetroot, and every vanilla seed (wheat/beetroot/melon/pumpkin/torchflower/pitcher pod); animal mods and packs extend the tag with their own feed (Animal Coverage → item tags). A **hay bale** converts on insert: 1 bale → 9 wheat (accepted only if the stored type is wheat or empty, and at least 9 capacity remains).

- **Insert:** right-click with an accepted item moves the whole held stack in (up to capacity); mismatched type is refused (no swing).
- **Withdraw:** right-click with an empty hand returns the entire stored stack to the player.
- **Hoppers** may insert accepted items from above; hoppers **cannot extract** (a feeder, not storage).
- **Comparator** reads fill level 0–15, proportional.
- **Breaking** drops the stored stack and the trough.

**Feeding loop.** Every `troughFeedIntervalTicks` (default 40) while non-empty, the trough selects at most one eligible animal within `troughRadiusBlocks` (default 10, spherical from the block center) and feeds it:

1. **Eligible adult:** a livestock-set animal (Animal Coverage), adult, not in love, breeding cooldown expired, whose own breeding foods include the stored item — and the count of covered animals in radius is at most `troughPopulationCap` (default 16; 0 = uncapped). The animal paths to the trough; on arrival (≤ 1.5 blocks) it consumes 1 item, plays the eat sound with food particles, enters love mode exactly as if a player had fed it, and is marked trough-fed (`lastTroughFeedTime = now`, feeding §3's well-fed test).
2. **Eligible baby:** if no eligible adult was found, a livestock-set baby whose foods match may eat instead — 1 item, growth accelerated by 10% of remaining time, at most once per 600 ticks per baby.

The population cap gates only step 1 (breeding); babies may always eat. When the cap is met or exceeded, the trough still holds food but initiates no new love states — the passive farm self-limits instead of overflowing.

**Idle behavior.** Animals never seek a trough when not eligible; the trough emits no redstone, no sounds, no particles while idle.

### Edge cases

- **Overlapping troughs:** each trough runs its own loop; an animal already pathing to one trough is ineligible for another until it finishes or gives up (20-second pathing timeout).
- **Unloaded chunks:** the trough only acts while its chunk ticks; nothing accrues or queues.
- **Both parents trough-fed:** the love states pair naturally via vanilla breeding AI; §3 inheritance sees both as well-fed.
- **Stored type nobody eats** (e.g. melon seeds with only cows in range): the loop finds no eligible animal and idles — no waste, no error.
- **Creative:** interactions identical; the block has no creative-only behavior.

### Multiplayer

The trough is world infrastructure — any player's animals in radius benefit; the stored stack is one shared inventory with last-click-wins semantics (standard block-entity behavior).

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableTrough` | bool | `true` | |
| `troughRadiusBlocks` | int | `10` | 4–24 |
| `troughFeedIntervalTicks` | int | `40` | 10–200 |
| `troughPopulationCap` | int | `16` | 0–64 (0 = uncapped) |

`enableTrough = false` disables the feeding loop only; the block remains placeable inert storage (insert/withdraw/comparator still work).

### Implementation Notes

- `FeedingTroughBlockEntity` with a server ticker gated on the interval; the entity scan uses the level's entity lookup by AABB, then filters. One trough scan per interval — the block does the finding, animals get no injected goal (keeps per-entity cost zero for farms with no trough).
- The "pathing to trough" claim is a transient (non-persistent) entity flag holding the trough position + expiry tick; consumed on arrival or timeout.
- Love state via `Animal#setInLove(null)` (no player attribution — the "fed by" player is absent, exactly like a dispenser can't do; advancement `Best in Show` therefore triggers on grade, not on feeding).
- Trough-fed marker writes `GeneticsData.lastTroughFeedTime` (§3 attachment) — one attachment, two features.

---

## 6. Command Whistle

One item that moves the whole pack.

### Problem

Commanding N pets means N crouch-less right-clicks, each toggling one animal, some of which stand back up before you reach the fifteenth. Pack play is unmanageable past three or four pets.

### Behavior

**The item.** `instinct:command_whistle` — stack size 1, no durability (never breaks). Crafted (shaped, 1 result):

```
_ C _
C B C
```

`C` = copper ingot, `B` = bone.

**Left-click (swing), any target or air:** toggles every **owned, tamed, non-downed** pets-set animal (Animal Coverage) within `whistleRadiusBlocks` (default 20) of the player:
- If at least one such pet is currently standing (following) → **Stay**: all of them sit. Feedback: `✦ <n> pets will stay.` + the falling stay cue.
- Otherwise (all sitting) → **Follow**: all of them stand. Feedback: `✦ <n> pets will follow.` + the rising follow cue.
- No pets in radius: `✦ No pets in range.`, no cue.

**Right-click (use):** raycasts from the player's eyes up to `whistleTargetRangeBlocks` (default 24) for a living entity. Two orders, resolved by what the ray hits:

**Attack.** On a valid attack target — any living entity that is not the user, not a tamed animal owned by the user, not a covered livestock-set animal (those order a round-up, below), not downed, not a spectator/creative player, and (if a player) only when PvP is enabled:
- Every owned, tamed, non-downed, **combat-capable** pet (a pet with an attack-damage attribute — wolves and most modded fighters; cats and parrots are not combat-capable) within `whistleRadiusBlocks` stands (an attack order overrides Stay) and sets its attack target to the raycast entity. Feedback: `✦ <n> pets attack.` + the sharp attack cue.
- No valid target on the ray: `✦ No target in sight.`, no cue.

**Round-up.** When the raycast entity is a covered livestock-set animal and `enableHerding` is true (§4), the whistle orders a round-up — covered livestock are never whistle attack targets:
- The **drive group** is the target animal plus every covered animal of the same species within `roundUpGroupRadiusBlocks` (default 8) of it. Leashed and in-vehicle animals are excluded.
- Every owned, tamed, non-downed pet within `whistleRadiusBlocks` joins the order, at most `herdingMaxPets` working at once; they press the group toward the player using §4's press mechanic, with the player's live position as the destination.
- Each group animal is done when within 5 blocks of the player; the order ends when every animal is done or after 600 ticks, and the pets return to their prior follow state. Whistling a new order (any kind) replaces a running round-up.
- Feedback: `✦ <n> pets round up the herd.` + the herd cue. Empty drive group (all excluded, or herding disabled): `✦ Nothing to round up.`, no cue.

**Cooldown:** `whistleCooldownTicks` (default 20) item cooldown after any whistle action (vanilla item-cooldown overlay on the slot).

### Interplay

- Pets commanded onto a creeper still keep the §1 creeper berth — they engage, and break off while a fuse burns. Working as intended.
- Downed pets (§7) neither respond nor count toward `<n>`.
- The whistle commands only the user's own pets; it never affects another player's animals, regardless of permissions.
- A round-up presses any player's covered livestock (livestock have no owner), but only toward the whistling player — the same neutrality as luring them with wheat.
- Pets on a round-up keep the §1 creeper berth and break off to flee; a pet that enters combat drops its herding claim (§4's eligibility re-applies).

### Edge cases

- **Mixed pack states** resolve by the standing-check rule above (any-standing → everyone sits) — one press always produces one coherent pack state.
- **Pets in vehicles/leashed:** they receive the sit/stand state change; movement follows vanilla rules for their restraint.
- **Target dies mid-flight:** vanilla target invalidation applies; pets disengage normally.
- `enableWhistle = false`: both clicks do nothing and show `✦ The whistle is silent here.` (crafting stays available; the item is inert, not removed).

### Multiplayer

Server-authoritative: the left-click gesture is reported by the client, but pet selection, state changes, and feedback all resolve on the server against the server's radius and ownership data.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableWhistle` | bool | `true` | |
| `whistleRadiusBlocks` | int | `20` | 8–48 |
| `whistleTargetRangeBlocks` | int | `24` | 8–64 |
| `whistleCooldownTicks` | int | `20` | 0–100 |
| `roundUpGroupRadiusBlocks` | int | `8` | 4–16 |

### Implementation Notes

- Right-click: `Item#use` + entity raycast (`ProjectileUtil`-style clip) server-side.
- Left-click on air produces no server event in vanilla; the client detects the swing while holding the whistle (client attack hook) and sends one custom payload (`instinct:whistle_toggle`, empty body). The server validates: main hand holds a whistle, not on cooldown, feature enabled — then executes. Left-click on a block or entity routes through `AttackBlockCallback`/`AttackEntityCallback` to the same handler (and cancels the attack, so the whistle never punches).
- Pet enumeration: server entity lookup by AABB, filtered on `TamableAnimal#isTame` + `isOwnedBy(player)` + not downed.
- Attack order: `pet.setOrderedToSit(false)` then `pet.setTarget(target)`; combat-capability = `getAttribute(ATTACK_DAMAGE) != null`.
- Round-up: builds the drive group by AABB + same-type filter, then hands the group and the ordering player to §4's press machinery (claims, behind-points, expiries) with a 600-tick order deadline. No new goals — the whistle is a second trigger on the same `HerdWorkGoal`.

---

## 7. Downed Pets & Revival

A pet's death becomes a rescue, not a funeral — at a price.

### Problem

One creeper, one skeleton volley, one mistake — a pet representing sixty days of veterancy dies permanently, and the rational response is to stop bringing pets anywhere. Permanent stakes with no mitigation teach players not to engage with the feature at all.

### Behavior

Applies to every tamed animal in the **pets set** (Animal Coverage).

**Going down.** When lethal damage would kill the pet, the death is cancelled and the pet enters the **downed** state instead:
- health is set to 1.0; the pet is invulnerable to all further damage (exceptions below),
- all AI stops; the pet lies in place (sitting pose, head low) and cannot be commanded, whistled, tempted, or teleported,
- hostile mobs treat it as no target (any mob currently targeting it retargets),
- a species whine plays every 100 ticks (the entity's own hurt sound — wolf whine, cat hurt, a modded species' own voice — at volume 0.5) with one `smoke` particle,
- the owner — online, any distance, same dimension or not — gets one chat line (not action bar; this one must not be missed): `✦ <name> is down.`

The downed state is indefinite: it persists across saves, chunk unloads, dimension border, and owner logout. Downed pets never despawn.

**Beyond saving.** The death is **not** cancelled — the pet dies exactly as vanilla — when the lethal damage is fire or lava damage, void damage, or a kill command. VISION.md names this edge: fire, lava, and the void are beyond saving (and §1 exists to keep pets out of them).

**Revival.** Any player (not only the owner) uses an item in `#instinct:revive_items` on the downed pet. The tag ships with:
- the **golden apple** (regular and enchanted), and
- the **vet kit** — `instinct:vet_kit`, stack size 16, crafted shapeless: 1 paper + 1 string + 1 honey bottle → 1 vet kit (bottle returned).

Siblings and packs extend the tag to add their own remedies (Animal Coverage → item tags).

The item is consumed; the pet revives: health set to `reviveHealthFraction` (default 0.5) × max health, Regeneration II for 10 seconds, 60 ticks of post-revive invulnerability, stands in Stay (sitting) state, revival cue + 5 `heart` particles. If `downedRankPenalty` is true and the pet has a veterancy rank, it loses exactly one rank: `accruedDays` is set to the threshold of the new rank (rank 1 → its threshold day count; rank 1 dropping to 0 → 0 days) — and with the rank go the learned behaviors above it (§2): a demoted Veteran forgets your swing, a demoted Venerable stops mentoring. Feedback to the reviving player: `✦ <name> is back on their feet.`

**Wrong item on a downed pet:** nothing happens (no swing, no consume). Regular interactions (sit toggle, dye, food) are all suppressed while downed.

### Edge cases

- **Explosion that downs the pet:** the triggering damage resolves first (pet goes down); subsequent blast/fire ticks hit invulnerability. A pet downed *in* fire that keeps burning: the next fire tick is lethal-class fire damage against a downed pet — downed pets are invulnerable to it like everything else; the "beyond saving" test applies only to the *lethal blow that would have killed a healthy pet*, not to damage after downing. (A pet downed at the lava edge is safe; a pet swimming in lava never goes down at all.)
- **`/kill` and void:** bypass downed entirely (die normally), including while already downed — a downed pet that falls into the void dies.
- **Owner never returns:** the pet stays down forever; that is the design (no timeout, no auto-death).
- **`enableDownedState = false`:** vanilla deaths, including for already-downed pets on next damage — existing downed pets remain downed until revived or killed; no new downs occur.
- **Untamed animals** never enter downed; taming state is checked at the lethal blow.

### Multiplayer

Per-pet server state. Any player can revive (co-op rescue is a feature); only the owner is notified of the down. The downed pose and particles are visible to everyone.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableDownedState` | bool | `true` | |
| `reviveHealthFraction` | double | `0.5` | 0.1–1.0 |
| `downedRankPenalty` | bool | `true` | |

### Implementation Notes

- Death interception: `ServerLivingEntityEvents.ALLOW_DEATH` returning false for qualifying pets, then applying the downed attachment + synced entity flag (tracked data) for client pose rendering.
- Downed attachment `DownedData { long downedAtGameTime; }`; the synced flag drives pose (`setInSittingPose`-equivalent + suppressed AI via goal gate), whine cadence, and interaction suppression. The pose is best-effort for modded species without a sitting animation — AI suppression, the whine, and the particle carry the downed read regardless.
- "Beyond saving" test: `source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)`.
- Target immunity: a `Mob#canAttack`-site guard (or targeting-conditions predicate injection) plus a sweep clearing `getTarget() == downed` on down.
- Revival: `UseEntityCallback` intercepting item-on-downed before vanilla interactions.
- `InstinctPetDownedCallback` / `InstinctPetRevivedCallback` fire at the respective transitions (§Public API).

---

## Configuration

All features are independently toggleable via a ModMenu / Cloth Config screen and a JSON config file (`config/instinct.json`), created with defaults on first launch. `configVersion` is **1**. Unknown/missing fields are filled with defaults and clamped to their stated ranges after load; a corrupted file falls back to defaults and is left untouched on disk.

### Server Config

| Key | Type | Default | Description |
|---|---|---|---|
| `autoDetectAnimals` | bool | true | Heuristic membership for modded animals (Animal Coverage) |
| `petsInclude` | list | [] | Entity types forced into the pets set |
| `petsExclude` | list | [] | Entity types forced out of the pets set |
| `livestockInclude` | list | [] | Entity types forced into the livestock set |
| `livestockExclude` | list | [] | Entity types forced out of the livestock set |
| `enableSelfPreservation` | bool | true | §1 master toggle |
| `creeperBerthBlocks` | int | 4 | Distance pets keep from ignited creepers (2–8) |
| `teleportSuppressFallDistance` | double | 3.0 | Owner fall distance that suppresses pet teleport (0.5–10.0) |
| `enableVeterancy` | bool | true | §2 master toggle |
| `veterancyThresholdDays` | int list | [10, 30, 60] | Days for ranks 1–3, ascending (each 1–1000) |
| `healthPerRank` | double | 2.0 | Max-health bonus per rank (0.0–20.0) |
| `damagePerRank` | double | 1.0 | Attack-damage bonus per rank (0.0–10.0) |
| `enableRankBehaviors` | bool | true | §2 rank behaviors: warning, sweep-safety, mentor |
| `warningRadiusBlocks` | int | 16 | Radius a Seasoned pet watches for threats (8–24) |
| `mentorRadiusBlocks` | int | 16 | Venerable mentor aura radius (8–32) |
| `mentorRateBonus` | double | 0.25 | Accrual bonus near a mentor (0.0–1.0) |
| `enableGenetics` | bool | true | §3 master toggle |
| `enableGenericDropMirror` | bool | true | Mirror-fallback drop bonus for species without a product row |
| `hayRadiusBlocks` | int | 8 | Hay-bale scan radius for the well-fed test (2–16) |
| `crowdingThreshold` | int | 12 | Animal count that marks a breeding as crowded (4–64) |
| `crowdingRadiusBlocks` | int | 8 | Radius for the crowding count (4–16) |
| `gradeUpgradeChance` | double | 0.5 | Chance a well-fed breeding gains a grade (0.0–1.0) |
| `gradeDowngradeChance` | double | 0.5 | Chance a crowded breeding loses a grade (0.0–1.0) |
| `enableFlocking` | bool | true | §4 master toggle |
| `flockSpeedMultiplier` | double | 1.15 | Tempt-speed multiplier while flocking (1.0–1.5) |
| `flockSpacingBlocks` | double | 2.0 | Preferred spacing between flock members (1.0–4.0) |
| `enableHerding` | bool | true | Drive assist (§4) + whistle round-up (§6) |
| `herdingMaxPets` | int | 2 | Pets working a drive or round-up at once (1–4) |
| `enableTrough` | bool | true | §5 feeding loop toggle (block stays as inert storage) |
| `troughRadiusBlocks` | int | 10 | Trough feeding radius (4–24) |
| `troughFeedIntervalTicks` | int | 40 | Ticks between trough feed attempts (10–200) |
| `troughPopulationCap` | int | 16 | Max animals in radius before breeding pauses (0–64, 0 = uncapped) |
| `enableWhistle` | bool | true | §6 master toggle (item stays craftable, inert) |
| `whistleRadiusBlocks` | int | 20 | Pet command radius (8–48) |
| `whistleTargetRangeBlocks` | int | 24 | Target raycast range for attack and round-up (8–64) |
| `whistleCooldownTicks` | int | 20 | Item cooldown after a whistle action (0–100) |
| `roundUpGroupRadiusBlocks` | int | 8 | Round-up gathers same-species animals within this radius (4–16) |
| `enableDownedState` | bool | true | §7 master toggle |
| `reviveHealthFraction` | double | 0.5 | Health fraction restored on revival (0.1–1.0) |
| `downedRankPenalty` | bool | true | Revival costs one veterancy rank |
| `enableInspection` | bool | true | Crouch-look inspection lines (§2/§3) |

### Client Config

None. Instinct has no client-side options in v1 — no HUD, no client rendering toggles; inspection lines are server-driven.

---

## Commands

Root command `/instinct`. All output localized.

| Command | Permission | Behavior |
|---|---|---|
| `/instinct info` | 0 | Reports on the animal the sender is looking at (≤ 8 blocks): species, set membership and which rule granted it (config / tag / heuristic — the modded-animal debugging surface), grade and perk (§3), veterancy days/rank (§2), downed status (§7), trough-fed recency, pedigree-treat flag, product-row source (data / mirror). Errors with a localized line if no covered animal is on the crosshair. |
| `/instinct set grade <ordinary\|sturdy\|prime>` | 2 | Sets the bloodline grade of the looked-at covered animal. |
| `/instinct set veterancy <days>` | 2 | Sets accrued days (0–100000) on the looked-at pet; rank and attributes re-derive immediately. |
| `/instinct reload` | 2 | Reloads `config/instinct.json`; reports the count of changed keys. |

---

## Public API

Package **`com.rfizzle.instinct.api`** — the only stable package, per concord [`API-STANDARD.md`](../../concord/API-STANDARD.md): read-only accessors, provider/callback registration as the sole mutation pattern, error isolation on every provider, server-authoritative, `@Stable`-marked (local annotation, no shared jar).

### Accessors

| Method | Returns |
|---|---|
| `InstinctAPI.isPet(EntityType<?>)` | pets-set membership after full resolution (Animal Coverage) |
| `InstinctAPI.isLivestock(EntityType<?>)` | livestock-set membership after full resolution |
| `InstinctAPI.getGrade(Animal)` | `Grade` enum (`ORDINARY`, `STURDY`, `PRIME`); `ORDINARY` for uncovered/absent |
| `InstinctAPI.getPerk(Animal)` | `Perk` enum (`NONE`, `HARDY`, `FLEET`, `FERTILE`, `PLACID`); `NONE` for uncovered/absent |
| `InstinctAPI.getVeterancyDays(TamableAnimal)` | accrued days (double), `0.0` if untracked |
| `InstinctAPI.getVeterancyRank(TamableAnimal)` | rank 0–3 |
| `InstinctAPI.isDowned(LivingEntity)` | downed state (§7) |
| `InstinctAPI.isTroughFed(Animal)` | trough-fed within the last 24000 ticks (§5) |

### Providers

| Hook | Contract |
|---|---|
| `InstinctAPI.setVeterancyRateProvider(ToDoubleFunction<TamableAnimal>)` | Multiplies live veterancy accrual (§2). Non-finite or ≤ 0 returns are clamped to 1.0; a throwing provider is caught, logged once, and treated as 1.0. Last registration wins. Composes multiplicatively with §2's mentor bonus. |

### Events (Fabric `Event` objects, fired server-side)

| Event | Fires |
|---|---|
| `InstinctAnimalBredCallback(parentA, parentB, child, grade)` | after grade resolution at breeding (§3) |
| `InstinctPetDownedCallback(pet, source)` | on entering downed (§7) |
| `InstinctPetRevivedCallback(pet, reviver, item)` | on revival (§7) |

No HUD accessors — Instinct has no HUD slot (`design/DESIGN.md` §2); the accessor convention applies to HUD-bearing mods only.

---

## Compatibility

### Required

- Fabric Loader ≥ 0.16.10
- Fabric API (data attachments, entity events, networking)
- Minecraft 1.21.1

### Optional Integrations

All sibling integrations are `modCompileOnly` + `FabricLoader.isModLoaded` guarded, in `compat/<modid>/` packages that fail gracefully. With no siblings installed, every feature above is complete.

- **ModMenu + Cloth Config** — config screen.
- **Jade / WTHIT** — on covered animals: grade, perk, veterancy days + rank, downed status; on the feeding trough: stored item, count, population count vs. cap.
- **Tribulation** (consumer) — Instinct registers its own veterancy-rate logic using `TribulationAPI.getEffectiveLevel`/`getTier`: live accrual counts **double** while the pet's local difficulty tier is 3 or higher. Guarded; absent Tribulation, rate is 1.0.
- **Prosperity** (consumer) — a conditional loot injection adds the **vet kit** (uncommon) and **pedigree treat** (rare) to chests at Prosperity's higher distance tiers, using Prosperity's injection datapack schema with a mod-presence condition. Both items remain craftable without it.
- **Mercantile** (provider) — Instinct exposes item ids and the grade API; Mercantile's conditional trade packs (its repo, its jar) may sell or buy against them. Nothing ships in Instinct's jar.
- **Meridian** (none required) — grade bonus drops are added after the vanilla loot roll, so Meridian's harvest/combat enchantment effects and Instinct's bonuses compose additively with no compat code on either side.
- **Distillation** (provider) — the revival path is open by convention: Distillation adds its brewed remedy to `#instinct:revive_items` (one tag entry in its jar) and it revives at the same `reviveHealthFraction`. Nothing ships in Instinct's jar, no API call needed.
- **Respite** (none required) — veterancy accrual and breeding cooldowns run on world game time, so Respite's accelerated nights advance them naturally with no compat code on either side.
- **Cultivation** (provider) — the trough is open by convention: Cultivation adds its farm produce to `#instinct:trough_food` (tag entries in its jar) and herds feed on it like any vanilla crop. Boundary contract: Instinct never changes what grows or what food does when eaten; Cultivation never changes what animals do or drop. Nothing ships in Instinct's jar.

### Mod Compatibility

- **Sodium / Iris / EBE** — no rendering mixins, no block-entity renderer replacement (the trough uses a static baked model); full compatibility expected.

### Modded animals

Third-party animals are supported by convention, not per-mod code — the full mechanism is Animal Coverage (membership heuristic + `#instinct:*` tags + config overrides + product data + mirror fallback). A modded animal is covered automatically when it extends the vanilla tamable/breedable base behaviors, which is true of most animal mods.

- **Critters and Companions** (the reference case) — its tameable species (otters, ferrets, red pandas) read as pets via the heuristic: hazard pathing, veterancy, whistle commands, and downed/revival attach generically. Its breedable species read as livestock: grades, birth perks, crowding/well-fed inheritance, and trough feeding work out of the box (the trough serves each animal's own breeding foods once that feed is in `#instinct:trough_food`), with death-drop bonuses via the mirror fallback until a product row exists.
- An animal mod deepens support with **data only, zero code**: a tag entry making membership explicit (`#instinct:pets` / `#instinct:livestock`), a `#instinct:trough_food` entry for its feed items, and one product-row JSON per species. Curated rows for popular animal mods may ship in Instinct's own data — inert when the mod is absent (unknown ids skip at debug).
- Degradation is always graceful (Animal Coverage): a custom follow goal keeps its own teleport rules, a custom tempt goal is never swapped, a species without a sitting pose still reads as downed via AI stop + whine + particles. Instinct never alters a modded behavior it does not recognize.

---

## Sound Design

Custom cues are synthesized via the `/sfx` pipeline (concord `design/DESIGN-SYSTEM.md` §9), mono Ogg, each with a subtitle. Everything organic stays vanilla.

| Feature | Event | Sound | Subtitle |
|---|---|---|---|
| Whistle — Follow (§6) | left-click, pack stands | custom `instinct:whistle_follow` — rising two-note trill | "Whistle rises" |
| Whistle — Stay (§6) | left-click, pack sits | custom `instinct:whistle_stay` — falling two-note trill | "Whistle falls" |
| Whistle — Attack (§6) | right-click on target | custom `instinct:whistle_attack` — three sharp pips | "Whistle snaps" |
| Whistle — Round-up (§6) | right-click on livestock | custom `instinct:whistle_herd` — two quick pips | "Whistle herds" |
| Warning (§2) | Seasoned pet warns | the pet's own vanilla aggression/hurt sound, volume 1.0 | — |
| Veterancy rank-up (§2) | rank gained, pet loaded | custom `instinct:rank_up` — warm two-chime | "Pet grows stronger" |
| Revival (§7) | downed pet revived | custom `instinct:revive` — soft rising shimmer | "Pet revives" |
| Trough insert (§5) | items added | vanilla `block.composter.fill` | — |
| Trough feeding (§5) | animal eats | vanilla `entity.generic.eat` | — |
| Downed whine (§7) | every 100 ticks downed | the species' own vanilla hurt/whine sound (any species, modded included), volume 0.5 | — |
| Pedigree treat (§3) | fed to animal | vanilla `entity.generic.eat` + `happy_villager` particles | — |

---

## Localization

All user-facing text uses translation keys in `assets/instinct/lang/en_us.json`, namespaced by surface per concord DESIGN-SYSTEM §10. Notification lines carry the ✦ marker inside the localized value. Grade and rank names route through `Grade.translationKey()` / `Veterancy.rankKey(int)` — code never formats an enum for the player.

| Pattern | Example | Used for |
|---|---|---|
| `config.instinct.*` (+ `.tooltip`) | `config.instinct.enableVeterancy` | Cloth Config labels and descriptions |
| `command.instinct.*` | `command.instinct.info.grade` | `/instinct` feedback |
| `notification.instinct.*` | `notification.instinct.pets_stay` | ✦ action-bar lines (whistle, inspection, rank-up) and the downed chat line |
| `tooltip.instinct.*` | `tooltip.instinct.trough.stored` | Item tooltips + Jade/WTHIT lines |
| `advancements.instinct.*` | `advancements.instinct.old_friend.title` | Advancement titles/descriptions |
| `subtitles.instinct.*` | `subtitles.instinct.whistle_follow` | Custom sound subtitles |
| `block.instinct.feeding_trough` | — | Block name (vanilla-mandated) |
| `item.instinct.<id>` | `item.instinct.command_whistle` | Item names (vanilla-mandated) |
| `instinct.grade.*` / `instinct.rank.*` | `instinct.rank.venerable` | Grade/rank display names embedded across surfaces |

Parameterized messages use `%s`/`%d` — e.g. `"notification.instinct.inspect_pet": "✦ %s has seen %d days — %s."`.

---

## Advancements

Small set, husbandry tab rooted under the vanilla Husbandry tab's visual neighborhood (own tab `instinct:root`, icon: command whistle).

| Id | Title | Trigger |
|---|---|---|
| `instinct:root` | Instinct | craft a command whistle, vet kit, pedigree treat, or feeding trough |
| `instinct:old_friend` | Old Friend | a pet you own reaches rank 3 (Venerable) |
| `instinct:best_in_show` | Best in Show | an animal you bred is born prime |
| `instinct:back_from_the_brink` | Back from the Brink | revive a downed pet |
| `instinct:pack_leader` | Pack Leader | whistle-command 10+ pets with one press |

Custom criterion triggers: `instinct:pet_rank`, `instinct:bred_grade`, `instinct:pet_revived`, `instinct:whistle_pack`.

---

## HUD

Instinct has **no HUD element** and publishes no HUD accessors — the slot decision and reasoning live in [`DESIGN.md`](DESIGN.md) §2 per concord `HUD-STANDARD.md`. Every transient message in this spec is an action-bar line or (the downed notice only) a single chat line.

---

## Testing Strategy

### Unit Tests (JUnit + `fabric-loader-junit`, `src/test/`)

- Config round-trip: defaults, clamping to ranges, corrupted-file fallback, list parsing.
- Membership resolution: config > tag > heuristic precedence, exclude-beats-include at each layer, pet-never-heuristic-livestock, `autoDetectAnimals = false` reduces to tags + config.
- Product data: JSON parsing (optional fields, `special: wool_coat`), unknown entity/item ids skipped, duplicate-id override order.
- Mirror fallback: candidate selection (edible + `#instinct:mirror_products`), largest/second-largest ordering, no-candidate = no bonus.
- Veterancy math: `accrue()` with rate multipliers and gaps, `rankFor()` boundaries (exactly 10/30/60), threshold-list edits promoting/demoting, revival rank-penalty day arithmetic, mentor × provider composition (2.0 × 1.25 = 2.5), mentor non-stacking.
- Warning bookkeeping: per-owner threat dedupe (one warning per threat per 300 ticks), nearest-pet selection, `ducksSweep()` predicate (rank ≥ 2, owner-only).
- Genetics resolution: `resolveGrade()` with seeded random — all 9 parent-grade combinations × {well-fed, crowded, both, neither}; treat override; uncovered-partner = 0.
- Perk resolution: `resolvePerk()` with seeded random — shared-perk 80%, split 40/40/20, single-parent 50/50, perkless uniform; not-well-fed always uniform; grade-0 offspring always `NONE`; fertile cooldown arithmetic (−15% × grade).
- Herding math: `pressPoint()` geometry (2 blocks behind on the straggler→player axis), `stragglersOf()` selection at the 8-block threshold, claim expiry accounting.
- Drop-bonus table: per-species primary/secondary counts at each grade, cooked-in-kind mirroring, goat = no products.
- Trough storage: accepted-item predicate, hay→9-wheat conversion (capacity guard, type guard), hopper insert/no-extract, comparator levels.
- Whistle selection: ownership/tamed/downed filtering, standing-check toggle direction, combat-capability filter, PvP gate.
- Downed "beyond saving" damage-source classification.
- Attribute idempotency: re-derive twice, values equal once.

### Gametests (Fabric Gametest API, `src/gametest/`)

- Self-preservation: wolf paths around a lava strip to reach its owner; wolf escapes when spawned on a cactus-adjacent node; ignited creeper at 3 blocks → sitting wolf stands, moves ≥ 4 blocks, re-sits; owner on a falling platform → no teleport, owner lands → teleport resumes.
- Veterancy: set game time forward 10 days → rank 1, max health +2, healed; `/instinct set veterancy` re-derives attributes.
- Rank behaviors: zombie targeting the owner within 16 blocks → the Seasoned wolf's warning fires (dedupe state set) and does not re-fire inside 300 ticks; owner sweep-attacks through a rank-2 wolf → wolf undamaged, a rank-0 wolf beside it is hit; pup accruing beside a Venerable wolf gains days at 1.25× a control pup.
- Genetics: breed two cows beside a hay bale with `gradeUpgradeChance = 1.0` → sturdy calf; breed inside a 13-animal crush with `gradeDowngradeChance = 1.0` → downgrade; pedigree treat → prime calf, flag cleared; prime cow death drops +2 beef +1 leather.
- Perks: two fleet parents bred well-fed with a seeded roll → fleet calf at the 80% branch; the same pair not well-fed → uniform branch; placid cow shot by an arrow → no panic sprint (navigation stays idle); placid cow set on fire → flees; fertile prime parents → love cooldown 4200 ticks, non-fertile control 6000.
- Herding: 6 tempted cows with one straggler at 12 blocks + a following wolf → all cows within 5 blocks of the player inside the time budget, wolf never damages a cow; whistle right-click on a cow in a group of 3 → all 3 reach the player within 600 ticks, no cow acquires an attack target, feedback line correct; whistle right-click on a cow with `enableHerding = false` → `Nothing to round up.`, no attack.
- Trough: filled trough + 2 eligible cows → both enter love within the interval budget and a calf appears; population cap 2 with 3 cows → no new love states; hopper feeds trough; empty-hand withdraw returns the stack.
- Whistle: 3 wolves mixed sit states → one toggle payload sits all; second stands all; attack command sets all wolves' target to a zombie; downed wolf ignored.
- Downed: lethal arrow → wolf at 1.0 health, invulnerable, no mob targets it; golden apple → revived at 50% max, rank reduced by one; lava-swim lethal damage → actual death; `/kill` on a downed pet → death.
- Flocking: 6 tempted cows hold ≥ 2-block spacing after path settle; tempted speed ≈ 1.15× vanilla tempt.
- Coverage overrides: a cow in `livestockExclude` (or `#instinct:livestock_exclude` via a test datapack) breeds with no grade roll and drops no bonus; a fox (heuristic livestock) breeds with grade inheritance and mirror-fallback drops.

### Manual Testing

- Flocking feel at chokepoints and river crossings; standoff behavior at 2.5 blocks.
- Herding choreography — the looks-right bar lives here, not in gametests: pet arcs and pacing on a drive, no oscillation at the behind-point, round-up across broken terrain; warning audio register (growl/hiss/squawk reads as a warning, not random ambience).
- Downed pose, whine cadence, and particle rendering on all three species; revival visuals.
- Whistle cue audio character and subtitles; rank-up moment (particles + chime + line together).
- Jade/WTHIT lines on animals and trough; Cloth Config screen; Sodium/Iris smoke test.
- Tribulation compat: veterancy accrual doubling at tier 3+ (with Tribulation on the classpath); Prosperity injection appearing at far tiers.
- Modded-animal smoke test with Critters and Companions installed: tame an otter/ferret (pets: whistle response, downed/revival, veterancy inspection line), breed a covered modded species near hay (grade roll, mirror-fallback drops), trough feeding with its feed added to `#instinct:trough_food`.
