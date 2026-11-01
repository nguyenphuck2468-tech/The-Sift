# Credits & asset provenance

The bundled resource pack (`src/main/resources/pack`) carries the reference mod's art and
audio so a Paper server looks and sounds like Mielon's The Sift without a mod client. All
of it is MIT-licensed; the import that brought it in is scripted at
`scripts/import_mod_assets.py` (namespace `the_sift` → `thesift`, GeckoLib geo skipped).

## People

| Contributor | Contribution |
| --- | --- |
| **Mielon** | The Sift mod — design, code, art, audio (MIT) |
| **ImpSteve** | Dark Sniffer texture |
| **Fuzja Jądrowa** | Rift music disc (`fuzja_jadrowa_rift.ogg`) |
| **Echo_Dev** | Wellspring remix (`music_disc_wellspring.ogg`) |
| **StrawberrySushi_** | Korean localization |

## Sources this repository pulls from

- [Arta48/Mielons-The-Sift](https://github.com/Arta48/Mielons-The-Sift) — the original
  Fabric mod (MIT). License: `Mielon-MIT.txt`.
- [MikiKox1/mielons-the-sift-neoforge-port](https://github.com/MikiKox1/mielons-the-sift-neoforge-port)
  — the unofficial NeoForge port for MC 26.1.2/26.2/26.3 (MIT). **Primary asset source**:
  its 26.3-format item definitions, models, blockstates, particles, shaders and sounds are
  what this pack imports. License: `Mielon-NeoForge-Port-MIT.txt`; upstream provenance and
  AI-assistance disclosure: `NeoForge-Port-SOURCE_PROVENANCE.md` (+ `AI_DISCLOSURE.md`
  upstream).
- [byMr712/MielonsTheSift-1.21.4-MinecraftMod](https://github.com/byMr712/MielonsTheSift-1.21.4-MinecraftMod)
  — a 1.21.4 build of the mod (MIT). License: `byMr712-Build-MIT.txt`.
- [KarldasLamaGaming/TheSiftPaperPlugin](https://github.com/KarldasLamaGaming/TheSiftPaperPlugin)
  — an independent Paper port with its own resource pack (MIT). Used as a reference for
  Paper-side pack structure; no files copied from it. License:
  `KarldasLamaGaming-PaperPlugin-MIT.txt`.
- [Carl-AI-Lab/sonorous-deepslate-reference](https://github.com/Carl-AI-Lab/sonorous-deepslate-reference)
  — a cited JSON reference for the Sonorous Deepslate mechanic (MIT). License:
  `Carl-AI-Lab-Reference-MIT.txt`.

## What is wired into gameplay

- `thesift:block.sonorous_deepslate.sound_1..8` — the eight per-note Sonorous recordings,
  played by both the Ancient City gate and the mod-port console when notes are clicked.
- `thesift:block.sift_portal.trigger` — plays when a rift begins to tear open.
- The full block/item/entity texture and model set, particle definitions and post-effect
  shaders ship inside the plugin jar; `ResourcePackBridge` materialises them into
  `plugins/TheSift/pack/thesift-resource-pack.zip` and can auto-send it to players.
- GeckoLib geometry/animation files are intentionally not bundled: they only run inside the
  Fabric/Forge mod client. Mob stand-ins remain vanilla entities (see
  `docs/parity-matrix.md`).

All names and ideas of The Sift belong to their authors; this project is not affiliated
with Mojang or Microsoft.
