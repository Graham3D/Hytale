# R032-AI — Healing Beam visibility checkpoint

**NOT DEPLOYED.** This is the repair plan's first bounded checkpoint, not a completed Healing Beam visual repair. Gate A remains **CONNECTED-UNVERIFIED**. No new material, infinite-effect ownership system, two-ended production renderer or elastic-path implementation is being represented as accepted.

Date: 2026-09-12. Branch: `RPG`. Baseline: `f5aeb31cf9dea6ede1db1f63f6b19080c381320c` / R032-AH. The latest supplied `Healing_Beam_Codex_Repair_Plan.md` explicitly prohibits deployment or live-save modification without a separate instruction. That task-specific restriction takes precedence over the earlier standing deployment preference.

## Launcher-only authentication correction — 2026-09-12

The owner's first isolated AI server booted/listened, but its `2026-09-12_16-31-27_server.log` recorded OFFLINE mode at line 3 and rejected Direct Connect at line 1009: `offline mode is only valid in singleplayer`. This exposed an error in the original helper, not a Healing Beam runtime failure. The prior headless/offline smoke did not exercise a client connection and therefore could not validate Direct Connect readiness.

`tools/New-HealingProbeAI.ps1` now explicitly passes `--auth-mode authenticated`. Installed pre.2 `Options.AuthMode` supports AUTHENTICATED; its shipped `AuthLoginDeviceCommand` and `AuthStatusCommand` provide the dedicated-server session workflow. The helper and checklist instruct the owner to run `auth login device` in the running **server console**, complete browser sign-in/profile selection, and check `auth status` before joining. No auth bypass, singleplayer flag, credentials copied from the live server, automated account login, or auth-persistence change was added. Fresh disposable directories may need sign-in again.

The disposable-root naming, unique directory creation, native-build pin, exact three-mod hash checks, loopback binding, permission gate and optional explicit ImmersiveNPC comparison are unchanged. Default owner port remains 5591. The old offline server must be stopped normally before restarting on that port. Previously printed offline launch commands must be regenerated.

Targeted validation used `tools/Test-HealingProbeLauncher.ps1 -NativeSmoke`. It exercised the real helper's `-Start` argument construction, checked isolation/auth/loopback/three-mod invariants, then booted those exact arguments on separate loopback port 5592 in a fresh disposable directory. The native server reported `Authentication mode: AUTHENTICATED`, `Hytale Server Booted! [Multiplayer, Fresh Universe]`, listened on 127.0.0.1:5592 and exited cleanly with code 0. It explicitly still lacked server tokens: **interactive authentication and connected join remain unverified**. The test did not initiate a login flow or interfere with the owner's port-5591 process.

Evidence: [launcher validation](../../evidence/stage-13/cohort-ai/launcher-auth/validation.json) and [native startup markers](../../evidence/stage-13/cohort-ai/launcher-auth/native-startup-markers.txt). Only selected startup markers are archived, not login codes/tokens. No Java, Healing Beam implementation, assets, profiles, packaged JAR or live JAR changed. The artifact hashes below remain valid. No new JAR build, full gameplay regression rerun or live deployment was warranted for this launcher-only correction. Gate A rendering remains unverified.

## Delivery status

| Assertion | Status |
|---|---|
| IMPLEMENTED | Opt-in, permission-gated visibility probe, independent controls, non-consuming native queue/outbound observers, finite cleanup, six regressions |
| PACKAGED | Exact final `HyARPG.jar` and verified three-mod archive; AH binary rollback retained |
| DEPLOYED | **NO** — live AH JAR unchanged |
| CONNECTED-VERIFIED | **NO** — no AI player session, visual recording, late viewer or transport acknowledgement |
| Complete requested presentation repair | **NOT COMPLETE** — stop at unresolved Gate A, before dependent lifetime/material/geometry changes |

## Evidence review: what AH actually proves

The supplied AH extract identifies 689 records from the cumulative skill trace, original lines 4942–5630, starting at `2026-09-12T19:12:35Z`. The three channel roots were `input-1-fa459bbf`, `input-3-9429dba8` and `input-6-8e3784ff`. Each reached STARTED, UPDATED and REMOVED at `COMMAND_BUFFER_CONSUMED`; all ended on `CHANNEL_INPUT_RELEASED`. UPDATED is emitted once by AH, not per successful frame. There were no Healing presentation failure receipts in this window.

