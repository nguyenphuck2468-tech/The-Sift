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
| Custom mob spawning | — | Detected (spawn path; readiness not safely probeable) | — | Detected | Detected + Generated | Detected (file mapping only) | — | — | — | — | — | — | — |
| Mob rendering | — | — | — | — | — | — | Detected (attach path) | — | — | — | — | — | — |
| Bedrock level 1 detection | — | — | — | — | — | — | — | Detected | Detected | Detected | Detected | Detected | Detected |
| Bedrock level 2 generated pack / Geyser mappings | **MISSING** | **MISSING** | **MISSING** | — | — | — | — | **MISSING** | **MISSING** | — | — | — | — |

`Detected` for optional plugins is based on the declared plugin name and reflective/service path in the code, not proof of API compatibility. `ResourcePackManager` is currently detection and doctor reporting only. Its public Java API supports pack registration, but integration is deferred; no API calls or telemetry are used. Geyser, Floodgate, ViaVersion, ViaBackwards, ViaRewind, and ResourcePackManager never gate dimension readiness.

Bedrock level 2 (pack generation, custom mappings, and end-to-end Geyser validation) is estimated **high / roughly 1–3 engineering weeks**, assuming usable asset provenance and a test server with Geyser + ResourcePackManager. This is a planning estimate, not a commitment.

## Content source and generation

- Canonical definitions: [`integrations/content-spec.json`](../integrations/content-spec.json).
- Nexo stable block variations: [`integrations/nexo/block-variants.yml`](../integrations/nexo/block-variants.yml). The generator rejects duplicated IDs/variations, out-of-range values, and missing mappings. The allocated variation range may still conflict with unrelated Nexo content on a user's server; there is no safe public API collision probe implemented yet.
- Run `./gradlew generateProviderDefinitions`. Outputs go under `build/generated/provider-definitions/` and are not bundled in the plugin jar.
- The generator emits Nexo, ItemsAdder, and Oraxen item/block YAML, plus MythicMobs mob YAML. It does not generate ItemsAdder custom entities or EliteMobs boss files because the canonical spec currently has no verified target schema for those formats. Those cells remain partial/missing until a tested definition can be produced.
- Generated item/block models refer to `thesift` model paths. This repository's PNG provenance is unresolved; do not publish a resource-pack ZIP or ship those textures until provenance is cleared.
- `ichor_water` is a simulated block placement and does not implement a flowing custom fluid. Record it as a **STAND-IN** in gameplay parity until a provider can represent the required fluid behavior.

## Strict readiness

With `providers.strict: true`, readiness requires at least one selected backend resolving **every** mandatory item ID, at least one selected backend resolving **every** mandatory block ID, and at least one selected mob backend resolving **every** mandatory mob ID. The canonical lists are in `integrations/content-spec.json`; Gradle generates the runtime lists in `ProviderContentIds`. Some APIs (notably ItemsAdder custom entity and EliteMobs bosses) do not yet have a verified side-effect-free readiness probe, so doctor reports those IDs unresolved rather than spawning test mobs. The dimension and its existing save remain untouched when readiness fails.

With `providers.strict: false`, vanilla fallback remains available for development/CI and a startup warning is logged. Bedrock support is optional and controlled by `providers.bedrock-enabled`.

## Public references used

- Nexo custom blocks and stable `custom_variation` range: [NoteBlock Mechanic](https://docs.nexomc.com/mechanics/custom-block-mechanics/noteblock-mechanic), [Custom Block Mechanics](https://docs.nexomc.com/mechanics/custom-block-mechanics).
- ItemsAdder item/entity/block APIs and definitions: [ItemsAdder Java API examples](https://github.com/LoneDev6/Wiki-ItemsAdder/blob/master/developers/java-api/examples.md), [My First Block](https://wiki.itemsadder.com/plugin-usage/beginners/my-first-block/).
- Oraxen custom block API and item mechanics: [Oraxen API](https://docs.oraxen.com/developers/api), [Oraxen items](https://docs.oraxen.com/configuration/items).
- MythicMobs uses `MythicMobs` plugin name; source descriptor: [plugin.yml](https://github.com/Magmaguysv/MythicMobs/blob/master/dist/plugin.yml) (if unavailable in a release branch, verify the installed descriptor before setup).
- EliteMobs plugin name: [public plugin.yml](https://github.com/MagmaGuy/EliteMobs/blob/master/src/main/resources/plugin.yml).
- ResourcePackManager plugin name: [public plugin.yml](https://github.com/MagmaGuy/ResourcePackManager/blob/master/resourcepackmanager-bukkit/src/main/resources/plugin.yml).
- ResourcePackManager's public Java API allows programmatic resource-pack registration: [API reference](https://wiki.nightbreak.io/ResourcePackManager/api/). The Sift only detects it for now.
- Geyser's current supported Java server version and ViaVersion condition: [setup](https://geysermc.org/wiki/geyser/setup/), [supported versions](https://geysermc.org/wiki/geyser/supported-versions/).
- Floodgate installation and auth use: [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/).
- Plugin names and public evidence notes: [`docs/provider-sources.md`](provider-sources.md). Bukkit softdepend names are in `src/main/resources/plugin.yml`; Paper's optional load order is in `src/main/resources/paper-plugin.yml`.
