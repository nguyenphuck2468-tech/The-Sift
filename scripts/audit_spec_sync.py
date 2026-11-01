"""One-off audit: content IDs referenced by Java runtime vs integrations/content-spec.json.

Extracts plausible content-ID string literals from the foundation sources and reports
which ones are missing from the spec (and which spec IDs are never referenced).
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java" / "com" / "gamepathics" / "thesift"

spec = json.loads((ROOT / "integrations" / "content-spec.json").read_text(encoding="utf-8"))
spec_ids = {e["id"] for e in spec["items"] + spec["blocks"] + spec["mobs"]}
mob_ids = {e["id"] for e in spec["mobs"]}
item_ids = {e["id"] for e in spec["items"]}

# Patterns where a content ID literal is passed to the bridge/registry.
CALL_PATTERNS = [
    re.compile(r'\.is\([^,]+,\s*"([a-z0-9_]+)"'),
    re.compile(r'\.isContentEnabled\("([a-z0-9_]+)"'),
    re.compile(r'\.forId\("([a-z0-9_]+)"'),
    re.compile(r'\.spawn\("([a-z0-9_]+)"'),
    re.compile(r'\.place\("([a-z0-9_]+)"'),
    re.compile(r'\.isBlock\("([a-z0-9_]+)"'),
    re.compile(r'placeBlock\([^,]+,\s*"([a-z0-9_]+)"'),
    re.compile(r'\.create\([^,]+,\s*"([a-z0-9_]+)"'),
]
# summonCreature/spec-id switch cases.
CASE_PATTERN = re.compile(r'case "([a-z0-9_]+)" ->')
MOB_SWITCH_IDS = {"blub", "dark_sniffer", "bulb", "loomer", "wisp", "violet_wisp", "koi",
                  "hollow_bulb", "glowbell", "thornback", "glimmerwing", "the_sifter",
                  "spire_tyrant", "sifter", "frost_stalker", "sunspore", "drifter",
                  "shardling", "prism_warden", "prismite", "rift_walker", "sift_wastes_stalker"}

referenced = {}
for path in SRC.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    for pattern in CALL_PATTERNS:
        for match in pattern.finditer(text):
            referenced.setdefault(match.group(1), set()).add(path.name)

# Switch-case mob ids that reach mobFactory.spawn via summonCreature.
for path in SRC.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "mobFactory.spawn(id, location, type)" in text:
        for match in CASE_PATTERN.finditer(text):
            referenced.setdefault(match.group(1), set()).add(path.name + " (case)")

missing = sorted(set(referenced) - spec_ids)
unused = sorted(spec_ids - set(referenced))

print("=== Referenced in Java but MISSING from spec ===")
for mid in missing:
    print(f"  {mid:36s} <- {sorted(referenced[mid])}")
print()
print("=== In spec but never referenced by the audited patterns (informational) ===")
for uid in unused:
    print(f"  {uid}")
