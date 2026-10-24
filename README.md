# The Sift plugin foundation

This project is the single source/build project for the next The Sift plugin
update. It targets Java 25 and Paper 26.3 for its current build, while preparing
the Sift dimension integration for the planned Minecraft 27.1 release. Here,
“vanilla-like 27.1 experience” means custom Sift content should feel built into
the game; the Paper implementation may use content plugins to supply custom
blocks, items, mobs, models, and packs. The 27.1 data format and runtime API
are not available yet, so this is a working 26.3 prototype rather than a
claim of 27.1 compatibility.

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
custom sky, fog, and ambient settings. The prototype still uses Overworld
noise and built-in block/entity fallbacks where no external custom-content
provider is configured; 27.1 compatibility remains unverified.

## Current foundation scope

- Gradle/Java 25 build.
- Versioned plugin configuration (`config-version: 3`).
- Refusal to rewrite configs newer than this build.
- Registry for Siftslate, Ichor, Charoite, Siftite, Resonance, Rift content,
  and the planned mobs.
- Ichor v2: persistent cauldron levels, refill/bottle costs, tagged Ichor
  Glass/Panes and Ichor Snow blocks, recipes, particles, and sounds.
- Charoite and Siftite progression: tagged ore drops, ingot/nugget recipes,
  smithing upgrades for armor/tools, and Siftite item diagnostics.
- Singer resonance slice: persistent Singer-tagged Allay prototype, timed
  cosmetic sonic pulses, boss bars, drops, and summon diagnostics. Charoite
  protection is not active while the pulse has no damage/debuff effect.
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
  a Mini Rift effect; pending recovery is saved across server restarts.
- Charoite uses a Lapis Lazuli stand-in in enchanting tables: offers add 20
  levels and raise rolled enchantments above level I. Charoite-enchanted items
  can still be renamed but cannot be combined on anvils.
- The bundled Java item/block model assets can be built into
  `build/distributions/TheSift-resource-pack.zip` with
  `./gradlew siftResourcePack`; players must install or be served the pack to see
  those custom models.
- Optional Nexo, ItemsAdder, and Oraxen item bridges: The Sift resolves
  provider items when available and keeps its own persistent content IDs for
  recipes and gameplay. `auto` tries registered API adapters first, then Nexo,
  ItemsAdder, and Oraxen. Set the provider to `nexo`, `itemsadder`, `oraxen`,
  `vanilla`, or an external provider's service ID to choose explicitly.
- Optional MythicMobs spawning: configured Mythic mobs are used for natural
  Sift wildlife and `/sift summon`; unconfigured mob IDs continue to use the
  vanilla-backed prototypes. Starter configs are in `integrations/`.

Portal, mob, Ichor, and Rift behavior are being migrated into the source
registry in vertical slices. Do not install an older TheSift jar alongside
this build because both use the `TheSift` plugin identity.

## Custom content providers

For the intended custom-content presentation, install **Nexo**, **ItemsAdder**,
or **Oraxen** (choose one) and serve The Sift resource pack through it. Without
a custom-content provider, the plugin uses built-in material/entity fallbacks.
The item bridge calls the provider
API and adds The Sift's persistent item ID afterward, so the existing recipes,
commands, drops, and listeners continue to recognize the item. With `auto`,
Nexo is preferred when multiple built-in providers are installed. Recipes are
refreshed shortly after startup to account for asynchronous item registry loads.

For Nexo, copy `integrations/nexo/the-sift.yml` into `plugins/Nexo/items/`,
merge `assets/thesift` from `TheSift-resource-pack.zip` into Nexo's pack assets,
then run `/nexo reload`. For ItemsAdder, copy
`integrations/itemsadder/sift-items.yml` into
`plugins/ItemsAdder/contents/thesift/`, copy the resource pack's
`assets/thesift/models` and `assets/thesift/textures` folders into that
namespace, then run `/iazip` and `/iareload`. Starter definitions cover the
configured Sift item IDs; wrapper models use bundled custom models where
available and otherwise retain the item's vanilla material appearance.

For Oraxen, copy `integrations/oraxen/the-sift.yml` into
`plugins/Oraxen/items/`, copy the resource pack's `assets/thesift/models` into
`plugins/Oraxen/pack/models/thesift/` and `assets/thesift/textures` into
`plugins/Oraxen/pack/textures/thesift/`, then reload Oraxen and regenerate or
reupload its pack.

### Custom blocks in generated terrain

