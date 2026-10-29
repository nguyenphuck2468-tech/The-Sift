# The Sift — fan-made Paper plugin

The Sift is a fan-made implementation inspired by the dimension announced at
Minecraft Live 2026. It targets **Paper 26.3**, Java 25, and Paper API
`26.3.build.218-stable`; it is not affiliated with Mojang or Microsoft.
Custom-content providers supply items, blocks, and mobs where the Paper API
does not provide those game concepts directly. The current implementation
status and stand-ins are tracked in [`docs/parity-matrix.md`](docs/parity-matrix.md).

## Build

```bash
./gradlew clean build
```

The `siftResourcePack` task packages PNG files whose provenance has not been
verified. Do not publish or redistribute `TheSift-resource-pack.zip` until the
asset sources and usage rights are confirmed.

The default compile dependency is Paper API `26.3.build.218-stable`. Override
it when validating another Paper API version:

```bash
gradle build -PpaperApiVersion=<paper-api-version>
```

The plugin registers its dimension datapack through Paper's datapack-discovery
lifecycle. This requires Paper's plugin loader and Paper 26.3 or newer. The
dimension generates 15 biomes: the ten from the community mod reference
(Overgrown Forest/Clearing/Slopes/Peaks, Siftslate biomes, Ichor Snowy Peaks,
Sift Wastes, Sift Deep Dark) plus five fan-made ones, over the mod's ported
noise router with the three ported carvers wired into every biome. It has
custom sky, fog, and ambient settings; custom blocks use vanilla stand-ins
where the relevant provider or implementation is absent.
This is a Paper 26.3 implementation; provider parsing and live-server behavior
remain unverified until recorded in the parity matrix.

The progression datapack (`src/main/resources/datapack`) supplies advancements
only; biome and worldgen definitions are owned by the separate dimension
datapack. The old duplicate progression biome/worldgen files were removed
because their feature registry entries did not load on Paper 26.3. The removed
generation features remain incomplete; see `docs/parity-matrix.md`.

### Configuration keys awaiting implementation

These keys are retained for compatibility. The comments beside them in
`src/main/resources/config.yml` mark the exact keys that are presently
informational or have no gameplay effect; do not treat those values as working
controls. This includes `content.siftslate`, `content.healthy_sculk`,
`content.overgrown_willow`,
`echo-golem.resonance-damage`,
`echo-golem.slowness-seconds`, `echo-golem.slowness-amplifier`,
`echo-golem.mining-fatigue-seconds`,
`echo-golem.mining-fatigue-amplifier`,
`charoite.material`, `siftite.ingot-material`, `siftite.nugget-material`,
`siftite.template-material`, and `siftite.smithing-only`. Existing config
files keep these keys; they have not been removed or migrated.

Keys activated in the latest parity pass (previously informational):
`content.blub` and `content.sifter` now gate Sift ecology spawning and the
Blub Soul Block taming interaction; `content.siftite_spear` gates recipes for
the spear; `siftite.durability-multiplier` adds bonus durability to freshly
crafted Siftite tools/weapons; `siftite.spear-reach` sets the thrown-spear
reach; and `echo-golem.charoite-protection` extends resonance healing to
Charoite holders.

### Advancement coverage

The bundled progression datapack mirrors the reference mod's 22 advancements (`root`, 3 under `story/`, 18
under the main tab), ported from its MIT-licensed source with the mod's own titles and descriptions. Biome and
Ancient City visits use vanilla triggers; the rest are awarded by gameplay events.

Wired: `root`, `story/brave_the_unknown`, `story/fallen_civilization`, `story/song_of_the_past`, `rifter`,
`rift_resonance`, `blood_of_the_gods` (approximated by Ichor bottles), `certified_sifter`, `kinda_purpleish`,
`sifted_nugget`, `cover_me_in_siftite`, `keep_inventory`, `better_enchanter`, `pitch_perfect`.

Not wired yet because the mechanic does not exist in the plugin: `like_father_and_son`, `friend_of_the_sift`,
`food_chain`, `false_alarm`, `smells_creepy`, `planting_the_dark`, `spicewood` (and `blub`, which depends on
the Blub bond being awarded from the taming code). Earlier plugin-only advancements (Prismatic, Matriarch,
Tyrant, ...) were removed because they have no counterpart in the mod. See `docs/mod-gap-analysis.md`.

### Spawn eggs (mod parity)

The reference mod ships a spawn egg for every Sift creature. The plugin maps
each creature content id onto a vanilla stand-in egg material (e.g.
`dark_sniffer` → Sniffer egg, `spire_tyrant` → Ravager egg) and a listener in
the ecology manager intercepts right-clicks with tagged eggs: the vanilla
spawn is cancelled and the creature is summoned through the normal provider-
first `summonCreature` path (config stats, PDC tagging, Singer/Echo Golem
special handling). Untagged vanilla eggs are untouched. Behaviour is gated by
`spawn-eggs.enabled` and `spawn-eggs.permission-mode` (`op` or `all`) in
`config.yml`. Custom-textured egg items still require the resource pack and
are tracked as unverified in `docs/parity-matrix.md`.

## Current foundation scope

- Gradle/Java 25 build.
- Versioned plugin configuration (`config-version: 4`).
- Refusal to rewrite configs newer than this build.
- Registry for Siftslate, Ichor, Charoite, Siftite, Resonance, Rift content,
  and the current mob stand-ins.
- Ichor v2: persistent cauldron levels, refill/bottle costs, tagged Ichor
  Glass/Panes and Ichor Snow blocks, recipes, particles, and sounds.
