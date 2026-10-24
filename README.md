# The Sift plugin foundation

This project is the single source/build project for the next The Sift plugin
update. It targets Java 25 and Paper 26.3 for its current build, while preparing
the Sift dimension integration for the planned Minecraft 27.1 release. The
27.1 data format and runtime API are not available yet, so this is a working
26.3 prototype rather than a claim of 27.1 compatibility.

## Build

```bash
gradle clean build
```

The default compile dependency is Paper API `26.3.build.218-stable`. Override
it when validating another Paper API version:

```bash
gradle build -PpaperApiVersion=<paper-api-version>
```

The plugin registers its dimension datapack through Paper's datapack-discovery
lifecycle. This requires Paper's plugin loader and Paper 26.3 or newer. The
dimension selects among the original five prototype biomes and ten more biome
names from the community mod reference: Overgrown Forest/Clearing/Slopes,
Siftslate biomes, Ichor Snowy Peaks, Sift Wastes, and Sift Deep Dark. It has
custom sky, fog, and ambient settings. The prototype still uses vanilla
Overworld noise and vanilla block/entity substitutes; 27.1 compatibility
remains unverified.

## Current foundation scope

- Gradle/Java 25 build.
- Versioned plugin configuration (`config-version: 2`).
- Refusal to rewrite configs newer than this build.
- Registry for Siftslate, Ichor, Charoite, Siftite, Resonance, Rift content,
  and the planned mobs.
- Ichor v2: persistent cauldron levels, refill/bottle costs, tagged Ichor
  Glass/Panes and Ichor Snow blocks, recipes, particles, and sounds.
- Charoite and Siftite progression: tagged ore drops, ingot/nugget recipes,
  smithing upgrades for armor/tools, and Siftite item diagnostics.
- Singer resonance slice: persistent Singer-tagged Allay prototype, timed
  sonic pulses, Charoite protection, boss bars, drops, and summon diagnostics.
- `/sift doctor` to inspect enabled content flags.
- `/sift reload` with migration validation.
- The Sift dimension with five custom biomes, entered with `/sift dimension`
  and left with `/sift return`.
- First-load biome decoration: cherry groves, mossy bulb forests, crystal
  spires, shattered stone fields, and Carapace shell landmarks.
- Vanilla-entity stand-ins for mod creatures, with named Loomers, Wisps,
  Glimmerwings, Sunspores, Hollow Bulbs, Prismites, Shardlings, Thornbacks,
  Blubs, Frost Stalkers, and Drifters. Singer, Echo Golem, Prism Warden, and
  The Sifter encounters can also appear naturally in selected biomes.
- Rare natural Rift events in Shattered Peaks, using the existing opening,
  wave, resonance-pulse, closing, and reward flow.
- `/sift summon <species> [player]` for direct prototype spawns, including
  Blub, Bulb, Koi, Thornback, The Sifter, Prism Warden, and Lumen Tulip.
- A cycling Sift Tide that changes movement/mining effects for players and
  nearby creatures.
- A reference-mod portal melody prototype: right-click note blocks to cycle
  eight notes, then left-click notes in order `1, 3, 7, 6, 5, 2, 4, 8` to open
  a Rift. This experimental interaction intercepts normal note-block clicks.
- Rifts link The Sift and the Overworld in both directions, transport nearby
  entities, and default to a five-minute active window.
- Ichor weather adds colored rain particles, regeneration/slowness on contact
  with Sift water, and up to two snow layers in Ichor Snowy Peaks.
- Chunk decoration can add Ichor springs and abandoned portal ruins with
  prototype loot, plus Soul Canyon cuts, Sniffer Cave entrances, cliff arches,
  and monoliths. Dark Sniffer proxies use a sonic-pull effect.
- Tagged Siftite equipment dropped on death is returned after respawn through
  a Mini Rift effect; this prototype keeps pending recovery in memory.