All 46 healing attempts had Health 100 → 100, actual healing 0. That is full-health tether evidence, not injured-target healing evidence. The 401 fractional upkeep receipts reported `nativeWriteObserved=true`; they are not 401 full per-second charges. The UI extract already includes Blizzard countdown 3 and LOW_MANA. Nothing in AI changes Blizzard or resource/HUD ownership.

The supplied extract did not contain the matching client log. It was found locally, read-only:

`Hytale/data/pre-release/Logs/2026-09-12_15-12-06_client.log`

It contains 2,300 lines; SHA-256 `E6B3B1CFAC43620C9DF0E5DA39846CE79A0B0EEC0C3930772404F30E3A744FE1`. The recorded texture/beam search found no missing-texture/failed-texture/Empty.png or stream-ID line. Absence of an error is **not** proof that the client instantiated the visual. In particular, AG's missing inferred `NPC/MISC/Empty.png` failure must not be recycled as the established explanation for AH.

The source hashes and installed asset/API evidence are in [visibility-boundary-audit.json](../../evidence/stage-13/cohort-ai/visibility-boundary-audit.json). Original owner files and live traces were not edited, cleared, rotated or copied into a different live state.

### Confirmed design defect versus unresolved invisibility

`HealingParticleVisuals.spawn/present/update` uses an empty-model particle carrier with a fixed scale of 1. It sends a source position and rotation, but no target endpoint or length. Collinear endpoints 2 m and 18 m away give the same orientation. A new regression records that exact limitation: the current path cannot guarantee a recipient-terminated variable-length strand. It is a diagnostic regression, **not** a passing test of correct two-ended rendering.

The current invisibility cause is still unresolved between actual viewer tracking/update delivery and client asset instantiation/rendering. AH server allocation receipts cannot discriminate those stages. The new isolated native effect-queue test also cannot establish what the actual AH client's viewer received. Possible overwrite, empty-model initialization, culling and third-party appearance interactions remain hypotheses, not findings.

The already-solved AD Store-processing mutation boundary is preserved. AE's visible oversized native ribbons establish historical geometry capability, not approval of that material. No rejected material has been installed as a replacement production beam.

## Implemented controls

The new command is registered only with `-Drpg.healingPresentationProbe=true`. It additionally requires `inigmasgames.rpg.healingprobe` and a real-path-validated `rpg.healingPresentationProbeRoot`. The root must have a `healing-probe-*` name; the executing world must be inside it; neither that root nor its ancestor/descendant may be the real RPG save. Aliased paths are resolved before this check. A normal startup exposes neither the probe command nor its additional tick/packet observers.

Each command runs one mode for ten seconds. There is at most one run per world and four worlds globally. It uses a new generation/root UUID. `native` spawns one temporary native `RPG_Summon_Decoy` six metres toward world -Z, with native mannequin appearance and the nonserialized marker. Alternatively supply the **current runtime entity UUID**, not an ImmersiveNPC profile ID. The borrowed recipient must be loaded, living and not the caster. The probe does not heal, change Mana/Health, charge a cooldown, choose skill targets or invoke skill execution.

| Mode | Single independent comparison |
|---|---|
| `world` | Exact shipped `Beam_Heal_Green2`, one world particle packet with finite maxDuration |
| `empty` | Exact AH particle carrier, through the shared production constructor/update path |
| `visible` | Same particle attachment, scale and bounds; only model and texture changed to native Mannequin |
| `recipient-once` | Recipient controller, exact `Effect_Health_Pack` wrapper, one 12-second application, explicit release at 10 seconds |
| `recipient-overwrite` | Same probe wrapper/controller, explicit 0.3-second Overwrite at the AH 50-ms scheduling interval |
| `staff-once` | Audited PrimaryItem staff-head node, exact `Staff_Bronze`, once for this finite run |
| `staff-overwrite` | Same staff wrapper, AH's 0.3-second Overwrite comparison |
| `beam` | One native Beam span using the existing AF asset at width multiplier .025, **geometry-only/rejected-material control** |

No control combines the core, recipient and staff effects. The 12-second once control stays alive through the complete ten-second test without re-add. It is intentionally **not** a production adoption of `addInfiniteEffect`. A finite probe cannot establish the safe persistent, shared, incarnation-keyed ownership required by the later repair gate.

The stock world effect has no per-instance cancellation handle on this audited packet surface. Early stop does not issue broad `CancelParticleSystems` calls that could erase unrelated effects. Its native duration/particle tail is explicitly reported; immediate instance cancellation and actual tail duration remain connected checks. The entity-attached controls explicitly remove their probe effect/carrier. Borrowed NPCs are never removed. Only the temporary native control NPC belongs to the probe.