- Charoite and Siftite progression: tagged ore drops, ingot/nugget recipes,
  smithing upgrades for armor/tools, and Siftite item diagnostics.
- Singer resonance slice: persistent Singer-tagged Allay prototype, timed
  sonic pulses with configurable damage/slowness, carried Charoite immunity,
  boss bars, drops, and summon diagnostics.
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
- Food chain parity with the reference mod: Sifters drop Raw Sifter Meat
  (smelts to Cooked Sifter Meat) and Blubs drop Blub Meat (smelts to Cooked
  Blub Meat); both furnace recipes are registered by the plugin itself. The
  bundled progression datapack now also ships matching loot tables
  (`thesift:loot_table/entity/sifter|blub`), and all plugin-owned recipes respect `recipes.enabled` in
  config.yml and are unregistered on disable/reload so no ghost recipes
  survive a hot swap.
- Rare natural Rift events in Shattered Peaks, using the existing opening,
  wave, resonance-pulse, closing, and reward flow.
- `/sift summon <species> [player]` for direct prototype spawns, including
  Blub, Bulb, Koi, Thornback, The Sifter, Prism Warden, and Lumen Tulip.
- A cycling Sift Tide that changes movement/mining effects for players and
  nearby creatures.
- Sonorous Deepslate portal station: place the tagged block, put a vanilla
  note block above it, right-click to tune, then left-click notes in order
  `1, 3, 7, 6, 5, 2, 4, 8` to open a Rift. Other note blocks and provider-owned
  note blocks are not intercepted. Provider parsing and live server behavior
  remain unverified.
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

Provider parity and definition coverage are tracked in
`docs/parity-matrix.md`. `providers.strict` defaults to `true`; dimension
creation and entry are blocked until a selected item, block, and mob backend
each resolves every required ID. Set it to `false` only for development/CI,
where vanilla fallback remains available. `/sift doctor` reports the installed
backends, versions, selected providers, resolved/missing IDs, and setup hints.

The canonical content definition is `integrations/content-spec.json`. Generate
provider configs with `./gradlew generateProviderDefinitions`; install the
outputs under `build/generated/provider-definitions/`, as described in
`docs/TESTING.md`. Do not treat the older hand-maintained YAML snapshots under
`integrations/{nexo,itemsadder,oraxen,mythicmobs}/` as canonical. The generator
currently emits item/block definitions for Nexo, ItemsAdder, and Oraxen, and
mob definitions for MythicMobs. It does not emit ItemsAdder custom-entity or
EliteMobs boss definitions. No generated file has yet been parsed by its real
provider or tested on a real server; see `docs/parity-matrix.md`.

With `auto`, item/block backends are tried in this order: registered Sift
services, Nexo, ItemsAdder, Oraxen. Mob order is registered service,
ItemsAdder entity, MythicMobs, EliteMobs. Set each
`integrations.{items,blocks,mobs}.provider` to override selection. Other
plugins remain optional soft dependencies. Strict mode checks every mandatory
ID before allowing dimension access; it preserves existing world data when a
provider disappears. `/sift return` remains an emergency exit.

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
When strict mode is enabled, a missing provider block is a readiness failure;
fallback placement is only available with `providers.strict: false`.

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

Custom block placement is wired into selected generated features. Provider
definitions are generated from the canonical spec, but API parsing and live
runtime behavior still need checks with each provider installed. The parity
matrix records these evidence levels separately.

## The Sift dimension

The plugin supplies its own datapack and does not load or require the Fabric
reference mod. Some biome names, the portal melody, Ichor weather, structures,
and creature behaviors are inspired by the MIT-licensed community project
[Mielon's The Sift](https://github.com/Arta48/Mielons-The-Sift). Inspiration
does not imply affiliation with that project, Mojang, or Microsoft. Feature
comparisons and unverified provider behavior are tracked in
[`IMPLEMENTATION_REPORT.md`](IMPLEMENTATION_REPORT.md) and
[`docs/parity-matrix.md`](docs/parity-matrix.md).

On Paper 26.3, use `/sift dimension` to create/load and enter the dimension.
The console command creates/loads it without a player; players can enter with
the same command. Use `/sift return` to return to the Overworld spawn.
`/datapack list enabled` should show `TheSift/dimension`. Current worldgen
uses the mod's multi-noise biome source over the ported noise settings (vanilla stand-in blocks; see docs/parity-matrix.md).

The dimension is stored in the same world save as the Overworld, Nether, and
End. Paper's dimension-aware world storage places it under
`<level-name>/dimensions/thesift/the_sift/`; Bukkit creates/loads this through
the `thesift:the_sift` world key. The plugin should not create a separate
top-level world folder manually.

The Sift is an independent fan project and does not claim affiliation with or
endorsement by Mojang or Microsoft.

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

The Singer currently uses an Allay tagged with The Sift's persistent entity ID.
Its pulse emits a sonic cue, deals 1 health point (half a heart) by default,
and applies Slowness I for 3 seconds. Set `resonance.singer.attack-enabled: false`
to disable both damage and slowness; tune `damage`, `slowness-seconds`,
`slowness-amplifier`, and `pulse-seconds` under `resonance.singer`. Charoite
protection applies while a player carries a tagged Charoite in their personal
inventory/hotbar, offhand, or armor slots; ender-chest contents do not count.
Creative and spectator players are unaffected. The Echo Golem currently uses
a passive Iron Golem: an Echo Shard bonds it to a player, and that player can
give or retrieve a Soul Block. These behaviors have unit-test coverage but
still need a live Paper 26.3 smoke test with the selected providers.
