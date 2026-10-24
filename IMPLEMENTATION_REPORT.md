# The Sift expansion pass — implementation report

Updated: 2026-10-24  
Build baseline: Java 25, Paper API `26.3.build.218-stable`  
Target: Minecraft 27.1 is planned, not released or verified.

## Custom content provider integration (2026-10-24)

- Added reflection-based Nexo/ItemsAdder/Oraxen item bridges, preserving The
  Sift's persistent content tags and keeping provider APIs optional at compile time.
- Added a public Bukkit `SiftItemProvider` service interface so third-party
  item plugins can register adapters without The Sift depending on their APIs,
  plus a `SiftMobProvider` service interface for other creature plugins.
- Added provider selection (`auto`, `nexo`, `itemsadder`, `oraxen`, `vanilla`,
  or a registered service ID), optional
  load ordering, and a delayed recipe refresh for Nexo's asynchronous registry.
- Added Nexo, ItemsAdder, and Oraxen item definitions and import instructions.
- Added a reflection-based MythicMobs spawn bridge. Configured IDs are used for
  natural Sift wildlife and summon commands; missing IDs retain the Bukkit
  entity fallback. Added starter definitions for the named creatures.
- Added an EliteMobs custom-boss API bridge configured by Sift ID and a
  ModelEngine API bridge that attaches server-provided model IDs to spawned
  entities. Both remain optional and require user-provided plugin configs or
  compatible model assets.
- Added `SiftMobProvider` so other mob plugins can provide entities without a
  direct dependency. The current entity JSON assets are not ModelEngine or
  Blockbench model files and are not claimed to render as animated mobs.
- Routed the Dark Sniffer cave spawn through the same mob integration factory
  as natural spawns, commands, Singer, Echo Golem, and Rift Walker.
- Expanded all three item-provider starter configs to cover the plugin's Sift
  item IDs, and added wrapper models so assets with no unique art retain their
  configured vanilla appearance.
- Added direct ItemsAdder custom-entity spawning before MythicMobs, with
  per-creature ID overrides and Bukkit-entity fallback behavior.
- Added reload hooks for Nexo, ItemsAdder, and Oraxen item registries, while
  retaining a delayed startup refresh for providers without a load event.
- Added `SiftBlockProvider` plus reflection bridges for custom blocks from
  Nexo, ItemsAdder, and Oraxen. Generated Charoite ore, Sift tree logs/leaves,
  and Ichor spring water can use configured provider block IDs, with material
  fallback if a provider is absent or placement fails.
- Mini Rift recovery now serializes pending tagged equipment to the plugin's
  `mini-rift-recovery.yml`, so a restart between death and respawn does not
  discard the recovery.
- Remaining integration gaps: more generated block types need provider
  mappings and gameplay interactions; server-specific block definitions need
  validation with each provider.
  Provider runtime behavior still needs validation on servers with each
  external plugin installed.
- `jar` and `siftResourcePack` both pass after this integration update. The
  provider YAML files parse, and all Nexo/Oraxen/ItemsAdder item model paths
  resolve to bundled wrapper model files. A live server with the external
  plugins installed was not available for runtime verification.

## Added in this pass

- Ten additional biome definitions based on the community Mielon's The Sift
  reference: Ichor Snowy Peaks, Overgrown Clearing, Overgrown Forest, Overgrown
  Forest Slopes, Overgrown Peaks, Overgrown Slopes, Sift Deep Dark, Sift Wastes,
  Siftslate Peaks, and Siftslate Slopes. The dimension datapack now offers these
  alongside the five earlier prototype biomes.
- Rough surface palettes and landmark placement for the additional biome names.
- Chunk-generated Ichor spring stand-ins and abandoned portal ruins with chest
  loot. A music disc and upgrade template use vanilla stand-ins.
- Chunk landmarks now include carved Soul Canyon cuts, Sniffer Cave entrances,
  cliff arches, and overgrown monoliths using vanilla blocks.
- Cyclic Ichor weather visuals, regeneration and slowness in Sift water, and
  capped two-layer snow accumulation in Ichor Snowy Peaks.
- Ichor Bottle use now consumes the bottle, returns a glass bottle, and grants
  Regeneration I for three seconds.
- Eight-note portal melody prototype. Right-click a note block to set one of
  eight notes; left-click the blocks in `1, 3, 7, 6, 5, 2, 4, 8` order to open a
  Rift. This uses normal note blocks as a stand-in for Sonorous Deepslate.
- Rift exits now connect the Overworld and The Sift, transport nearby entities
  in either direction, and have a 300-second default active period.
- Dark Sniffer and Sifter summon aliases; Dark Sniffer proxies use a sonic pull.
- Mini Rift recovery returns tagged Siftite weapons/armor from death drops at
  respawn, with a portal effect and inventory-overflow drops.
- Charoite is now a tagged Lapis Lazuli stand-in, so enchanting tables can
  accept it. Offers add 20 levels and raise eligible enchantments; tagged
  items can be renamed but not combined on anvils.
- README feature notes and this report document the approximation boundaries.

## Reference coverage and remaining work

