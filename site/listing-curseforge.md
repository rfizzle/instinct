# Instinct — Husbandry Overhaul

**_Worth raising._**

![Instinct logo](https://raw.githubusercontent.com/rfizzle/instinct/master/art/logo.png)

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

## At a glance

- Minecraft **1.21.1**, **Fabric** loader (0.16.10+), **Fabric API** required.
- Install on the **server** and every **client**.
- **2 blocks, 4 items, 5 advancements** — no new mobs, no new dimensions,
  nothing to migrate.
- Every feature independently tunable through `config/instinct.json` (62
  options) — hot-reload with `/instinct reload`, or edit it in-game with
  **Mod Menu + Cloth Config**.
- **Jade** and **WTHIT** read an animal's state straight off your crosshair.
- MIT licensed.

## Features

### Pets that look before they leap

Tamed wolves, cats, and parrots treat lava, fire, and cactus as walls when they
pathfind. A lit creeper sends them to a 4-block berth at 1.4× speed — a sitting
pet stands, steps clear, and sits back down. The follow-owner teleport is
suppressed while you're falling, swimming in lava, or gliding, so your wolf
never materializes into the hole you're falling down.

Your own hand is off the table too: your arrows, splash potions, and TNT never
land on your own pets. And a shoulder parrot finally *stays* — through jumps,
short falls, and scratches — coming down on a deliberate double-tap sneak
instead of the first time you hop a fence.

### Veteran pets

Every pet counts the in-game days since you tamed it. At **10, 30, and 60 days**
it becomes **Seasoned, Veteran, Venerable** — each rank +1 heart and +1 attack
damage, to +3/+3.

Each rank also teaches it something an old animal would know. A Seasoned pet
warns you in its own voice when a monster sets its eye on you. A Veteran ducks
your sweeping blade. A Venerable one steadies the young — pets near it earn
their days **25% faster**. Crouch-look reads any pet's days and rank.

### Quality genetics

Livestock carry a bloodline grade — **ordinary → sturdy → prime**. Breed near a
hay bale and the calf has a 50% chance to climb a grade; breed in a crush of
twelve-plus and it risks slipping one. A prime cow drops +2 beef and +1 leather;
prime sheep shear +2 wool, prime chickens lay 20% faster.

Newborns of graded stock also inherit a **perk** — *hardy*, *fleet*, *fertile*,
or *placid* — biased toward the parents' when the pen is well fed, so a tended
bloodline stabilizes line by line. A craftable **Pedigree Treat** guarantees the
next offspring is born prime. Three tended generations beat three hundred
crammed ones.

### Flocking, herding & droving

Hold wheat and the matching animals form a flock behind you — 15% faster than
vanilla, holding a loose 2-block spacing instead of shoving into your back.
Lead three or more and up to two of your pets fall in behind on their own, each
getting behind a straggler and pressing it home. Pets press, never bite.

Flocks **swim rivers in formation** instead of scattering at the bank, and a
following pet takes your boat's spare seat and steps back out when you land.

### The dog that guards the pen

Leave a pet on Stay near your livestock and it watches the fence line. A fox
creeping in on the chickens or a wild wolf stalking the sheep finds its hunt
broken and itself driven off the pasture. Any pet guards — a cat turns a fox as
a wolf does. It costs no command, and a predator with no pet stationed nearby
behaves exactly as vanilla.

### The feeding trough

A craftable wooden block that holds a stack of feed and breeds the herd while
you're away. Hoppers fill it; comparators read it. It **self-limits** — past 16
animals in range it keeps feeding babies but stops starting new pairs, so your
passive farm can't quietly become a lag machine.

### The command whistle

Copper and bone. It speaks to every tamed pet within 20 blocks:

- **Left-click** — toggle the whole pack between Stay and Follow, in one press.
- **Right-click a mob** — every combat-capable pet stands and attacks it.
- **Right-click your own livestock** — a **round-up**: your pets fall in behind
  that animal's whole nearby herd and drive it home to you.
- **Sneak + right-click a spot** — the pack **holds that ground** and engages
  hostiles that wander in, returning to post once the threat is down. The order
  survives unloads and restarts.
- **Sneak + left-click** — the **lost-pet census**: every bonded pet beyond
  earshot, nearest first, with distance and compass bearing — or just the
  dimension for the one you left in the Nether. A downed pet is flagged so you
  know which is the patient.

### Downed, not dead

Lethal damage **downs** a pet instead of killing it: helpless, whimpering,
ignored by monsters, and waiting — indefinitely, across saves and logouts.
Revive it with a golden apple or a craftable **Vet Kit** and it stands at half
health, at the cost of one veterancy rank.

A **tamed mount** — the whole horse family — goes down the same way, with no
rank to lose. Losing a maxed horse to one creeper stops being permanent.

A downed cat, parrot, or pup can be **scooped up and carried** clear of the
fire: no inventory slot, but you move slower, so a rescue under fire still costs
something.

Fire, lava, and the void remain final — and a pet lost that way leaves a
**keepsake collar**, engraved with its name and the rank it earned. A sixty-day
companion deserves better than an empty lead.

### A home for the pack

Place a **kennel post** — planks, a fence, a bone — and point the whistle at it:
every pet in earshot takes it as home. From then on a Stay order sends them
walking back to their post to settle, instead of dropping them wherever you
happened to be standing.

A pet that goes down beside its post **mends on its own** over about five
minutes — no item, no rank lost. The golden apple is the instant path back in
the field; the post is the patient, free one at home.

### Modded animals welcome

Any modded animal that behaves like a vanilla one joins automatically: a
tameable otter reads as a pet, a breedable critter reads as livestock. Mods and
datapacks opt species in or out with **one tag entry**, add their feed to the
trough, and give their species proper product yields with one small data file —
no code, no dependency on Instinct. Server owners get the final word by name in
the config. Species with no curated data still work: drop bonuses mirror the
animal's own loot table.

## Commands

- `/instinct info` — how the animal on your crosshair resolved: its sets, grade,
  perk, veterancy. Available to everyone.
- `/instinct set veterancy <days>` · `/instinct set grade <grade>` — operator.
- `/instinct reload` — re-read `instinct.json` without a restart. Operator.

## Advancements

**Instinct** (root) · **Old Friend** — raise a pet to Venerable · **Best in
Show** — breed a prime animal · **Back from the Brink** — revive a downed pet ·
**Pack Leader** — command ten pets with a single whistle.

## Part of Concord

Part of **[Concord](https://concord.rfizzle.com)** — a modular collection of
system overhauls. Install any, combine all.

**Enhanced by** (both optional, never required): **Tribulation** — days survived
at difficulty tier 3+ count double toward veterancy · **Prosperity** — Vet Kits
and Pedigree Treats turn up in outlands- and depths-tier chests.
