# Hywind ME12: native lifecycle test and guarded-QA unblock

## Decision and scope

Continue the existing Stage 3 task. This is not another architecture pass or a new sub-stage. The immediate deliverable is a passing integration test of Hywind's real birth transaction, followed by the existing Task 4 exact-commit verification and publication. It is not deployment authorization.

Use the latest local `codex/me12-stage3` work. The last owner-reported checkpoint is `9e77c48e5441a5a905b7c8e23ecd6013c130a3c4`, with eight unpublished commits. Verify HEAD and preserve all work. Do not reset to the remotely inspected checkpoint.

This handoff was prepared by inspecting the published source at `ad1e31bd88818fdf11eaf213a35685a1bba8df09`, the supplied continuation reports, and official Hytale documentation. The later local implementation was not accessible. Check each proposed edit against the local source; reuse anything already implemented. No tests were run by this review, and no proposed fix is claimed to have passed.

Keep the existing per-role QA routing. Turret, Seeker and Firesteel must not globally block a different eligible actor's QA route. This does not permanently remove them from the intended population or certify their unfinished behavior. Production release remains closed. Owner-authored learning remains unassigned and its full acceptance remains deferred, not passed.

## 1. Concrete finding: the current staging test cannot exercise the real handoff

In the inspected `NativeEnemySpawnGroups.java` [G1]:

- The public four-argument `capture(Job, Owner, Store<EntityStore>, Runnable)` delegates to the native-job overload with `null`.
- The five-argument overload receiving `SpawnJobData` is private.
- That overload invokes `owner.captured(store, group, nativeJob)` after collecting the group.

In `NativeEnemyBirthOwner.java` [G2]:

- The two-argument `captured(store, group)` rejects with `ENEMY_BIRTH_NATIVE_JOB_REQUIRED`.
- The three-argument overload rejects a null job, wrong thread or ineligible job with `ENEMY_BIRTH_CAPTURE_OWNER`.
- It invokes the real `reservation.begin(store, group, nativeJob)` and production birth processing.

`EnemyNativeStagingTest` [G3] uses the four-argument capture helper and a small recording implementation of `NativeEnemySpawnGroups.Owner`. It verifies real native holder/staging behavior, but deliberately does not invoke the real birth owner/reservation chain.

**Conclusion:** There is a specific testability gap at the transition from the existing capture test to the production owner. This is not proof that gameplay spawning is broken, and it is not proof that fixing this seam alone completes the fixture.

### Minimal edit

If still applicable locally, change only the five-argument capture overload from `private static` to package-private `static`, preserving its body and all production callers. Put the new test in `com.inigmasgames.hytalerpg.execution.hytale`.

The existing method signature is:

```java
static void capture(
        Job job,
        Owner owner,
        Store<EntityStore> store,
        Runnable nativeTick,
        SpawnJobData nativeJob) {
    // Existing implementation, unchanged.
}
```

This snippet illustrates the visibility change, not a replacement method body. Do not add a public runtime "test mode", skip a null check, manufacture certification, or change `captured` to accept a missing job. The production tick and test must enter the same capture implementation.

## 2. Build one continuous transaction fixture, not an entire headless game

Create a native-control test, for example `Me12NativeBirthOwnerIntegrationTest`. Register it with the existing `nativeControlTest` task and its real logger/native JVM bootstrap. Inspect `build.gradle` for the actual includes/excludes so the test runs once in the correct suite, not as a native test accidentally launched by ordinary `test`.

Reuse test-side native ECS/component registration from `EnemyNativeStagingTest` and related native fixtures. That test already uses real native `ComponentRegistry`, holders, `Store<EntityStore>`, staging components and codecs without launching a server. Its partially initialized World supports only the accessors exercised there; do not assume it can schedule callbacks or run full production classification unmodified.

Use real `FileEncounterStore` / `PersistentEncounterRuntime` with temporary data outside `Saves`. Reuse the root/compensation fault infrastructure in `EnemyBirthPersistenceTest` [G4]. Do not replace file persistence with a map or mock success.

### Keep real

Exercise the actual:

`NativeEnemySpawnGroups.capture(..., nativeJob)`

`-> NativeEnemyBirthOwner.captured(..., nativeJob)`

