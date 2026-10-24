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

## Bedrock (detection level only)

For a Paper 26.3 server, install `Geyser-Spigot` for Bedrock connections. Floodgate is optional and enables Bedrock account authentication without a Java account. Current Geyser documentation says its Java client emulation matches Java 26.3; ViaVersion is needed when the Paper server is older than the Java version Geyser emulates. Therefore Paper 26.3 does not need ViaVersion solely for current Geyser compatibility. ViaBackwards/ViaRewind are not required for this 26.3 baseline; they are only relevant when the server intentionally accepts older client protocols through ViaVersion. See the official [Geyser setup](https://geysermc.org/wiki/geyser/setup/), [supported versions](https://geysermc.org/wiki/geyser/supported-versions/), and [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/).

`providers.bedrock-enabled` defaults to `false`. Turn it on if the server intends to offer Bedrock access; `/sift doctor` then warns when Geyser, Floodgate, or ViaVersion is absent. These warnings never block dimension creation. Bedrock resource pack generation, custom item/block translation, and Geyser mappings are not implemented yet.

## Repeatable local checks

```powershell
.\gradlew.bat test
.\gradlew.bat generateProviderDefinitions
.\gradlew.bat runServer
```

On the test server, run `/sift doctor`, then verify the provider logs and each mandatory content ID. Also check `/plugins` for duplicate TheSift installations and inspect `logs/latest.log` for API errors. No real-provider server test is claimed by the repository until recorded as such in `docs/parity-matrix.md`.

Do not publish a resource-pack ZIP yet: the repository contains PNG assets of unresolved provenance. The embedded `TheSiftGeyser.jar.embedded` also has unknown provenance; it remains excluded from plugin artifacts and must not be released pending owner confirmation.