The reference is a community Fabric mod, not Mojang's planned 27.1 release.
Its README describes ten reference biomes, Soul Canyons, Sniffer Caves, Ichor
Lakes/springs, monoliths, cliff arches, sculk formations, abandoned portals,
custom mobs, Charoite enchanting, Siftite recovery, new wood/plants/ores, music,
and 20 advancements. Mojang has not confirmed this feature list for Minecraft
27.1. Here, “vanilla-like” describes the player experience of custom Sift
content feeling integrated into the game; it does not restrict implementation
to built-in Minecraft blocks or entities. Paper content plugins are valid
providers for custom assets and behaviors.

| Area | Current Paper approximation | Still missing or not equivalent |
|---|---|---|
| Worldgen | 15 biome entries; chunk decoration; configurable provider blocks for Charoite ore, Sift tree parts, and Ichor spring water | Custom terrain/noise, caves, lakes, complete provider-block mapping, full biome-specific feature/structure placement |
| Structures | Ruined portal frame, Ichor spring, rough canyon/cave/arch/monolith landmarks, existing tree/shell/crystal landmarks | Deep independently generated caves/canyons, full structure variants, protected generation boundaries and real loot tables |
| Blocks/plants/ores | Provider-backed blocks for selected generated features; tagged item content and built-in material fallbacks | Full provider-backed block set, complete willow family, plants/sculk variants, Siftslate ores, native loot and block interactions |
| Portal/Rifts | Note-block melody opens a temporary two-way portal; nearby entities teleport | Ancient City integration, persistent paired portal positions/state, passenger/projectile/item edge cases, exact mod exit recovery |
| Mobs | Named vanilla entity proxies, Singer/Echo Golem/Rift systems, Dark Sniffer sonic pull | Custom entity models/AI/animations, Blub tame/follow/sit/defend, Sifter pack behavior, Dark Sniffer infection/digging, complete drops |
| Ichor | Bottle regeneration, cauldron state, colored rain, snow and water-contact effects | Registered non-infinite fluid, bottle/bucket source placement, dripstone refill, accurate snowball/block fluid interactions |
| Progression | Charoite enchanting approximation, Siftite recipes/gear and restart-persistent Mini Rift recovery | Exact XP/level roll behavior, all reference recipes/loot and 20-advancement story tree |
| Visual/audio | Item/block/entity model assets packaged as a resource pack; ModelEngine bridge | Client entity renderer integration, animated sky/shader, complete textures, music/disc and eight unique note sounds; provider assets need live validation |

## Build and deferred diagnostics

- `jar` and `siftResourcePack` compiled successfully using the cached Gradle
  9.7.1 distribution. The `gradle` command is absent from this shell's PATH;
  the cached Gradle executable was used instead.
- Java compiler reports three pre-existing removal warnings for
  `RecipeChoice.ExactChoice(ItemStack)` in `IchorContent.java` and
  `SiftiteContent.java`. These are unrelated to this gameplay expansion and
  were logged rather than blocking feature work.
- The first compile attempt in this pass found a Paper API signature mismatch
  for `Note.natural`; the implementation was changed to the supported `Note`
  constructor and the next build succeeded.
- `gradle build` compiles main and test sources, then fails in the pre-existing
  Mockito-based unit tests: 25 of 31 fail during mock initialization at test
  setup; 6 pass. No failure points to the newly added gameplay classes. The
  standalone `jar siftResourcePack` tasks succeed.
- A live-server playthrough was not run in this pass.
- No Minecraft 27.1 Paper API, datapack schema, or official vanilla content
  definitions are available in this project. This build therefore only claims
  a Paper 26.3 prototype.
- Mini Rift recovery persists across restarts. Its overflow fallback drops
  items beside the player rather than orbiting them until inventory space opens.

## Licensing and provenance

The community reference repository is a source for reference research, but its
license does not automatically license this repository's code or art. Mojang's
planned 27.1 dimension is also not evidence that a specific community feature
is official. The table below records the current evidence and uncertainty; it
is not a provenance certification.

