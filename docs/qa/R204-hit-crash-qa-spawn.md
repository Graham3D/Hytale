# R204-U7P5 — hit crash and Larva QA spawn repair

## R203 connected evidence

- The active server log `2026-10-04_16-19-34_server.log` records `/rpg spawn Larva_Void unique stoneskin manaburn reflective`, normalized to `[ME-004, ME-013, ME-026]`, then rejected as `ENEMY_QA_NATIVE_MODEL_MISSING`. The command parser is therefore working. The installed SDK's `NPCPlugin.spawnEntity` pre-add holder gets a `ModelComponent` only when the caller supplies a non-null Model; HyARPG intentionally passes null so native role appearance selection remains authoritative. The binding and installed assets both name `Larva_Void` as the native model asset.
- At 20:20:21 UTC, the server log shows `IllegalStateException: Store is currently processing!` from `EnemyHealthBarPresentation.hideByDefault` through `reveal` and `HytaleDamageLifecycleSystems.Inspect`, followed by `Player removed from world!`. The R203 lazy health-bar fallback wrote `UIComponentList` directly while handling a damage event. The disconnect is a consequence of that exception, not a separate player-save failure.

## R204 changes

- For an unbound ordinary hostile, the first qualifying hit schedules baseline setup through the damage event's native `CommandBuffer.run` writable phase. It validates the actor refs, hides the shared native bar, then queues the viewer-local reveal. Deferred presentation errors are logged and contained; the damage Inspect presentation branch is also guarded so a bar error cannot abort interaction processing. Existing bound-enemy baseline handling is retained.
- QA spawn clearance reads `ModelAsset` using the role's already-authored `modelAssetId` before `NPCPlugin.spawnEntity`, matching the existing campaign Golem placement route. Native spawn still receives a null Model and selects the original role appearance. The pre-add callback only stages the original holder. Missing model assets fail with an exact ID.
- No save, monster ability, damage formula, affix, reward, or inventory behavior was changed.

## Offline verification and deployment

- `:compileJava`, focused `RpgSpawnCommandSyntaxTest`, `EnemyNativeBindingsTest`, `EnemyQaBirthPlannerTest`, `auditNativeProcAssets`, `validateCustomUi` (88 documents), and `:jar` passed. Offline bytecode inspection confirms `CommandBuffer.run` on the unbound bar path and `ModelAsset.getAssetMap().getAsset(role.modelAssetId())` on QA spawn. No standalone Hytale server was started.
- R203 recovery JAR: `evidence/r204-qa/HyARPG-R203-U7P5.jar`, SHA-256 `EA463FD8865BEE3C8A5C93BA2A15BF61152BE7DD09F322A1AACD339719C65A7C`, outside Saves.
- Sole active RPG save deployment: `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, manifest version `0.2.0-R204-U7P5`, `RpgRevision=R204-U7P5`, SHA-256 `D7F873F3269C078DA1B8031ADF7CF8C80DBEEC7EC13150FDE2DDC697A051F07F`, modified 2026-10-04 16:29:35 America/New_York.
- Connected hit stability, bar rendering, and QA spawn completion remain owner QA.
