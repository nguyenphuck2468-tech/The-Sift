# The Sift provider parity matrix

## Status scale

Each cell lists the highest verified level reached:

1. **Detected**: runtime can identify the optional plugin/service by name.
2. **Generated**: `generateProviderDefinitions` emits definitions from `integrations/content-spec.json`.
3. **Parseable**: that provider has successfully parsed the generated definitions.
4. **Live checked**: behavior was exercised on a real Paper 26.3 server with that plugin installed.

No plugin in this matrix has reached **Live checked** in this repository. `Generated` describes repository generation only; it does not mean a provider accepted or loaded the files.

| Content | Nexo | ItemsAdder | Oraxen | Sift service | MythicMobs | EliteMobs | ModelEngine | ResourcePackManager | Geyser | Floodgate | ViaVersion | ViaBackwards | ViaRewind |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Item lookup / creation | Detected + Generated | Detected + Generated | Detected + Generated | Detected | — | — | — | — | — | — | — | — | — |
| Placeable blocks | Detected + Generated | Detected + Generated | Detected + Generated | Detected | — | — | — | — | — | — | — | — | — |
| Custom mob spawning | — | Detected (registry probe + spawn path; definitions not generated) | — | Detected | Detected + Generated | Detected (file mapping only) | — | — | — | — | — | — | — |
| Mob rendering | — | — | — | — | — | — | Detected (attach path) | — | — | — | — | — | — |
| Bedrock level 1 detection | — | — | — | — | — | — | — | Detected | Detected | Detected | Detected | Detected | Detected |
| Bedrock level 2 generated pack / Geyser mappings | **MISSING** | **MISSING** | **MISSING** | — | — | — | — | **MISSING** | **MISSING** | — | — | — | — |

`Detected` for optional plugins is based on the declared plugin name and reflective/service path in the code, not proof of API compatibility. `ResourcePackManager` is currently detection and doctor reporting only. Its public Java API supports pack registration, but integration is deferred; no API calls or telemetry are used. Geyser, Floodgate, ViaVersion, ViaBackwards, ViaRewind, and ResourcePackManager never gate dimension readiness.

Bedrock level 2 (pack generation, custom mappings, and end-to-end Geyser validation) is estimated **high / roughly 1–3 engineering weeks**, assuming usable asset provenance and a test server with Geyser + ResourcePackManager. This is a planning estimate, not a commitment. Geyser documentation checked 2026-10-24 lists Java 26.3 support; Skilleed's product page (updated 2026-10-09) advises ViaVersion. This plugin has not been tested on a server with Geyser, either with or without ViaVersion.

## Implemented feature slices

