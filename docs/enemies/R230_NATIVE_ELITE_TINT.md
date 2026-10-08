# R230 native per-entity Elite tint proof

## Result and limit

The installed Hytale SDK provides a generic **per-entity tint transport** through `EntityEffect.ApplicationEffects.EntityBottomTint` and `EntityTopTint`. It does not require a replacement model or texture. Hywind now applies one infinite, tint-only native effect to each promoted actor at birth/rebind via `HytaleEliteTint.applyEliteTint(actor, rarity)`. That call is role-independent: Larva_Void, Skeleton_Scout and Golem_Firesteel use the same code path, with Champion blue `#1d4dff`, Unique purple `#a000ff` and Super Unique orange `#ff9100` respectively in the focused proof fixtures. Any of those roles can receive any rarity color through the same API when its frozen descriptor carries that rarity.

**Server-side transport is proven; connected client appearance is pending.** The SDK packet and existing native tint assets establish an intended render channel, but an offline test cannot establish final pixel color, attachment coverage, shader blending with the original texture, or precedence against another simultaneous visual effect. No local Hytale server was launched. Do not describe the connected visual result as proven until the owner sees it in the game.

## Installed SDK inspection

Inspected the installed pre-release `HytaleServer.jar` at SHA-256 `6F4233203E804D6B0071A6416D26DD867F5CFB0C60D3E78B69A6A91415C1A59E` (Hywind's pinned three-leaf/receipt patch over original SHA-256 `35A34A32175CD92CE5E2A51310953DB3A4A64CAC89D995CBD4830C52A9B1B904`) and the pinned installed `Assets.zip`.

- `ApplicationEffects` stores `entityBottomTint` and `entityTopTint` as protocol colors; `toPacket()` writes both to `protocol.ApplicationEffects`. Existing Hytale/Hywind effect assets use those fields.
- The live `EntityEffectUpdate` identifies a registered effect by index; it does **not** carry a newly chosen RGB value. Thus the public path supports arbitrary *authored* tint colors selected per entity at runtime, but not an unregistered RGB argument sent ad hoc for each actor. The three requested rarity colors need only three tiny effect assets, not role-specific textures or a shader system.
- `EffectControllerComponent.addInfiniteEffect(ref, index, effect, store)` runs the native application predicate, records a per-entity active effect and enqueues `EntityEffectUpdate(Add, ...)`. `createInitUpdates()` includes active infinite effects for newly tracking clients. An effect without `ApplyConditions` passes `LivingEntityEffectSystem.canApplyEffect()`.
- Hywind's new effect assets contain only `Infinite`, `Debuff:false` and the two tint fields. They have no damage, stat, movement, control, model-change, particles or status icon fields. The original `ModelComponent` and original main/attachment texture references are not changed by this API.
- `ModelVFX` offers highlight/post-color fields and can be selected by an effect's `ModelVFXId`, but the tested path does not need a VFX asset or a shader adapter.
- `ModelOverride` exposes model, texture and animation overrides, not a color parameter; it is unsuitable for a texture-preserving tint. `Model`/`protocol.Model` expose texture and gradient set/ID, but no general per-actor RGB tint field. Gradients rely on authored sets/IDs and cannot be assumed to color arbitrary installed models. `protocol.Tint` is a separate six-face tint data type, with no inspected generic NPC model-component setter.

## Implementation and lifecycle

- `HytaleEliteTint.applyEliteTint(Ref<EntityStore> actor, EnemyRarity rarity)` selects one of three small packaged native effects. It checks the world thread, actor, asset and effect controller; removes a stale different Elite tint, and skips an already active matching effect. This is one operation on publication/rebind, not a tick refresh.
- `NativeEnemyBirthPublication.finish` calls the API for frozen Champion/Unique/Super Unique descriptors after native palette projection. Normal/Boss actors receive no Elite tint. Native effect removal is owned by the actor's normal effect-controller lifecycle; no independent rendering entity is created.
- No recolored textures, new shader framework, changed affix owners, combat changes, or model/material overrides were added for this proof. Existing Trork-specific texture variants remain separate legacy content and were not expanded.

## Offline validation and connected check

`HytaleEliteTintTest` checks the three installed native role assets, the shared rarity mapping, exact tint-only JSON schema, and absence of name collisions with stock effects. The R228 semantic baseline guard explicitly reviews only the three R230 JSON additions. Package/reference validation confirms the assets are in the JAR. A connected visual check should spawn or encounter promoted Larva_Void, Skeleton_Scout and Golem_Firesteel and confirm their original texture detail remains visible under the respective color overlay. The client result may be affected by other concurrent native effects; report any such observation before changing tint ownership.

## R230-U7P5 deployment record

- `gradlew check --offline --quiet`: PASS on 2026-10-08. Main test task: **3,409 tests in 341 suites, zero failures/errors**. The package, CustomUI, native control and R228-to-R230 asset/reference gates also passed.
- Deployed once to the active RPG save at `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar` as `InigmasGames:HyARPG@0.2.0-R230-U7P5`.
- Deployed and build-artifact SHA-256: `4EE2AC5D9D405E806C85CEED86F7DACB29F232B25603F0949A12AD962FB6E9D9`.
- The replaced R229 JAR was backed up outside Saves at `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R229-U7P5-06472874-20261008.jar`, SHA-256 `06472874DD16894D351C306ADEE6ADC22AB8C971A84E1CFA80FF8E721E95B29F`.
- No local game/server or authentication flow was started. Connected color appearance remains unverified.

For connected proof, restart the RPG world and use `/rpg spawn Larva_Void champion normal`, `/rpg spawn Skeleton_Scout unique normal`, and `/rpg spawn Golem_Firesteel superunique normal`. Observe the blue, purple and orange native overlays and whether original texture detail remains visible; `/rpg spawn clear` removes the transient QA actors.
