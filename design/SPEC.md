# Instinct — Feature Spec

Minecraft 1.21.1 Fabric mod. Husbandry overhaul.

**Architectural philosophy:** Augment, never replace. Instinct never registers replacement entity types, never swaps or subclasses vanilla animals, and never rewrites a vanilla AI brain — all behavior changes are *additional* goals, goal wrappers, and pathfinding penalties injected into vanilla mobs at load, and all persistent state (veterancy, bloodline grade, downed status, trough-fed recency) rides **persistent Fabric data attachments** on the vanilla entities (the same `AttachmentType` mechanism the rest of the Concord suite uses). Remove Instinct and every animal is a byte-compatible vanilla animal plus inert attachment data. New registrations are limited to one block (feeding trough), three items (command whistle, vet kit, pedigree treat), six sounds, and the block entity behind the trough. All gameplay decisions run server-side; the client receives only display state (downed pose flag, trough fill).

**Asset philosophy:** Vanilla animals keep vanilla looks, always — grades and ranks surface through inspection lines and tooltips, never retextures. Custom pixel art (glyph pipeline — `/glyph`, `mc-textures` skill, concord `design/DESIGN-SYSTEM.md` §8, `.glyph` sources beside masters) covers only what Instinct adds: trough block faces, the three item sprites, and the 16×16 Jade glyph. Particles are vanilla (`heart`, `smoke`, `happy_villager`). Sounds stay vanilla where the cue is organic (eating, whines, block fill, a pet's warning growl); the whistle's four commands and the two milestone moments (rank-up, revival) are custom synthesized cues via the `/sfx` pipeline (§9), each with a subtitle.

---

## Animal Coverage — the pets and livestock sets

Every feature below applies to one of two membership sets, resolved per entity type. The goal: a modded animal that behaves like a vanilla one gets Instinct's treatment automatically, with no code from its author — and every automatic decision can be overridden by that author, a pack maker, or a server owner.

**The three sets:**
- **Pets** — self-preservation (§1), veterancy (§2), herding work (§4/§6), the whistle (§6), downed & revival (§7). Each feature additionally requires the individual animal to be tamed.
- **Livestock** — genetics (§3), flocking & being herded (§4/§6), the trough (§5).
- **Mounts** — self-preservation (§1, riderless) and downed & revival (§7), and nothing else. The horse family carries vanilla bred-stat inheritance, so it stays out of genetics; but self-preservation and downed answer a survival need that inheritance does not, so the horse family gets exactly those two. Each feature additionally requires the individual animal to be tamed.

**Resolution order** (first match wins), per entity type and per set:

1. **Config** — `petsExclude` / `livestockExclude` / `mountsExclude`, then `petsInclude` / `livestockInclude` / `mountsInclude`. The server owner has the last word.
2. **Tags** — `#instinct:pets_exclude` / `#instinct:livestock_exclude` / `#instinct:mounts_exclude`, then `#instinct:pets` / `#instinct:livestock` / `#instinct:mounts`. Entity-type tags are ordinary data: a third-party animal mod ships a one-file tag entry in Instinct's namespace, a pack maker uses a datapack — no compile-time dependency, no API call, nothing to guard.
3. **Heuristic** (when `autoDetectAnimals` is true, the default) — a tamable animal type (`TamableAnimal`) is a **pet**; a horse-family type (`AbstractHorse`) is a **mount**; any other breedable animal type (`Animal`) is **livestock**. The three are mutually exclusive by class, so a pet is never heuristically a mount or livestock, and a mount is never heuristically livestock.

Instinct ships its vanilla membership as default tag contents, overridable like any tag: `#instinct:pets` = wolf, cat, parrot; `#instinct:livestock` = cow, sheep, pig, chicken, rabbit, goat; `#instinct:livestock_exclude` = the horse family (horse, donkey, mule, camel, llama, trader llama, skeleton horse, zombie horse) — vanilla horses already have their own bred-stat inheritance, and grafting grades onto it would double-dip; `#instinct:mounts` = the tamable horse family (horse, donkey, mule, camel, llama, trader llama). The undead horses (skeleton horse, zombie horse) are not husbandry animals — they are neither livestock nor mounts — but a pack may add them to `#instinct:mounts` like any tag entry.

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

Applies to every tamed animal in the **pets set** and every tamed animal in the **mounts set** (Animal Coverage). Three independent protections:

**1. Hazard-aware pathing.** The pet's navigation treats lava, fire, and cactus path nodes as impassable — it paths around them or, if no safe route exists, stays put rather than walking through. This affects navigation only; combat targeting, sitting, and all other AI are untouched. On a mount, the maluses apply the same way while riderless; a ridden mount's navigation is idle (its rider steers), so the maluses simply never come into play.

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

### Mounts

Protections 1 and 2 apply to tamed mounts, with three differences from pets. The berth fires only while the mount is **riderless** — a ridden mount answers to its rider, not its instinct — and a mount has no sit pose, so it simply resumes its prior behavior after clearing the berth rather than re-sitting. The berth is a goal-selector goal, so it attaches only to **goal-driven** mounts; a brain-driven mount (the camel, whose movement runs on a brain rather than the goal selector) gets hazard-aware pathing (protection 1, which prices path nodes regardless of AI architecture) but not the creeper berth — graceful degradation per Animal Coverage, never a broken goal fighting the brain. Protection 3 (teleport refusal) does not apply: a mount never follow-teleports to an owner, so there is nothing to suppress. A downed mount (§7) has no AI and is exempt.

### Owner friendly-fire

Scoped to the **pets set** only — livestock and mounts stay vulnerable to their keeper. While `enableOwnerFriendlyFireProtection` (default true), an owner's own damage never lands on their own pet, whatever the source: melee and the sweep arc, arrows, splash and lingering potions, an explosion the owner set off. Every one of these credits the owning player as the blow's causing entity, so a single rule covers them all — the pet takes no damage and is not knocked back. Another player's damage is untouched; this is about your own hand, not invulnerability. A pet that would have gone down (§7) to its owner's own blow instead simply shrugs it off, so an owner can no longer down or kill their own pet by their own hand while this is on.

The rank-2 "Knows your swing" sweep-dodge (§2) is the narrower veteran trick — the arc only, rank 2+ only — that governs a pet when this blanket protection is switched off.

### Shoulder riding

A pets-set animal perched on its owner's shoulder — vanilla parrots, and any modded shoulder-rider that resolves into the pets set — rides steady while `enableSteadyShoulders` (default true). Vanilla dislodges a shoulder parrot on almost any knock: a jump, a fall of half a block, any damage down to a cactus scratch, so no one carries one anywhere that matters. Here the bird stays put through the incidental ones — jumps, sprint-jumps, short falls, and minor damage.

It comes off in two deliberate ways: the owner performs the **dismount gesture**, or the owner takes a **serious hit** — a blow whose raw incoming damage is at least `steadyShoulderDismountDamage` (default 4.0), which the fall damage of a hard landing also clears. A scratch keeps the bird; a real blow puts it in the air. Vanilla's other dismount states are unchanged — swimming, flying, sleeping, and standing in powder snow still set the bird down — and how a parrot climbs onto the shoulder in the first place is untouched. A modded shoulder-rider outside the pets set keeps exact vanilla behavior.

The gesture is `shoulderDismountGesture` (default `DOUBLE_TAP_SNEAK`). Players sneak constantly — working a ledge, placing a block precisely, peeking over an edge — so the drop is keyed to a motion no one makes by accident: **two sneak taps within `shoulderDismountDoubleTapTicks`** (default 12). A tap counts on release, and only when the press was held no longer than that window, so a held crouch contributes nothing; the second tap's press is what drops the bird. `SNEAK` is the alternative — any sneak, tapped or held — for a server that wants the drop on the crouch itself.

Because the gesture is read from the sneak state the server already tracks, it is sampled once per tick: two taps that both begin and end inside a single tick are not distinguishable, which is what `shoulderDismountDoubleTapTicks` exists to widen.

### Multiplayer

Per-pet, evaluated against the pet's own owner only. Another player falling past your wolf changes nothing. Owner friendly-fire keys on the blow's causing player: your damage spares only *your* pets, and another player's damage on your pet lands as vanilla.

### Failure paths

If the pet is already standing in a hazard (spawned there, pushed there), hazard-aware pathing does not trap it: escaping a damaging node to a safe node is always permitted; only *entering* hazard nodes is blocked. If every path out crosses lava, the pet stays put and vanilla damage/death (or §7 Downed) proceeds.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableSelfPreservation` | bool | `true` | |
| `creeperBerthBlocks` | int | `4` | 2–8 |
| `teleportSuppressFallDistance` | double | `3.0` | 0.5–10.0 |
| `enableOwnerFriendlyFireProtection` | bool | `true` | |
| `enableSteadyShoulders` | bool | `true` | |
| `steadyShoulderDismountDamage` | double | `4.0` | 0.0–20.0 |
| `shoulderDismountGesture` | enum | `DOUBLE_TAP_SNEAK` | `DOUBLE_TAP_SNEAK`, `SNEAK` |
| `shoulderDismountDoubleTapTicks` | int | `12` | 2–40 |

### Implementation Notes

- Goal injection on `ServerEntityEvents.ENTITY_LOAD`: if the entity's type resolves into the pets set, set pathfinding maluses (`PathType.LAVA`, `PathType.DAMAGE_FIRE`, `PathType.DAMAGE_OTHER` → `-1.0`) and add a `CreeperBerthGoal` (priority 1, above sit) implemented as a targeted flee with a re-sit memory (was-sitting flag captured on trigger, restored on completion).
- Malus application is idempotent (setting the same malus twice is harmless), so re-loads are safe.
- Teleport suppression: a mixin gating the vanilla follow-owner goal's teleport step on the owner-state predicate above. The predicate lives in one helper (`SelfPreservation.ownerUnsafeToJoin(owner)`) so the goal mixin stays a two-line guard. Modded pets running the vanilla goal get this free; custom follow goals are left alone (Animal Coverage → graceful degradation).
- Escape-vs-enter asymmetry comes free with maluses: maluses affect node *cost evaluation* for nodes being entered; the current node is not re-evaluated. No extra code, but the gametest below pins it.
- Owner friendly-fire: a `ServerLivingEntityEvents.ALLOW_DAMAGE` listener returns `false` (cancelling the blow, before any mitigation) when `source.getEntity()` is a player who owns the pets-set victim. `getEntity()` resolves the causing player across melee, arrows, potions, and player-primed TNT alike, so one listener covers every source. Pure verdict in `FriendlyFire.blocks(protectionOn, ownedPetOfAttacker)`; the listener fails open (a broken check allows the blow, never leaves a pet unhittable). Independent of the §2 sweep filter, which stays as the off-switch fallback.
- Shoulder riding: vanilla funnels every dismount through `Player#removeEntitiesOnShoulder`, called from `aiStep()` (fall/water/flying/sleeping/powder-snow) and `hurt()` (any damage). A `PlayerMixin` `@WrapWithCondition` on each of those two call sites skips the removal — the `aiStep()` one only for the fall branch (water/flying/sleeping/powder-snow are re-read and left to vanilla), the `hurt()` one only below `steadyShoulderDismountDamage` (the raw amount captured via `@Local`). The deliberate drop is a `tick()`-TAIL inject calling the shadowed `removeEntitiesOnShoulder` server-side; the riptide spin-attack call site is untouched. Every path is gated on a shoulder tag resolving into the pets set (`AnimalCoverage.typeById` on the tag's stored id → `AnimalCoverage.isPet`), so a non-pet shoulder-rider keeps vanilla behavior. The id → type resolution is memoized, since the entity-type registry is fixed at bootstrap; only ids that resolve are stored, so a hand-edited tag naming an unknown type cannot grow the memo, and an unknown or malformed id reads as "not a pet" rather than falling back to the entity-type registry's `minecraft:pig` default. Pure verdicts (`suppressesFallDismount`, `dismountsOnHit`) live in `SteadyShoulders`; vanilla's built-in 20-tick post-mount grace already blocks a drop the instant a bird lands.
- Dismount gesture: the double-tap state machine is the pure `SneakTapTracker`, fed one sneak sample per tick from the same `tick()`-TAIL inject and reading the level's game time as its clock. One tracker rides each player as a `@Unique` field on `PlayerMixin` rather than a keyed map, because nothing outside that hook reads it — so a player logging out mid-gesture cannot leak an entry, and no disconnect or shutdown hook is owed. The tracker is seeded with the current sneak state on creation and stood down on every tick the shoulder is empty, so a crouch already underway is never read as a press and a gesture made with a bare shoulder cannot bank toward a later drop. Completing clears it, so a gesture finished inside vanilla's 20-tick grace — spotted here, eaten there — leaves nothing to fire once the grace lapses; the owner simply gestures again.

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

**Knows your swing (rank 2+).** The owner's sweeping-edge area damage skips their own rank-2+ pets. It is the arc only — a direct hit is outside its scope (the pet learned to duck the sweep, not to be immune to its owner) — and rank 2+ only. Other players' sweeps, and all other damage, are unaffected. When §1 owner friendly-fire protection is on (its default), every owned pet is already spared from all of its owner's damage at every rank, so this dodge is what still governs a veteran when a server turns that protection off.

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
| **Fertile** | fecundity: breeding cooldown, egg-lay interval, and wool-regrowth cadence each −15% × grade (prime fertile = −30% on each), composed on top of grade's own renewable scaling |
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

**Yield — renewables.** Grade shortens a living animal's renewable cadence, and the fertile perk shortens it further; the two compose multiplicatively, and the combined factor floors so a maxed config can never zero a cadence.
- Shearing a sheep yields +1 wool at sturdy, +2 at prime (on top of the vanilla 1–3) — a quantity bonus, unchanged.
- Chicken egg-lay interval is reduced 10% at sturdy, 20% at prime; a fertile hen's interval is reduced a further `fertileRenewableReduction` (default 0.15) per grade.
- Sheep wool regrowth: a sheep regrows shorn wool by eating grass, and grade (10% at sturdy, 20% at prime) plus the fertile perk (a further `fertileRenewableReduction` per grade) raise how often it seeks and eats grass, so a graded or fertile sheep re-wools sooner. There is no separate re-wool timer; the cadence is the grass-eating rate.

**Pedigree treat.** New item `instinct:pedigree_treat`, stack size 16. Crafted shapeless: 1 golden carrot + 1 hay bale + 1 honey bottle → 1 treat (bottle returned). Using it on an adult covered animal consumes the treat, plays the eat sound with `happy_villager` particles, and sets a persistent flag: that animal's **next** offspring is born prime (resolution rule 5). Feeding a second treat to the same animal before it breeds does nothing and is refused (hand swing, no consume, no message). The flag survives save/load and shows in `/instinct info`.

**Inspection.** Same crouch-look mechanism as §2, for covered animals within 8 blocks (any player, not just an owner — livestock have no owner): `✦ A sturdy cow — hardy.` / `✦ A prime sheep — placid.` (grade, then perk — the read a breeder culls by). Ordinary animals produce no line (silence is the baseline).

### Edge cases

- **Mixed pairs:** breeding always uses both parents' grades; a wild-caught ordinary partner drags the base grade down — deliberate: bloodlines take upkeep.
- **Conversions:** where vanilla copies entity data across a conversion (mooshroom sheared → cow), the grade and perk copy with it.
- **Uncovered partners:** if only one parent's type is in the livestock set (membership edited mid-world, or a cross-mod pairing where one side is excluded), the missing grade counts as 0.
- **Hay bale detection** is by block state in the scan radius; hay in item frames or inventories does not count.
- **Fertile scope:** fertile is the fecundity perk — it shortens the vanilla post-breed love cooldown (each parent's own), the chicken egg-lay interval, and the sheep wool-regrowth graze rate, each by its own grade × `fertileRenewableReduction`. It never touches baby growth, milk (no vanilla cadence axis), or the shear quantity bonus (that stays grade-only).
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
| `fertileRenewableReduction` | double | `0.15` | 0.0–0.5 (fertile's per-grade cut to egg-lay and wool-regrowth cadence; 0 = breeding only) |

`enableGenetics = false` freezes grades (no inheritance rolls, no bonus yields, no treat effect); existing attachment data is retained untouched.

### Implementation Notes

- Attachment `GeneticsData { int grade; Perk perk; boolean primeNextOffspring; long lastTroughFeedTime; }` on the entity, persistent. Absent ⇒ ordinary/no perk.
- Inheritance hook: mixin at `Animal#spawnChildFromBreeding` (after the child exists, before `finalizeSpawnChildFromBreeding` completes), computing grade via a pure helper `Genetics.resolveGrade(baseA, baseB, wellFed, crowded, random)` and the perk via `Genetics.resolvePerk(perkA, perkB, wellFed, random)` — both unit-testable with a seeded random.
- Hardy and fleet are fixed-id attribute modifiers (`instinct:genetic_health` ADD_VALUE, `instinct:genetic_speed` MULTIPLY_BASE), applied once at birth and re-asserted idempotently on load.
- Fertile: scale the post-breed love cooldown at the `spawnChildFromBreeding` site (each parent's reset scaled by its own perk × grade). The egg-lay and wool-regrowth cadences fold the same perk in through a shared pure helper `Genetics.renewableIntervalFactor(grade, perk, fertileRenewableReduction)`.
- Placid: the exact-class vanilla `PanicGoal` on covered animals is swapped for a perk-aware subclass that stands down unless the animal is on fire or in lava — the same swap discipline as §4's tempt swap (a modded `PanicGoal` subclass is never touched; disabled or perkless ⇒ vanilla behavior exactly).
- Death drops: `ServerLivingEntityEvents.AFTER_DEATH` spawns the bonus `ItemEntity`s beside vanilla loot; the species→product table is a `SimpleSynchronousResourceReloadListener` over `instinct/products/*.json` (Animal Coverage), falling back to the drop mirror; cooked-in-kind mirrors whether the vanilla roll was cooked (entity on fire at death).
- Shear bonus: mixin at the sheep shear drop site (grade-only quantity). Egg interval: scale `eggTime` when (re)rolled through the renewable factor. Wool regrowth: a `@ModifyArg` mixin on `EatBlockGoal#canUse`'s `nextInt` modulus scales the graze roll for covered sheep (the goal is shared, so the handler gates to sheep and floors the modulus at 1).
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

**Water crossings.** While `enableFlocking` is on, a flock crosses water together instead of scattering at the shoreline. For the span it is actively tempted, a flock member — and a pet working the drive behind it — has its water pathfinding malus zeroed, the same treatment vanilla already gives a pet following its owner across a river, so the herd commits to the direct line across instead of pacing the bank for a way around. The animals already float (their vanilla `FloatGoal`); left alone they simply would not *choose* the water. Movement in water is slower than on land at vanilla's own rate — no separate speed rule — and the 2-block spacing holds at the surface. Drowning stays vanilla: a flock led into open ocean is the drover's mistake, and drowning is not a panic trigger, so a crossing never scatters from the water itself.

**Boarding.** While `enablePetBoating` is on, a following pet takes the spare seat in its owner's boat. A vanilla boat seats two: the owner fills one seat and the single nearest following pet claims the other — at most one pet per boat — swimming to the boat and boarding on its own, then hopping back out the moment the owner leaves it. A pet on Stay, downed, or already riding never boards, and a full boat (a chest boat, or a second player aboard) has no seat to give — the pack simply swims the crossing behind the boat. Boarding rides on vanilla's own passenger handling (position, sync, and auto-eject on logout or death come free), so it adds no persistent state.

### Edge cases

- **Multiple luring players:** vanilla target selection (nearest qualifying player) is unchanged; the flock splits by target, and spacing applies within each flock. Drive assist follows the split — each player's own pets work each player's own drive.
- **Doorways and chokepoints:** spacing is a steering preference, not a collision rule — animals still funnel through 1-wide gaps single-file.
- **The trough (§5)** never tempts; flocking applies only to player-held food.
- **Straggler behind a wall:** if the working pet cannot path to the behind-point within its claim window, the claim expires and the straggler becomes claimable again (or is simply left — the drive never stalls waiting on one animal).
- **`enableFlocking = false`, `enableHerding = true`:** drive assist requires a tempt flock, so it never activates; the whistle round-up (§6) still works. Water crossing folds into `enableFlocking` as well — with flocking off, a flock (or a round-up) moves as vanilla and does not cross water.
- **No spare seat:** a chest boat seats one and a boat carrying a second player is full; no pet boards either, and the pack swims the crossing behind the boat instead.
- **Seat priority:** when several pets follow, only the nearest eligible one walks up to the seat; the vanilla two-seat cap arbitrates any tie, so a boat is never overfilled.

### Multiplayer

Server-side movement adjustment; every player experiences the same herd shape. Drive assist uses only the driving player's own pets; two players driving herds side by side each get their own pets' help and never each other's. Boarding is per-pet against its own owner's boat — a pet only ever takes a seat in the boat its owner is riding.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableFlocking` | bool | `true` | |
| `flockSpeedMultiplier` | double | `1.15` | 1.0–1.5 |
| `flockSpacingBlocks` | double | `2.0` | 1.0–4.0 |
| `enableHerding` | bool | `true` | drive assist + round-up (§6) |
| `herdingMaxPets` | int | `2` | 1–4 |
| `enablePetBoating` | bool | `true` | pets take a boat's spare seat |

### Implementation Notes

- On entity load, an exact-class vanilla `TemptGoal` on covered animals is replaced with a `FlockingTemptGoal` subclass (same priority, same tempt items via the entity's own food predicate) adding the speed factor, the separation vector (computed against other flock members within 2× spacing, capped contribution), the 2.5-block standoff, and a widened tempt range of 16 blocks (vanilla is 10) so a lagging flock member stays tempted while a drive moves — so while flocking is on, held food gathers matching animals from 16 blocks rather than vanilla's 10. A modded `TemptGoal` *subclass* is never swapped — the animal keeps its custom behavior untouched (Animal Coverage → graceful degradation).
- Goal replacement is the only pattern where Instinct swaps rather than adds — always a vanilla goal for a *subclass* of itself (tempt here; panic for §3's placid perk), preserving all vanilla semantics when the feature toggles off mid-world (disabled ⇒ the subclass behaves exactly as the vanilla goal).
- Drive assist is two injected goals plus math: a `HerdWorkGoal` on pets (idle unless its owner is driving; computes the behind-point, holds the claim) and a press response on the claimed straggler via a transient high-priority move impulse (no persistent state — the claim map lives server-side, cleared on `SERVER_STOPPED`). The behind-point and straggler selection are pure helpers (`Herding.pressPoint(straggler, player)`, `Herding.stragglersOf(flock, player)`) for unit testing.
- **Feel over choreography:** gametests assert outcomes (the herd converges within the time budget), never appearance; the looks-right bar — arcs, pacing, no jitter — lives in manual testing. If pet positioning proves janky, the pressure valve is staging: the pet holds a fixed rear point and only the straggler's hustle carries the read. Drive assist is the core promise; round-up (§6) is the detachable extension.
- Water crossing is a save-zero-restore of the `PathType.WATER` pathfinding malus around the active lifetime of `FlockingTemptGoal` and `HerdWorkGoal` (gated on `enableFlocking`, so a disabled flock behaves exactly as the vanilla tempt goal it replaced), the same trick vanilla `FollowOwnerGoal` uses — no new navigation class, mixin, or move-control impulse. Drowning needs no rule: `minecraft:drown` is not in the `panic_causes` tag, so a crossing never scatters from the water.
- Boarding is one injected goal, `BoardBoatGoal` on every tamed pet (inert unless its owner is boating), plus the pure `Boating.chooseBoarder`/`eligibleToBoard` helpers for unit testing. It boards with an *unforced* `startRiding`, so the vanilla two-seat cap — not mod bookkeeping — guarantees one pet per boat; there is no claim map. `CreeperBerthGoal` and `HerdWorkGoal` stand down while the pet is a passenger, so nothing fights `Boat.positionRider`.

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

**Left-click (swing), any target or air, without sneaking:** toggles every **owned, tamed, non-downed** pets-set animal (Animal Coverage) within `whistleRadiusBlocks` (default 20) of the player:
- If at least one such pet is currently standing (following) → **Stay**: all of them sit — but a pet homed to a kennel post walks there to settle instead (§9). Feedback: `✦ <n> pets will stay.` + the falling stay cue.
- Otherwise (all sitting) → **Follow**: all of them stand. Feedback: `✦ <n> pets will follow.` + the rising follow cue.
- No pets in radius: `✦ No pets in range.`, no cue.

**Right-click (use):** raycasts from the player's eyes up to `whistleTargetRangeBlocks` (default 24) for a living entity. Two orders, resolved by what the ray hits:

**Attack.** On a valid attack target — any living entity that is not the user, not a tamed animal owned by the user, not a covered livestock-set animal (those order a round-up, below), not downed, not a spectator/creative player, and (if a player) only when PvP is enabled:
- Every owned, tamed, non-downed pet with an **attack-damage attribute** (wolves and most modded fighters) within `whistleRadiusBlocks` stands (an attack order overrides Stay) and sets its attack target to the raycast entity. A pet without a melee goal to act on that target — a cat or parrot, which carries the attribute yet never melees — is set on the target harmlessly and simply doesn't pursue. Feedback: `✦ <n> pets attack.` + the sharp attack cue.
- No valid target on the ray: `✦ No target in sight.`, no cue.

**Round-up.** When the raycast entity is a covered livestock-set animal and `enableHerding` is true (§4), the whistle orders a round-up — covered livestock are never whistle attack targets:
- The **drive group** is the target animal plus every covered animal of the same species within `roundUpGroupRadiusBlocks` (default 8) of it. Leashed and in-vehicle animals are excluded.
- Every owned, tamed, non-downed pet within `whistleRadiusBlocks` joins the order, at most `herdingMaxPets` working at once; they press the group toward the player using §4's press mechanic, with the player's live position as the destination.
- Each group animal is done when within 5 blocks of the player; the order ends when every animal is done or after 600 ticks, and the pets return to their prior follow state. Whistling a new order (any kind) replaces a running round-up.
- Feedback: `✦ <n> pets round up the herd.` + the herd cue. Empty drive group (all excluded, or herding disabled): `✦ Nothing to round up.`, no cue.

**Guard (sneak + right-click).** Sneak and right-click to post the pack to a spot rather than order an attack. The post is the block the crosshair rests on within `whistleTargetRangeBlocks`, or the player's own feet when the ray hits nothing:
- Every owned, tamed, non-downed pet that **can fight** — one carrying a vanilla melee-attack goal (wolves and modded fighters; not cats or parrots, which have no such goal) — within `whistleRadiusBlocks` stands and takes a persistent guard order anchored at that spot. Feedback: `✦ <n> pets will hold here.` + the steady guard cue.
- A guarding pet patrols within `guardRadiusBlocks` (default 8) of its post and engages **hostile monsters only** that enter it — never players, never any animal, so a guard never turns on your livestock or another player's pets. It hands the fight to its vanilla melee once a target is set and returns to post when the threat is down. The order holds through chunk unload and server restart until countermanded.

**Send home (right-click a kennel post).** Right-click a placed kennel post — a block, not an entity, so this never collides with the attack/round-up raycast — to assign the pack home to it and send them there. The full behavior lives in §9.

**Locate (sneak + left-click).** Sneak and left-click — on air, a block, or an entity — to answer for every **owned, tamed** pet (downed included) beyond the whistle's `whistleRadiusBlocks` voice, wherever it stands. Not a command but a search aid: one dry chat line each, nearest first, capped at ten with an "…and N more." tail. It never moves or commands a pet.
- A pet in the player's own dimension reads its distance, eight-point compass bearing, and posture: `Rex — 240m northwest, sitting.` A pet in another dimension reads only that dimension (a bearing is meaningless across worlds): `Rex — in the Nether.`, with a downed one flagged: `Bolt — in the End, downed.`
- The census finds only pets in **loaded** chunks — a pet in an unloaded chunk is not in memory to be found. Empty census: `✦ No pets beyond earshot.`, no cue. It shares the whistle's item cooldown.

**Cooldown:** `whistleCooldownTicks` (default 20) item cooldown after any whistle action (vanilla item-cooldown overlay on the slot).

### Interplay

- Pets commanded onto a creeper still keep the §1 creeper berth — they engage, and break off while a fuse burns. Working as intended.
- Downed pets (§7) neither respond nor count toward `<n>`.
- The whistle commands only the user's own pets; it never affects another player's animals, regardless of permissions.
- A round-up presses any player's covered livestock (livestock have no owner), but only toward the whistling player — the same neutrality as luring them with wheat.
- Pets on a round-up keep the §1 creeper berth and break off to flee; a pet that enters combat drops its herding claim (§4's eligibility re-applies).
- A guard order rides a persistent per-pet attachment, so a stationed pet holds its post whether or not its owner is online — a dog left at the gate. Self-preservation still wins: a guarding pet breaks off for the §1 creeper berth and stands down while on fire or in lava.
- Any new whistle order — Stay/Follow, attack, round-up, or a fresh guard — replaces a running guard order on the pets it commands.

### Edge cases

- **Mixed pack states** resolve by the standing-check rule above (any-standing → everyone sits) — one press always produces one coherent pack state. A guarding pet counts as standing, so a Stay/Follow toggle clears its post and sits the pack with the rest.
- **Pets in vehicles/leashed:** they receive the sit/stand state change; movement follows vanilla rules for their restraint.
- **Target dies mid-flight:** vanilla target invalidation applies; pets disengage normally.
- `enableWhistle = false`: every whistle gesture — toggle, attack, round-up, guard, and locate — does nothing and shows `✦ The whistle is silent here.` (crafting stays available; the item is inert, not removed).

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
| `guardRadiusBlocks` | int | `8` | 4–16 |

### Implementation Notes

- Right-click: `Item#use` + entity raycast (`ProjectileUtil`-style clip) server-side.
- Left-click on air produces no server event in vanilla; the client detects the swing while holding the whistle (client attack hook) and sends one custom payload (`instinct:whistle_toggle`, empty body). The server validates: main hand holds a whistle, not on cooldown, feature enabled — then executes. Left-click on a block or entity routes through `AttackBlockCallback`/`AttackEntityCallback` to the same handler (and cancels the attack, so the whistle never punches).
- Pet enumeration: server entity lookup by AABB, filtered on `TamableAnimal#isTame` + `isOwnedBy(player)` + not downed.
- Attack order: `pet.setOrderedToSit(false)` then `pet.setTarget(target)`; combat-capability = `getAttribute(ATTACK_DAMAGE) != null`.
- Round-up: builds the drive group by AABB + same-type filter, then hands the group and the ordering player to §4's press machinery (claims, behind-points, expiries) with a 600-tick order deadline. No new goals — the whistle is a second trigger on the same `HerdWorkGoal`.
- Guard: sneak + right-click routes `Item#use` to `performGuard`, which resolves the anchor with a block raycast (`Entity#pick`) and writes a `GuardData` attachment (anchor `BlockPos`) on each posted pet that can fight — a pet carrying a vanilla melee-attack goal, a truer capability check than the attack-damage attribute (cats and parrots carry the attribute in 1.21.1 but have no melee goal to act on a target). A `GuardGoal`, added to every tamable on load and inert without the attachment (like predator watch), scans on an interval for the nearest hostile within `guardRadiusBlocks` of the anchor and `setTarget`s it — vanilla melee does the fighting — then pins the pet to its post. It yields the move slot the instant a target is set and stands down for the creeper berth (same priority). Every other whistle order clears the attachment.
- Locate: sneak + left-click routes to `performLocate` — the client swing hook branches on sneak to send `instinct:whistle_locate` (empty body) on air, and `onLeftClick`'s `AttackBlockCallback`/`AttackEntityCallback` path branches on `isShiftKeyDown()` for a block or entity, so all three gesture handlers agree. It scans every loaded `ServerLevel` (`getAllLevels`) for the player's owned tamed pets, drops the same-dimension pets within `whistleRadiusBlocks`, sorts nearest-first, caps at `WhistleLocator.MAX_LINES`, and sends the census to chat (`sendSystemMessage`, one line per pet — not the action bar), sharing the whistle item cooldown. The compass bearing, distance rounding, and same-vs-cross-dimension line-key choice are the pure `WhistleLocator` (Tier-1 unit-tested); downed pets are included, unlike every command.

---

## 7. Downed Pets & Revival

A pet's death becomes a rescue, not a funeral — at a price.

### Problem

One creeper, one skeleton volley, one mistake — a pet representing sixty days of veterancy dies permanently, and the rational response is to stop bringing pets anywhere. Permanent stakes with no mitigation teach players not to engage with the feature at all.

### Behavior

Applies to every tamed animal in the **pets set** and every tamed animal in the **mounts set** (Animal Coverage). A downed mount differs from a downed pet in three ways: it has no sit pose (the AI stop, whine, and smoke carry the read), it ejects any rider the instant it goes down (a mount kept mounted could otherwise still be steered), and revival costs no rank because a mount has no veterancy. Everything else — the 1.0-health pin, invulnerability, target immunity, the owner notice, the whine cadence, the beyond-saving edges, and the revival item path — is identical.

**Going down.** When lethal damage would kill the pet, the death is cancelled and the pet enters the **downed** state instead:
- health is set to 1.0; the pet is invulnerable to all further damage (exceptions below),
- all AI stops; the pet lies in place (sitting pose, head low) and cannot be commanded, whistled, tempted, or teleported,
- hostile mobs treat it as no target (any mob currently targeting it retargets),
- a species whine plays every 100 ticks (the entity's own hurt sound — wolf whine, cat hurt, a modded species' own voice — at volume 0.5) with one `smoke` particle,
- the owner — online, any distance, same dimension or not — gets one chat line (not action bar; this one must not be missed): `✦ <name> is down.`

The downed state is indefinite: it persists across saves, chunk unloads, dimension border, and owner logout. Downed pets never despawn.

**Beyond saving.** The death is **not** cancelled — the pet dies exactly as vanilla — when the lethal damage is fire or lava damage, void damage, or a kill command. VISION.md names this edge: fire, lava, and the void are beyond saving (and §1 exists to keep pets out of them). A tamed pets-set pet lost to fire, lava, or the void leaves a keepsake collar (below).

**Revival.** Any player (not only the owner) uses an item in `#instinct:revive_items` on the downed pet. The tag ships with:
- the **golden apple** (regular and enchanted), and
- the **vet kit** — `instinct:vet_kit`, stack size 16, crafted shapeless: 1 paper + 1 string + 1 honey bottle → 1 vet kit (bottle returned).

Siblings and packs extend the tag to add their own remedies (Animal Coverage → item tags).

The item is consumed; the pet revives: health set to `reviveHealthFraction` (default 0.5) × max health, Regeneration II for 10 seconds, 60 ticks of post-revive invulnerability, stands in Stay (sitting) state, revival cue + 5 `heart` particles. If `downedRankPenalty` is true and the pet has a veterancy rank, it loses exactly one rank: `accruedDays` is set to the threshold of the new rank (rank 1 → its threshold day count; rank 1 dropping to 0 → 0 days) — and with the rank go the learned behaviors above it (§2): a demoted Veteran forgets your swing, a demoted Venerable stops mentoring. Feedback to the reviving player: `✦ <name> is back on their feet.`

**Wrong item on a downed pet:** nothing happens (no swing, no consume). Regular interactions (sit toggle, dye, food) are all suppressed while downed.

**Carrying.** While `enableCarryDowned` is true (default), a downed pet small enough to lift can be scooped up and carried out of danger. A downed pets-set animal is **carryable** when it is a baby (a pup) or its type is in `#instinct:carryable` (ships cat and parrot; a mod extends it) — full-size pets and every mount stay where they fall. Sneak + empty-hand use on a carryable downed pet makes it a **passenger of the rescuer**: `✦ Carrying <name>.` The pet stays downed the whole time — invulnerable, no AI, still whimpering — so lifting only relocates it. Carrying occupies no inventory slot but slows the carrier: a transient `MOVEMENT_SPEED` modifier of `carrySlowdownFraction` (default 0.30 → ×0.70), removed the instant the carry ends and never persisted, so a logout can never strand a slowed player. A rescuer carries one pet at a time (`✦ Your hands are full.`). Sneak + empty-hand use on a block sets the pet down again, still downed: `✦ <name> set down.` Reviving a carried pet — a co-op partner can, with a `#instinct:revive_items` item — releases it from the carrier's arms and clears the slowdown; the carrier's own flow is to set the pet down, then revive it on the ground. However a carry ends — set down, revived, or the carrier logging out or dying — the pet is left downed wherever it comes to rest, no worse off than where it fell. Fire, lava, and the void remain beyond saving, carried or not: a pet taken into them dies as it would anywhere.

**Keepsake collar.** While `enableKeepsakeCollar` is true (default), a tamed **pets-set** animal lost beyond saving to fire, lava, or the void leaves a **keepsake collar** (`instinct:keepsake_collar`) — a memento engraved with the pet's name and its veterancy standing at the moment of loss. Every tamed pet leaves one, ranked or not: the engraving reads the pet's name, then its rank and days (`Venerable — seen 63 days`) or the days alone for an unranked pet (`Seen 4 days`), snapshotted so a later threshold-config edit never rewrites a memento. The collar is a pure keepsake — zero gameplay power: fire-resistant so the drop survives the lava that took the pet, in no tag, revives nothing, crafts into nothing, stacks to 1. A fire or lava loss drops it in place; a void loss lays it on the ground at the pet's own column — the surface it walked off — rather than into the void. Mounts and livestock leave nothing (a collar is a pet's alone), and the `/kill` command, though beyond saving, leaves nothing — it is an admin act, not grief.

### Edge cases

- **Explosion that downs the pet:** the triggering damage resolves first (pet goes down); subsequent blast/fire ticks hit invulnerability. A pet downed *in* fire that keeps burning: the next fire tick is lethal-class fire damage against a downed pet — downed pets are invulnerable to it like everything else; the "beyond saving" test applies only to the *lethal blow that would have killed a healthy pet*, not to damage after downing. (A pet downed at the lava edge is safe; a pet swimming in lava never goes down at all.)
- **`/kill` and void:** bypass downed entirely (die normally), including while already downed — a downed pet that falls into the void dies.
- **Keepsake over a bottomless column:** a pet lost down a column with no block above the world floor leaves no collar rather than drop one into the void it was lost to; every other beyond-saving loss over ground leaves one.
- **Owner never returns:** the pet stays down forever unless it lies beside a kennel post, which brings it back on its own over time (§9); there is no timeout and no auto-death.
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
| `enableCarryDowned` | bool | `true` | |
| `carrySlowdownFraction` | double | `0.30` | 0.0–0.9 |
| `enableKeepsakeCollar` | bool | `true` | |

### Implementation Notes

- Death interception: `ServerLivingEntityEvents.ALLOW_DEATH` returning false for qualifying pets, then applying the downed attachment + synced entity flag (tracked data) for client pose rendering.
- Downed attachment `DownedData { long downedAtGameTime; }`; the synced flag drives pose (`setInSittingPose`-equivalent + suppressed AI via goal gate), whine cadence, and interaction suppression. The pose is best-effort for modded species without a sitting animation — AI suppression, the whine, and the particle carry the downed read regardless.
- Keepsake drop: `ServerLivingEntityEvents.AFTER_DEATH` (not the `ALLOW_DEATH` cancel hook — the death has resolved by then), re-checking tame, pets-set membership, and the fire/lava/void source on its own; it shares no state with the downed engine. The engraving rides the stack as the `instinct:keepsake_engraving` data component (`{ Component petName; int rank; int daysSeen; }`). The collar item is `fireResistant()`, so fire and lava losses drop in place and survive; a void loss resolves the drop to the `MOTION_BLOCKING_NO_LEAVES` heightmap surface at the pet's column, skipping the drop only when that column is bottomless.
- "Beyond saving" test: `source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)`.
- Target immunity: a `Mob#canAttack`-site guard (or targeting-conditions predicate injection) plus a sweep clearing `getTarget() == downed` on down.
- Revival: `UseEntityCallback` intercepting item-on-downed before vanilla interactions.
- Carrying: the pet rides the player via `startRiding`, so vanilla owns the render, position sync, portal travel, and auto-eject on logout/death. The `CarryHandler` `UseEntityCallback` (pick up) registers ahead of the revival handler so the sneak + empty-hand gesture is claimed before empty-hand suppression; set-down is a `UseBlockCallback`. The slowdown is an `addTransientModifier` on `MOVEMENT_SPEED` (never serialized); a per-second sweep over the tracked carriers strips a stale modifier if a carried pet leaves by an untracked path (`/kill`, the void).
- `InstinctAnimalDownedCallback` / `InstinctAnimalRevivedCallback` fire at the respective transitions, for pets and mounts alike (§Public API).

---

## 8. Predator Watch

A pet on Stay near livestock turns wild predators from the pasture — the dog that herds also guards.

### Problem

Foxes hunt chickens and rabbits; untamed wolves hunt sheep and rabbits. A pet stationed at the pasture watches its prey get taken and does nothing — the dog that drives a herd (§4/§6) should also keep the fox off it. The only vanilla defense is a wall, and a wall is not a dog.

### Behavior

Applies to every tamed **pets-set** animal that is **on Stay** (ordered to sit) with covered livestock inside `predatorWatchRadiusBlocks` (default 12). Any pets-set species guards — watching is stationing, not combat, so a cat turns a fox as a wolf does; a combat-capable guardian additionally fights what it reaches, exactly as vanilla wolves already fight foxes. While a **wild predator** (a predator-set member that is not a tamed animal) is inside the radius, the guardian does two things each scan:

**Deter.** Every wild predator in the radius has any covered-livestock attack target cleared — its hunt broken — and is driven away from the guardian along the guardian→predator axis, so it paths away from the watched pasture rather than into it.

**Intercept.** The guardian stands from its seat and paths to a blocking point between the nearest predator and that predator's nearest prey, putting its body in the way. When no predator or no livestock remains in the radius it re-sits where it stands — stay means stay, minus the predator.

A guardian with no livestock in range has no pasture to keep, so a predator merely passing a lone stationed pet is never harassed. The predator set defaults to fox and wolf and is edited like any coverage set: the `#instinct:predators` entity-type tag plus `predatorsInclude`, minus `predatorsExclude`. Only wild instances ever count — a tamed wolf is someone's pet, never a predator.

### Sitting and commanded state

The watch engages only a **Stay** pet; a following pet keeps following. The guardian stands by preempting the sit goal and never touches the sit order itself — so a whistle to Follow ends the watch at once (the pet is never re-sat against a command), and when the watch ends the pet re-sits on its own because the Stay order still holds. Self-preservation always wins: a swelling creeper within the §1 berth's awareness radius, or the guardian catching fire or stepping in lava, ends the watch at once so the pet can flee. Downed pets (§7) have no AI and never guard.

### Edge cases

- **Deterrence is scoped to a live guardian.** A wild predator with no stationed pet nearby behaves exactly as vanilla — nothing about the world's predators changes until a guardian is watching them, and no goal is ever installed on a wild fox or wolf.
- **Multiple guardians** each deter independently; overlapping deterrence is idempotent (a target is cleared once), and each guardian intercepts its own nearest threat.
- **`enablePredatorWatch = false`:** no pet ever leaves its seat and no predator's hunt is touched — exact vanilla behavior.

### Multiplayer

Livestock is unowned, so a guardian keeps the pasture for whoever's animals stand in it — cross-owner by design, like the §2 mentor aura, and it needs no online owner. There is no per-player or server-wide state: a stationed dog guards a shared farm the same for everyone, and the absent player is never punished.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enablePredatorWatch` | bool | `true` | |
| `predatorWatchRadiusBlocks` | int | `12` | 4–24 |
| `predatorsInclude` | list | `[]` | |
| `predatorsExclude` | list | `[]` | |

### Implementation Notes

- One injected goal, `PredatorWatchGoal` (priority 1), added to every tamed pets-set animal on `ServerEntityEvents.ENTITY_LOAD` (idempotent, the §4 install pattern). Both halves run from the guardian, so no goal is ever installed on a wild predator: an untamed fox or wolf with no guardian nearby is exactly vanilla and costs nothing.
- Inert until engaged: scans are interval-gated (`adjustedTickDelay`, the §1 berth cadence), and a stationed pet with no predator in range pays only the scan. Deterrence is `setTarget(null)` on a covered-livestock target plus a re-issued away-navigation; interception is a move to `PredatorWatch.interceptPoint(...)`.
- Priority 1 preempts the sit goal so a Stay pet stands without the watch ever mutating the sit order (the order stays the player's, so a whistle to Follow ends the watch instead of being swallowed, and no forced re-sit ever fights a command). It ties `CreeperBerthGoal` and `TamableAnimalPanicGoal`; rather than lean on priority (equal priorities never preempt mid-run), the watch yields to both itself — never engaging while a creeper is swelling, dropping the watch when one appears, and standing down while on fire or in lava — so self-preservation always takes the slot within a scan.
- Predator-set resolution (`#instinct:predators` ∪ `predatorsInclude`, minus `predatorsExclude`) has no heuristic layer; the pure geometry (`interceptPoint`, `fleePoint`) is unit-tested, the live behavior gametested.

---

## 9. Kennel Post

A home for the pack — one placed block the whistle sends pets to, and beside which a downed pet mends on its own.

### Problem

Dismissing the pack scatters it wherever you stand, and a downed pet comes back only by spending a golden apple or a Vet Kit on the spot. There is no home to send an animal to, and no way to let time do the healing. A settled farm wants a place its animals belong.

### Behavior

**The block.** The **kennel post** (`instinct:kennel_post`) is a humble wooden marker — planks, a fence, and a bone — that joins the trough in the creative register (§5). It is a *place, not a system*: no block entity, no storage, no ownership, no spawn mechanics. It has no collision — a thin marker a pet paths right onto — and is axe-mineable, dropping itself.

**Assigning a home.** Point the command whistle at a kennel post and right-click (no sneak — sneak + right-click is the guard order, §6): every commandable pet within the whistle's voice (`whistleRadiusBlocks`, §6) adopts that post as home and walks there now. The home — the post's position and dimension — rides a persistent `HomeData` attachment on the pet. Feedback: `✦ <n> pets will call this home.`

**Recall on Stay.** A **Stay** order (left-click, §6) sends every homed pet to its post to settle, instead of sitting it where it stands. A pet that is un-homed, or whose post is in another dimension, sits in place exactly as before. A recalled pet stands and paths home; on arrival it sits, and a post it cannot reach (walled off, too far) is given up at a deadline so a recalled pet always settles rather than pathing forever. A post mined out from under a homed pet degrades to sitting in place. Any other whistle order (attack, guard, round-up, Follow) clears an in-progress recall.

**Recovery.** A downed **pets-set** pet (§7) within `kennelRecoveryRadiusBlocks` (default 4) of *any* kennel post recovers on its own, over `kennelRecoverySeconds` (default 300 — five minutes) of real time, **without an item and without losing a veterancy rank**. The golden apple and Vet Kit remain the instant, one-rank-cost path in the field; the post is the patient, no-cost path at home. Recovery accrues only while the pet is beside a post — a downed pet cannot move itself, so it simply waits — and a small pet carried (§7) to a post recovers there. The recovered pet comes back exactly as an item revival does — `reviveHealthFraction` of max health, Regeneration II, the post-revive grace window, the Stay pose — minus the rank penalty. Its owner, online at any distance, gets one chat line: `✦ <name> recovered at their post.` Recovery fires `InstinctAnimalRevivedCallback` with a null reviver and an empty stack (§Public API), so a consumer sees every path back up. A downed mount is not the pack's — only pets recover at a post.

### Edge cases

- **Post in another dimension:** a homed pet led to another dimension sits in place on Stay (there is no cross-dimension pathing); its home is remembered and honored again once it returns.
- **Post mined during a recall:** the pet settles where it stands rather than trekking to the empty spot.
- **Recovery pauses off-post:** a downed pet moved away from a post holds its recovery progress and resumes beside one; progress persists across save/reload on the downed attachment.
- **Recovery in an unloaded chunk:** a downed pet whose chunk is unloaded does not tick, so recovery pauses until the chunk loads — the same as the whine (§7).
- **`enableKennelPost = false`:** the post is inert decoration — no assignment, no recall, no recovery; the block stays placeable and craftable.

### Multiplayer

Per-pet server state; the post itself holds none. Recovery keys on proximity to any kennel post, so it never punishes an absent player and needs no online owner. A recovered pet's notice goes to its owner alone.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableKennelPost` | bool | `true` | |
| `kennelRecoveryRadiusBlocks` | int | `4` | 2–8 |
| `kennelRecoverySeconds` | int | `300` | 30–3600 |

### Implementation Notes

- The block is a plain `Block` (`KennelPostBlock`) — no block entity — with a post `VoxelShape` outline, empty collision, and `isPathfindable` returning true, so a recalled pet paths onto it. Registered beside the trough in `InstinctBlocks`, axe-mineable, dropping itself.
- The home is a codec-backed `HomeData { BlockPos post; ResourceKey<Level> dimension }` attachment (the first Instinct attachment to persist a dimension). Assignment writes it from the whistle; the recall itself is a transient in-memory order (`KennelHandler`), not persisted — a reload ends the recall, leaving the pet where it stands.
- `HomeGoal` (priority 1, the §6 guard / §8 watch install pattern via `ServerEntityEvents.ENTITY_LOAD`) is inert until the pet is recalled; it repaths to the post on a scan interval, settles on arrival or at a deadline, and degrades a mined or cross-dimension post to sitting in place. It is mutually exclusive with the guard order — each whistle order clears the other's state.
- Recovery is a sibling pass in the §7 downed engine's per-tick sweep, reusing the bounded loaded-downed set: config-gated, staggered per pet, a hard-clamped-radius block scan for a nearby post, accruing `recoveryTicks` on the downed attachment until the threshold, then a shared rank-free state restore extracted from the item-revival path.

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
| `mountsInclude` | list | [] | Entity types forced into the mounts set |
| `mountsExclude` | list | [] | Entity types forced out of the mounts set |
| `enableSelfPreservation` | bool | true | §1 master toggle (pets and mounts) |
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
| `fertileRenewableReduction` | double | 0.15 | Fertile's per-grade cut to egg-lay and wool-regrowth cadence (0.0–0.5; 0 = breeding only) |
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
| `enableCarryDowned` | bool | true | Sneak-use to carry a downed small pet to safety |
| `carrySlowdownFraction` | double | 0.30 | Movement slowdown while carrying a downed pet (0.0–0.9) |
| `enablePredatorWatch` | bool | true | §8 master toggle |
| `predatorWatchRadiusBlocks` | int | 12 | Radius a Stay guardian watches for predators over livestock (4–24) |
| `predatorsInclude` | list | [] | Entity types forced into the wild-predator set |
| `predatorsExclude` | list | [] | Entity types forced out of the wild-predator set |
| `enableKennelPost` | bool | true | §9 master toggle (block stays placeable, inert) |
| `kennelRecoveryRadiusBlocks` | int | 4 | Radius a downed pet recovers within of a kennel post (2–8) |
| `kennelRecoverySeconds` | int | 300 | Seconds a downed pet takes to recover beside a post (30–3600) |
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
| `InstinctAPI.isMount(EntityType<?>)` | mounts-set membership after full resolution (the horse family; §1 + §7 only) |
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
| `InstinctAnimalDownedCallback(animal, source)` | on entering downed, pets and mounts alike (§7) |
| `InstinctAnimalRevivedCallback(animal, reviver, item)` | on every path back up: item revival (§7) and kennel-post recovery (§9). `reviver` is null and `item` empty on the recovery path — consumers must null-check. |

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
| `instinct:root` | Instinct | tame an animal, or craft a command whistle, vet kit, pedigree treat, or feeding trough |
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
- Membership resolution: config > tag > heuristic precedence, exclude-beats-include at each layer, pet-never-heuristic-livestock, mount-never-heuristic-livestock, tamable-never-heuristic-mount, `autoDetectAnimals = false` reduces to tags + config.
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
- Mounts: the horse family resolves into the mounts set via the shipped tag (and stays out of pets/livestock), while skeleton/zombie horses do not; a tamed horse paths around a lava strip; a riderless horse flees a swelling creeper while a ridden one does not; a lethal arrow downs a tamed horse (health 1.0, invulnerable, untargetable, rider ejected); a golden apple / vet kit revives it with no rank penalty; lava is a real death; an untamed horse dies normally; the downed state survives an NBT round trip; `enableDownedState = false` restores vanilla death; a tamed **camel** (brain-driven) gets hazard maluses and downs/revives but is injected no berth goal (graceful degradation).
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
