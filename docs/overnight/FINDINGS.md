# Findings — Phase 0 (verified against source, not memory)

## T0.3 claim verification
1. **Spear duplication — VALID (durability variant).** `onSpearThrow` spawns the Snowball with
   `item.clone()` BEFORE `item.damage(1)`, so the thrown copy carries FULL durability; `onSpearHit`
   then drops that copy. Net effect: every throw converts 1 durability point into a free
   full-durability spear on the ground. Count is not duplicated; durability is.
2. **Rift economy — VALID.** `RiftManager` reads `rift.reward-drops` default **3** and
   `rift.rift-key-chance` default **0.3** (lines 649/662). Plan defaults (0 / 0.0) will be applied
   via config.yml defaults + migration 4→5, keeping user-set values.
3. **findConsole scan — VALID, worse than claimed.** BFS pops up to 64 note blocks and each popped
   block scans a 33x33x33 cube (`dx/dy/dz -16..16`) = up to ~2.3M `getType()` calls on the main
   thread per left-click note. Fix: cap total block lookups (4096) + early stop.
4. **Biome prototype branches — CLAIM REJECTED, task dropped.** The dimension biome_source now
   lists all 15 biomes (10 mod + 5 fan, verified in dimension/the_sift.json); the code branches
   keyed on `pink_meadows`/`carapace`/`crystal_spires`/`shattered_peaks`/`bulb_forest` are LIVE.
   Removing them would delete generating content. Only @Deprecated-style cleanup is possible, and
   it is not worth the risk — skipped per plan rule 5.
5. **rift.natural.\*** defaults verified: enabled=true, 6-14 minutes, max 2 active (left as-is; the
   plan only mandates reward-drops/key-chance defaults).

## T0.2 file notes (files not previously reviewed in depth)
- `ProviderReadiness`: readiness aggregates item/block/mob backends per strict spec; strict=true
  blocks dimension until all mandatory IDs resolve. No P0 issue found.
- `SonorousBlockRegistry`: chunk-PDC based position lists; gate + rift managers share marking.
  Registry positions are the natural replacement for the findConsole cube scan (T1.3).
- `plugin.yml` + `paper-plugin.yml` both present: paper-plugin.yml is the loader descriptor,
  plugin.yml kept for legacy permission defaults (`thesift.foundation.admin: op`). Permissions
  registered from plugin.yml are NOT guaranteed when paper-plugin.yml exists — noted for Phase 5
  (Brigadier permissions will register explicitly).
- `config.yml`: rift section defaults reward-drops 3 / key 0.3; spawn-eggs, requirements,
  providers sections consistent with code defaults. config-version 4.
- `docs/TESTING.md`: references scripts still present; no broken paths found.

## Baseline
- Branch overnight/hardening created from main (adc1fc0 lineage, CI green).
- `./gradlew clean build --no-daemon` result recorded in LOG.md.
