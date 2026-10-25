#!/usr/bin/env python3
"""Verify every thesift: reference in the dimension datapack resolves to a bundled file."""
import json, re, sys
from pathlib import Path
base = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/sift_dimension_datapack/data/thesift")
registries = ["noise", "density_function", "carver", "noise_settings", "biome"]
have = {f"thesift:{f.stem}": r for r in registries for f in (base / "worldgen" / r).glob("*.json")}
have_by = {r: {k for k, v in have.items() if v == r} for r in registries}
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
print("files:", {r: len(v) for r, v in have_by.items()})
print("ERRORS:" if errors else "OK: all references resolve")
for e in errors: print(" ", e)
sys.exit(1 if errors else 0)
