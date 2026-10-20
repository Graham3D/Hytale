# Resource owner implementation result

## Changed owners

- `src/main/java/com/inigmasgames/hytalerpg/execution/support/FiniteSupportEffects.java`: added `durationSeconds(context)`, selecting WA-110 from the admitted gear snapshot only for friendly finite support effects. It leaves authored profile duration, hostile finite effects, and sustained auras unchanged.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSupportSystem.java`: the canonical `finiteEffect` adapter calls that same selection method before admission, native status handling, and ledger publication. Existing fear control can still reduce the selected duration through its status result.
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonNativeMovement.java`: the existing native NPC movement owner now shares selection, payload measurement, acceptance, and typed applied projection through `apply`. Runtime `refresh` uses the public `EffectControllerComponent.addEffect` API and checks `hasEffect` before that projection is returned. No additional movement system was added.
- `src/test/java/com/inigmasgames/hytalerpg/MasterAffixResourceCompletionTest.java`: added a committed finite-support simulator fixture calling the same duration selector. WA-110 is checked against actual ledger receipt `ends`, presence just before expiry, disappearance at expiry, unrolled and removed controls, and hostile/aura exclusions. It also checks unchanged magnitude and movement values.
- `src/test/java/com/inigmasgames/hytalerpg/execution/summon/MasterAffixSummonCompletionTest.java`: added WA-118 accepted native payload, unrolled/removed, rejected application, and speed mismatch cases. The existing six WA-113 through WA-119 tests were run unchanged.
- `evidence/master-affix/workers/ResourceOwnerFocusedRunner.java`: direct focused test runner used without Gradle.

## Verification

- Direct `javac -source 25 -target 25 -implicit:none` compilation of the three changed production owners and two completion tests: passed.
- `MasterAffixResourceCompletionTest#wa110ExtendsOnlyActualFriendlyFiniteReceiptExpiry`: passed.
- `MasterAffixSummonCompletionTest#wa118ReportsOnlyAcceptedNativeMovementPayload`: passed.
- Existing summon completion methods for WA-113, WA-114, WA-115, WA-116, WA-117, and WA-119: all passed.
- `git diff --check` on changed tracked owners: passed.

## Native boundary and gate

WA-118's focused fixture exercises the **same typed application path** called by runtime with a public native `EntityEffect` payload and an accepting/rejecting application callback. Its reported multiplier is read from that submitted effect after accepted application; asset ID selection alone is never treated as applied speed. The fixture does **not** instantiate a live Hytale `Store`/NPC or measure actual velocity. Runtime uses the real native controller, and no live server measurement was performed.

The current shared `GearAffixRuntime.ENABLED` set still excludes WA-118. `refresh` retains that production gate; the candidate-accepted test calls the shared `apply` path directly. Opening production gates and registry resistance remains with Main. No game, server, auth, Gradle, deployment, or commit action was taken.