| Slice | In-repository implementation | Generated definitions | Test evidence | Provider parsing / real server |
|---|---|---|---|---|
| 1: Soul Block | `soul_block` item + block IDs; placement tracking, drops, and Echo Golem interaction. Singer and Echo Golem remain vanilla entity stand-ins. | Nexo, ItemsAdder, and Oraxen item/block YAML; MythicMobs mob YAML for Singer/Echo Golem. ItemsAdder entity and EliteMobs files are not generated. | Unit test suite; exact generated ID membership test. | Not verified on provider or Paper server. |
| 2: Sonorous Deepslate melody (Ancient City gate: implemented, untested) | `sonorous_deepslate` item/block ID, placed block tagging, and melody guard. Ordinary note blocks remain untouched; provider-owned note blocks are not intercepted. Natural Rifts also open inside The Sift every few minutes (`rift.natural.*`), in addition to the chunk-based shattered-peaks rifts. A Goat Horn used in the Deep Dark wakes a Singer (`resonance.horn-wakes-singer`). In an Ancient City, the same horn scans for the Reinforced Deepslate frame and places eight Sonorous Deepslate blocks in front of it (`ancient-city.*`); completing the melody on Note Blocks above them opens a long-lived portal Rift at the frame (`rift.portal-song-opens-portal`, `rift.portal-active-seconds`; no mob waves or rewards) with light beams. The frame is detected by block scan, not by structure API, and has not been run against a real Ancient City. Successful melodies now have a server-wide cooldown. | Nexo, ItemsAdder, and Oraxen item/block YAML. | Fixture tests cover note-block ownership; tracker tests cover cooldown, idle timeout, and player cleanup. | Not verified on provider or Paper server. |
| 3: Mob food chain (Sifter / Blub meat) | `raw_sifter_meat` → `cooked_sifter_meat` and `blub_meat` → `cooked_blub_meat` IDs resolve through `SiftiteContent.forId()` (vanilla stand-ins BEEF/COOKED_BEEF, MUTTON/COOKED_MUTTON). Sifter and Blub deaths drop the raw meat; `registerFurnish()` installs two furnace recipes (`smelt_raw_sifter_meat`, `smelt_blub_meat`) that are remove-then-add so reloads are safe. | Not generated (vanilla stand-in items only). | `MeatFoodChainTest` (ID resolution + recipe registration/re-register); `NewContentTest` drop coverage; `DatapackIntegrityTest.progressionPackShipsLootTablesReferencedByTheFoodChain`. | Not verified on provider or Paper server. |
| 4: Spire Tyrant boss variant (advancement parity) | The bundled `thesift:tyrant` advancement ("Crimson Conqueror") was unobtainable because no creature carried a matching ID. Ecology chunk decoration in shattered/siftslate peaks now spawns a crimson-named **Spire Tyrant** (ravager stand-in, same 220 HP / 24 dmg statline as The Sifter) ~50% of the time when the Sifter boss roll hits, gated by `content.spire_tyrant` (default true). Killing it grants `tyrant` (The Sifter kills also grant it), drops one `spire_fragment` (two from the Tyrant) alongside the usual Heart/Sift Shard boss loot, and `/sift summon spire_tyrant` is tab-completable with a spawn-egg item ID. Caveat: the mod's Crimson Keep structure is not ported, so the advancement's location text stays aspirational. | Not generated (stand-in mob + item). | `SiftAdvancementManagerTest.killingSpireTyrantRequestsTyrantAdvancement`; updated `killingTheSifterDoesNotRequestOtherBossAdvancements`. | Not verified on provider or Paper server. |
| 5: Creature spawn eggs (mod parity) | The mod ships one spawn egg per creature. Every Sift creature id now maps to a vanilla stand-in egg material in `SiftiteContent.forId()` (echo_golem→Iron Golem, singer→Allay, dark_sniffer→Sniffer, spire_tyrant/the_sifter→Ravager, rift_walker→Enderman, prism_warden→Warden, …). `SiftEcologyManager.onSpawnEggUse` intercepts right-click-block with tagged eggs, cancels the vanilla spawn, and routes through `summonCreature` (provider-first, config stats, PDC tagging, Singer/Echo Golem special cases); untagged vanilla eggs are untouched. Gated by new `spawn-eggs.enabled` / `spawn-eggs.permission-mode` keys in `config.yml`. | Not generated (vanilla stand-in egg materials; custom textures need the resource pack). | Egg-id mapping covered indirectly by content-registry tests; listener behaviour not yet unit-tested (needs MockBukkit interact events). | Not verified on provider or Paper server. |

Useful smoke commands: `/sift give soul_block <player>`, `/sift summon singer <player>`, `/sift summon echo_golem <player>`, `/sift give sonorous_deepslate <player>`. Place the last item and a normal note block above it; tune by right-click and play `1, 3, 7, 6, 5, 2, 4, 8` by left-clicking to open a Rift. See `docs/TESTING.md` for the Paper 26.3 and Bedrock procedure.

## Content source and generation

