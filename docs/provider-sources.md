# Public provider-name and API sources

These references were read as public documentation/source only. No third-party plugin JAR was downloaded or unpacked. `plugin.yml` and Paper `dependencies` declarations are optional; no provider is a hard dependency.

| Declared plugin/service key | Public evidence for product/API name | Runtime behavior used by The Sift |
|---|---|---|
| `Nexo` | [Nexo docs](https://docs.nexomc.com/) and [Nexo commands](https://docs.nexomc.com/general-usage/commands) | Item API + custom block API through reflection. |
| `ItemsAdder` | [ItemsAdder API examples](https://wiki.itemsadder.com/developers/java-api/examples/), [CustomEntity registry API](https://lonedev6.github.io/API-ItemsAdder/dev/lone/itemsadder/api/CustomEntity.html) | CustomStack, CustomBlock, CustomEntity `isInRegistry` and spawn reflection paths. |
| `Oraxen` | [Oraxen official API docs](https://docs.oraxen.com/developers/api), [public build descriptor names Oraxen](https://github.com/oraxen/oraxen/blob/master/build.gradle.kts) | Item and block API through reflection. |
| `MythicMobs` | [MythicCraft API documentation](https://wiki.mythiccraft.io/mythicmobs/API), [mob file locations and `/mm reload`](https://wiki.mythiccraft.io/mythicmobs/Guides/%28Step-2%29-Files-And-Directories) | Mythic mob registry/spawn API through reflection. |
| `EliteMobs` | [Public plugin.yml](https://github.com/MagmaGuy/EliteMobs/blob/master/src/main/resources/plugin.yml) | Configured boss spawn bridge. Strict readiness remains unresolved until a side-effect-free registry check is implemented. |
| `ModelEngine` | [MythicCraft ModelEngine API](https://wiki.mythiccraft.io/modelengine/API) | Optional model attachment only; it does not qualify as a mob provider. |
| `Geyser-Spigot` | [Official Spigot plugin descriptor](https://github.com/GeyserMC/Geyser/blob/master/bootstrap/spigot/src/main/resources/plugin.yml) (name template `${name}-Spigot`; official artifact is `Geyser-Spigot.jar`) | Presence/version in doctor; no dimension gate. |
| `floodgate` | [Official Floodgate setup](https://geysermc.org/wiki/floodgate/setup/), [official source repo](https://github.com/GeyserMC/Floodgate) | Presence/version in doctor; no dimension gate. |
| `ViaVersion` | [Official ViaVersion site](https://viaversion.com/), [official repository](https://github.com/ViaVersion/ViaVersion) | Presence/version in doctor; no dimension gate. |
| `ViaBackwards` | [ViaVersion project/releases](https://github.com/ViaVersion/ViaBackwards) | Presence/version in doctor; no dimension gate. |
| `ViaRewind` | [ViaVersion project/releases](https://github.com/ViaVersion/ViaRewind) | Presence/version in doctor; no dimension gate. |
| `ResourcePackManager` | [Public plugin.yml](https://github.com/MagmaGuy/ResourcePackManager/blob/master/resourcepackmanager-bukkit/src/main/resources/plugin.yml), [public Java API](https://wiki.nightbreak.io/ResourcePackManager/api/) | Name/version detection and doctor only; API-based pack registration is deferred. No analytics. |
| `SiftItemProvider`, `SiftBlockProvider`, `SiftMobProvider` | This repository's [`foundation/api`](../src/main/java/com/gamepathics/thesift/foundation/api) interfaces | Bukkit services; selection uses `getProviderId()` and strict validation uses `supports(id)`. |

The Geyser documentation says Geyser emulates the current Java server version and requires ViaVersion when the server is older. Its current supported Java version is 26.3, so Paper 26.3 does not require ViaVersion just for Geyser. Floodgate is optional unless Bedrock users must authenticate without a Java account. See [setup](https://geysermc.org/wiki/geyser/setup/), [supported versions](https://geysermc.org/wiki/geyser/supported-versions/), and [Floodgate setup](https://geysermc.org/wiki/floodgate/setup/).

**Evidence limitation:** ItemsAdder, MythicMobs, ModelEngine, Geyser-Spigot, ViaVersion, ViaBackwards, and ViaRewind do not all publish Bukkit `plugin.yml` files in a public source repository. Their canonical keys were matched to official product/API documentation and official platform artifacts where public. `/sift doctor` reports the actual installed plugin name/version so a server can expose a renamed or platform-specific installation.