`-> NativeEnemyBirthReservation.begin(...)`

`-> NativeEnemyBirthDecision` and current native context provider

`-> actual planner / capacity / archive / root transaction`

`-> NativeEnemyBirthAttachment`

`-> NativeEnemyBirthPublication`

`-> compensation and existing restart/rebind recovery owners`.

Production conditions must determine success. A synthetic Group, preselected completed plan, mocked birth owner, or stubbed durable acknowledgement is not the requested joined test.

### Supply controlled environmental inputs

Supply an actual native `SpawnJobData` and compatible pinned native role/configuration/component inputs, not a fake object that answers every validation with success. Use one already-supported simple role, such as `Trork_Warrior`, only after verifying its local certificate and provenance requirements. Choose a deterministic birth seed that exercises the v2 Unique route through existing test facilities; do not change production rarity weights.

Inside the capture operation, use real holder/component additions and the actual capture hook. The outer Hytale terrain search/world-generation loop may be supplied as a controlled operation. Clearly state that this establishes Hywind's integration from a prepared native spawn opportunity, not Hytale terrain generation or live placement correctness.

Use the real native source classifier and production context provider. Register only the assets/components/resources required by that route. Do not pass `CERTIFIED_STATIC_FIXTURE` through a production-only guard or relabel fixture evidence as connected certification.

### The permitted testability seam for scheduling

`NativeEnemyBirthReservation` dispatches back through `nativeWorld.execute(...)` after the root writer completes [G5]. If the offline world fixture cannot execute this, introduce the smallest package-private dependency seam for **scheduling only** in the existing owners. The production default must still use the actual World's scheduler and current lifetime/thread checks.

A test executor can queue callbacks and drain them on its designated native-store thread. Do not replace the decision, reservation, attachment, publication or recovery logic. Do not instantiate and patch large portions of a running game merely to test callback ordering.

Drive asynchronous steps with bounded completion barriers and a deterministic queue, not arbitrary sleeps. Asset initialization, logger setup, temporary files and scheduling are test environment responsibilities; correctness of the transaction stays in production code.

If another engine dependency blocks construction, identify the exact constructor/method/field and isolate only that external dependency. Do not stop merely because the fixture does not yet exist, and do not invent a second spawning engine.

## 3. Required assertions, using the same fixture and real stores

Start with one success/restart scenario. Then parameterize the existing fixture at the relevant failure boundaries rather than authoring another harness.

### Success and restart

Observe real capture, actual source classification, capacity reservation, selected plan, policy archival, durable root, attachment, publication and release of staging protection. Assert original entity UUIDs remain unchanged, selected additions exist once, and independently owned native flags remain intact.

Close the first runtime and store. Reconstruct native holder state through the existing serialization/recovery path and reopen the same temporary files with new runtime instances. Do not retain old live refs across this simulated restart. Verify frozen identity, affixes, generation, original versus added members, pack accounting and residual state. Rebind must not roll another identity or repeat publication/economic entitlement.

### Rejected submission before durability

Use the existing bounded writer rejection/fault seam. Verify compensation restores the original group and releases only its own unsealed reservation. No v2 record or economic reward may appear from a declined promotion.

### Uncertain root write or interrupted derived writes

Exercise a real `FileEncounterStore` fault boundary. After uncertainty, preserve staging/quarantine according to existing policy; never immediately pretend the write failed cleanly. Reopen the store and resolve the same durable root. Existing tests already rebuild derived projections from the root [G4]; now join that recovery to the native owner state.

### Attachment/publication failure after root durability

Fail at the actual attachment/publication seam. Verify the established compensation/recovery branch, not an invented general rollback. Added entities must not leak; original actors must not receive a second identity, reward or loss of unrelated native flags. Durable compensation remains idempotent after restart.

### Async stale inputs

After capture, reuse or invalidate the original pooled spawn-job input before completing the asynchronous work. Verify that the existing immutable snapshot is used and ephemeral native/world conditions are revalidated. A replaced world, expired lease or changed selected member must not receive a late publication. Release only the failed operation's resources.

Add mixed-member coverage using concrete selected roles accepted by the local registry. Never assume the leader's role for all members. Do not change native random flock selection just to make this fixture homogeneous. If the admitted QA population can reach mixed additions, that branch needs these same safety assertions before admission.