- Canonical definitions: [`integrations/content-spec.json`](../integrations/content-spec.json).
- Nexo stable block variations: [`integrations/nexo/block-variants.yml`](../integrations/nexo/block-variants.yml). The generator rejects duplicated IDs/variations, out-of-range values, and missing mappings. The allocated variation range may still conflict with unrelated Nexo content on a user's server; there is no safe public API collision probe implemented yet.
- Run `./gradlew generateProviderDefinitions`. Outputs go under `build/generated/provider-definitions/` and are not bundled in the plugin jar.
- The generator emits Nexo, ItemsAdder, and Oraxen item/block YAML, plus MythicMobs mob YAML. It does not generate ItemsAdder custom entities or EliteMobs boss files because the canonical spec currently has no verified target schema for those formats. Those cells remain partial/missing until a tested definition can be produced.
- Generated item/block models refer to `thesift` model paths. This repository's PNG provenance is unresolved; do not publish a resource-pack ZIP or ship those textures until provenance is cleared.
- `ichor_water` is a simulated block placement and does not implement a flowing custom fluid. Record it as a **STAND-IN** in gameplay parity until a provider can represent the required fluid behavior.

## Strict readiness

With `providers.strict: true`, readiness requires at least one selected backend resolving **every** mandatory item ID, at least one selected backend resolving **every** mandatory block ID, and at least one selected mob backend resolving **every** mandatory mob ID. The canonical lists are in `integrations/content-spec.json`; Gradle generates the runtime lists in `ProviderContentIds`. ItemsAdder custom entity readiness uses the documented registry probe, while EliteMobs has no verified side-effect-free registry probe and remains unresolved. The dimension and its existing save remain untouched when readiness fails.

`providers.strict` is now always enforced (a `false` value is ignored with a warning), so there is no vanilla fallback. A `requirements` section in `config.yml` (defaults: ModelEngine required; one plugin each for items, blocks, mobs) is checked on the first server tick, and The Sift disables itself and logs what is missing if any rule fails. Bedrock support is optional and controlled by `providers.bedrock-enabled`.

## Public references used

