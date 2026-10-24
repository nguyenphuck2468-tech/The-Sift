# The Sift provider parity matrix

This matrix records the repository state at commit `c17369e` (2026-10-24).
It audits starter definitions and implemented adapter paths, not whether an
external plugin is installed or has successfully loaded them on a server.
“Full” means the current repository's 72 item IDs are all declared for that
provider; it does not certify asset rights, API compatibility, or future
content. A block item definition is not a custom-block definition.

| Content surface | Nexo | ItemsAdder | Oraxen | MythicMobs | EliteMobs | ModelEngine |
|---|---|---|---|---|---|---|
| Items | có định nghĩa đầy đủ | có định nghĩa đầy đủ | có định nghĩa đầy đủ | chưa | chưa | chưa |
| Placeable custom blocks | chưa | chưa | chưa | chưa | chưa | chưa |
| Custom mob spawning | chưa | có định nghĩa một phần | chưa | có định nghĩa một phần | có định nghĩa một phần | chưa |
| Mob models | chưa | chưa | chưa | chưa | chưa | có định nghĩa một phần |

## Evidence and gaps

- **Items:** `integrations/nexo/the-sift.yml`,
  `integrations/itemsadder/sift-items.yml`, and
  `integrations/oraxen/the-sift.yml` each declare the same 72 IDs. The code
  bridges are in `ExternalItemBridge`; live API resolution is not verified.
  Resource-pack assets include PNG files of unresolved provenance, so the
  definitions do not authorize publishing the pack.
- **Blocks:** the Nexo, ItemsAdder, and Oraxen placement/detection API paths
  exist in `ExternalBlockBridge`, but `integrations.blocks.definitions` is
  empty in the default config and there are no provider-specific custom-block
  definitions. The existing item YAML entries do not make those IDs placeable
  custom blocks. The `SiftBlockProvider` service contract is available for
  third-party adapters.
- **ItemsAdder entities:** `MythicMobBridge` includes a `CustomEntity` path and
  `integrations.mobs.itemsadder.entities` permits ID mapping, but the current
  ItemsAdder YAML has no custom-entity section. This is bridge support, not a
  ready custom-entity pack.
- **MythicMobs:** `integrations/mythicmobs/Mobs/TheSift.yml` contains 22
  prototype entries and is loaded by the MythicMobs bridge for IDs routed
  through `SiftMobFactory`. Some encounter managers still spawn their own
  Bukkit entity directly and bypass that provider path; full mob parity is
  therefore not complete.
- **EliteMobs:** the bridge can spawn configured custom bosses and the config
  provides a definitions map, but the default definitions map is empty. It is
  not a full wildlife backend.
- **ModelEngine:** `ModelEngineBridge` can attach a configured model ID to
  provider-spawned entities; the default model map is empty. It supplies models,
  not mob spawning or AI.

## Proposed primary providers (not yet approved)

Recommend **ItemsAdder** as the primary item and block backend: the item bridge
and all 72 current item IDs already exist, the plugin has custom-block API
paths, and the established `thesift:<id>` namespace matches current config and
starter data. The significant remaining work is a real block-definition pack,
strict per-ID validation, and live API testing. Nexo and Oraxen retain their
current 72-item definitions and adapters; their block-definition parity is
also still absent.

Recommend **MythicMobs** as the primary mob backend: it already has 22
prototype definitions and a direct configured-spawn path for ecology IDs.
It still needs ID-by-ID coverage, encounter-manager routing, Mythic skill/AI
polish, and server validation. Keep ModelEngine optional for visual models;
its absence should warn in `/sift doctor`, but must not fail strict readiness.

No primary provider has been selected by the owner. Do not create the large
primary-provider block/entity definitions until that decision is confirmed.
