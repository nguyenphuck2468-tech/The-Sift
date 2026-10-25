#!/usr/bin/env python3
"""Port the terrain data of Mielon's The Sift (MIT) into the plugin's dimension datapack.

Copies worldgen/noise, density_function, carver and noise_settings, rewrites the
`the_sift:` namespace to `thesift:`, maps the mod's custom terrain blocks to vanilla
stand-ins (a datapack cannot place provider blocks), replaces the dimension's biome
source with the mod's 10-biome multi-noise table and attaches the mod's carver list to
the ten mod-named biomes. Usage: port_mod_terrain.py <mod-data-root> [plugin-root]
"""
import json, re, sys, shutil
from pathlib import Path

SRC = Path(sys.argv[1]) / "the_sift"
ROOT = Path(sys.argv[2] if len(sys.argv) > 2 else ".")
DST = ROOT / "src/main/resources/sift_dimension_datapack/data/thesift"

# Terrain stand-ins: datapack noise settings can only use vanilla blocks.
BLOCKS = {
    "the_sift:siftslate": "minecraft:deepslate",
    "the_sift:siftslate_growth": "minecraft:moss_block",
    "the_sift:healthy_sculk": "minecraft:sculk",
    "the_sift:dry_healthy_sculk": "minecraft:pale_moss_block",
}
MOD_BIOMES = ["ichor_snowy_peaks", "overgrown_clearing", "overgrown_forest", "overgrown_forest_slopes",
              "overgrown_peaks", "overgrown_slopes", "sift_deep_dark", "sift_wastes",
              "siftslate_peaks", "siftslate_slopes"]

def convert(text):
    for k, v in BLOCKS.items():
        text = text.replace(f'"{k}"', f'"{v}"')
    return text.replace("the_sift:", "thesift:")

def port_dir(sub):
    out = DST / "worldgen" / sub
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for f in sorted((SRC / "worldgen" / sub).glob("*.json")):
        data = json.loads(convert(f.read_text()))
        (out / f.name).write_text(json.dumps(data, indent=2) + "\n")
        n += 1
    return n

counts = {s: port_dir(s) for s in ("noise", "density_function", "carver", "noise_settings")}

dim_path = DST / "dimension/the_sift.json"
dim = json.loads(dim_path.read_text())
mod_dim = json.loads(convert((SRC / "dimension/the_sift.json").read_text()))
dim["generator"]["settings"] = "thesift:the_sift"
dim["generator"]["biome_source"] = mod_dim["generator"]["biome_source"]
dim_path.write_text(json.dumps(dim, indent=2) + "\n")

dt_path = DST / "dimension_type/the_sift.json"
dt = json.loads(dt_path.read_text())
dt.update({"min_y": 0, "height": 256, "logical_height": 256})
dt_path.write_text(json.dumps(dt, indent=2) + "\n")

carvers = [c.replace("the_sift:", "thesift:") for c in
           json.loads((SRC / "worldgen/biome/overgrown_forest.json").read_text())["carvers"]]
for b in MOD_BIOMES:
    p = DST / f"worldgen/biome/{b}.json"
    d = json.loads(p.read_text())
    d["carvers"] = carvers
    p.write_text(json.dumps(d, indent=2) + "\n")
print(counts, "carvers:", carvers)
