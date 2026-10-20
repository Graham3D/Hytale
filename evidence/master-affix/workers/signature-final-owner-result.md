# Signature consumer proof — 2026-09-30

## Scope and production change

Read `independent-qualification-result.md` and the scope correction/audit. Kept authored skills, chance tables, proc timing, and signature mechanics intact. `GearSignatureBindings.request` now calls its small `cullChild` seam for the existing finite Cull payload; it retains the native hostility, exclusion, death, and source checks before enqueueing that payload. No shared large production file was edited. `GearSignatureProcRuntime.java` was inspected but did not need a change.

## Focused proof

`SignatureConsumerProofTest` resolves each affix from a catalog legal mapped carrier into admitted equipment. It compares rolled, unrolled, broken, wrong slot, NoProc, target, and timing controls as relevant. Authoritative runtime contacts enqueue children into `NativeGearSignatureProcs`; `drain` constructs the production Hytale `Damage` envelope with cause, amount, origin, correlation, and NoProc metadata. A controlled recipient passes the submitted amount through `DamageCalculationService` and records actual resulting Health and death counts. The test does not copy any proc or mitigation formula.

| ID | Observed result |
| --- | --- |
| WA-135 | Crushing drains next tick for 40 Physical against 1000 Health; one root and ICD suppress repeats; later eligible hit damages again; invalid equipment and NoProc submit nothing. |
| WA-137 | First hit stores a fragment; second same-pair hit drains 8 Physical and changes recipient Health from 500 to 492; wrong target, expiry, unrolled, broken, and NoProc controls submit nothing. |
| WA-138 | Existing native defense factor mutates a submitted Physical hit; post-armor recipient Health differs from ordinary. Expiry, unrolled, and NoProc restore the original amount. |
| WA-139 | The extracted binding payload drains through the same native child queue and takes a controlled 50-Health eligible recipient to zero exactly once. Above-threshold, boss, protected, NoProc, broken, unrolled, and duplicate controls do not create another death. |
| WA-140 | Existing native factor mutates a 100-point eligible incoming hit to 95 and the controlled recipient ends at 405 Health. Wrong recipient, periodic/non-direct, expiry, unrolled, broken, and NoProc controls retain baseline. |
| WA-142 | Retribution drains one reflected Physical `Damage`, reduces the attacker recipient by the rolled amount, and carries NoProc metadata. Duplicate, pair ICD, reflected recursion, and unrolled controls submit nothing. |
| WA-143 | Credited Kill Burst drains Physical and Fire components to the eligible nearby recipient for combined 42 damage; duplicate credit and ICD do not resubmit. Wrong/uncredited and unrolled controls submit nothing; the burst creates no duplicate death. |

The offline recipient is controlled; this does not claim connected ECS Health, native shield/armor world setup, or in-game death rewards. The native `Damage` construction and queue dispatch are the existing Hytale-facing adapter. Connected HP observation remains section 16 work.

## Verification

- Direct `javac` compiled `GearSignatureBindings.java` and the focused new test against the existing classes and pinned native jar.
- Direct JUnit launcher, separate JVMs: `SignatureConsumerProofTest` 4/4; `GearSignatureProcRuntimeTest` 7/7; `GearSignatureIntegrationTest` 2/2.
- No Gradle, build task, server, game, auth, deployment, registry, gate, commit, or agent run.
