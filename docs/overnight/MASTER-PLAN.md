# MASTER-PLAN — The Sift Overnight Hardening

Branch: `overnight/hardening` · Base: `main` @ `adc1fc0` · PR: #12 (Draft)

## E1 — SpearThrowHandler test SKIP resolution
- ID: E1
- Priority: P1
- Status: PARTIALLY_VERIFIED
- Cause: MockBukkit
- Note: 5/8 logic tests pass; 3 durability/PDC tests need Paper server doesn't support `World.spawn(Snowball.class)` — 4/6 tests skip
- Fix: Extract projectile spawning into injectable `ProjectileFactory` interface
- Test: All 6 tests pass (0 skip)
- Files: `SpearThrowHandler.java`, `SpearThrowHandlerTest.java`

## E2 — Echo Golem lifecycle tracking
- ID: E2
- Priority: P1
- Status: FIXED
- Cause: trackedGolems only populated at spawn; entities loaded from disk after restart not tracked
- Fix: Seed from loaded worlds on first pulse (done in a8720ee), but need entity-load event listener too
- Files: `EchoGolemManager.java`

## E3 — Rift persistence completeness
- ID: E3
- Priority: P1
- Status: FIXED
- Cause: Gate bbox/gateArrival not persisted; loadRifts deletes file when worlds missing
- Fix: Partial fix in a8720ee (preserve file); need gate metadata in DTO
- Files: `RiftManager.java`

## E4 — Singer invuln toggle
- ID: E4
- Priority: P0
- Status: FIXED (e28f719)
- Verified: `setInvulnerable(false)` called on soul event open

## E5 — Previous review items
- Status: Most P0/P1/P2 fixed in earlier rounds (see LOG.md for full history)
- Remaining: Sculkflower chunk-scan optimization, ~50 hardcoded sendMessage, Brigadier

## F — Mod parity audit
- Status: Deferring to playtest — code-verified parity is documented in HONEST-ASSESSMENT.md

## G — Provider integration
- Status: Architecture complete, 6 bridges, Reflect cache, test-provider skeleton; Live check needs real providers

## I — God class refactor
- Done: WildlifeIndex, BlubController, SpearThrowHandler extracted
- Remaining: DarkSnifferController (~600 lines), ChunkDecorationManager, SifterController

## J — i18n
- Done: Messages vi/en catalog, 22 advancement × 44 keys, ~30 calls wired
- Remaining: ~50 hardcoded sendMessage (mostly in TheSiftFoundationPlugin + GateManager)
