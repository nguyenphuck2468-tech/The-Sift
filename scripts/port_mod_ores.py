#!/usr/bin/env python3
"""Port the mod's Siftslate ore features (coal, coal surface, diamond, emerald) into the dimension datapack.

Vein size, count, height range and filters come from the mod unchanged. The ore blocks are
STAND-INS: vanilla deepslate ores replacing the vanilla deepslate that the ported terrain
uses as its base block, because a datapack cannot place provider blocks. Charoite and Siftite
ore stay Java/provider generated (SiftEcologyManager). Usage: port_mod_ores.py <mod-data-root> [plugin-root]
"""
import json, sys
from pathlib import Path

SRC = Path(sys.argv[1]) / "the_sift/worldgen"
ROOT = Path(sys.argv[2] if len(sys.argv) > 2 else ".")
DST = ROOT / "src/main/resources/sift_dimension_datapack/data/thesift/worldgen"
ORES = {  # feature -> vanilla stand-in block
    "siftslate_coal_ore": "minecraft:deepslate_coal_ore",
    "siftslate_coal_ore_surface": "minecraft:deepslate_coal_ore",
    "siftslate_diamond_ore": "minecraft:deepslate_diamond_ore",
    "siftslate_emerald_ore": "minecraft:deepslate_emerald_ore",
}
UNDERGROUND_ORES_STEP = 6
fix = lambda t: t.replace("the_sift:", "thesift:")

for sub in ("feature", "placed_feature"):
    (DST / sub).mkdir(parents=True, exist_ok=True)
for name, block in ORES.items():
    feat = json.loads((SRC / f"feature/{name}.json").read_text())
    feat["targets"] = [{
        "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:deepslate"},
        "state": {"id": block}}]
    (DST / f"feature/{name}.json").write_text(json.dumps(json.loads(fix(json.dumps(feat))), indent=2) + "\n")
    placed = json.loads(fix((SRC / f"placed_feature/{name}.json").read_text()))
    (DST / f"placed_feature/{name}.json").write_text(json.dumps(placed, indent=2) + "\n")

for f in sorted((SRC / "biome").glob("*.json")):
    mod = json.loads(f.read_text())
    wanted = [fix(x) for x in mod["features"][UNDERGROUND_ORES_STEP] if x.split(":")[1] in ORES]
    if not wanted:
        continue
    p = DST / "biome" / f.name
    d = json.loads(p.read_text())
    steps = d.get("features") or []
    steps += [[] for _ in range(11 - len(steps))]
    steps[UNDERGROUND_ORES_STEP] = [x for x in steps[UNDERGROUND_ORES_STEP] if x not in wanted] + wanted
    d["features"] = steps
    p.write_text(json.dumps(d, indent=2) + "\n")
    print(f.stem, wanted)
