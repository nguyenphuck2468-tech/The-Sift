"""One-off P1-G sync: align content-spec.json with the runtime (code mirrors the mod).

- Mob statlines/type: blub RABBIT, dark_sniffer 40/7, singer 20/0, echo_golem 34/0,
  the_sifter marked fan-made (plugin boss variant).
- Add core herd mob `sifter` (40/10) and fan-made boss `spire_tyrant` (220/24).
- Add missing meat items; mark plugin-extension/fan-made meats.
"""
import json
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "integrations" / "content-spec.json"
spec = json.loads(p.read_text(encoding="utf-8"))
mobs = spec["mobs"]
items = spec["items"]


def mob(mid):
    return next(m for m in mobs if m["id"] == mid)


def item(iid):
    return next(i for i in items if i["id"] == iid)


ids = {m["id"] for m in mobs}

# --- mob statline parity with SiftMobFactory/modMobStats + managers (review P1/P2) ---
mob("blub")["type"] = "RABBIT"  # code fallback in summonCreature; spec AXOLOTL drifted
mob("dark_sniffer")["health"] = 40
mob("dark_sniffer")["damage"] = 7
mob("singer")["health"] = 20
mob("singer")["damage"] = 0
mob("echo_golem")["health"] = 34
mob("echo_golem")["damage"] = 0
mob("the_sifter")["fanmade"] = True  # plugin boss variant, not a mod mob

# --- missing mob IDs the runtime actually spawns (audit_spec_sync.py) ---
if "sifter" not in ids:
    mobs.append({"id": "sifter", "type": "RAVAGER", "display": "&dSifter",
                 "health": 40, "damage": 10})
if "spire_tyrant" not in ids:
    mobs.append({"id": "spire_tyrant", "type": "RAVAGER", "display": "&5&lSpire Tyrant",
                 "health": 220, "damage": 24, "fanmade": True})

# --- missing item IDs (meats) ---
item_ids = {i["id"] for i in items}
new_items = [
    {"id": "raw_sifter_meat", "material": "BEEF", "name": "Raw Sifter Meat",
     "model": "minecraft:item/beef"},
    {"id": "cooked_sifter_meat", "material": "COOKED_BEEF", "name": "Cooked Sifter Meat",
     "model": "minecraft:item/cooked_beef"},
    {"id": "blub_meat", "material": "MUTTON", "name": "Blub Meat",
     "model": "minecraft:item/mutton", "fanmade": True},
    {"id": "cooked_blub_meat", "material": "COOKED_MUTTON", "name": "Cooked Blub Meat",
     "model": "minecraft:item/cooked_mutton", "fanmade": True},
]
for entry in new_items:
    if entry["id"] not in item_ids:
        items.append(entry)

# --- fan-made flags the filter missed (Loomer is fan-made content) ---
item("raw_loomer")["fanmade"] = True
item("cooked_loomer")["fanmade"] = True

p.write_text(json.dumps(spec, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

# report
mobs_after = {m["id"]: m for m in spec["mobs"]}
print("mobs:", len(spec["mobs"]), "items:", len(spec["items"]))
for mid in ("blub", "dark_sniffer", "singer", "echo_golem", "sifter", "spire_tyrant", "the_sifter"):
    m = mobs_after[mid]
    print(f"  {mid:15s} {m['type']:12s} hp={m['health']} dmg={m['damage']} fanmade={m.get('fanmade')}")
