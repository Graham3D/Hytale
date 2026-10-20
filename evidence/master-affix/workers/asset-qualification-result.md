# Native offline carrier asset qualification — 2026-09-30

## Scope and boundary

Added a plain JUnit native fixture and `NativeAffixCarrierAssetQualificationTest`. No gameplay Java, source bindings, or source asset JSON was changed by this worker. The 90 focus/shield/bomb bindings remain blocked. No Hytale server, authentication, connected client, or Gradle command was run by this worker.

The test uses public `AssetStore.decode` for 450 item assets, 24 private interaction/root/projectile assets after generator repair, native codec registries, real installed Physical/Projectile and six RPG elemental cause files, `GearNativeItems.create/read`, and `ItemStack.CODEC` persistence. It also walks installed stock and local interaction JSON from each carrier's action roots with actual per-item `Replace` variables and inherited fields. It rejects reachable `DamageEntity`, `LaunchProjectile`, `ModifyInventory`, explosion, and `EntityDamage` routes. It asserts typed custom interaction classes, referenced root publication, one durable item, quality, maximum durability, frozen affix/payload, UUID, metadata, and worn durability across all 450 stack roundtrips.

The fixture uses reference-only sound, particle, reticle, animation, and projectile model entries where collision/codec validation requires a registry. It verifies installed model file presence and real item/interaction/projectile decoders; it is not visual, audio, or connected action proof. `NativeAssetTestFixtures.open()` is public for GearCombatProductionTest and proc/summon workers. Open it once in a fresh native-control JVM and share it across test methods because Hytale caches asset stores statically. `damageCause(id)` exposes the loaded causes without private-field reflection.

## Exact findings

1. **Source assets currently fail qualification.** `Item.CODEC` validates `InteractionVars` string values as `RootInteraction` IDs. Four staged managed damage leaves have Interaction files but no identically named RootInteraction wrapper:
   - `RPG_Carrier_Focus_Melee_Damage`
   - `RPG_Carrier_Focus_Projectile_Damage`
   - `RPG_Carrier_Bomb_Damage`
   - `RPG_Carrier_Shield_Guard_Damage`
2. The installed `Spellbook_Cast_Cost` parent has `Next: {Type: ModifyInventory, AdjustHeldItemQuantity: -1}`. Staged book cast-cost variables inherit that native item consumption. The generated repair removes this ancestry and retains the exact authored mana debit as a standalone `ChangeStat` node.
3. `Complete-NativeGearCarriers.ps1` now emits those four roots and nonconsuming book cost in generated carriers. `Test-ManagedCarrierGraphs.ps1` rejects missing roots and the inherited book cost. These script edits do not promote bindings.

## Verification performed

- Direct `javac` of the four new test Java files against the installed exact Hytale JAR and cached JUnit API: **passed**.
- Standalone JUnit launcher with Hytale's native logging manager against current source assets: **0/5 passed**. Failures are expected from the missing roots; the strict 24-file graph count also fails until regeneration.
- `Complete-NativeGearCarriers.ps1 -VerifyOnly` and `Test-ManagedCarrierGraphs.ps1` against current source: **fail** on the first absent damage root.
- Generated a preview under `build/native-offline/generated-assets` from a copy of the source carriers and binding inputs. This did not change source resources. `Complete-NativeGearCarriers.ps1 -OutputRoot build/native-offline/generated-assets -VerifyOnly`: **passed**. `Test-ManagedCarrierGraphs.ps1 -OutputRoot build/native-offline/generated-assets`: **passed** with 450 carriers and 90 pinned SHA-256 stock sources.
- Direct standalone JUnit launcher with `-DnativeCarrier.serverRoot=build/native-offline/generated-assets/Server`: **5/5 passed**, including all 450 native item decodes, 450 `GearNativeItems.create/read` and `ItemStack.CODEC` roundtrips, all 24 typed graph decodes, and all 450 installed-stock action-graph reachability walks.
- No Gradle, server, main, auth, commit, or deployment was run.

## Exact next steps for main

After the carrier owner finishes asset edits, from the workspace root in PowerShell:

```powershell
pwsh -NoProfile -File tools/Complete-NativeGearCarriers.ps1
pwsh -NoProfile -File tools/Complete-NativeGearCarriers.ps1 -VerifyOnly
pwsh -NoProfile -File tools/Test-ManagedCarrierGraphs.ps1
.\gradlew.bat nativeControlTest --tests 'com.inigmasgames.hytalerpg.gear.NativeAffixCarrierAssetQualificationTest'
```

Run the focused native test in a fresh test JVM because installed native asset classes cache their first `AssetStore` statically. The test is already selected by the `NativeAffix*Test` nativeControlTest filter. If any decoder or reachability assertion fails, keep the bindings blocked and fix the exact asset or registry failure; the test has no skip path.

The test-only binding overlay maps the 90 rows inside an isolated classloader solely for the 450 `GearNativeItems.create/read` proofs. The source binding file stays blocked until main completes codec/system registration, shared equipment ownership checks, this source-asset gate, and its other required tests. Main then runs `pwsh -NoProfile -File tools/Complete-NativeGearCarriers.ps1 -Promote` and reruns native tests against the promoted binding file. Connected cast, projectile contact, shield block, and owner gameplay QA remain separate proof. `NativeGearActionProfiles` template loading was left to the active actions3 generalizer owner.
