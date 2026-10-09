# R203-U7P5 — QA spawn syntax and damage-triggered enemy bars

## R202 connected evidence

- `2026-10-04_15-45-45_server.log` contains the owner QA spawn strings but no `RPG_ENEMY_QA_SPAWN_REQUEST` or handler result. The screenshot's `Expected: 2, actual: 4` is the native command parser rejecting positional affix words; R202 declared `affixAlias` as a named optional flag.
- The same log reports seven `RPG_ENEMY_HEALTHBAR outcome=BASELINE_MISSING` decisions for damaged actors without a difficulty-owned bar baseline. It also reports `REVEAL_QUEUED` for Emberwulf `72754bda-5b13-31e8-81d6-dd42976c2c55`. The damage trace records positive nonlethal Health loss to that Emberwulf over several seconds, but the owner did not see a bar. The queue trace establishes only server-side submission, not client rendering.

## R203 changes

- `/rpg spawn` retains its two required native arguments, admits extra positional tokens, and parses the trailing affix aliases for the existing QA request owner. The named optional arg was removed. Invalid aliases and unsupported action capabilities still report their existing rejection reasons. In particular, Golem bindings cannot deliver `manaburn`; `stoneskin reflective` is the supported Golem example.
- On positive player Health damage, an ordinary native hostile without an authored difficulty profile can acquire the existing hidden native `Healthbar` baseline lazily. The route excludes non-NPC, reserved, summon, conversion, and tracked native boss-bar targets. Bound actors keep their existing baseline.
- Viewer-local reveal now gets one delayed requeue on the existing bounded HUD poll to avoid a same-tick native UI-list update superseding the first reveal. The shared NPC UI list remains hidden; only the damaging viewer receives the native `UIComponentsUpdate`. The trace says `REVEAL_REQUEUED` when that second packet is queued, without claiming client-render confirmation. Native default component IDs now follow the SDK's index order.
- No player save, monster state, reward, damage, or persistent bar timer was changed.

## Offline verification and deployment

- `:compileJava`, focused `RpgSpawnCommandSyntaxTest` and `EnemyQaSpawnRequestTest`, `auditNativeProcAssets`, `validateCustomUi` (88 documents), and `:jar` passed. Offline bytecode inspection confirms the command constructor calls `setAllowsExtraArguments(true)` and the handler invokes `QaSpawnAliasSyntax.positionalAliases`; there is no `withListOptionalArg` call. The native patch remained pinned at SHA-256 `6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e`.
- Packaged and installed `HyARPG.jar` contains `R203-U7P5` in manifest version and `RpgRevision`; the command, alias parser, and native health-bar owner classes are present.
- R202 recovery JAR: `evidence/r203-qa/HyARPG-R202-U7P5.jar`, SHA-256 `E80FCBFA49140D12A948CB00E97920E967C6D92C27C87ED5CA308185D105B3A6`, outside Saves.
- Sole active RPG save deployment: `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `EA463FD8865BEE3C8A5C93BA2A15BF61152BE7DD09F322A1AACD339719C65A7C`, modified 2026-10-04 16:10:46 America/New_York.
- Connected bar rendering and command execution remain owner QA; no standalone server was started.
