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

With `providers.strict: false`, vanilla fallback remains available for development/CI and a startup warning is logged. Bedrock support is optional and controlled by `providers.bedrock-enabled`.

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