## 4. Guarded QA must consume this evidence without a circular requirement

Preserve the current per-role routing and existing QA command; do not build another activation framework.

Separate three questions:

1. Is the exact candidate's applicable offline integration/safety evidence valid?
2. Is this particular native role/group eligible and certified for the guarded route?
3. Has the owner accepted connected gameplay and the full release?

Only questions 1 and 2 can be prerequisites for the first guarded QA activation. Question 3 remains false until testing. Do not forge a Guardian receipt or set connected/owner acceptance true to unlock QA.

For first admission, all members and selected modifiers must satisfy the tested dependencies. A role outside the QA support set retains its ordinary native behavior and a visible exclusion reason for **this QA scope**, not a new permanent production policy exclusion. The global production `ENABLED` guard remains closed.

Test the existing lease for absent/stale/mismatched receipts, wrong world/save, expiry/revoke and valid exact candidate evidence. Receiving a lease must not depend on future screenshots or all-27 connected affix verdicts. Missing instrumentation must remain BLOCKED in reports, never a fabricated PASS.

Do not activate the lease or deploy during this engineering task. After a passing fixture, freeze source, run the mandatory cumulative verification once through the existing build/Guardian workflow, publish all reviewed local commits and request the owner's explicit Stage 4 authorization.

## 5. The three monster-specific blockers: diagnosis and bounded disposition

These are separate certification work, not prerequisites for the simple-role lifecycle fixture. Do not rebuild the modern projectile owner already implemented locally. For initial guarded QA, retain their local unadmitted status unless their focused checks are already available and pass. Full-release coverage remains unresolved.

### Goblin Turret

Missing an explicit hostile-player field in a role template is not proof the effective role is nonhostile. Official Chapter I modder notes rename the **attitude role** `Goblin_Miner` to `Goblin_Brave` [W1]. This is a relevant investigation lead, not proof that Turret references it and not a reason to rename the NPC itself.

Resolve the installed Turret's concrete overrides, template chain, support configuration, attitude groups and targeting conditions. Hytale exposes `WorldSupport.getDefaultPlayerAttitude()` and target-specific `getAttitude(...)` [W2]; Hywind's own spawn trace already reads the former [G1]. Use those authoritative values to verify the source classifier. A raw JSON search for "Hostile", attack animation, or similarity to another Goblin is not sufficient.

If effective hostility is conditional or still unresolved, leave Turret native-only in this QA scope. Do not change its attitude, weaken the source rule or add a speculative production binding. Once resolved, validate the implemented modern receipt against the installed Physical 8 / Stone_Death / impulse / removal chain from the pinned audit [D1].

### Scarak Seeker

Use the current local modern-projectile implementation and certify the exact Seeker override, not its inherited Eye attack or the older scalar projectile route. Preserve the pinned Poison 10 packet, native variance, Antidote Match=None, clear/reapply Poison_T1 and independent removal [D1]. These values describe the audited impact graph, not total poison damage over time.

Official Chapter I notes changed Seeker attack cadence, spit speed and poison damage-over-time [W3]. The patch notes do not supply a replacement source certificate. Compare the exact installed asset hashes and full impact graph, and never relabel Physical/Poison as the legacy PROJECTILE channel merely to satisfy a receipt check.

Modern API documentation exposes creatorRef, a command buffer and the returned projectile ref [W4]. Correlate the already implemented owner/strike/projectile identity there; proximity to a launch event is not ownership. Verify hit, miss, cancellation, expiry/removal and reload failure policy without changing native effects. Only then add the exact role certificate and recompute legal affix plans. Until then, keep its ordinary route.

### Firesteel

The continuation already implemented a pinned incoming-only attachment contract [D1]. Do not solve its absence of certified outgoing strikes by assigning an invented attack root or removing the global nonempty-strike guard.

Use that explicit incoming-only mode through the same lifecycle fixture as the simple role. Verify real attach/publish, incoming permitted defensive/control/reflection behavior, unchanged native offense and serialization/rebind without regenerating Health, shields or reward identity. Negative tests must reject outgoing-only affixes and unknown actors attempting to use this special contract. Static legal-plan feasibility alone is not lifecycle evidence.

