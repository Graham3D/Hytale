# Native action worker evidence — 2026-09-30

## Changed files

`NativeGearActionProfiles.java`, `NativeSwordActionAssets.java`, `NativeTwinDaggersAssets.java`, `NativeTwinAssaultEligibility.java`, `NativeTwinAssaultGate.java`, `GearNativeItems.java`, `HytaleGearEquipment.java`, and action-specific sections of `ManagedGearDamageInteraction.java` under `src/main/java/com/inigmasgames/hytalerpg/gear/`; `NativeGearActionProfilesTest.java` and `NativeTwinAssaultTest.java` under the corresponding test package; `tools/New-NativeGearActionProfiles.ps1`, `tools/New-NativeTwinDaggers.ps1`; 28 JSON templates in `src/main/resources/rpg/gear/action-templates/twin-daggers/`. See `actions2-result.md` for each change and its exact route.

## Exact implemented IDs and native wiring

**WA-008, WA-014 and WA-141 are each partial; zero IDs are claimed fully implemented.** The current `GearAffixRuntime.ENABLED` gate is unchanged. WA-008/014 sword primary uses a valid accepted snapshot, exact frozen rate/reach, dynamically published native root, matching animation and selector geometry, and a CAS-bound carrier before the next native chain. WA-141 has a native dagger gate, committed offhand ID/snapshot, second selector and managed 0.40 offhand calculator. The gate type must be registered by main before this path can load.

## Tests and missing boundaries

Direct `javac` against the installed public server JAR succeeded. Eight focused offline JUnit tests passed with positive/control/negative fixtures and packaged/native asset reads. Both generators ran against the installed `Assets.zip`. No Gradle or server/game run was performed, as instructed. Native asset publication, real client prediction, native contact and Utility equip transactions are not yet proven. WA-008 still lacks non-sword legal action families and fixed-warning/animation checks for them; WA-014 lacks other melee classes and wall/victim traces. WA-141 lacks plugin codec registration, NoProc/NoLeech metadata propagation, and native second-contact/visual QA. Do not enable or classify these IDs as FUNCTIONAL yet.

## Cross-owner changes required

Main must register `NativeTwinAssaultGate.TYPE`, class and CODEC on `Interaction.CODEC` in `HyArpgPlugin.java` near the existing managed damage registration. The combat owner must set `canProc=false` and `Origin.TRIGGERED` in `HytaleDamageAdapter.ManagedGearGather` when `ManagedGearDamageInteraction.noProcChild(hit)` is true, preserving the offhand `gearHit` witness. The recovery owner must exclude that triggered child from leech while retaining kill credit. The combat skill owner must make `GearCombatEffects.contributingItem` recognize validated action carrier variants. Main should enable these IDs only after complete legal-carrier native consumer tests. The full exact wiring and validation list is in `actions2-result.md`.

## ACTIONS3 continuation

See `actions3-result.md` for the expanded native primary action implementation, 21 passing focused tests, and current cross-owner wiring. WA-008, WA-014, and WA-141 remain **partial**. The previously listed gate and NoProc registrations have since been connected by their owners; native asset loading, connected prediction/contact, remaining carriers, and source normalization remain. No ID or 160-affix completion is claimed.