The ecology generator can place provider-owned custom blocks for Charoite ore,
Sift logs/leaves, and Ichor spring water. Map those Sift IDs to custom blocks
that you define in the selected provider:

```yaml
integrations:
  blocks:
    provider: auto # or nexo, itemsadder, oraxen
    definitions:
      charoite_ore: thesift:charoite_ore
      sift_log: thesift:sift_log
      sift_leaves: thesift:sift_leaves
      sift_grass: thesift:sift_grass
      siftstone: thesift:siftstone
      siftstone_bricks: thesift:siftstone_bricks
      ichor_water: thesift:ichor_water
```

`auto` tries registered `SiftBlockProvider` adapters, then Nexo, ItemsAdder,
and Oraxen. ItemsAdder IDs without a namespace are treated as `thesift:<id>`.
Failed placements use the built-in material fallback. Starter item definitions
alone do not create placeable blocks; each mapped ID must be configured as a
custom block in the selected provider.

Other plugins can integrate without a The Sift code change by registering
Bukkit services implementing `com.gamepathics.thesift.foundation.api.SiftItemProvider`,
`SiftMobProvider`, and/or `SiftBlockProvider`. A block provider receives a
configured block ID and location and reports whether placement succeeded. The item provider gets a
content ID (for example, `rift_key`) and returns an `ItemStack` or `null` if it
does not own that item. Give the add-on a `depend: [TheSift]` entry, register
the service during its enable, and set `integrations.items.provider` to the
provider ID, or leave it `auto` for service discovery. The Sift adds its own
persistent ID after receiving the item, preserving recipe and gameplay checks.
The mob provider receives an ID and location and returns a spawned Bukkit
`LivingEntity` or `null`. Select it with `integrations.mobs.provider`, or leave
it `auto` to discover services before trying built-in integrations. These
SPIs are extension points for content plugins that do not have a built-in
bridge; an add-on can register adapters for supported backends. Service
registration:

```java
getServer().getServicesManager().register(
    SiftItemProvider.class, myProvider, this, ServicePriority.Normal);
getServer().getServicesManager().register(
    SiftMobProvider.class, myMobProvider, this, ServicePriority.Normal);
getServer().getServicesManager().register(
    SiftBlockProvider.class, myBlockProvider, this, ServicePriority.Normal);
```

Install **MythicMobs** if you want editable definitions and stats for the
Sift's natural creatures, Singer, Echo Golem, Rift Walker, and bosses. Copy
`integrations/mythicmobs/Mobs/TheSift.yml` into `plugins/MythicMobs/Mobs/` and
run `/mm reload`. The bridge spawns a configured Mythic mob by its lowercase
Sift ID; missing definitions automatically fall back to the Bukkit entity
prototype.

**ItemsAdder custom entities** are also supported. When ItemsAdder is enabled,
The Sift checks for a custom entity ID `thesift:<sift-id>` before MythicMobs.
Override individual mappings in `integrations.mobs.itemsadder.entities` if
your ItemsAdder entity namespace or IDs differ. The bridge returns its backing
Bukkit entity so The Sift can still apply its encounter tags and behavior.
For example, an ItemsAdder entity named `adventure:rift_beast` can replace
The Sift's Sifter by setting:

```yaml
integrations:
  mobs:
    itemsadder:
      entities:
        the_sifter: adventure:rift_beast
```

**EliteMobs** is also supported for bosses. Put a custom boss file in
`plugins/EliteMobs/custombosses/`, then map its filename in The Sift config.
For example:

```yaml
integrations:
  mobs:
    provider: elitemobs
    elitemobs:
      definitions:
        prism_warden: sift_prism_warden.yml
```

With `auto`, registered `SiftMobProvider`s get first chance, then ItemsAdder,
MythicMobs, and EliteMobs for IDs explicitly mapped in `definitions`.

**ModelEngine** can attach models to spawned Sift entities. Install/import your
ModelEngine model pack, then map IDs such as
`sift:prism_warden` to `integrations.mobs.modelengine.models.prism_warden`.
ModelEngine only supplies rendering; MythicMobs, EliteMobs, or a registered
`SiftMobProvider` supplies the entity and behavior. The repository's current
entity JSON files are not ModelEngine model assets, so they need conversion or
replacement before ModelEngine can display them.