If these checks are not complete, leave Firesteel unadmitted to v2 QA. That must not block other roles which passed their own lifecycle checks.

## 6. Scope and completion

Do not redo the 309-role census, extend the 27-affix diagnostic framework, redesign Skills, select learning assignments, add a new QA service, or simplify the weighted sampler. Preserve existing numerical, archive, progression and native patch regression contracts.

Retain prior logs and unpushed commits. Run the focused native test with its proper bootstrap first. Once the lifecycle and role-scoped authorization checks pass, proceed directly to the existing Task 4 cumulative gate and Guardian publication. Do not pay for repeated full runs while the fixture is still being constructed.

Report one compact table: success/restart, pre-durable rejection, uncertain write, attachment/publication failure, stale world/job, reachable mixed roster, QA receipt/lease. Name the exact production methods exercised and any external engine surface supplied by the harness. Report the three role-specific statuses separately. Do not call an offline pass a live native-collision certificate.

A technical stop must identify an attempted runnable fixture and the concrete failing API/assertion; "fixture missing" is not a completed engineering result. Stage 4 deployment remains separately authorized after the verified handoff. After the owner accepts the tested cumulative scope, follow the already agreed safe merge to remote main with COMMANDS.md and DEVELOPMENT_BASELINE.md included.

## Evidence and limitations

Repository paths below were read at `ad1e31bd88818fdf11eaf213a35685a1bba8df09`; these are code-review findings, not an execution result. The proposed test seam and harness design are engineering recommendations.

[G1] NativeEnemySpawnGroups.java: public capture drops nativeJob; private overload supplies it; capture hook and effective attitude observation.
https://github.com/Graham3D/Hytale/blob/ad1e31bd88818fdf11eaf213a35685a1bba8df09/src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeEnemySpawnGroups.java

[G2] NativeEnemyBirthOwner.java: real captured overload, job requirement, injected production owners.
https://github.com/Graham3D/Hytale/blob/ad1e31bd88818fdf11eaf213a35685a1bba8df09/src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeEnemyBirthOwner.java

[G3] EnemyNativeStagingTest.java: real native ECS fixture but recording capture owner; not full transaction coverage.
https://github.com/Graham3D/Hytale/blob/ad1e31bd88818fdf11eaf213a35685a1bba8df09/src/test/java/com/inigmasgames/hytalerpg/enemies/EnemyNativeStagingTest.java

[G4] EnemyBirthPersistenceTest.java: real temporary store, root recovery, compensation and interrupted-write tests.
https://github.com/Graham3D/Hytale/blob/ad1e31bd88818fdf11eaf213a35685a1bba8df09/src/test/java/com/inigmasgames/hytalerpg/enemies/EnemyBirthPersistenceTest.java

[G5] NativeEnemyBirthReservation.java: begin/selectAsync, capacity, archive/root write and nativeWorld.execute handoff; uncertainty differs from synchronous rejection.
https://github.com/Graham3D/Hytale/blob/ad1e31bd88818fdf11eaf213a35685a1bba8df09/src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeEnemyBirthReservation.java

[D1] Supplied ME12_STAGE3_CONTINUATION.md, especially native blocker groups and Firesteel/source corrections. Later local notes supersede its implementation status.

[W1] Official Chapter I modder notes: attitude-role rename and projectile lifetime changes.
https://hytale.com/news/2026/10/2026-10-12-modder-facing-patch-notes-update-7/

[W2] Official WorldSupport API. Check the installed 0.7.1 signatures before use.
https://docs.hytale.com/api/com/hypixel/hytale/server/npc/role/support/WorldSupport

[W3] Official Chapter I notes, Spiders & Scaraks.
https://hytale.com/news/2026/10/2026-10-12-chapter-i-out-of-the-breach/

[W4] Official ProjectileModule API. Public release and pre-release documentation expose differing overloads; the installed pinned bytecode is authoritative.
https://docs.hytale.com/api/com/hypixel/hytale/server/core/modules/projectile/ProjectileModule
https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/projectile/ProjectileModule

No public report found in this research establishes a drop-in fix for Hywind's exact capture-to-recovery test. The workable approach above comes from the inspected Hywind test/owner boundaries, supported by native API documentation. It still needs implementation and verification on the local checkpoint.
