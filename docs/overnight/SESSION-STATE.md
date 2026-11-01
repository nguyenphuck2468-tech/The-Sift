# Session state — The Sift

Updated: 2026-11-01 (review rounds R1-R5 merged to main)

- **Branch state:** `overnight/hardening` fully merged into `main` via PR #12 (merge commit
  `7e08624`, then command/worldgen refactors continue on the branch). CI green throughout.
- **Tests:** 184+ JUnit tests, 0 failures, 1 MockBukkit-limited skip; Paper 26.3 boot
  verified in CI for both fallback and strict modes.
- **Review rounds closed:** five AI reviews triaged claim-by-claim against the code. The
  spear dupe (R3), strict worldgen ore gap, entity-index over-reach, wildlife persistence
  growth, ProviderAccessGuard eager readiness scan, sonic-target lock and the two-flow
  Ancient City portal were real and are fixed. Provenance for the 36 non-mod content IDs is
  documented in NOTICE.md ("Plugin-original content") and flagged `fanmade` in the spec.
- **Bedrock:** level-2 operational tier shipped (BedrockBridge, pack skip, per-player sound
  fallbacks, doctor reporting). The .mcpack generation tier remains future work
  (docs/parity-matrix.md).
- **Known remaining work:** Brigadier is registered (BasicCommand) and the handler is split
  into per-subcommand executors; remaining refactors are DarkSnifferController extraction
  and optional Bedrock pack generation. Nothing is blocked on code.
- **Blocked on the owner:** gameplay playtest on a real Paper 26.3 server (with and without
  providers), Bedrock smoke test if a Geyser server exists, and general release packaging
  (version/package naming, Modrinth page).