Cleanup is idempotent, with independent effect/core/owned-NPC attempts and per-layer failure receipts. It runs at deadline, stop, endpoint death/invalidation, owner disconnect/world drain and shutdown. Carriers and temporary NPCs are unsaved; effects are finite. These are bounded fallback safeguards, not a claim that abnormal shutdown or world teardown has been visually verified. The world task verifies current run identity/generation before allocating. Each root has a two-second post-cleanup observation tail.

The core controls use a disclosed body/world anchor (`position + Y 1.35`), **not** an asserted animated staff-tip attachment. Only the staff effect uses the audited native PrimaryItem node. Spellbook visual anchoring and a genuinely two-ended staff-tip renderer are still later-gate work.

## Observability and API audit

Production `HEAL_PRESENTATION` records carry probe mode, root, generation, world, segment and layer. Entity receipts include runtime UUID, NetworkId, model/texture, particle count and bounds. Frame samples record actual source/destination coordinates and length, at most once per second during the finite run. Dependency hashes are in the package/native manifest rather than recalculated on the world thread.

Transitions are distinct:

- REQUESTED, TARGET_RESOLVED, ENTITY_CREATED or EFFECT_ATTACHED.
- VIEWER_TRACKED from native `EntityViewer.visible`.
- UPDATE_QUEUED from a copy returned by `EntityUpdate.toUpdatesArray()`.
- OUTBOUND_OBSERVED from the actual installed `PacketAdapters` outbound callback; model and effect fields are inspected, not rewritten or re-sent.
- RELEASE_COMPLETED / RELEASE_FAILED, CLEANUP, outbound removal where observed, and SUMMARY.

`PacketHandler.writePacket` invokes the adapter before transport writing. Therefore **OUTBOUND_OBSERVED is not PACKET_SENT**, acknowledgement or CLIENT_RENDERED. The summary explicitly marks packet sent/client rendered UNVERIFIED. World-particle observation is attributed only by exact system ID and active probe window; that protocol has no unique emitter acknowledgement. Entity packets use watched NetworkIds.

The observer calls no `consumeChanges`, `consumeNetworkOutdated` or `clearChanges` to inspect state. Its native integration audit delegates change consumption to Hytale's actual `EffectControllerSystem`, then confirms repeated diagnostic reads leave the produced queue intact. Network-observer/read exceptions are bounded diagnostics and cannot abort native transport/world ticking.

Success transitions are deduplicated per root/layer; regular receipts cap at 128 plus one reserved summary. Finite frame work is capped at 20 Hz, with no replay/catch-up loop. Tick, observation and deferred world work participate in existing `NativeRpgTickMetrics` HUD-phase measurements. NORMAL aggregation and PERFORMANCE raw-sample semantics are unchanged. Counters describe matching entity/component updates, not measured transport bytes or a connected cost qualification.

Actual sorted system indices in the final isolated candidate:

| Native/diagnostic owner | Index |
|---|---:|
| Probe Tick | 437 |
| EntityModel | 440 |
| HytaleSkillExecutionSystem | 562 |
| Native stat Changes | 563 |
| Native stat EntityTrackerUpdate | 564 |
| EffectControllerSystem | 565 |
| Probe Observe | 567 |
| SendPackets | 568 |
| Native stat ClearChanges | 570 |

The observer is explicitly after the queue-update group and before SendPackets. AH's StatModifyingSystem implementation and execution → stat changes → tracker → clear ordering remain intact. The ordering above is measured in an isolated server, not evidence that a particular AH connected viewer received a model packet.

Installed dependencies were traversed without overrides:

- Beam_Heal_Green2 → `_Sparks`, `_Glow`, `_Plus` and their exact texture references. The similarly named standalone particlespawner is not selected by this system.
- Effect_Health_Pack → Health_Pack_Crosses / Health_Pack_Rays. Their native emission/lifetime definitions are retained in the audit, unchanged. No speculative looping derivative was created.
- Staff_Bronze → Staff_Bronze_Sparks / Staff_Bronze_Air.
- AH Empty model and explicit projectile texture; Mannequin model/texture; existing Void_Green geometry-control texture.

Disassembly of effect/model trackers, their queue reader, SendPackets and PacketHandler is retained under `evidence/stage-13/cohort-ai/native-api`.

## Validation, including the failure

Focused implementation tests initially found an extra explicit `Scale: 1` in the new recipient comparison asset. It was removed from the **new probe asset**, so the comparison changes only duration. The test assertion and AH asset were not changed. Fourteen focused tests then passed.

