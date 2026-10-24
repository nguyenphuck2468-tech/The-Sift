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
`run/paper-26.3`. It has not yet been verified with an interactive operator,
third-party providers, or a Bedrock client. Do not accept the Minecraft server
EULA automatically on behalf of the server owner; if Gradle stops at the EULA
step, review `run/paper-26.3/eula.txt` and start the disposable server yourself
after accepting it.

Local automated evidence for the provider bridge and strict gate is recorded
in `IMPLEMENTATION_REPORT.md`; it does not establish that a third-party
provider has parsed its generated YAML or works on a live server.

On the test server, run `/sift give soul_block <player>`, `/sift summon singer <player>`, `/sift summon echo_golem <player>`, and `/sift give sonorous_deepslate <player>`. Place Sonorous Deepslate and a normal vanilla note block on top. Right-click to tune; left-click the sequence `1, 3, 7, 6, 5, 2, 4, 8` to open a Rift. Provider-owned note blocks are left to their provider. Then run `/sift doctor`, verify provider logs and each mandatory content ID, check `/plugins` for duplicate TheSift installations, and inspect `logs/latest.log` for API errors. No real-provider server test is claimed until recorded in `docs/parity-matrix.md`.

When upgrading from a release that used vanilla fallback for Sonorous Deepslate, existing vanilla blocks that were only indexed by the legacy world-level scan are intentionally not migrated: their identity is ambiguous. Re-place them using the provider-defined Sonorous Deepslate item to create new persistent station markers. Provider-defined blocks found in the legacy index are migrated when their chunks are loaded; the migration-complete flag is persisted at world scope.

Disabling The Sift while players are in its world attempts to teleport them to the first loaded non-Sift world spawn. The plugin does not unload the dimension. This has unit/build coverage only; an actual Paper plugin disable/re-enable was not run because the disposable server EULA remains unaccepted.

Do not publish a resource-pack ZIP yet: the repository contains PNG assets of unresolved provenance. The embedded `TheSiftGeyser.jar.embedded` also has unknown provenance; it remains excluded from plugin artifacts and must not be released pending owner confirmation.
