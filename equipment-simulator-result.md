# Equipment simulator result (2026-09-30)

## Scope and outcome

- Candidate equipment admission and effects use the same package-private capability set. Public `GearEquipmentResolution.resolve`, `GearAffixRuntime.effects`, and `GearAffixRuntime.supported` still use the unchanged 21-ID `ENABLED` set. This is candidate qualification only, not functional gameplay qualification or a release-gate change.
- `MasterAffixEquipmentSimulatorTest#authoredAffixReachesFrozenEquipmentSnapshotAndLeavesOnEveryInvalidation`: **160/160 passing** distinct authored IDs. Each invocation uses `GearAffixQaSuite.preview` to construct a frozen roll and verifies authored source window, tier, rarity, gate, selector, real equipment admission at owner level 99 with five permanent attributes at 500, source-preserving accepted snapshot, exact affix value and item identity, JSON reload, unequip, backpack, broken, wrong slot, duplicate identity, unmet requirements, same-base unrolled control, and public capability rejection for IDs outside `ENABLED`.
- `MasterAffixEquipmentSimulatorTest#authoredCarrierIsProductionEligible`: **159/160 passing**. **WA-141 fails** on `gm.daggers_adamantite.h`: `GearDropGenerator.eligible` does not currently interpret the authored `M*` dagger eligibility token as legal. Main owns `GearDropGenerator`; it needs to admit the appropriate dagger `*` scope through the production eligibility gate without changing WA-141's authored values or requirements. Rerun this test after that change.
- Existing `GearEquipmentLifecycleTest`: **10/10 passing**.
- Combined direct JUnit run: **329 passed, 1 failed, 330 found**. The only failing invocation is WA-141 production carrier legality. [Run output](evidence/master-affix/equipment-simulator/run-output.txt) and [per-invocation IDs and statuses](evidence/master-affix/equipment-simulator/invocations.tsv) record the result. The [isolated runner](evidence/master-affix/equipment-simulator/Run-EquipmentSimulator.ps1) compiles only the owned files and runs JUnit directly, using existing compiled classes and resources for other dependencies. No Gradle, game, server, auth, deploy, or commit was run.

## Proof registration for main

The equipment authority candidate anchor is `com.inigmasgames.hytalerpg.gear.MasterAffixEquipmentSimulatorTest#authoredAffixReachesFrozenEquipmentSnapshotAndLeavesOnEveryInvalidation`. Its `@ValueSource` enumerates all 160 IDs literally, and each JUnit display name includes the ID. A per-ID `sourceAnchors.equippedAuthorityTest.parameterId` can point to this method after main obtains fresh normal JUnit XML; the current direct-run TSV is supplemental evidence, not the coverage generator's accepted XML. The test asserts the real `GearEquipmentResolution.resolve(...).effects().snapshot()` item source, value, and invalidation, rather than a fabricated snapshot or constant.

Do not register this method as a gameplay `positiveTest`, `controlTest`, or `negativeTest` for an affix consumer. It proves equipment admission and removal only. The carrier legality method likewise does not prove a mapped native carrier or gameplay behavior.

## Remaining limits

- WA-141 cannot pass production carrier legality until main changes the dagger `*` eligibility interpretation. Its candidate equipment lifecycle invocation already passes.
- `GearEffectSnapshot.revision()` can differ across JSON reload when map serialization order changes, although the item roll, `Effects`, and snapshot value remain equal. The simulator asserts stable revision for repeat resolution of the same frozen item and exact effects after reload; revision normalization belongs to the snapshot owner.
- Advanced Stats and native gameplay consumers were outside this equipment-only ownership. No functional status follows from this run.
