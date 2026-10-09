# R202-U7P5 — enemy presentation and QA spawn feedback

## QA evidence from R201

- The active RPG server log `2026-10-04_15-10-48_server.log` records all three `/rpg spawn` invocations, including `Golem_Crystal_Earth unique stoneskin manaburn reflective`, but no command outcome. It has no enemy-health-bar or nameplate decision trace.
- `skill-trace.jsonl` records Charged Bolt hits against Zombie Burnt `116d4a4a-7a21-36f6-b90e-b66ba3e4edc4`: Health 126 to 123 and later 11 while alive. The projectile damage metadata credits player `73f9b698-2494-480d-8406-2943e4a7505b`. R201's health-bar Gather/Inspect route accepted only a direct PlayerRef source, so those qualifying projectile hits could not reveal the bar.
- `HytaleDifficultyCombat.nativeDisplayName` could return an unresolved `server.*` key or raw underscore ID. The existing nameplate and staged Master Enemy paths then skipped the name entirely. R202 uses an existing persistent display name when available and otherwise formats the native role ID as a readable fallback.
- The Golem binding has no certified direct-hit action and explicitly excludes ME-013 Mana Burn. This is a capability rejection, not permission to attach a second attack owner. Stone Skin and Reflective remain supported on that passive QA binding.

## Changes

- Health-bar Inspect resolves the authenticated player actor from existing Hywind damage metadata for projectile and Skill hits. It uses the existing pre-ApplyDamage Health receipt and keeps the native per-viewer Healthbar projection. Direct player hits retain their existing route. The bar still requires positive nonlethal Health loss, a living bound enemy, and an actual tracking viewer.
- The existing native nameplate owner derives a readable fallback from the native role ID only when native localization and persistent display name are unavailable.
- `/rpg spawn` now records request, accepted, and rejected outcomes in the server log and reports a readable affix capability reason in chat. It preserves current role/action gates.
- Focused health-bar outcome diagnostics report the first decision or a change in decision per damaged enemy: `RPG_ENEMY_HEALTHBAR outcome=...`. `RPG_ENEMY_NAME_FALLBACK` identifies roles using the native ID fallback.

## Offline verification and deployment

- `:compileJava` passed on U7P5. The version-pinned native patch rebuilt and retained SHA-256 `6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e`.
- Five directly affected test suites passed (13 tests total). `validateCustomUi` passed for 88 documents, and `auditNativeProcAssets` passed. The packaged JAR contains the Healthbar owner, QA spawn command, and 27-affix catalog.
- R201 backup: `evidence/r202-qa/HyARPG-R201-U7P5.jar`, SHA-256 `3038B8919AEC602A01F06C2244AADA4C12ECF0B054504AA82277F9AAB994106F`, outside Saves.
- Deployed R202 `HyARPG.jar` to the sole active `Hytale/data/pre-release/Saves/RPG/mods` directory. Verified installed SHA-256 `E80FCBFA49140D12A948CB00E97920E967C6D92C27C87ED5CA308185D105B3A6` and modified time 2026-10-04 15:31:40 America/New_York.
- Connected visual and spawn acceptance remains owner QA; no standalone server was started.
