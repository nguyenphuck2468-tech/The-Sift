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

The plugin packages a small dimension datapack and registers it through
Paper's datapack-discovery lifecycle. This requires Paper's plugin loader and
Paper 26.3 or newer. The prototype uses the vanilla Meadow biome and Overworld
terrain with custom Sift dimension visuals; the larger, unfinished biome pack
is kept separate so its missing worldgen features do not prevent the server
from loading.

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
- The Sift custom dimension prototype, entered with `/sift dimension` and left
  with `/sift return`.

Portal, mob, Ichor, and Rift behavior are being migrated into the source
registry in vertical slices. Do not install an older TheSift jar alongside
this build because both use the `TheSift` plugin identity.

## The Sift dimension prototype

The Java datapack model was cross-checked against the Fabric reference mod
`The Sift 1.1.2` (Minecraft 26.3), recovered from this repository's initial
history, and the Paper datapack-discovery documentation. The plugin supplies
the dimension data itself; it does not load or require the Fabric mod.

On Paper 26.3+, use `/sift dimension` to create/load and enter the dimension.
The console command creates/loads it without a player; players can enter with
the same command. Use `/sift return` to return to the Overworld spawn.
`/datapack list enabled` should show `TheSift/dimension`. The prototype uses
the vanilla Meadow biome and Overworld terrain settings, so custom Sift blocks
and the Fabric mod's bespoke terrain noise are not required.

Minecraft's September 2026 announcement says The Sift is planned for Java and
Bedrock next year, but does not publish a 27.1 datapack schema or Paper API.
Recheck the dimension data against the official 27.1 release before claiming
compatibility with that version.

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