- Charoite uses a Lapis Lazuli stand-in in enchanting tables: offers add 20
  levels and raise rolled enchantments above level I. Charoite-enchanted items
  can still be renamed but cannot be combined on anvils.
- The bundled Java item/block model assets can be built into
  `build/distributions/TheSift-resource-pack.zip` with
  `gradle siftResourcePack`; players must install or be served the pack to see
  those custom models.

Portal, mob, Ichor, and Rift behavior are being migrated into the source
registry in vertical slices. Do not install an older TheSift jar alongside
this build because both use the `TheSift` plugin identity.

## The Sift dimension prototype

The Java datapack model was cross-checked against the Fabric reference mod
`The Sift 1.1.2` (Minecraft 26.3), the repository's existing biome and asset
files, the Paper datapack-discovery documentation, and Mojang's announcement.
The plugin supplies its own datapack; it does not load or require the Fabric
mod. The announced Java/Bedrock release has not shipped its official worldgen,
so current terrain and creatures are gameplay approximations built from
vanilla blocks and entities.

The extra biome names, portal melody, Ichor weather, structures, and creature
behaviors are inspired by the MIT-licensed Mielon's The Sift reference project;
they are not confirmed descriptions of Mojang's final 27.1 implementation.
Feature comparisons and deferred problems are tracked in
[IMPLEMENTATION_REPORT.md](./IMPLEMENTATION_REPORT.md).

On Paper 26.3+, use `/sift dimension` to create/load and enter the dimension.
The console command creates/loads it without a player; players can enter with
the same command. Use `/sift return` to return to the Overworld spawn.
`/datapack list enabled` should show `TheSift/dimension`. The prototype uses
the vanilla Meadow biome and Overworld terrain settings, so custom Sift blocks
and the Fabric mod's bespoke terrain noise are not required.

Mojang has named Meadows and Carapace and described new mobs, hazardous blocks,
and shifting tides. The Java/Bedrock announcement does not publish final
27.1 worldgen, item, mob, or API definitions. This prototype's remaining named
biomes and creatures come from the repository's mod reference and are not
claims about Mojang's final release. Recheck the pack against 27.1 before
claiming compatibility.

## Ichor v2 test flow

1. Use `/sift give ichor_bottle <player>` to obtain a source bottle.
2. Right-click a configured cauldron with the Ichor Bottle to create a full
   persistent Ichor cauldron.
3. Hold empty vanilla glass bottles and right-click the Ichor cauldron. Each
   bottle consumes the configured number of Ichor levels.
4. Craft Ichor Glass and Ichor Glass Panes using the registered recipes.
5. Place and break the tagged blocks to verify identity-preserving drops.

The slice uses vanilla block materials as a safe Paper foundation. The state
is persisted in the plugin config until a Paper 26.3-compatible custom block
strategy is finalized. This avoids NMS and keeps the feature portable.

## Charoite and Siftite test flow

Use the following commands on a test server:

```text
/sift give charoite <player>
/sift give siftite_nugget <player>
/sift give siftite_ingot <player>
/sift give siftite_sword <player>
/sift give siftite_upgrade_smithing_template <player>
```

Charoite uses an Amethyst-based prototype ore/drop path. Siftite gear is
represented by vanilla Netherite materials with persistent content IDs and
custom names, so it remains usable before the custom model pack is completed.
The smithing recipes intentionally use the vanilla Netherite upgrade template
until a custom smithing-template item strategy is finalized.

## Singer and Resonance test flow

```text
/sift summon singer <player>
/sift give charoite <player>
```

The Singer prototype is an Allay tagged with The Sift's persistent entity ID.
Every configured number of seconds it emits a sonic pulse, damaging and
slowing players within range. A player carrying a tagged Charoite item is
protected from the pulse. The encounter uses a boss bar and drops Charoite
when the Singer dies. The implementation uses Bukkit/Paper APIs rather than
NMS; the custom Singer model can be connected through the resource pack later.
