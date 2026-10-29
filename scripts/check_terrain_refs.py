#!/usr/bin/env python3
"""Verify every thesift: reference in the dimension datapack resolves to a bundled file."""
import json, re, sys
from pathlib import Path
base = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/sift_dimension_datapack/data/thesift")
registries = ["noise", "density_function", "carver", "noise_settings", "biome", "feature", "placed_feature",
              "structure", "structure_set", "template_pool"]
have_by = {r: {f"thesift:{f.stem}" for f in (base / "worldgen" / r).glob("*.json")} for r in registries}
# Structures ship as NBT templates too; template_pool entries reference them by bare ID.
have_by["structure"] |= {f"thesift:{f.stem}" for f in (base / "worldgen" / "structure").glob("*.nbt")}
# tag_match predicates resolve against bundled block tags.
have_by["tags/block"] = {f"thesift:{f.stem}" for f in (base / "tags" / "block").glob("*.json")}
have = set().union(*have_by.values())
IGNORE_NAMES = {"thesift:bedrock_floor", "thesift:the_sift"}  # random_name / dimension keys
errors = []
for f in base.rglob("*.json"):
    text = f.read_text()
    json.loads(text)  # must parse
    for ref in set(re.findall(r'"(thesift:[a-z0-9_/]+)"', text)):
        if ref in IGNORE_NAMES or ref in have:
            continue
        # worldgen features/particles/entities referenced from biomes are checked elsewhere
        if "/worldgen/biome/" in str(f) or "/dimension_type/" in str(f):
            continue
        errors.append(f"{f.relative_to(base)} -> {ref}")
dim = json.loads((base / "dimension/the_sift.json").read_text())
for b in dim["generator"]["biome_source"]["biomes"]:
    if b["biome"] not in have_by["biome"]:
        errors.append(f"dimension biome missing: {b['biome']}")
if dim["generator"]["settings"] not in have_by["noise_settings"]:
    errors.append("dimension settings missing")
for f in (base / "worldgen/biome").glob("*.json"):
    for step in json.loads(f.read_text()).get("features", []):
        for ref in step:
            if ref not in have_by["placed_feature"]:
                errors.append(f"biome {f.stem} feature missing: {ref}")
for f in (base / "worldgen/placed_feature").glob("*.json"):
    ref = json.loads(f.read_text())["feature"]
    if ref not in have_by["feature"]:
        errors.append(f"placed_feature {f.stem} -> missing feature {ref}")
print("files:", {r: len(v) for r, v in have_by.items()})
print("ERRORS:" if errors else "OK: all references resolve")
for e in errors: print(" ", e)
sys.exit(1 if errors else 0)
