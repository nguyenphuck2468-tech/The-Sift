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

No repo-wide LICENSE or NOTICE was added. Recommendation: add a license only
for original code the owner confirms they control, and create a NOTICE after
asset and code provenance is verified. The embedded
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
| `rift_block` / Singer–Charoite review | No block producer exists: `rift_block` PDC is attached to Rift Walker entities, while break protection checks Bukkit block metadata. Singer pulse only emits effects visually/audibly; `protectedByCharoite` is not called. | `RiftBlockMetadataTest` verifies a normal block is not cancelled. `ResonanceManagerTest` drives a tagged Singer pulse and verifies player health is unchanged. Source inspection confirms no block marker producer and no active damage/debuff call. The suspected protection bugs are not present in current behavior; no gameplay behavior was added. |
| Provenance | Inventory and uncertainty table above. No global license added. | Repository resource inventory and reference comparison recorded; individual authorship is not proven. |
| Chunk benchmark | Added opt-in `debug.chunk-generation-metrics.enabled` timer and stage/surface-attempt count in `SiftEcologyManager.populate`. | Compiles in full build. Disabled by default. No server timing calibration. |

### Regression detail and risks

- The Rift Key advancement suspicion was false: `createRiftKey()` creates the
  tagged Siftite item with ID `rift_key`; the pickup hook requests that
  advancement. Test: `src/test/java/com/gamepathics/thesift/foundation/SiftAdvancementManagerTest.java`.
- `rift_block` currently has no producing Bukkit block. The Rift Walker's PDC
  marker cannot satisfy `Block.hasMetadata("rift_block")`; the break handler
  is not active for the current particle-only portal implementation.
- The Singer's `damage`, slowness, and Charoite-protection config values are
  not implemented in the active pulse. The unused helper should be wired only
  alongside a defined damage/debuff behavior.
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
LICENSE/NOTICE creation, and 27.1 compatibility remain open.

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