| Group | Evidence and in-repository paths | Source / terms | Confidence |
|---|---|---|---|
| Biome JSON | `src/main/resources/datapack/data/thesift/worldgen/biome/`; `src/main/resources/sift_dimension_datapack/`; additions described in this report | Some biome names/feature concepts were researched in the community reference [Mielon's The Sift](https://github.com/Arta48/Mielons-The-Sift). Plugin JSON uses its own vanilla-backed feature definitions; no per-file authorship record is present. | Medium for reference influence; low for exact per-file provenance |
| Models | `src/main/resources/pack/assets/thesift/models/` (block, item, entity JSON) | Files are in the repository history; no per-file author/source/license metadata is recorded. Resource-pack model JSON is not covered merely by a reference project's MIT license. | Low |
| Textures | `src/main/resources/pack/assets/thesift/textures/`; `src/main/resources/pack/assets/minecraft/textures/entity/` | 63 PNG files were inventoried. Prior SHA-256 comparison against the available Mielon reference asset set found no exact matches; that does not establish authorship or permission. Minecraft namespace overrides need particular provenance review. No Dark Sniffer texture was identified. | Low / unresolved |
| Sounds | `src/main/resources/pack/` | No OGG, WAV, MP3, or MIDI files were found. Runtime sounds currently use Bukkit/Minecraft sound identifiers rather than bundled recordings. | High for absence of bundled audio in this tree |
| Names / ideas | README, config, biome IDs, mob/item IDs, and worldgen descriptions across `src/main/resources/` and `src/main/java/` | The Mielon project and the Mojang 27.1 announcement informed research. The reference does not prove that the features are official 27.1 mechanics, nor does it grant ownership of names. | Medium for reference source; low for official 27.1 parity |

At the time of the original Phase 1 inventory, no repo-wide LICENSE or NOTICE
had been added. A provenance-only `NOTICE.draft.md` is now present; no LICENSE
has been created. Add a project license only for original code the owner
confirms they control, and finalize any NOTICE after asset and code provenance
is verified. The embedded
`src/main/resources/bedrock/TheSiftGeyser.jar.embedded` also has unresolved
origin and terms. Do not add the Rift music or Dark Sniffer texture without
permission evidence.

## Foundation correctness and 27.1-style simulation pass (2026-10-24)

“Vanilla-like” in this project means a cohesive survival experience for the
planned custom dimension, with optional external content providers where Paper
does not supply planned custom blocks, items, or entities. Minecraft 27.1 is
still a future target; this pass only builds against Paper 26.3.

- Ichor cauldrons add one charge per bottle, honor extraction cost, and return
  overflow items as world drops. Bottle use no longer cancels unrelated block
  interactions. Ichor state snapshots are synchronized for async saves.
- Generated Charoite and Siftite ores carry chunk-PDC location markers; only
  marked generated blocks yield custom drops. Survival ore placement and Soul
  Block canyon clusters were added to the Sift dimension prototype.
- Siftite recipes use Netherite equipment as their base and exact custom
  template/ingot ingredients. Material-only vanilla recipes are filtered for
  tagged Siftite ingredients. Mining speed and weapon/armor attributes are
  applied by equipment type.
- Rift timers/configuration share one scheduler; key use consumes one key;
  shutdown discards transient Rifts instead of granting a reward. Singer no
  longer damages players, while Echo Golem has a basic owner bond and Soul
  Block transfer interaction.
- Soul Block placement/break identity is tracked, the existing advancements
  datapack is discovered, and a Bukkit advancement listener awards current
  progression milestones.
- Block detection checks optional Nexo, ItemsAdder, and Oraxen APIs and supports
  third-party `SiftBlockProvider` adapters. These integrations still depend on
  server-side ID mappings and have not been runtime-tested with each provider.
  Provider recognition does not guarantee every feature, AI, or model works.
- Item model definition JSONs were added for bundled item-model references.
  This does not create missing textures, animated entity models, sounds, or a
  custom client renderer.

Build verification: `jar siftResourcePack` succeeds on Java 25 / Paper API
26.3. Three `RecipeChoice.ExactChoice` deprecation-for-removal warnings remain.
No live server test was performed. Previously observed Mockito test setup
failures were not re-run in this pass.

## Phase 1 completion report (2026-10-24)

### Changes and verification

| Work | Result | Verification |
|---|---|---|
| P0 Ichor migration | Reads `runtime.ichor-cauldrons` and `runtime.ichor-blocks`, backs up config, preserves existing state on coordinate collision, writes `ichor-state.yml` before removing old keys, and is idempotent. Config version is 3. | Unit tests: migration cases pass, including two runs, missing keys, backup, collision, and failed write. No server test. |
| Gradle wrapper / CI | Gradle wrapper pinned to 9.7.1; workflow runs the wrapper and executes test once through `build`. `gradlew` executable bit was added after Linux CI reported `Permission denied`. | GitHub Actions passed for commit `87241ed`. The latest regression-test commit will trigger the same workflow. Local full build below. |
| Mockito / Java 25 | Recorded pre-fix root exception above; upgraded Mockito and replaced incomplete plugin mocks with MockBukkit fixture. | Full local build: 43 tests, 0 failures/errors/skips. No live server test. |
| RecipeChoice | Replaced deprecated `ExactChoice(ItemStack)` construction while keeping exact tagged-item matching. | Unit tests verify tagged Ichor glass/Siftite ingot accepted and plain materials rejected. |
| Advancement regressions | Fixed template being treated as gear, The Sifter granting Tyrant/Matriarch advancements, and Ichor custom drink not granting `storm`. Rift Key pickup test passes without a production fix: the generated key already has the expected content tag. | Tests were run red before relevant fixes (two advancement tests failed; then Ichor test failed because advancement lookup was never called), then focused suite passed 4/4. Full suite passed 43/43. |
| `rift_block` / Singer–Charoite review (pre-follow-up snapshot) | At this point no block producer existed and Singer only emitted effects. | The follow-up below supersedes this snapshot: ineffective Rift handler removed and Singer damage/debuff implemented. |
| Provenance | Inventory and uncertainty table above. No global license added. | Repository resource inventory and reference comparison recorded; individual authorship is not proven. |
| Chunk benchmark | Added opt-in `debug.chunk-generation-metrics.enabled` timer and stage/surface-attempt count in `SiftEcologyManager.populate`. | Compiles in full build. Disabled by default. No server timing calibration. |

### Regression detail and risks

- The Rift Key advancement suspicion was false: `createRiftKey()` creates the
  tagged Siftite item with ID `rift_key`; the pickup hook requests that
  advancement. Test: `src/test/java/com/gamepathics/thesift/foundation/SiftAdvancementManagerTest.java`.
- In the pre-follow-up snapshot, `rift_block` had no producing Bukkit block.
  The Rift Walker's PDC marker could not satisfy `Block.hasMetadata("rift_block")`;
  the follow-up removed that ineffective handler and marker.
- Singer damage, slowness and Charoite protection were not active in the
  snapshot above; the follow-up below implements and tests these settings.
- Benchmark `operation-attempts` is an approximate generation-stage count plus
  28 surface sample attempts, not a count of individual placed blocks/entities.
  It exists for comparative future profiling and should be refined in Phase 2.
- Optional provider APIs remain unverified against live Nexo, ItemsAdder,
  Oraxen, MythicMobs, EliteMobs, or ModelEngine installations. Compile-time and
  configuration presence are not runtime compatibility guarantees.
- 27.1 is still the planned target. Everything here is built against Paper
  26.3; there is no claim of compatibility with an unreleased API/schema.

### Commits

Pushed to `main` during this phase: `213dead` (Ichor migration), `594d682`
(Gradle wrapper/CI), `baa1fd7` (Rift Key formatting), `e6db80c`
(Mockito/test fixtures), `3da3e21` (exact recipe choices), `618548a`
(advancement regressions), `35d72c8` (optional chunk metrics), `87241ed`
(executable wrapper bit), and `30f6f41` (negative-case verification tests).
This report and HANDOFF update are a separate documentation-only commit.

### Deferred items

BlockPopulator migration, Rift persistence, note-block behavior, live server
smoke testing, provider-by-provider server validation, asset provenance proof,
owner's project-license decision, final asset provenance clearance, and 27.1
compatibility remain open. `NOTICE.draft.md` is a research note, not a final
license/attribution statement.

## Step 1 follow-up (2026-10-24)

### Changes and verification

| Item | Result | Verification |
|---|---|---|
| Singer damage / slowness / Charoite | Singer pulse now deals 1.0 health point by default (half a heart) and applies Slowness I for 3 seconds. `resonance.singer.attack-enabled` disables both effects; damage, duration, amplifier and pulse period are configurable. Tagged Charoite in the player's storage inventory/hotbar, offhand, or armor blocks both effects. Ender chest does not count. Creative/Spectator are ignored. README and handoff now describe this behavior. | Unit tests: damage, slow, tagged Charoite immunity, no tagged Charoite, creative, spectator, and global switch. Full local build passed. No live Paper server test. |
| `rift_block` | No block producer exists; the old handler tested Bukkit block metadata while the marker was attached to an entity. Removed the ineffective handler and entity marker. | Unit test asserts no block-break handler remains without a block producer. Full local build passed. |
| Embedded Geyser binary | `src/main/resources/bedrock/TheSiftGeyser.jar.embedded` was added in initial commit `7b1e0f0ee93dec57cef775f1e4668e9eb0f4bd44` by `nguyenphuck2468-tech` on 2026-10-22. It is 6,928 bytes, SHA-256 `3093ba847f63022869dc17b353f1cc0dc6af208087d6dbaef778becd1bf89319`. Read-only ZIP inspection found `META-INF/MANIFEST.MF`, `extension.yml`, and `com/gamepathics/thesift/geyser/TheSiftGeyser.class`; extension metadata says Geyser API 2.11.0 and version 1.6.2, author `Gamepathics`. No code references this resource; no embedded license/source identity is present. It was never executed. Recommend removing it from the repository after confirming it is not needed for a separate Bedrock extension distribution. | Git history and static archive metadata inspected. Gradle `processResources` excludes it from the Paper JAR. Full local build and JAR inventory verified exclusion. No provenance/license verification. |
| ItemsAdder block ID | Detection now compares `CustomBlock.byAlreadyPlaced(block).getNamespacedID()` to `integrations.blocks.definitions.<contentId>`; unrelated ItemsAdder blocks no longer match. | Fixture tests exact match and mismatch. Full local build passed. No ItemsAdder server installed/tested. |
| Chunk decoration marker | PDC state is now `in progress` while decorating and `complete` only after success. Runtime failure removes marker, removes entities created during the attempt, logs, and allows retry. Singer/Echo Golem/Rift spawns now execute in the synchronous chunk-load path, so a failed attempt cannot leave those spawns queued for a later tick. An abrupt process stop with in-progress state retries on next chunk load. Partial block placements from an exception are not transactionally rolled back; deterministic placement makes retry possible but is not a full rollback guarantee. | Unit test injects failure, checks marker cleared, retries, then checks completion and idempotent skip. Full local build passed. No server crash/restart simulation. |
| Config audit | Completed; see table below. No config keys were deleted. | Source/config inspection. No dedicated config-audit unit test. |
| CI / artifact | Runner pinned to `ubuntu-24.04`; actions updated to checkout v7, setup-java v5, setup-gradle v6, upload-artifact v7. Wrapper performs one `clean build`. Paper JAR contains 495 entries: 33 classes, 401 JSON, and 61 other files. All 63 PNGs and `TheSiftGeyser.jar.embedded` are excluded; no `.jar` or `.embedded` entry remains. Full entry list: [`docs/step1-paper-jar-inventory.txt`](docs/step1-paper-jar-inventory.txt). The separate resource-pack task still builds from `src/main/resources/pack`. JAR SHA-256: `489f1d5a11b7139c5f2c389a7be1f12199e98d38d7530dc36a712300ae305a1c`. | Full local `clean build` passed (52/52 tests); current artifact entry listing inspected. GitHub Actions result for the latest packaging change is checked after push and linked in the final response. No server test. |
| NOTICE / license | Added [`NOTICE.draft.md`](NOTICE.draft.md) as a provenance note only; it makes no project-wide license claim. No `LICENSE` was created. | Manual review against available repo history and source notes. |

### Config key audit

“Read” means code retrieves the key. “Effective” means the value reaches a
runtime gate or setting. No key was removed; any future removal needs an
explicit decision and a versioned migration plan.

| Keys | Status | Recommendation |
|---|---|---|
| `config-version` | Used by `ConfigMigration`; resource default now matches current version 3. | Keep. |
| `content.*` for all declared `ContentId` values | All 34 flags are read dynamically by `loadContentFlags()` and shown by `/sift doctor`. `content.siftslate`, `content.healthy_sculk`, `content.overgrown_willow`, `content.blub`, `content.sifter`, and `content.siftite_spear` currently only affect the status snapshot; their related generation/command paths do not consult the flags. The other Siftite equipment flags gate recipe registration, but `/sift give` does not enforce those flags and some generated-item paths bypass them. | Keep for compatibility; fix incomplete enforcement in a later targeted change or mark experimental in docs. Do not delete yet. |
| `resonance.*`, `resonance.singer.*` | Used. Singer attack values were added/activated in this pass. | Keep. |
| `echo-golem.enabled`, `max-health`, `attack-damage`, `resonance-period`, `resonance-range`, `heal-amount`, `drop-siftite` | Used. | Keep. |
| `echo-golem.resonance-damage`, `slowness-seconds`, `slowness-amplifier`, `mining-fatigue-seconds`, `mining-fatigue-amplifier` | Not read by Java. | Recommend removal only with approval and config migration; alternatively implement the documented effects separately. |
| `echo-golem.charoite-protection` | Read only by a private helper that has no call sites; currently no gameplay effect. | Recommend wiring into a defined Echo Golem attack or removing with migration after approval. |
| `rift.*` | Used by Rift lifecycle and rewards. | Keep. |
| `ichor.*` | Used for module enablement, material choice, cauldron costs/effects and Ichor items/state. | Keep. |
| `charoite.enabled`, `world`, `ore-material`, `drop-min`, `drop-max` | Used. | Keep. |
| `charoite.material` | Not read; current item creation hardcodes its vanilla fallback material. | Recommend wiring or removing with migration after approval. |
| `siftite.enabled`, `ore-material`, `knockback-resistance`, `weapon-damage-bonus`, `spear-damage`, `mining-speed-bonus`, `mini-rift-recovery` | Used. | Keep. |
| `siftite.ingot-material`, `nugget-material`, `template-material`, `smithing-only`, `durability-multiplier`, `spear-reach` | Not read by Java. Recipes currently always use smithing and hard-coded vanilla materials; durability and reach are not applied. | Recommend either implement with tests or remove only after approval and config migration. |
| `compatibility.minecraft-target` | Used in startup and `/sift doctor` output. | Keep. |
| `integrations.blocks.provider`, `integrations.blocks.definitions.*` | Used by external block bridge; definitions are exact IDs. | Keep. |
| `integrations.items.provider` | Used by external item bridge. | Keep. |
| `integrations.mobs.provider`, `integrations.mobs.itemsadder.entities.*`, `integrations.mobs.elitemobs.level`, `integrations.mobs.elitemobs.definitions.*`, `integrations.mobs.modelengine.models.*` | Used by reflection/service-provider adapters. | Keep. |
| `sift-world.tide-period-seconds`, `sift-world.ichor-weather-period-seconds` | Used by ecology scheduler. | Keep. |
| `debug.chunk-generation-metrics.enabled` | Used to enable/disable timing and operation-count logging. | Keep. |

### Rebuttal and remaining risk

Repeated Singer pulses can drain health and refresh Slowness while a player
stays in range. The default is deliberately low: 1 damage every 5 seconds;
servers can disable attacks or tune the values. Charoite protection uses
personal carried equipment/inventory because it is accessible during play and
does not let remote ender-chest storage grant immunity. Enchanted/cursed item
interactions and damage attribution still need a live gameplay check.

The requested order is workable. The one recommendation is to make the
config-key ownership decision before any cleanup: deleting compatibility keys
without an agreed migration would surprise existing servers. The embedded
Geyser binary is excluded from the Paper artifact, but repository deletion is
still a maintainer decision because it may have an undocumented separate use.

CI version note (superseded by the follow-up below): the earlier report kept
`actions/setup-java@v5`. The owner later explicitly requested latest stable
versions, so the workflow now pins the latest verified releases for all
actions, including setup-java v6.1.0.

### Commits and verification state

Pushed follow-up commits: `1199aba31f2875f3a633aabd3f33ff3ebb7d06cc`
(Singer combat), `73d05eee5306cebf318ad37fda6c74939080c2fc` (remove unused
Rift block handler), `40a332b3252da92e95c4a3487ebf4b9ccc1c8e45` (ItemsAdder
ID check), `fe832c57ef3782caab567ce6e32e5c01544a0acc` (chunk completion
state), `d7dd35479e469315ebcf6a19c7ccfd43cbd20872` (exclude Geyser binary and
inventory Paper JAR), `648276bf57ddb7300a69ccef259a63d68b6bc685` (CI actions and
runner), and `0140842b8e71dfeeae6c9dbb0beadff65a10db8c` (report, handoff,
NOTICE draft), `330d1381fe7ab53e21b96eb966a001abe1da6b44` (CI evidence),
`362a8e520145e0eb5c6c5e38d66fc84b7ffc66dc` (complete ContentId config audit),
and `3ff2a135b4e5b4e5a98539e59069f89c297194e6` (exclude unverified PNGs from
Paper JAR and refresh its inventory). Local clean build passed all 52 tests;
GitHub Actions run `40086670762` passed for `3ff2a13`. This report-only
commit-list correction is pushed separately; its CI result is linked in the
final response. No live server verification was performed.

## Gradle/Mockito root-cause capture (2026-10-24, before remediation)

Reproduced locally before changing Mockito, Byte Buddy, or mock-maker settings:

- Command: `./gradlew --no-daemon clean test --stacktrace --info`
- Runtime: Temurin Java 25.0.4.1, Gradle 9.7.1, Paper API
  `26.3.build.218-stable`, Mockito 5.12.0.
- Result: 25 of 31 tests fail while Mockito initializes mocks of
  `org.bukkit.plugin.java.JavaPlugin`; the six tests not requiring that mock
  path pass.
- Root exception, copied verbatim from the Gradle test output:

  ```text
  java.lang.IllegalArgumentException: Java 25 (69) is not supported by the current version of Byte Buddy which officially supports Java 22 (66) - update Byte Buddy or set net.bytebuddy.experimental as a VM property
  ```

No remediation was applied before recording this exception. Candidate fixes
were upgrading Mockito (and its managed Byte Buddy version), declaring a
compatible Byte Buddy version explicitly, or switching affected fixtures to a
subclass mock maker / non-mock fixture. The experimental JVM property is a
diagnostic workaround, not the preferred long-term fix.

Decision: upgrade both Mockito artifacts to 5.24.0. Mockito's own v5.24.0
dependency catalog uses Byte Buddy 1.17.7, and Byte Buddy documents Java 25
support beginning in 1.17.0. This keeps the existing inline mock behavior and
avoids depending on an experimental JVM property. Pinning only Byte Buddy while
retaining old Mockito, or switching the suite to the subclass mock maker, is
less direct.

After upgrading Mockito, the original Byte Buddy exception no longer occurs.
The next full run reached test setup and exposed a separate fixture issue in
`IchorContentTest`, `EchoGolemManagerTest`, and `RiftManagerTest`: the mocked
`JavaPlugin#getName()` returns `null`, so Paper's `NamespacedKey(JavaPlugin,
String)` constructor fails. Exact exception:

```text
java.lang.NullPointerException: Cannot invoke "String.length()" because "this.namespace" is null
```

The trace enters `org.bukkit.NamespacedKey.validate` and then the relevant
content/manager constructor. Paper 26.3's `NamespacedKey(Plugin, String)`
constructor calls `Plugin.namespace()`, not `getName()`. A first attempt to
stub only `getName()` did not change the failure. I replaced incomplete
`JavaPlugin` mocks with a real Paper 26.3 MockBukkit test server and named mock
plugin. This initializes registries, plugin services, and the scheduler; no
assertions were removed or weakened.

MockBukkit version `4.118.0` targets Paper 26.3. Mockito 5.24.0 resolves with
Byte Buddy 1.17.7 by itself; once MockBukkit is present, Gradle selects Byte
Buddy and byte-buddy-agent 1.18.11 for the test runtime. Both dependencies now
run without the original Java 25 instrumentation exception. The previous 19
post-upgrade failures were environment/fixture problems (uninitialized
registries, null service manager, and missing scheduler), not failing
production behavior assertions.

The existing Rift Key display assertion also caught a real formatting
regression: the key was pink (`§d`) rather than purple bold (`§5§l`). Restored
the purple bold label without changing the assertion.

With the Paper 26.3 MockBukkit harness initialized, the original 35-test suite
passed 35/35. This verifies the Mockito fix and test server setup, but is not a
live Paper server smoke test. Gradle still reports deprecations for Gradle 10,
and Byte Buddy emits a JDK Unsafe terminal deprecation warning; these do not
fail the build.

## Follow-up: reproducible build and Bước 2 preparation

### Completed in this follow-up

| Item | Result | Verification |
|---|---|---|
| Inactive configuration keys | Kept all keys and config-version 3. Added `chưa triển khai / chưa có tác dụng` comments beside the audited no-effect keys in `src/main/resources/config.yml`; README now lists them. | Source review and `clean build`; no server test. |
| Resource-pack release warning | README warns that `siftResourcePack` includes PNG files whose provenance is not verified and says not to distribute the ZIP yet. | Source review. |
| Reproducible archives | `AbstractArchiveTask` now disables timestamps and fixes entry order for both JAR and ZIP. | Consecutive `clean build siftResourcePack` runs, including after configuring the dev server, succeeded with matching SHA-256: JAR `1D8AAA8063A378F7BD14B6E883C507E7C3F8947F7A523CC1B6610358E7C6B2B6`; resource pack `9795C548941762918393D14FD87DF32B41B549F82761876C89E48327FA09A8AF`. |
| CI action updates | Workflow uses checkout v7.0.1, setup-java v6.1.0, Gradle Actions v6.4.0, and upload-artifact v7.0.2, on pinned `ubuntu-24.04`. | YAML/source review; hosted CI has not run for these changes yet. |
| Paper dev server | Added run-paper 3.1.0 and a `runServer` task configured for Minecraft/Paper 26.3 in `run/paper-26.3`. | Gradle task listing and `runServer --dry-run` succeeded. Server itself was not launched. |
| Geyser embedded jar | Prepared commit `bab35db` removing `src/main/resources/bedrock/TheSiftGeyser.jar.embedded` on `pending/remove-geyser-embedded`. Not merged or pushed; `main` still contains the file pending owner confirmation. | Commit diff reviewed; binary was not executed. |

Follow-up commits on `main` (pushed after approval):

- `f127e91c4826d8e3ea6e273976aa6706e952211b` — document inactive config keys
  and resource-pack provenance warning.
- `e6aeb4918380c2a52f1cee4e6175fc97bd6184f7` — reproducible archives and
  Paper 26.3 dev-server task.
- `e72920757b0467ee7ddb4b008fe0292125d8814e` — pin current stable GitHub
  Actions releases.

### Step 2.1 note-block design decision required before code

`RiftManager.onPortalSong` currently handles every note block and cancels normal
click interactions. A strict vanilla-behavior boundary needs an explicit way
to identify the portal station. Options:

| Approach | Benefits | Trade-offs |
|---|---|---|
| Persist registered note-block coordinates in chunk PDC, with permission-checked register/unregister commands | Precise allowlist; follows the world and chunk; ordinary note blocks remain untouched; no external custom-block dependency. | Requires an operator to register each station; command UX and cleanup on breaking/moving the block must be defined. |
| Configure a portal volume/region | Simple persistence and administration; no per-block registration. | Every note block in the region is intercepted, including unrelated builds; region overlap and edits need maintenance. |
| Auto-detect a surrounding block pattern | No command after construction. | No agreed vanilla-27.1 The Sift portal pattern exists in the repository, so any pattern would be invented and risks false positives. |

Recommendation: chunk-PDC registration, with a command to register the targeted
note block and unregister it, and cleanup when the registered block is broken.
This is the strongest guarantee that normal note blocks preserve vanilla
behavior. Do not implement the note-block or Rift work until the owner chooses
the registration policy.

### Step 2.3 measurement preparation (mechanism unchanged)

`build.gradle` applies `xyz.jpenilla.run-paper` 3.1.0. Run
`./gradlew runServer` (Windows: `.\\gradlew.bat runServer`) from the repository
root. The task targets Paper/Minecraft 26.3 and uses `run/paper-26.3`; first
launch needs network access to obtain Paper. If that download is blocked, run
the same plugin JAR on a disposable Paper 26.3 server. The current environment
has not launched a server.

1. First launch once, accept the EULA in `run/paper-26.3/eula.txt`, then stop.
2. In `run/paper-26.3/plugins/TheSift/config.yml`, set
   `debug.chunk-generation-metrics.enabled: true`.
3. Start again. Use a fresh test world/run directory each trial so the chunk
   PDC completion marker does not suppress the measured decoration.
4. Enter The Sift. Starting at chunk `(0,0)`, move east through 20 target chunk
   centers `(x*16+8, z=8)`, `x=0..19`, pausing two seconds at each target.
   Keep view distance and simulation distance fixed at 3/2; note that the
   server can decorate adjacent chunks in its load radius, so record their
   emitted lines too and distinguish route targets from preloaded neighbors.
5. Collect the `Sift chunk ... generation=...ms operation-attempts=...` lines
   from `run/paper-26.3/logs/latest.log`. Repeat on a new world with the same
   settings before comparing.

Record each trial here; do not switch to `BlockPopulator` based on guesswork.

| Trial / server build | World seed | Route chunk | New chunk? | Generation ms | Operation attempts | Failure/retry notes |
|---|---|---:|---|---:|---:|---|
| Pending owner-run | Paper 26.3 | (0,0) | | | | |
| Pending owner-run | Paper 26.3 | (1,0) | | | | |
| Pending owner-run | Paper 26.3 | … (continue to 19,0) | | | | |

Paper API 26.3 `ChunkLoadEvent` Javadoc explicitly warns under
`isNewChunk()`: “Do not use this to generated blocks in a newly generated
chunk. Use a BlockPopulator instead.” This is high-confidence evidence that
Paper discourages block generation from this event for newly generated chunks;
it is not an API prohibition and does not itself document entity spawning.
The Javadoc only says `ChunkLoadEvent` is called when a chunk loads. Therefore
whether the current entity spawning causes practical issues on Paper 26.3
remains unverified until server testing. Source: Paper 26.3 API Javadoc,
`ChunkLoadEvent.isNewChunk()`.

Static failure-path review: `SiftEcologyManager.decorateChunkOnce` removes the
completion marker after a `RuntimeException`, so a later load retries. Block
changes are not rolled back. Decoration uses a seed derived from world seed and
chunk coordinates, and writes to deterministic positions; most retry writes
overwrite the same locations rather than adding a second structure. New
non-player entities are removed by the failure handler. Partial tile-state or
other side effects have no transaction guarantee. The existing unit test
`SiftEcologyChunkMarkerTest.failedDecorationLeavesNoCompletionMarkerAndCanRetry`
verifies marker/retry state only; it does not prove server block rollback or
duplicate-free real-world behavior. The metrics logger currently records only
successful decoration, so failed attempts must be inspected in logs separately.

### Step 2.4 datapack definition audit

Confirmed repeated biome IDs across separately registered datapack roots:
`thesift:bulb_forest`, `thesift:crystal_spires`, `thesift:pink_meadows`, and
`thesift:shattered_peaks` exist under both `src/main/resources/datapack` and
`src/main/resources/sift_dimension_datapack`. In each case, the 26.3 overlay
JSON is byte-identical to the corresponding dimension-pack JSON, while the
progression pack's default JSON is different. These four roots are registered
as `progression` and `dimension` in `TheSiftBootstrap`. Duplicate registration
is therefore confirmed; which definition wins or whether Paper reports a
conflict is unconfirmed without the server's enabled-pack order and logs.
Confidence: high for duplicated source IDs and file comparisons; low for
runtime precedence.

### Server checks still required

On a disposable Paper 26.3 server, run `/sift summon singer <player>` and
verify one low damage pulse and Slowness, then carry tagged Charoite in the
player inventory/hotbar, offhand, or armor and verify both effects are blocked.
For ItemsAdder, configure `integrations.blocks.definitions` with the exact
configured namespaced block ID, generate/place that block, and verify only an
exact ID match is recognized; the unit fixture covers matching and mismatch,
but no live ItemsAdder API server test has run. For the Step 2 server checks,
follow the fresh-world chunk runbook above; additionally inspect datapack
startup/reload logs for duplicate biome warnings and compare the active biome
definition. Record Paper build, plugin versions, seed, route, logs, and results
in the tables before drawing a runtime conclusion.

## Provider-first strict mode and generated provider definitions (2026-10-24)

Implemented provider-first readiness with `providers.strict: true` by default,
optional soft dependencies, per-ID item/block/mob checks, `/sift doctor`
diagnostics, and a fallback mode when strict is disabled. `integrations/content-spec.json`
is the canonical list for generated Nexo/ItemsAdder/Oraxen item and block YAML,
MythicMobs mob YAML, and the generated Java readiness ID lists. Nexo block
variation assignments live in `integrations/nexo/block-variants.yml` with
duplicate/range validation. ItemsAdder custom entity and EliteMobs definitions
are not generated. ItemsAdder entity readiness uses its documented, read-only
`CustomEntity.isInRegistry` API and configured ID mapping; EliteMobs remains
unresolved because no side-effect-free complete ID probe was verified.
ModelEngine is only
rendering support. ResourcePackManager is detected, but its public Java API
registration integration is deferred.

Compatibility risk: existing installations without `providers.strict` now
read the default `true` and can lose dimension access until mandatory IDs
resolve. Existing world data is preserved; `/sift return` remains an exit
route. This is the explicit provider-first behavior the owner requested. A
future compatibility migration to opt legacy servers into development mode
would soften the change, but would contradict strict-by-default unless the
owner asks for it.

Exit safety: `/sift return` is intentionally left available when readiness
fails so players can leave an existing dimension save.

Verification: local `test` and `generateProviderDefinitions` pass (52 tests in
the latest run); generated YAML syntax was parsed with PyYAML in an earlier
run. Provider-native parsing and Paper 26.3 live checks have not run. The
matrix explicitly separates detection, generation, parsing, and live
validation. Do not describe any backend as fully supported until its live
server row is verified. See `docs/parity-matrix.md`, `docs/provider-sources.md`,
and `docs/TESTING.md`.
