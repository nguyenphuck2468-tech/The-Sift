# Provider and server setup checks

## Never install two TheSift plugins

Install only one plugin whose name is `TheSift` and only one plugin declaring `/sift`. A duplicate can replace command routing or prevent a plugin from loading. `/sift doctor` reports duplicate plugin names and command declarations it can see.

## Optional provider installation

All integrations are optional soft dependencies. Install only the backends you intend to use, restart Paper, then run `/sift doctor`.

- **ItemsAdder**: place definitions from `build/generated/provider-definitions/itemsadder/items.yml` under `plugins/ItemsAdder/contents/thesift/configs/`. Place properly licensed model resources under the matching namespace, then use ItemsAdder's documented content reload/zip workflow. Verify every ID in doctor.
- **Nexo**: merge `build/generated/provider-definitions/nexo/items.yml` into `plugins/Nexo/items/`; preserve `integrations/nexo/block-variants.yml` values. Restart/reload Nexo using the command supported by the installed version, then check doctor.
- **Oraxen**: copy `build/generated/provider-definitions/oraxen/items.yml` to `plugins/Oraxen/items/`, preserve assets/permissions, and use the installed version's documented reload or restart procedure.
- **MythicMobs**: copy `build/generated/provider-definitions/mythicmobs/TheSift.yml` to `plugins/MythicMobs/Mobs/` and run `/mm reload`.
- **EliteMobs**: the integration accepts configured boss definition paths, but this repository does not generate a complete EliteMobs pack. Configure `integrations.mobs.elitemobs.definitions` only with files already installed and validated by EliteMobs.
- **Sift services**: third-party adapters can register `SiftItemProvider`, `SiftBlockProvider`, or `SiftMobProvider` through Bukkit's ServicesManager. Implement `supports(id)` as a side-effect-free probe for every content ID.
- **ModelEngine**: optional render attachment only. It does not provide mob AI/spawning.
- **ResourcePackManager**: current integration detects the plugin/version in doctor only. Its [public Java API](https://wiki.nightbreak.io/ResourcePackManager/api/) supports pack registration; The Sift API integration is deferred. No telemetry is used.

Set `providers.strict: true` for production. The default is `true`: The Sift dimension is blocked until one item backend, one block backend, and one mob backend each resolve every mandatory ID. `/sift doctor` prints missing IDs and setup hints. Existing dimension save data is preserved while locked. `/sift return` remains available as an emergency exit. Set `providers.strict: false` only for development/CI; startup logs that vanilla fallback is active.

## Bedrock (detection level only; checked 2026-10-24)

The official Geyser supported-versions page currently lists Java 26.3 as the emulated version; its rolling download page did not provide a stable semantic version in the inspected snapshot. Record the exact installed build with `/geyser version`. Official guidance says ViaVersion is needed when the Java server is older than the version Geyser emulates, implying Paper 26.3 does not need ViaVersion solely for Geyser protocol support. Skilleed's Spigot product documentation for The Sift (page updated 2026-10-09) has also recommended ViaVersion for 26.3. These sources disagree in practical advice, and neither verifies this plugin's Bedrock behavior. A real Paper 26.3 + Geyser smoke test is required.

On a disposable Paper 26.3 server, test once with the current official `Geyser-Spigot` build and optionally Floodgate, then repeat on a fresh copy with ViaVersion installed. Run `/geyser version`, `/viaversion`, `/plugins`, and `/sift doctor`; connect a Bedrock client and test login, dimension entry, Soul Block, Singer, Echo Golem, and Rift. Record exact server/plugin/client versions and logs in `docs/parity-matrix.md` before marking anything **Live checked**. Do not use a production world for protocol experiments.

Official references: [Geyser setup](https://geysermc.org/wiki/geyser/setup/), [supported versions](https://geysermc.org/wiki/geyser/supported-versions/), [downloads](https://geysermc.org/download), [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/). Conflicting product reference: [Skilleed The Sift documentation](https://www.spigotmc.org/resources/the-sift-new-minecrafts-dimension.139206/field?field=documentation).

`providers.bedrock-enabled` defaults to `false`. Turn it on if the server intends to offer Bedrock access; `/sift doctor` then warns when Geyser, Floodgate, or ViaVersion is absent. These warnings never block dimension creation. Bedrock resource pack generation, custom item/block translation, and Geyser mappings are not implemented yet.

## Repeatable local checks

```powershell
.\gradlew.bat clean build siftResourcePack
.\gradlew.bat generateProviderDefinitions
.\gradlew.bat runServer --console=plain
```

`runServer` is configured by run-paper 3.1.0 for Minecraft/Paper 26.3 and uses
`run/paper-26.3`. Paper 26.3 build 222 has reached `Done` locally and accepted
`/datapack list enabled` plus `/sift doctor`; third-party providers and a
Bedrock client have not been tested. Do not accept the Minecraft server EULA
automatically. The CI boot script is gated by the repository Actions variable
`MINECRAFT_EULA_ACCEPTED=true`; set it only after the repository owner accepts
the EULA. It starts Paper, waits for `Done`, checks the server logs for
datapack registry errors, then sends `stop`.

To run the equivalent local smoke check after accepting the EULA yourself:

```powershell
./gradlew.bat runServer --no-daemon --console=plain
datapack list enabled
sift doctor
stop
```

The progression pack contains advancements only. The dimension pack is the
sole biome owner. Legacy progression worldgen was removed after Paper 26.3
failed registry loading; each removed feature is tracked as **chưa làm** in
`docs/parity-matrix.md`.

Local automated evidence for the provider bridge and strict gate is recorded
in `IMPLEMENTATION_REPORT.md`; it does not establish that a third-party
provider has parsed its generated YAML or works on a live server.

On the test server, run `/sift give soul_block <player>`, `/sift summon singer <player>`, `/sift summon echo_golem <player>`, and `/sift give sonorous_deepslate <player>`. Place Sonorous Deepslate and a normal vanilla note block on top. Right-click to tune; left-click the sequence `1, 3, 7, 6, 5, 2, 4, 8` to open a Rift. Provider-owned note blocks are left to their provider. Then run `/sift doctor`, verify provider logs and each mandatory content ID, check `/plugins` for duplicate TheSift installations, and inspect `logs/latest.log` for API errors. No real-provider server test is claimed until recorded in `docs/parity-matrix.md`.

When upgrading from a release that used vanilla fallback for Sonorous Deepslate, existing vanilla blocks that were only indexed by the legacy world-level scan are intentionally not migrated: their identity is ambiguous. Re-place them using the provider-defined Sonorous Deepslate item to create new persistent station markers. Provider-defined blocks found in the legacy index are migrated when their chunks are loaded; the migration-complete flag is persisted at world scope.

Disabling The Sift while players are in its world attempts to teleport them to the first loaded non-Sift world spawn. The plugin does not unload the dimension. This has unit/build coverage only; an actual Paper plugin disable/re-enable was not tested (the live smoke test was a full server start/stop).

Do not publish a resource-pack ZIP yet: the repository contains PNG assets of unresolved provenance. The embedded `TheSiftGeyser.jar.embedded` also has unknown provenance; it remains excluded from plugin artifacts and must not be released pending owner confirmation.

The note melody currently opens the temporary Rift feature, not the mod-style
Ancient City dimension portal. Successful melodies share the configured
server-wide cooldown (`rift.portal-song-cooldown-seconds`, default 1800);
unfinished melodies expire after `rift.portal-song-timeout-seconds` (default
60) and are also cleared when a player quits. The cooldown is runtime-only and
resets on a full server restart. With strict providers unresolved, an Overworld
Rift is rejected so it cannot activate and award items without a usable Sift
destination.
