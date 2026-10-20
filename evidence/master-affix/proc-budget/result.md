# Item proc budget handoff (2026-09-30)

The scope correction `docs/MASTER_AFFIX_SCOPE_CORRECTION.md` governs this work. No base skill, ordinary skill chance, Ameliorate, or Simulacrum behavior was edited.

## Delivered

- `NativeItemProcBudget` charges at most 1 item-only coefficient per `(world, actor, root)`. Exact strike identity separates pellets that share a native root/victim contact string. A contact's different damage channels reuse one credit; a repeated callback on the same channel returns 0. Only admitted contacts claim credit. Source item and snapshot revision are frozen. Roots expire after 60 seconds and are cleared on actor/world/unbind through `NativeGearAttackAcceptance`.
- `AppliedReceipt.procCoefficient()` returns the committed item credit. `Application` commits it after positive accepted direct damage, native cancellation/block, hostile attitude, protection, actor identity, and root checks. A cancelled or protected component does not consume later credit. The existing skill/passive chance path remains separate.
- `NativeGearStatusObserver` passes receipt credit to the status owner's item coefficient input.
- `paidWindowCredit` accepts an audited paid interval in seconds and caps its shared allowance at `min(1, paidDelta / 1 second)`. It has no implicit timer or free cadence.
- Direct `javac` compiled the four owned production sources and focused JUnit source against the pinned pre-release Hytale server JAR and existing local main classes. `NativeItemProcBudgetRunner` executed 4 focused tests: control/duplicates/cancelled first channel, crowd/pellets, wrong world/source drift/cleanup, and paid/unpaid windows. Result: 4 passed. Output classes are only under `evidence/master-affix/proc-budget/classes`.

## Exact main integration work

1. `GearStatusBindings.itemProcCoefficient(AppliedReceipt)` currently returns `context(receipt).itemProcCoefficient()`. Return `receipt.procCoefficient()` instead. In `GearStatusBindings.periodic(...)`, replace both `context.itemProcCoefficient()` uses with `receipt.procCoefficient()`. Keep `skillChance(...)`, authored skill package, and D04 union inputs unchanged. This file was outside my edit ownership.
2. `GearSignatureBindings.outgoing(...)` currently constructs `GearSignatureProcRuntime.Contact` with `accepted.procCoefficient()` near line 203. Use `r.procCoefficient()` there. The signature's frozen source, exact hit identity, and aggregate channel validity stay as they are. This file was outside my edit ownership.
3. `NativeItemAffixBindings` is already wired in `HyArpgPlugin` with `AppliedReceipt::procCoefficient`; no change is needed there. Recovery uses accepted direct damage and does not read a proc coefficient.
4. For an authored continuous direct-hit channel that lacks its own coefficient profile, `HytaleSkillExecutionSystem` must pass its **actual paid delta in seconds** and stable interval ID to `NativeGearAttackAcceptance.itemProcBudget().paidWindowCredit(...)` at its existing paid channel boundary. Pass that returned item credit to the receipt construction route; unpaid/invalid intervals use 0. Do not change the skill's existing proc opportunity or invent a timer. This file was reserved for the active worker and was not edited.
5. D04's deterministic equal share over an area requires the producer to establish the eligible victim set before emitting its damage events, then assign `1/N` per eligible victim in stable UUID order. The receipt path has streaming post-Apply events and cannot reconstruct that set. Current streaming receipt ledger is **bounded** (first valid contacts receive remaining credit; later crowd contacts get 0), but equal sharing and order independence are unverified and must remain unqualified until an upstream area admission seam supplies that set. Native geometry was not tested or claimed qualified.
6. `NativeGearAttackAcceptance.commit(...)` calls `GearSignatureProcRuntime.deadly(...)` before native Apply using the authored leaf coefficient. Its premitigation outcome cannot be revised after the receipt validates a victim. For bomb/area leaves, main must verify the authored selector coefficient and root semantics at that producer; the post-Apply ledger does not by itself qualify WA-136's premitigation area chance.

No Gradle, server, game, authentication, deployment, commit, or subagent was used.