- Nexo custom blocks and stable `custom_variation` range: [NoteBlock Mechanic](https://docs.nexomc.com/mechanics/custom-block-mechanics/noteblock-mechanic), [Custom Block Mechanics](https://docs.nexomc.com/mechanics/custom-block-mechanics).
- ItemsAdder item/entity/block APIs and definitions: [ItemsAdder Java API examples](https://wiki.itemsadder.com/developers/java-api/examples/), [CustomEntity API including `isInRegistry`](https://lonedev6.github.io/API-ItemsAdder/dev/lone/itemsadder/api/CustomEntity.html), [My First Block](https://wiki.itemsadder.com/plugin-usage/beginners/my-first-block/).
- Oraxen custom block API and item mechanics: [Oraxen API](https://docs.oraxen.com/developers/api), [Oraxen items](https://docs.oraxen.com/configuration/items).
- MythicMobs uses `MythicMobs` plugin name; source descriptor: [plugin.yml](https://github.com/Magmaguysv/MythicMobs/blob/master/dist/plugin.yml) (if unavailable in a release branch, verify the installed descriptor before setup).
- EliteMobs plugin name: [public plugin.yml](https://github.com/MagmaGuy/EliteMobs/blob/master/src/main/resources/plugin.yml).
- ResourcePackManager plugin name: [public plugin.yml](https://github.com/MagmaGuy/ResourcePackManager/blob/master/resourcepackmanager-bukkit/src/main/resources/plugin.yml).
- ResourcePackManager's public Java API allows programmatic resource-pack registration: [API reference](https://wiki.nightbreak.io/ResourcePackManager/api/). The Sift only detects it for now.
- Geyser's current supported Java server version and ViaVersion condition: [setup](https://geysermc.org/wiki/geyser/setup/), [supported versions](https://geysermc.org/wiki/geyser/supported-versions/).
- Floodgate installation and auth use: [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/).
- Plugin names and public evidence notes: [`docs/provider-sources.md`](provider-sources.md). Bukkit softdepend names are in `src/main/resources/plugin.yml`; Paper's optional load order is in `src/main/resources/paper-plugin.yml`.

## Strict-mode parity gaps confirmed by static audit

The 15 bundled biome definitions currently have empty `features` and `carvers`; Paper chunk generation has not been run. The strict Java decoration path also does not generate the following mod-referenced content:

| Content | Source label | Strict-mode status |
|---|---|---|
| Abandoned portals and their loot chests | [mod] | Not generated; provider-backed structure and loot definition are missing. |
| Sculk formations and Soul Canyon where Echo Golems find Soul Blocks | [mod] | Not generated; required structure/feature and provider identities are missing. |
| Ichor springs/lakes | [mod] | Not generated in strict mode. `ichor_water` is a non-strict simulated block, not a flowing custom fluid: **STAND-IN**. |
| Biome carvers/features | [mod] | All 15 biome JSON arrays are empty; terrain carve and placed-feature parity is not implemented. |

This is a static repository audit, not a generated-chunk observation. No worldgen definitions were added in Stage 0.

## Progression-pack worldgen removal (Paper 26.3 boot repair)

The progression pack no longer defines biomes or worldgen features. Its four duplicate biome files and legacy feature files were removed from `data/` and `overlay_26_3/`; advancement JSON is retained. The dimension pack is now the sole owner of the four matching biome IDs. These removed generation features are explicitly **chưa làm**; they did not load on Paper 26.3 and were not silently promoted to implemented content.

| Removed progression feature/reference | Status | Reason / future parity path |
|---|---|---|
| `bulb_forest_geode`, `bulb_forest_healthy_sculk`, `bulb_forest_sculk_patch` | Chưa làm | Legacy configured-feature layout/schema did not bind on Paper 26.3. Reintroduce under `worldgen/feature/` with valid 26.3 definitions, a matching `worldgen/placed_feature/`, and provider-backed blocks in strict mode. |
| `crystal_spires_geode`, `resonance_crystal` | Chưa làm | Legacy configured-feature references were unbound. Reintroduce only after the same 26.3 schema and provider checks pass. |
| `echo_golem_spawn` / `crystal_spires_echo_golem_spawn` | Chưa làm | The old `minecraft:entity` feature type is not present in the Paper 26.3 feature registry report; mob placement needs a supported provider-backed path. |
| `pink_meadows_geode` | Chưa làm | Its placed-feature JSON referenced a configured feature that was absent, causing an unbound registry ID. |
| `pink_meadows` references to `sculk_patch`, `healthy_sculk`, `resonance_crystal`, `singer_spawn` | Chưa làm | Missing/legacy placed-feature graph removed with the progression worldgen tree. |
| `shattered_peaks` references to `geode`, `resonance_crystal`, `rift_structure`, `echo_golem_spawn` | Chưa làm | Missing/legacy placed-feature graph removed with the progression worldgen tree. |
| All 15 dimension-pack biome feature/carver arrays | Chưa làm | Currently empty. Worldgen content must be added to the dimension pack and validated by JSON graph tests and the Paper 26.3 boot gate. |

Any future worldgen work must use the 26.3 `worldgen/feature/` schema (not legacy `configured_feature/`), provide complete biome → placed feature → feature references, and avoid vanilla Sift stand-ins while `providers.strict: true`.


## Reference-mod parity audit (Mielon's The Sift, checked against the public CraftMC/CurseForge description)

Already present in the repository before this branch: Singer, Echo Golem, Blub, Sifter, Dark Sniffer, Ichor storm/snow weather (`sift-world.ichor-weather-period-seconds`), Siftite and Charoite systems, Mini Rift item recovery, abandoned portals with loot chests (including the Rift disc), Ichor springs, 1:1 coordinate scale.

Added on the `mielon-parity` branch: Goat Horn wakes a Singer; Ancient City frame + eight Sonorous Deepslate + beams; portal-mode Rift; timed natural Rifts in The Sift; 10 more advancements (20 total, matching the mod's count).

Still different or missing: Overgrown Willow wood set (cherry stand-in), sculk plants, Ichor as a real flowing fluid (**STAND-IN**), biome worldgen features/carvers in strict mode (all biome arrays empty), 10 vs 15 biomes, music-disc audio (needs a resource pack), Bedrock level 2.


## Terrain port from the mod (mielon-parity)

| Area | Status |
|---|---|
| Noise router, 21 density functions, 15 noises, `canyon`/`cave`/`cave_extra_underground` carvers, `the_sift` noise settings | **Ported** from the mod datapack by `scripts/port_mod_terrain.py` (namespace rewritten). Reference closure checked by `scripts/check_terrain_refs.py` (also run in CI). **CHƯA CHẠY** on a real Paper 26.3 server. |
| Dimension biome source | Replaced by the mod's 10-biome multi-noise table. The five prototype biomes stay registered but no longer generate. |
| Dimension type | `min_y 0`, `height 256`, `logical_height 256` to match the mod's density functions (previously -64/384). **Existing `the_sift` worlds must be regenerated.** |
| Terrain blocks | **STAND-IN**: `siftslate`→`deepslate`, `siftslate_growth`→`moss_block`, `healthy_sculk`→`sculk`, `dry_healthy_sculk`→`pale_moss_block`. A datapack cannot place provider blocks. |
| Biome carvers | The ten mod-named biomes now list the three mod carvers. |
| Natural-spawn hooks | Singer (`overgrown_clearing`), Echo Golem (`siftslate_slopes`), Prism Warden (`overgrown_forest`), natural Rifts/The Sifter (`siftslate_peaks`), Ichor springs (`sift_wastes`) re-keyed from the prototype biomes. Compile and live behaviour **CHƯA CHẠY**. |
| Biome `features` arrays, structures, lakes, willow trees, sculk and flora features | Still **chưa làm** (empty). |
| Siftslate ores (coal, coal surface, diamond, emerald) | **Ported** by `scripts/port_mod_ores.py` with the mod's size/count/height/filters. **STAND-IN**: vanilla `deepslate_*_ore` replacing deepslate, in step 6 of all ten mod biomes. Charoite and Siftite ore remain Java/provider generated. |

## Port from the mod source (mielons-the-sift-neoforge-port, MIT, (c) 2026 Mielon)

Compared against the NeoForge port source, these behaviours now follow the mod:

- **Frame:** `PortalFrameScanner` algorithm (anchor walk, rectangular Reinforced Deepslate frame of at least 3x3, air-like interior), 40-block scan radius.
- **Goat Horn:** after a 3 second delay a Singer appears at the frame's bottom centre (not if one is within 2 blocks) and awakens eight Sonorous Deepslate blocks in front of the frame.
- **Melody:** per-console sliding window of the last eight notes (no reset on a wrong note), opening 1,3,7,6,5,2,4,8 and closing = reverse; no cooldown between songs. Timeline: burst after 0.5 s, automatic replay after 1 s more, then the frame opens. The portal is permanent until the closing melody is played (it is not persisted across restarts yet).
- **Singer:** peaceful and invulnerable, trades Soul Blocks for the mod's 200-roll reward table (`SingerBarter`). The Wellspring disc is a vanilla disc stand-in. The old damaging pulse remains available as opt-in (`resonance.singer.attack-enabled`).

Not ported: Singer soul-event phases (appear/roam/disappear), HORN/NOTE block modes (Paper has no block states for provider blocks), custom `SIFT_PORTAL` block (a gate bounding box with particles is used), GeckoLib animations, shader/sky rendering, mixins.

Attribution: logic is re-implemented from the mod's MIT-licensed source; see its LICENSE and SOURCE_PROVENANCE.md (ImpSteve, Fuzja Jądrowa and others credited for individual assets).
