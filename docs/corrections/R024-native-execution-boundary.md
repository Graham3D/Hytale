# R024 — native execution-side ability entry

## Status and scope

`IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION`, RPG `0.0.17`, Hytale `0.7.0-pre.1`.
Not deployed. Casting is **not** declared fixed. Stage 06 has not started in this
correction. Baseline: `8aa4ae3af8925847f78361a5799108c724ed3c39` (R023).

The owner subsequently authorized Stages 06–13 and provided master v1.2. Its
MD-18–20 and sections 00.3/00.4 apply. The owner then confirmed, in this task,
“Yes, native Fireball cast” during the R023 vanilla control. That new connected
evidence justifies this separate integration correction before the stage program.

## Connected finding, not a unit-test inference

The R023 session `2026-09-07_14-02-02_server.log` used Quiche. Its 28-second control
trial and subsequent 105-second trial each recorded empty `packetTypes` and
`chainTypes`, zero SyncInteractionChains, and zero mapped initial ability updates.
The owner confirms visible vanilla Fireball execution. RPG activation, commit,
executor and projectile counts remained zero. The first trial restored normally;
the second disconnected with recovery pending.

The retained, hash-addressed summary is
`evidence/corrections/R024/r023-connected-control.json`. It separates the owner's
visual observation from recorded server counters. Full connection logs containing
certificate/network details are not republished.

## Exact installed dispatch audit

The current server JAR is SHA-256
`EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`;
Assets.zip is `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39`.

Bytecode establishes the concrete observation gap:

1. Legacy `PlayerChannelHandler.channelRead` calls `PacketAdapters.__handleInbound`
   before `PacketHandler.handle`.
2. `QuicheChannel` decodes a framed packet and invokes `ConnectionHandler.handle`
   directly (bytecode offset 526), without the adapter hook.
3. `PacketHandler.handle` calls `accept`; `GenericPacketHandler.accept` invokes the
   registered native handler without another adapter call.
4. Native `GamePacketHandler` enqueues interaction updates for `InteractionManager`.

Thus a working native Rune and a wholly silent RPG inbound watcher are consistent
on this transport. FirstClick's synchronization hint could not repair an observer
that this dispatch path bypasses. This does not imply all transports omit adapters.
No transport patch, settings change, injected packet or replacement server handler
was introduced. Relevant javap output is retained beside the connected summary.

## Correction and reasoning

The existing root retains branchless FirstClick and adds one registered
`RPG_ActivateSkill` operation, implemented using Hytale's supported
`SimpleInstantInteraction` extension/codec path. Hytale serializes it as its native
Simple interaction with `waitForDataFrom=Server`; the server operation reports
`needsRemoteSync=true`. This is an execution callback, not a damaging native action
added merely to induce traffic.

On native `firstRun`, the callback verifies the player owner, non-proxy execution,
non-fork chain, owned ItemAbility cast root, mapped Ability2/3 action and correct
Primary index. It enqueues an intention into the existing RPG input queue. Existing
world-thread skill validation remains responsible for the actual loadout,
equipment, resources, cooldown and family execution. Simulation is explicitly
no-op because the base SimpleInstantInteraction simulation otherwise calls
`firstRun` too.

An actual native chain object is deduplicated for its lifetime using weak keys;
retained live keys are capped at 256 per player. Pending native requests are capped
at 64 per player. Teardown clears them. Rune-control suppression is checked both
at enqueue and drain. Signature/Ability4, foreign runes, fork/proxy paths and wrong
slot indices cannot invoke RPG through this callback.

Production packet observation now remains diagnostic only; it cannot also enqueue
RPG casts. This prevents duplicate execution if a different transport does deliver
adapter packets. Existing historical packet fixtures remain to regress parsing and
control instrumentation, not to assert that production uses packet-sniffed input.

All twelve ItemAbility projections retain native cost 0, CostType None and cooldown
0. The bridge has no native damage/stat/resource/cooldown/projectile operations.
The audit's retained `effectFree` label refers to native gameplay effects; the new
callback intentionally requests RPG execution. The Stage 04/05 executors,
SkillExecutionService, formulas, resources, HUD, XP assets, CanvasUI and Ability4
policy are unchanged. No player-schema migration occurs (schema 3).

## Verification

- Complete retained suite plus seven new tests: **150 passed**, zero failures,
  errors or skips. Tests cover mapping/correlation, native-chain deduplication,
  diagnostic-only packet delivery, Signature/Ability4/foreign/wrong-slot rejection,
  suppression, queue limit, teardown, supported native serialization, and inert
  simulation. They do not prove connected callbacks or casting.
- Source/package CustomUI validation: 16/9 documents passed.
- Protected runtime/HUD/resources/CanvasUI diff: no changes, except the explicitly
  permitted root interaction asset.
- Actual isolated server asset loading compiled the two-operation root, passed
  its whitelist/remote-sync audit and resolved the unchanged shipped Rune.
- Three-mod loopback server reached `Hytale Server Booted`, then exited cleanly.
  This proves asset/server startup, not client input, animation, rendering or casts.
- Build SHA-256:
  `FA6C2AAB3E232665D78336EB64334C98048C38821388E2C869AD057E85C14D1F`.

The first compilation caught a mistaken API accessor (`getCast` instead of the
installed `getCastRootId`); it was corrected against the installed class before
validation. No failed build was deployed.

## Rollback and live-world safety

R023 remains deployed. Its exact JAR is copied and hash-checked in
`evidence/corrections/R024/rollback/HytaleRPG-0.0.16.jar` (SHA-256
`D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42`).
The pending second-trial recovery journal is preserved; R023/R024 restore it on
rejoin through the existing native-control mechanism. Never delete that journal
or downgrade to a revision without its recovery code while restoration is pending.
World deployment requires a stopped server and operator approval; later-stage
implementation authorization is not silently treated as live-world deployment.

## Outstanding connected gate

After an approved deployment and successful control restoration: equip Quick Slash
in skill01 and Fire Bolt in skill02. Use compatible equipment; confirm native
Ability2/Ability3 projection. Press each configured native control once, with
sufficient resources and cooldown ready. Require a correlated
`NATIVE_ABILITY_INPUT_OBSERVED` (`NATIVE_EXECUTION_MAPPED`) →
`SKILL_ACTIVATION_REQUEST` → validation → commit → executor sequence, plus projectile
spawn for Fire Bolt. Verify one RPG cost/cooldown, no native duplicate hit, actual
authoritative damage on a valid hostile target, and visual/animation behavior.

If the callback does not arrive, the earliest remaining boundary is now native
bridge execution → server callback. Do not modify downstream executors to conceal
it. Normal restart/rejoin and pending-journal restoration still need connected QA.