The full retained run was performed **once**: 2,176 RPG + 55 native-control + 21 CanvasUI = **2,252 tests**. It had **one failure**, `Stage13NativeTickMetricsTest.actualNativeTickSourceAndProductionHandoffRoutingAreRetained`, because the newly added probe tick callbacks lacked required cost instrumentation. That failure was real and was not dismissed. The probe callbacks and deferred mutation work were instrumented; the original test remained unchanged.

A final **17-test focused rerun passed**, covering NativeTickMetrics, the six new probe tests, and retained AH/AG presentation tests. The archive preserves the initial full-run XML separately from the effective results containing only the executed corrective test-class overlays. Effective retained results now contain 2,252 passing cases with no skipped cases. **This is not a claim that a second complete suite was run on the final instrumented binary.** See `full-validation.txt`, `full-run/`, `focused-final.txt`, `validation/` and [package-validation.json](../../evidence/stage-13/cohort-ai/package-validation.json).

Final defensive review also excluded outbound observations while PlayerRef has no world during login/drain, before accessing the concurrent world map. The same 17 focused tests and complete isolated smoke were repeated after that bounded guard. The earlier task-local binary/receipt was retained in `intermediate-before-null-world-guard`; it is superseded, not the final test artifact.

The exact final JAR subsequently passed the complete isolated three-mod smoke and actual native projectile, Blizzard, Healing model/effect, channel-only staff and Mana-replication audits. The additional AI audit passed for two model constructions, five effect assets, once/overwrite application, native effect queue production, non-consuming reads, effect removal and observer ordering. There was no connected client, so these are construction/queue/lifecycle proofs only.

Retained trace fixture validation also passed; the supplied 5,630-event skill file round-tripped with identical count/hash and compressed from 4,147,704 to 202,074 bytes. That preserves trace-storage regression coverage; it is not a new connected rotation test.

Archive entry count/hash verification and isolated AH → AI → AH binary rollback passed. The byte-differential guard rejects any JAR change outside the probe classes, the production carrier constructor-sharing refactor, conditional plugin wiring, opt-in native audit call, AI badge and six new probe assets. There are no removed entries or gameplay/profile changes.

## Artifacts and rollback

Native pin: Hytale **0.7.0-pre.2**, revision `b41721d651ef241809e402f6c3371781b2ea5f84`.

| Artifact | SHA-256 |
|---|---|
| HytaleServer.jar | `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E` |
| Assets.zip | `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126` |
| AI HyARPG.jar | `D0674A8173B460304562AA85F6F3A042361B55CA3AB4AD527204C06DAB28A99D` |
| CanvasUI-0.1.0.jar | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| AI three-mod ZIP | `B8764CE5D5921063E83F34E57139B87D67364B025E17B0C95664904B0B9EC2DF` |
| Unchanged installed AH HyARPG.jar | `272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692` |

Packaged JAR: [HyARPG.jar](../../evidence/stage-13/cohort-ai/artifacts/HyARPG.jar). Archive: [HyARPG-R032-AI-three-mods.zip](../../evidence/stage-13/cohort-ai/HyARPG-R032-AI-three-mods.zip).

The public filename remains HyARPG.jar; the internal manifest/data identity remains InigmasGames:HytaleRPGPhase00Audit. AH is retained at `evidence/stage-13/cohort-ah/artifacts/HyARPG.jar`. No live rollback is needed because nothing was deployed. If an isolated probe installation needs rollback, stop that isolated server and restore AH's JAR there; never leave both RPG JARs installed. No state migration is introduced. Live RPG save/mod data, installed third-party mods and untracked owner art were preserved.

## Exact next connected checkpoint

See [the probe checklist](healing-probe-ai-checklist.md). It prepares a fresh local world, not a copy of live RPG gameplay state. The helper is **not run automatically**. It validates the archived JARs and native build hashes, copies the three test mods and local permission grants, and optionally starts a loopback-only server when explicitly invoked with `-Start`.

For each control, retain the AI badge, timestamp/root, client log, short video, local view and a late second viewer. First use native NPCs, then the actual ImmersiveNPC runtime recipient. Stop the comparison at its earliest missing transition and inspect that boundary; a visible staff sparkle cannot promote the core or recipient to PASS.

The next implementation decision must be based on those controls. Gate B material/endpoint work, Gate C elastic motion, Gate D multi-owner/lifecycle changes and Gate E connected cost qualification remain unverified/deferred. No speculative renderer rewrite, native input change, gameplay rebalance or stage progression was performed.
