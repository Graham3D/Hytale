# R052 Skill Tree Unified QA Inspector and Polish

Status: **IMPLEMENTED / PACKAGED / DEPLOYED — CONNECTED CLIENT QA PENDING**

Implementation commit: `39f85447bc383eba1f6483a5e60309dd2cc157fd`

## Delivered

- Added durable presentation-port bindings keyed by authoritative `edgeId`. Exact Joint `a/b/c` connector choices now survive redraws, unrelated node moves, close/reopen, and server restart.
- Added dormant presentation topology for occupied-Skill unequip. Right-clicking an occupied Skill opens a frozen-target `Unequip Skill?` Yes/No confirmation. Confirming clears the Skill while retaining its visual parent links; re-equipping restores compatible authoritative links with their original connector ports.
- Added a structured right-side inspector. Skill rows are Resource, Cooldown, Range, Damage, Requires, and Linked Passives. Passive rows are Effect, Can Connect To, Cannot Connect To, and Current Link. The display uses structured projection fields rather than parsing prose.
- Implemented inspector precedence: active library drag, pointer hover, locked click selection, then neutral guidance. Search Mode uses the same immutable view model and inspector projection.
- Added an exact modal HUD lease. Opening the editor snapshots native visible HUD components and all existing Custom HUD instances, hides them, then restores the exact snapshot on Close, death, disconnect, world change, open failure, or plugin shutdown.
- Replaced placeholder node art with the approved Joint/passive assets and installed Hytale slot/connector assets. Skill and Passive names/action bindings are shown above square frames; family subtitles are removed. The server exposes authoritative `Ability2`/`Ability3` action bindings, not the player's physical remapped key, so no false `E`/`R` label is fabricated.
- Consolidated the screen into one outer frame with left library controls, center graph, and right details inspector. Removed the redundant `SKILL LIBRARY` banner.

## Verification

- RPG JUnit: 2,367 tests, zero failures/errors/skips.
- CanvasUI JUnit: 38 tests, zero failures/errors/skips.
- Tavern JUnit plus 8 retained executable gates: PASS.
- Persistent NPC retained executable gates and release-resource validation: PASS; local live-model tests remain intentionally skipped by that retained harness.
- CustomUI validation: 59 source documents PASS.
- Unified package audit: PASS; 5,831 entries, 2,206 classes, 2,090 UI documents, 17 authored icon hashes plus all 6 Skill Tree asset hashes verified.
- Final isolated Hywind-only smoke: PASS; one first-party JAR, all four subsystems started, server booted, clean shutdown.
- Final installed restart validation: 2/2 PASS; no legacy first-party plugin discovered and no scoped startup failure.

## Artifact and rollback

- Candidate and installed JAR: `Hywind.jar`
- Version/revision: `0.1.0-merge.7` / `R052`
- SHA-256: `CF062E57BBB6DB4FC230CFBC4277B8A2B0DF2AAAEA7159B55F9996EFA07EFD39`
- Installed path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- Full stopped-save rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T001005Z`

## Connected QA still required

Automated tests prove authority, persistence, packaging, startup, and cleanup structure; they do not prove client rendering or pointer delivery. In a connected client, open `/rpg skilltree` and verify: square authored frames; inspector hover/lock/drag precedence; Search Mode inspector retention; exact Joint connector persistence after reopen/restart; occupied-Skill right-click cancel/confirm; topology retention and re-equip restoration; and exact native/custom HUD restoration after every exit path.
