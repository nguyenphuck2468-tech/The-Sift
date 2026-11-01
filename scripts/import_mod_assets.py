"""ASSET import: bring the reference mod's assets into the bundled resource pack.

Source: MikiKox1/mielons-the-sift-neoforge-port (MIT) — the maintained 26.3-format fork of
Arta48/Mielons-The-Sift (MIT, by Mielon; contributors ImpSteve, Fuzja Jądrowa,
StrawberrySushi_, Echo_Dev — see SOURCE_PROVENANCE.md upstream and
THIRD_PARTY_LICENSES/CREDITS.md here).

All files are copied under OUR pack namespace (`thesift`) — every textual reference to the
mod namespace `the_sift` is rewritten so the pack stays internally consistent. Binaries
(.ogg/.png) copy verbatim. GeckoLib geo/animations are skipped (Fabric-only runtime).
"""
import re
import shutil
import sys
from pathlib import Path

SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else None
assert SRC and SRC.exists(), "pass the port assets path: python import_mod_assets.py <port>/src/main/resources/assets/the_sift"
DST = Path(__file__).resolve().parents[1] / "src/main/resources/pack/assets/thesift"

DIRS = ["textures", "models", "blockstates", "items", "particles", "shaders", "sounds", "lang", "equipment"]
TEXT_SUFFIXES = {".json", ".mcmeta", ".txt"}
SKIP_DIRS = {"geckolib"}
SKIP_FILES = {"icon.png"}

# the_sift NOT followed by an identifier character = namespace usage, not the_sifter.
NAMESPACE_RE = re.compile(r"(?<![a-z0-9_])the_sift(?![a-z0-9_])")

copied = rewritten = skipped = 0
for directory in DIRS:
    source = SRC / directory
    if not source.exists():
        print(f"skip missing {directory}")
        continue
    for path in source.rglob("*"):
        rel = path.relative_to(source)
        if any(part in SKIP_DIRS for part in rel.parts):
            skipped += 1
            continue
        if path.name in SKIP_FILES:
            skipped += 1
            continue
        target = DST / directory / rel
        if path.is_dir():
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        if path.suffix.lower() in TEXT_SUFFIXES:
            text = path.read_text(encoding="utf-8")
            # Namespace-only rewrite: `the_sift:`/`the_sift/`/`the_sift.` become `thesift`,
            # but the content id `the_sifter` (the substring "the_sift" + "er") must survive.
            rewritten_text = NAMESPACE_RE.sub("thesift", text)
            target.write_text(rewritten_text, encoding="utf-8")
            rewritten += 1
        else:
            shutil.copy2(path, target)
            copied += 1

# sounds.json lives at the namespace root.
sounds_json = SRC / "sounds.json"
if sounds_json.exists():
    target = DST / "sounds.json"
    target.write_text(NAMESPACE_RE.sub("thesift", sounds_json.read_text(encoding="utf-8")),
                      encoding="utf-8")
    rewritten += 1

print(f"binaries copied: {copied}; text files rewritten: {rewritten}; skipped: {skipped}")

# Sanity: no stray mod-namespace references remain in textual files under the pack.
strays = []
for path in DST.rglob("*"):
    if path.is_file() and path.suffix.lower() in TEXT_SUFFIXES:
        if NAMESPACE_RE.search(path.read_text(encoding="utf-8")):
            strays.append(str(path))
print("stray the_sift references:", strays if strays else "none")
