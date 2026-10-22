# The Sift plugin foundation

This project is the single source/build project for the next The Sift plugin
update. It targets Java 25 and keeps runtime code, plugin metadata, datapack,
resource pack, and Bedrock support under one source tree.

## Build

```bash
gradle clean build
```

The default compile dependency is Paper API `1.21.4-R0.1-SNAPSHOT`. Override
it when the Paper 26.3 API is published:

```bash
gradle build -PpaperApiVersion=<paper-api-version>
```

The `compatibility.minecraft-target` value is intentionally configuration
metadata until a matching Paper API is available. The foundation must not
claim 26.3 runtime compatibility based only on the resource-pack overlay.

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

Portal, mob, Ichor, and Rift behavior are being migrated into the source
registry in vertical slices. Do not install an older TheSift jar alongside
this build because both use the `TheSift` plugin identity.

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
