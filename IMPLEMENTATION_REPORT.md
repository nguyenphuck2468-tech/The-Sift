# The Sift expansion pass — implementation report

Updated: 2026-10-24  
Build baseline: Java 25, Paper API `26.3.build.218-stable`  
Target: Minecraft 27.1 is planned, not released or verified.

## Optional content-plugin integration (2026-10-24)

- Added reflection-based Nexo/ItemsAdder/Oraxen item bridges, preserving The
  Sift's persistent content tags and keeping provider APIs optional at compile time.
- Added a public Bukkit `SiftItemProvider` service interface so third-party
  item plugins can register adapters without The Sift depending on their APIs,
  plus a `SiftMobProvider` service interface for other creature plugins.
- Added provider selection (`auto`, `nexo`, `itemsadder`, `oraxen`, `vanilla`,
  or a registered service ID), optional
  load ordering, and a delayed recipe refresh for Nexo's asynchronous registry.
- Added starter Nexo and ItemsAdder item definitions and import instructions.
- Added a reflection-based MythicMobs spawn bridge. Configured IDs are used for
  natural Sift wildlife and summon commands; missing IDs retain the Bukkit
  entity fallback. Added starter definitions for the named creatures.
- ModelEngine is documented as an optional future rendering integration. The
  current entity JSON assets are not ModelEngine/Blockbench models and are not
  claimed to render as animated custom entities.
- Deferred: provider item configs cover the bundled custom item models and key
  progression/Ichor items; provider-specific custom block mechanics are not
  wired into the current Bukkit block-placement handlers. Provider runtime
  behavior still needs validation on a server with these plugins installed.

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

The reference is a community Fabric mod, not Mojang's planned vanilla release.
Its README describes ten reference biomes, Soul Canyons, Sniffer Caves, Ichor
Lakes/springs, monoliths, cliff arches, sculk formations, abandoned portals,
custom mobs, Charoite enchanting, Siftite recovery, new wood/plants/ores, music,
and 20 advancements. Mojang has not confirmed this feature list for Minecraft
27.1.

| Area | Current Paper approximation | Still missing or not equivalent |
|---|---|---|
| Worldgen | 15 biome registry entries; Overworld noise; chunk decoration | Custom terrain/noise, caves, lakes, full biome-specific feature/structure placement |
| Structures | Ruined portal frame, Ichor spring, rough canyon/cave/arch/monolith landmarks, existing tree/shell/crystal landmarks | Deep independently generated caves/canyons, full structure variants, protected generation boundaries and real loot tables |
| Blocks/plants/ores | Existing tagged Ichor/Charoite/Siftite content plus vanilla material stand-ins | Real registered block/fluid types, complete willow family, plants/sculk variants, Siftslate ores and native loot behavior |
| Portal/Rifts | Note-block melody opens a temporary two-way portal; nearby entities teleport | Ancient City integration, persistent paired portal positions/state, passenger/projectile/item edge cases, exact mod exit recovery |
| Mobs | Named vanilla entity proxies, Singer/Echo Golem/Rift systems, Dark Sniffer sonic pull | Custom entity models/AI/animations, Blub tame/follow/sit/defend, Sifter pack behavior, Dark Sniffer infection/digging, complete drops |
| Ichor | Bottle regeneration, cauldron state, colored rain, snow and water-contact effects | Registered non-infinite fluid, bottle/bucket source placement, dripstone refill, accurate snowball/block fluid interactions |
| Progression | Charoite enchanting approximation, Siftite recipes/gear and in-memory Mini Rift recovery | Exact XP/level roll behavior, restart-safe recovery, all reference recipes/loot and 20-advancement story tree |
| Visual/audio | Existing item/block/entity model assets packaged as a resource pack | Client entity renderer integration, animated sky/shader, complete textures, music/disc and eight unique note sounds |

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
- Mini Rift recovery is in-memory and will not survive a server restart between
  death and respawn. Its overflow fallback drops items beside the player rather
  than orbiting them until inventory space opens.

## Licensing and provenance

The additional biome names, portal melody, Ichor weather, and gameplay ideas
were checked against the MIT-licensed community reference project
[Mielon's The Sift](https://github.com/Arta48/Mielons-The-Sift). New biome JSON
files in this plugin strip the reference mod's custom feature references and
use vanilla-compatible decoration. Mojang's announcement of The Sift does not
confirm these exact details; do not describe them as finalized 27.1 vanilla
mechanics.
