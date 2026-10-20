# Final proof registration review

Scope: `tools/affix-proof-map-v1.json`, `tools/Generate-AffixCoverage.py`, `tools/test_affix_coverage.py`, and this note. No Gradle, game, server, production, gate, or Java test source was run or changed here.

## Acceptance used

The original task §§16 and 26 accept a measured result at the intended supported Hytale-facing adapter; the owner does connected QA later. A live native HP or collision event is not a universal offline requirement. A field, projection, queued child, hand-built desired effect, or stubbed successful native application still needs its real effect consumer. The registry keeps that distinction, and `check_proof` separately requires source assertions and fresh passing XML.

## Registered owner results

- WA-069/070/071 and GA-159/160: `NativeAffixDefenseContactTest` admits legal gear, compares unrolled/broken/wrong-slot, decodes the selected native protection effect, and measures `DamageSystems.ArmorDamageReduction.getResistanceModifiers` for Physical/Projectile. Production `HytaleGearEquipment.project` uses the same `nativeProtectionTenth` selector. Live ECS HP remains connected QA.
- WA-110: committed finite support cast stores a recipient expiry extended by `FiniteSupportEffects.durationSeconds`; unrolled/removed and hostile/aura controls. `HytaleSupportSystem.finiteEffect` calls that same selector.
- WA-117/119: `SummonRegistry.claimAttack` admits an earlier attack and a post-baseline-expiry attack, respectively, with unrolled/broken controls. Connected animation/despawn remains later QA.
- WA-113/114/115/116/118: the summon worker's final result reports direct compilation and 7/7 focused JUnit passes. WA-113 submits a Wolf hit through `DamageCalculationService` → `GearCombatEffects.skill` → `HytaleDamageAdapter.prepareGearDamage` and compares native `Damage.getAmount()` to unrolled/broken/unrelated and decoy controls. WA-114 measures max Health and preserved deficit in `EntityStatMap`; WA-115/116 measure matching native `Damage.setAmount` with wrong-cause/origin/cancellation/foreign controls; WA-118 decodes the packaged Pursuit effect, installs it through `EffectControllerComponent.addEffect`, and measures removal on invalidation. These are offline adapter results; connected victim HP and world ticks remain later QA. Fresh main XML remains required.
- WA-141: `NativeTwinAssaultGate.decide` feeds `ManagedGearDamageInteraction.offhandContact`; the measured child has a distinct offhand identity, 0.4 sampled Physical power, noncritical NoProc, and rejected wrong chain/actor/item/snapshot. The selector and leaf decode. A same-root unrolled contact and selector collision regression remain open.
- WA-152: admitted non-QA source yields the exact persisted Gold bonus; unrolled award gives base Gold and duplicate does not pay twice. The equipped death-freeze handoff remains open. WA-154 admits a non-QA reduced-requirement item for a lower-stat actor; same-base unrolled, empty, and broken controls exclude it.
- WA-094/096: the default `GearRecoveryRuntime` pays bound Sentinel NPC Health from an accepted admitted snapshot, while unrolled and NoLeech controls do not. The `GearRecoveryBindings` native accepted-damage receipt handoff remains open.
- WA-008/014/016: accepted, unrolled and exclusion controls reach the production animation renderer, melee selector renderer, and projectile travel path, respectively. The final-controls worker reported 16 direct isolated JUnit passes, with no JUnit XML; fresh XML remains required by the generator.
- WA-053/054/055/057/058/059/060/061/062/063: the same applied hit enters `GearStatusRuntime.admit`; periodic cases measure recipient packages, and control cases measure `StatusService` recipient state. Unrolled, chance miss, and protected controls are asserted. Fresh XML remains required.
- WA-144: same actor/request rejects the unrolled item, dispatches the admitted item skill, then rejects it after withdrawal. The execution port fabricates `COMMITTED`, so a borrowed skill effect recipient or projectile outcome remains open.
- WA-149/150: `SupportRuntime` grants ally Thorns/cooldown recovery and removes it on source end while an unrelated actor stays baseline. The native equipped-item aura selector must still prove that unrolled/invalid sources cannot activate it.

Source anchors for these rows point to assertions in the named test or bounded helper. The status parameter provider is tied to each exact WA ID. `passed_tests` recognizes method names with bracket parameters; a regression test covers a passed `[WA-094]` row and failed `[WA-096]` row.

## Stale holes closed and real holes retained

The broad live-victim-HP requirement was removed for WA-001/002/003/006/007/010, WA-066/067, WA-136, and WA-157/158 after inspecting their admitted source, real attack/status owner result, unrolled or exclusion control, and production owner call path. Each row records its own reason. Native world contact remains connected QA.

The registry retains exact offline owner gaps for:

- WA-013/065/068/078/082/085/134: status matrix or regen recipient, elemental filter, displacement application, or displayed STR comparison.
- WA-017–022 and WA-029–034: wrong active source/unsupported carrier and full native penetration cause matrix, respectively.
- WA-094–098, WA-111/112: accepted damage/death recovery handoff, player heal, or authored cleanse receipt.
- WA-121–133 and WA-144–147: rank-sensitive skill recipient results, borrowed/item child execution, and Succor Health credit.
- WA-135/137–143: signature child recipient, mitigation, Cull, fortification, reflection, burst, and Twin selector controls. WA-136 direct hit owner is closed.
- WA-148–152/156: Aura equipped-source selector/lifecycle, Magic Find/Gold death freeze, or protected stock transfer result.
- WA-155: remains gated pending owner-measured metres-to-native-light-radius calibration and effective-radius assertion, as the user directed.

The proof generator still gates rows for missing or stale Java XML, source assertions, carrier/admission checks, and Advanced Stats comparisons. Existing focused XML cannot be substituted for a fresh main gate. The final-controls worker supplied no XML, and the latest summon source is newer than its current XML. No complete-160 claim is made.

At this review point, 58 IDs have explicit `qualificationHoles`; every hole names a remaining offline owner measurement. `python -m unittest tools/test_affix_coverage.py` passed 19 tests using the installed Autodesk Python 3.11 interpreter. The generator's FUNCTIONAL/GATED totals are transient while the main worker refreshes build XML, so they are not a release result.