Nexo/ItemsAdder/Oraxen handle custom item definitions and resource packs.
ItemsAdder custom entities and MythicMobs provide custom mob spawns; EliteMobs
can replace configured boss spawns;
ModelEngine supplies rendering only. LevelledMobs, InfernalMobs, and similar
spawn-modifier plugins can act on Bukkit entities spawned by The Sift, subject
to their own world and spawn-reason settings. Other custom-mob systems can be
connected through `SiftMobProvider`; they are not automatically detected just
because they are installed.

Custom block placement is wired into selected generated features, but provider
block definitions are server-configured and still need runtime checks with
each provider installed. Other generated blocks continue to use built-in
fallbacks until their mappings and gameplay interactions are connected.

## The Sift dimension prototype

The Java datapack model was cross-checked against the Fabric reference mod
`The Sift 1.1.2` (Minecraft 26.3), the repository's existing biome and asset
files, the Paper datapack-discovery documentation, and Mojang's announcement.
The plugin supplies its own datapack; it does not load or require the Fabric
mod. The announced Java/Bedrock release has not shipped its official worldgen,
so the current dimension is a Paper simulation of the planned custom-content
experience. External providers can supply richer content than built-in
fallbacks, and the plugin connects them where APIs are available.

The extra biome names, portal melody, Ichor weather, structures, and creature
behaviors are inspired by the MIT-licensed Mielon's The Sift reference project;
they are not confirmed descriptions of Mojang's final 27.1 implementation.
Feature comparisons and deferred problems are tracked in
[IMPLEMENTATION_REPORT.md](./IMPLEMENTATION_REPORT.md).

On Paper 26.3+, use `/sift dimension` to create/load and enter the dimension.
The console command creates/loads it without a player; players can enter with
the same command. Use `/sift return` to return to the Overworld spawn.
`/datapack list enabled` should show `TheSift/dimension`. The prototype uses
the Meadow biome and Overworld terrain settings. This is the current worldgen
implementation, not the intended final 27.1-like custom terrain.

The dimension is stored in the same world save as the Overworld, Nether, and
End. Paper's dimension-aware world storage places it under
`<level-name>/dimensions/thesift/the_sift/`; Bukkit creates/loads this through
the `thesift:the_sift` world key. The plugin should not create a separate
top-level world folder manually.

Mojang has named Meadows and Carapace and described new mobs, hazardous blocks,
and shifting tides. The Java/Bedrock announcement does not publish final
27.1 worldgen, item, mob, or API definitions. This prototype's remaining named
biomes and creatures come from the repository's mod reference and are not
claims about Mojang's final release. Recheck the pack against 27.1 before
claiming compatibility.

## Ichor v2 test flow

1. Use `/sift give ichor_bottle <player>` to obtain a source bottle.
2. Right-click a configured cauldron with an Ichor Bottle to add one charge.
   Each bottle withdrawn consumes the configured number of charges, so this
   exchange cannot multiply Ichor.
3. Hold empty vanilla glass bottles and right-click the Ichor cauldron. Each
   bottle consumes the configured number of Ichor levels.
4. Craft Ichor Glass and Ichor Glass Panes using the registered recipes.
5. Place and break the tagged blocks to verify identity-preserving drops.

The slice uses vanilla block materials as a Paper foundation. Ichor cauldron
levels and placed Ichor block identities are persisted in the plugin data
folder's `ichor-state.yml`; generated ore/resource markers use chunk PDC.
Neither Ichor state is stored in `config.yml`. This avoids NMS and keeps the
feature portable.

## Charoite and Siftite test flow

Use the following commands on a test server:

```text
/sift give charoite <player>
/sift give siftite_nugget <player>
/sift give siftite_ingot <player>
/sift give siftite_sword <player>
/sift give siftite_upgrade_smithing_template <player>
```

Charoite drops only from generated, marked ore in The Sift, not ordinary
Amethyst Blocks. Siftite gear uses Netherite equipment as the smithing base,
plus a tagged Siftite Ingot and custom Siftite Upgrade Template. The custom
template is crafted from a vanilla Netherite Upgrade Template and a Siftite
Ingot. Material-only vanilla recipes reject tagged Siftite ingredients.

## Singer and Resonance test flow

```text
/sift summon singer <player>
/sift give charoite <player>
```

The Singer prototype is a passive Allay tagged with The Sift's persistent
entity ID. It emits a sonic cue and has a boss bar, without damaging nearby
players. The Echo Golem prototype is a passive Iron Golem: an Echo Shard bonds
it to a player, and that player can give or retrieve a Soul Block. Both are
early Paper approximations, not claims about Mojang's final 27.1 behavior.
