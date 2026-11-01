"""P2-A carrier mapping: align vanilla stand-in carriers with the reference mod (review P2).

Only the flagged carriers change; the review accepted Siftslate→Deepslate, the Sift tree
sift_log/sift_planks/sift_leaves→Cherry, and Siftite gear→Netherite as reasonable, so those
stay. Every replacement is an exact needle so a drifted file fails loudly here.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path, pairs, required=True):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count == 0:
            if required:
                raise SystemExit(f"NEEDLE NOT FOUND in {path}: {old[:90]!r}")
            continue
        if count > 1:
            raise SystemExit(f"NEEDLE AMBIGUOUS ({count}x) in {path}: {old[:90]!r}")
        text = text.replace(old, new)
    p.write_text(text, encoding="utf-8")
    print(f"patched {path}")


# --- SiftiteContent: recipes + carriers -----------------------------------------------
willow_cases = [
    ('case "overgrown_willow_log" -> item(Material.CHERRY_LOG', 'case "overgrown_willow_log" -> item(Material.OAK_LOG'),
    ('case "overgrown_willow_wood" -> item(Material.CHERRY_WOOD', 'case "overgrown_willow_wood" -> item(Material.OAK_WOOD'),
    ('case "stripped_overgrown_willow_log" -> item(Material.STRIPPED_CHERRY_LOG', 'case "stripped_overgrown_willow_log" -> item(Material.STRIPPED_OAK_LOG'),
    ('case "stripped_overgrown_willow_wood" -> item(Material.STRIPPED_CHERRY_WOOD', 'case "stripped_overgrown_willow_wood" -> item(Material.STRIPPED_OAK_WOOD'),
    ('case "overgrown_willow_planks" -> item(Material.CHERRY_PLANKS', 'case "overgrown_willow_planks" -> item(Material.OAK_PLANKS'),
    ('case "overgrown_willow_stairs" -> item(Material.CHERRY_STAIRS', 'case "overgrown_willow_stairs" -> item(Material.OAK_STAIRS'),
    ('case "overgrown_willow_slab" -> item(Material.CHERRY_SLAB', 'case "overgrown_willow_slab" -> item(Material.OAK_SLAB'),
    ('case "overgrown_willow_fence" -> item(Material.CHERRY_FENCE', 'case "overgrown_willow_fence" -> item(Material.OAK_FENCE'),
    ('case "overgrown_willow_fence_gate" -> item(Material.CHERRY_FENCE_GATE', 'case "overgrown_willow_fence_gate" -> item(Material.OAK_FENCE_GATE'),
    ('case "overgrown_willow_door" -> item(Material.CHERRY_DOOR', 'case "overgrown_willow_door" -> item(Material.OAK_DOOR'),
    ('case "overgrown_willow_trapdoor" -> item(Material.CHERRY_TRAPDOOR', 'case "overgrown_willow_trapdoor" -> item(Material.OAK_TRAPDOOR'),
    ('case "overgrown_willow_pressure_plate" -> item(Material.CHERRY_PRESSURE_PLATE', 'case "overgrown_willow_pressure_plate" -> item(Material.OAK_PRESSURE_PLATE'),
    ('case "overgrown_willow_button" -> item(Material.CHERRY_BUTTON', 'case "overgrown_willow_button" -> item(Material.OAK_BUTTON'),
    ('case "overgrown_willow_sign" -> item(Material.CHERRY_SIGN', 'case "overgrown_willow_sign" -> item(Material.OAK_SIGN'),
    ('case "overgrown_willow_hanging_sign" -> item(Material.CHERRY_HANGING_SIGN', 'case "overgrown_willow_hanging_sign" -> item(Material.OAK_HANGING_SIGN'),
    ('case "overgrown_willow_boat" -> item(Material.CHERRY_BOAT', 'case "overgrown_willow_boat" -> item(Material.OAK_BOAT'),
    ('case "overgrown_willow_chest_boat" -> item(Material.CHERRY_CHEST_BOAT', 'case "overgrown_willow_chest_boat" -> item(Material.OAK_CHEST_BOAT'),
    ('case "overgrown_willow_foliage" -> item(Material.CHERRY_LEAVES', 'case "overgrown_willow_foliage" -> item(Material.OAK_LEAVES'),
    ('case "overgrown_willow_sapling" -> item(Material.CHERRY_SAPLING', 'case "overgrown_willow_sapling" -> item(Material.OAK_SAPLING'),
    ('case "overgrown_willow_shelf" -> item(Material.CHERRY_SHELF', 'case "overgrown_willow_shelf" -> item(Material.OAK_SHELF'),
    ('case "sift_log", "overgrown_willow" -> Material.CHERRY_LOG;',
     'case "sift_log" -> Material.CHERRY_LOG;\n            case "overgrown_willow" -> Material.OAK_LOG;'),
    ('case "overgrown_willow_log" -> Material.CHERRY_LOG;', 'case "overgrown_willow_log" -> Material.OAK_LOG;'),
    ('case "overgrown_willow_wood" -> Material.CHERRY_WOOD;', 'case "overgrown_willow_wood" -> Material.OAK_WOOD;'),
    ('case "stripped_overgrown_willow_log" -> Material.STRIPPED_CHERRY_LOG;', 'case "stripped_overgrown_willow_log" -> Material.STRIPPED_OAK_LOG;'),
    ('case "stripped_overgrown_willow_wood" -> Material.STRIPPED_CHERRY_WOOD;', 'case "stripped_overgrown_willow_wood" -> Material.STRIPPED_OAK_WOOD;'),
    ('case "overgrown_willow_planks" -> Material.CHERRY_PLANKS;', 'case "overgrown_willow_planks" -> Material.OAK_PLANKS;'),
    ('case "overgrown_willow_stairs" -> Material.CHERRY_STAIRS;', 'case "overgrown_willow_stairs" -> Material.OAK_STAIRS;'),
    ('case "overgrown_willow_slab" -> Material.CHERRY_SLAB;', 'case "overgrown_willow_slab" -> Material.OAK_SLAB;'),
    ('case "overgrown_willow_fence" -> Material.CHERRY_FENCE;', 'case "overgrown_willow_fence" -> Material.OAK_FENCE;'),
    ('case "overgrown_willow_fence_gate" -> Material.CHERRY_FENCE_GATE;', 'case "overgrown_willow_fence_gate" -> Material.OAK_FENCE_GATE;'),
    ('case "overgrown_willow_door" -> Material.CHERRY_DOOR;', 'case "overgrown_willow_door" -> Material.OAK_DOOR;'),
    ('case "overgrown_willow_trapdoor" -> Material.CHERRY_TRAPDOOR;', 'case "overgrown_willow_trapdoor" -> Material.OAK_TRAPDOOR;'),
    ('case "overgrown_willow_pressure_plate" -> Material.CHERRY_PRESSURE_PLATE;', 'case "overgrown_willow_pressure_plate" -> Material.OAK_PRESSURE_PLATE;'),
    ('case "overgrown_willow_button" -> Material.CHERRY_BUTTON;', 'case "overgrown_willow_button" -> Material.OAK_BUTTON;'),
    ('case "overgrown_willow_sign" -> Material.CHERRY_SIGN;', 'case "overgrown_willow_sign" -> Material.OAK_SIGN;'),
    ('case "overgrown_willow_hanging_sign" -> Material.CHERRY_HANGING_SIGN;', 'case "overgrown_willow_hanging_sign" -> Material.OAK_HANGING_SIGN;'),
    ('case "overgrown_willow_foliage" -> Material.CHERRY_LEAVES;', 'case "overgrown_willow_foliage" -> Material.OAK_LEAVES;'),
    ('case "overgrown_willow_sapling" -> Material.CHERRY_SAPLING;', 'case "overgrown_willow_sapling" -> Material.OAK_SAPLING;'),
    ('case "overgrown_willow_shelf" -> Material.CHERRY_SHELF;', 'case "overgrown_willow_shelf" -> Material.OAK_SHELF;'),
]
siftite_pairs = siftite_rest = [
    # Charoite: code already used LAPIS_LAZULI as the item carrier (recipes) — unify the
    # ingredient pair and the forId switch with it; ore carrier becomes deepslate lapis ore.
    ("\" C \", \"CAC\", \" C \", 'C', Material.AMETHYST_SHARD, 'A', Material.AMETHYST_BLOCK)",
     "\" C \", \"CAC\", \" C \", 'C', Material.LAPIS_LAZULI, 'A', Material.DEEPSLATE_LAPIS_ORE)"),
    ('case "charoite_ore" -> item(Material.AMETHYST_BLOCK, id, "Charoite Ore");',
     'case "charoite_ore" -> item(Material.DEEPSLATE_LAPIS_ORE, id, "Charoite Ore");'),
    ('case "siftite_ore" -> item(Material.ANCIENT_DEBRIS, id, "Siftite Ore");',
     'case "siftite_ore" -> item(Material.DEEPSLATE_DIAMOND_ORE, id, "Siftite Ore");'),
    ('case "siftslate", "sonorous_deepslate" -> Material.DEEPSLATE;',
     'case "siftslate" -> Material.DEEPSLATE;\n            case "sonorous_deepslate" -> Material.REINFORCED_DEEPSLATE;'),
    ('case "soul_block" -> Material.SOUL_SAND;', 'case "soul_block" -> Material.SOUL_SOIL;'),
    ('case "shard_ore", "charoite_ore" -> Material.AMETHYST_BLOCK;',
     'case "shard_ore" -> Material.AMETHYST_BLOCK;\n            case "charoite_ore" -> Material.DEEPSLATE_LAPIS_ORE;'),
    ('case "siftite_ore" -> Material.ANCIENT_DEBRIS;', 'case "siftite_ore" -> Material.DEEPSLATE_DIAMOND_ORE;'),
    ('case "sift_shard", "spire_fragment", "charoite" -> Material.AMETHYST_SHARD;',
     'case "sift_shard", "spire_fragment" -> Material.AMETHYST_SHARD;\n            case "charoite" -> Material.LAPIS_LAZULI;'),
    ('case "sculkflower" -> Material.POPPY;', 'case "sculkflower" -> Material.CACTUS_FLOWER;'),
    ('case "raw_sifter_meat" -> Material.BEEF;', 'case "raw_sifter_meat" -> Material.PORKCHOP;'),
    ('case "cooked_sifter_meat" -> Material.COOKED_BEEF;', 'case "cooked_sifter_meat" -> Material.COOKED_PORKCHOP;'),
    ('item(Material.BEEF, "raw_sifter_meat", "Raw Sifter Meat")',
     'item(Material.PORKCHOP, "raw_sifter_meat", "Raw Sifter Meat")'),
    ('item(Material.COOKED_BEEF, "cooked_sifter_meat", "Cooked Sifter Meat")',
     'item(Material.COOKED_PORKCHOP, "cooked_sifter_meat", "Cooked Sifter Meat")'),
]
# Willow recipe variables (lines ~142-188): every CHERRY material on a line mentioning willow.
content_path = ROOT / "src/main/java/com/gamepathics/thesift/foundation/SiftiteContent.java"
text = content_path.read_text(encoding="utf-8")
out = []
for line in text.splitlines(keepends=True):
    if "CHERRY" in line and ("willow" in line.lower() or "Willow" in line):
        line = line.replace("CHERRY", "OAK")
    out.append(line)
content_path.write_text("".join(out), encoding="utf-8")
patch("src/main/java/com/gamepathics/thesift/foundation/SiftiteContent.java", siftite_pairs)

# --- Worldgen fallbacks + runtime checks ----------------------------------------------
patch("src/main/java/com/gamepathics/thesift/foundation/SiftEcologyManager.java", [
    ('placeBlock(ore, "charoite_ore", Material.AMETHYST_BLOCK);',
     'placeBlock(ore, "charoite_ore", Material.DEEPSLATE_LAPIS_ORE);'),
    ('placeBlock(ore, "siftite_ore", Material.ANCIENT_DEBRIS);',
     'placeBlock(ore, "siftite_ore", Material.DEEPSLATE_DIAMOND_ORE);'),
    ('placeBlock(soul, "soul_block", Material.SOUL_SAND);',
     'placeBlock(soul, "soul_block", Material.SOUL_SOIL);'),
])
patch("src/main/java/com/gamepathics/thesift/foundation/EchoGolemManager.java", [
    ("                    if (block.getType() == Material.SOUL_SAND",
     "                    if (block.getType() == Material.SOUL_SOIL"),
    ("                    // The Soul Block stand-in is soul sand; generated Soul Canyons are made of it.",
     "                    // The Soul Block stand-in is soul soil (mod parity); generated Soul Canyons are made of it."),
])
patch("src/main/java/com/gamepathics/thesift/foundation/SoulBlockListener.java", [
    ("if (!isMarked(block) || block.getType() != Material.SOUL_SAND",
     "if (!isMarked(block) || block.getType() != Material.SOUL_SOIL"),
])
patch("src/main/java/com/gamepathics/thesift/foundation/SculkflowerManager.java", [
    ('ItemStack stack = content.item(Material.POPPY, FLOWER_ID, "Sculkflower");',
     'ItemStack stack = content.item(Material.CACTUS_FLOWER, FLOWER_ID, "Sculkflower");'),
    ("        return new ItemStack(Material.POPPY);",
     "        return new ItemStack(Material.CACTUS_FLOWER);"),
])
patch("src/main/java/com/gamepathics/thesift/foundation/AncientCityGateManager.java", [
    ("        block.setType(Material.DEEPSLATE, false);\n        return true;",
     "        block.setType(Material.REINFORCED_DEEPSLATE, false);\n        return true;"),
])
patch("src/main/java/com/gamepathics/thesift/foundation/AncientCityPortal.java", [
    ("            block.setType(Material.DEEPSLATE, false);\n        }\n        registry.mark(block);",
     "            block.setType(Material.REINFORCED_DEEPSLATE, false);\n        }\n        registry.mark(block);"),
])

# --- Bundled datapack ------------------------------------------------------------------
patch("src/main/resources/datapack/data/thesift/recipe/soul_block.json", [
    ('"id": "minecraft:soul_sand",', '"id": "minecraft:soul_soil",'),
])
patch("src/main/resources/datapack/data/thesift/loot_table/block/soul_block.json", [
    ('"name": "minecraft:soul_sand",', '"name": "minecraft:soul_soil",'),
])
patch("src/main/resources/datapack/data/thesift/loot_table/entity/sifter.json", [
    ('"name": "minecraft:beef",', '"name": "minecraft:porkchop",'),
])
patch("src/main/resources/sift_dimension_datapack/data/thesift/worldgen/feature/siftslate_siftite_ore.json", [
    ('"id": "minecraft:ancient_debris"', '"id": "minecraft:deepslate_diamond_ore"'),
])

# --- Tests -----------------------------------------------------------------------------
patch("src/test/java/com/gamepathics/thesift/foundation/SoulBlockListenerTest.java", [
    ("placed.setType(Material.SOUL_SAND);", "placed.setType(Material.SOUL_SOIL);"),
    ("source.setType(Material.SOUL_SAND);", "source.setType(Material.SOUL_SOIL);"),
    ("destination.setType(Material.SOUL_SAND);", "destination.setType(Material.SOUL_SOIL);"),
    ('ItemStack item = new ItemStack(Material.SOUL_SAND);', 'ItemStack item = new ItemStack(Material.SOUL_SOIL);'),
    ('assertEquals("minecraft:soul_sand", entryName,', 'assertEquals("minecraft:soul_soil", entryName,'),
    ('"Soul Block stands in for SOUL_SAND; the loot entry must match"',
     '"Soul Block stands in for SOUL_SOIL; the loot entry must match"'),
])
for test_path in ["src/test/java/com/gamepathics/thesift/foundation/ResonanceManagerTest.java",
                  "src/test/java/com/gamepathics/thesift/foundation/SiftAdvancementManagerTest.java",
                  "src/test/java/com/gamepathics/thesift/foundation/NewContentTest.java",
                  "src/test/java/com/gamepathics/thesift/foundation/SiftiteRecipeTest.java"]:
    p = ROOT / test_path
    text = p.read_text(encoding="utf-8")
    text = text.replace('item(Material.AMETHYST_SHARD, "charoite", "Charoite")',
                        'item(Material.LAPIS_LAZULI, "charoite", "Charoite")')
    p.write_text(text, encoding="utf-8")
    print(f"patched {test_path}")

patch("src/test/java/com/gamepathics/thesift/foundation/ResonanceManagerTest.java", [
    ("void untaggedAmethystShardDoesNotCountAsCharoite()", "void untaggedLapisLazuliDoesNotCountAsCharoite()"),
    ("player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD));",
     "player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.LAPIS_LAZULI));"),
])
patch("src/test/java/com/gamepathics/thesift/foundation/NewContentTest.java", [
    ("player.getInventory().setItemInOffHand(new ItemStack(Material.AMETHYST_SHARD));",
     "player.getInventory().setItemInOffHand(new ItemStack(Material.LAPIS_LAZULI));"),
])

# --- content-spec.json -----------------------------------------------------------------
spec_path = ROOT / "integrations/content-spec.json"
spec = json.loads(spec_path.read_text(encoding="utf-8"))
material_by_id = {
    "charoite": "LAPIS_LAZULI",
    "charoite_ore": "DEEPSLATE_LAPIS_ORE",
    "siftite_ore": "DEEPSLATE_DIAMOND_ORE",
    "soul_block": "SOUL_SOIL",
    "sonorous_deepslate": "REINFORCED_DEEPSLATE",
    "sculkflower": "CACTUS_FLOWER",
    "raw_sifter_meat": "PORKCHOP",
    "cooked_sifter_meat": "COOKED_PORKCHOP",
}
for entry in spec["items"] + spec["blocks"]:
    if entry["id"] in material_by_id:
        entry["material"] = material_by_id[entry["id"]]
    elif entry["id"].startswith("overgrown_willow_") and "CHERRY" in str(entry.get("material", "")):
        entry["material"] = entry["material"].replace("CHERRY", "OAK")
spec_path.write_text(json.dumps(spec, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print("patched integrations/content-spec.json")

# --- report residual flagged carriers --------------------------------------------------
print("\nResidual flagged-carrier references (expected: none):")
import subprocess
for token in ["Material.SOUL_SAND", "Material.POPPY", "Material.BEEF", "Material.COOKED_BEEF",
              "Material.ANCIENT_DEBRIS"]:
    r = subprocess.run(["grep", "-rn", token, "src/main/java/"], capture_output=True, text=True)
    if r.stdout.strip():
        print(token, "->", r.stdout.strip()[:200])
print("done")
