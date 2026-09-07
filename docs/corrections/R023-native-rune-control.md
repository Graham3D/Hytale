# R023 — Native Rune control and observation differential

## Scope and status

R023 is a diagnostic experiment, not another proposed bridge fix. R022's branchless
FirstClick bridge is unchanged. Stage 06 has not begun. No Stage 04/05 executor,
SkillExecutionService, projectile, resource/cooldown formula, HUD/XP asset, native
resource ownership or Ability4 policy has been changed.

```ini
R022 = FAILED_CONNECTED_NATIVE_ABILITY_QA
R023 = DEPLOYED_AWAITING_CONNECTED_CONTROL
CastingFixed = false
Stage06Started = false
```

## Evidence reviewed before implementation

The baseline is RPG commit `f1e626fd1afd3e140777cdce37b6380ac7e3b607`, revision R022,
version 0.0.15. The exact installed target remains **pre-release 0.7.0-pre.1**,
server revision `e8b4d191fc98a977bf5546a951a7b25473d323e3`; the separate release
installation is not the API used for this build.

`2026-09-07_13-12-52_server.log` and the R022 records in `skill-trace.jsonl` show:

- runtime bridge resolution: one FirstClickInteraction, waitFor=Client,
  operationRemote=true, rootRemote=true;
- 87 skills, 66 passives, 12 executable pilots and six projectile pilots;
- four successful native RPG slot projection transitions;
- zero NATIVE_ABILITY_INPUT_OBSERVED, SKILL_ACTIVATION_REQUEST,
  SKILL_VALIDATION_PASS, SKILL_COMMITTED, EXECUTOR_DISPATCH or projectile events;
- two native slot-write conflicts at disconnect, after the successful projections.

The trace interval is `2026-09-07T17:13:03.458183400Z` through
`2026-09-07T17:15:57.452948100Z`. Hashed evidence and exact counts are retained in
`evidence/corrections/R023/r022-connected-failure.json`. Disconnect cleanup is a
separate known defect, not an established cause of the earlier casting failure;
R023 does not change that normal teardown implementation.

R022's static synchronization audit passed but its connected casting hypothesis
failed. Its report has been corrected accordingly. Importantly, the previous
adapter logs only **accepted initial, deduplicated ability chains**. A zero count
of that event never proved that all raw SyncInteractionChains traffic was absent.
The next experiment must distinguish transport observation from adapter filtering.

## Native control selection and structural comparison

The installed Assets.zip supplies these exact asset IDs and paths:

- `Server/Item/Items/Rune/Ability/Rune_Fireball.json`
- `Server/Item/RootInteractions/Abilities/Root_Ability_Fireball.json`
- `Server/Item/Interactions/Abilities/Fireball/Ability_Fireball_Cast.json`

Their definitions are retained in `shipped-control-assets.json` for comparison.
They are **not copied into or overridden by the RPG JAR**. The control creates an
ordinary ItemStack referencing the shipped asset, with no diagnostic metadata,
custom root, branches, stat changes, cost overrides or cooldown overrides.

| Field/path | RPG projection (unchanged) | Shipped Fireball control |
|---|---|---|
| Slot | Primary | Primary |
| Native Cost / CostType | 0 / None | 25 / Mana |
| Native Cooldown | 0 | 12 seconds |
| Cast | Root_RPG_Ability_Bridge | Root_Ability_Fireball |
| Root first operation | Inline branchless FirstClick | Reference to Ability_Fireball_Cast |
| RequireNewClick | true | true |
| Native execution | Effect-free bridge | StatsCondition, native ability cost/cooldown and native projectile/effects |
| Root tag | none | Attack: Ranged |

This comparison identifies differences, **not which difference is necessary**.
Copying StatsCondition, costs or damaging branches into the RPG bridge would not
be justified by this audit. The Rune is shipped content, but it cannot be called
known-working on this particular client/session until it actually casts there.

At plugin start (after real server asset loading, including bare mode), R023 audits
the native item, its source pack, cast root and compiled operation class names.
At trial start it requires a resolved Primary ability with the expected 25 Mana,
12-second cooldown and nonempty root. This is asset resolution proof only.

## Implemented diagnostic and safety boundaries

`/rpg dev rune-control start|status|stop` is development-entitlement gated and uses
normal command permissions. Start only accepts six native slots with RPG-owned
items in both primary cells and empty support cells. It refuses foreign runes.

Start writes and flushes a CREATE_NEW recovery journal containing both full native
ItemStack codec snapshots **before** replacing slots 0 and 3 with Rune_Fireball.
The journal is separate from RPG progression/loadout state. Logical skill and
passive assignments are not mutated. Projection reconciliation is paused for this
player during the trial; unrelated players continue normally.

The unchanged PacketAdapters.registerInbound watcher still calls the same adapter.
The adapter now offers a setup-time diagnostic callback **before** its type/initial
filters. During the 120-second trial it records:

- counts of every packet class reaching that watcher (including noninteraction
  traffic, so a completely silent watcher can be distinguished);
- total SyncInteractionChains packets;
- chain/update types, including noninitial, nonability and fork updates;
- Ability2/3 update and initial-update counts;
- up to 64 detailed chain records: type, initial flag, state, chain ID, override
  root index, equip slot, operation index, held item ID and fork depth;
- summaries on status, stop, timeout or detach, correlated to one trial ID.

These are received-packet/update counts, not unique physical presses or successful
casts. Detail output is capped; aggregate counters continue. The recursion guard
is 32 levels. No packet contents are altered, dropped or fabricated.

Mapped initial inputs still reach the existing observation callback, but carry
`NATIVE_CONTROL_RPG_SUPPRESSED`. RPG requests are blocked both at enqueue and at
drain (including requests queued immediately before start). This avoids one native
Rune cast also running an RPG skill. Ability1/Ability4 mapping policy is unchanged.

Stop or timeout restores exact original stacks only if a slot still holds the
injected ordinary Rune or already matches its original snapshot. Foreign changes
are preserved and restoration fails closed, retaining the journal and suppression.
After successful restoration the input guard remains for 15 seconds for delayed
control traffic. Projection can reconcile again; no RPG costs/cooldowns are reset.

Disconnect callbacks do not mutate control inventory off the world thread: they
summarize and retain the journal. R023 restores interrupted sessions on the next
ready/world tick, including after process restart. Do not downgrade while a journal
is pending: R022 does not understand this diagnostic recovery mechanism.

This is deliberately a **real vanilla gameplay control**, retaining native damage,
cost, cooldown and effects. Aim at safe empty ground. It does not prove the RPG
executor works and does not grant permission to change that executor.

## Automated verification and limitations

The retained tests and seven R023 tests run under `gradlew clean build`. Native
inventory tests use real ItemStack codecs and SimpleItemContainer with a minimal
test registry, not a connected client or a claim of shipped-asset resolution.
They prove pre-filter observation, suppression in both slots, queued-request
suppression, unaffected other players, normal deduplication/Ability4 mapping,
interrupted snapshot restoration, foreign-slot preservation/retry and disabled mode.

Native codec tests require Hytale's logging manager. They run in a separate
`nativeControlTest` JVM so retained tests of the normal JUL logger fallback remain
unchanged. An initial attempt to use that logger globally exposed this test-harness
conflict; it was isolated rather than weakening the retained assertion.

The first smoke attempt showed that bare mode did not deliver BootEvent. The
read-only audit was moved to plugin start, after asset loading. Further inspection
found the actual harness limitation: the installed ServerManager skips its normal
transport/listener setup under `--bare` but later rejects an empty listener list.
Both R022 and the initial R023 smoke log contained `Listeners is empty after
starting ServerManager!!`, `Failed to create HytaleServer`, and a null-transport
shutdown exception. The inherited script incorrectly accepted plugin readiness
and an exit-zero shutdown marker as a complete clean-start/stop gate.

R023's smoke script now starts a normal isolated server on **127.0.0.1:0**
(loopback-only, OS-assigned port), requires `Hytale Server Booted`, and rejects those
startup/shutdown errors. It still uses its own `run/r023-smoke` directory, not the
owner's RPG world. The initial false-positive artifact is retained in implementation
commit `bad70b1`; the final smoke evidence supersedes it. No production transport
configuration or gameplay was changed to repair the test harness.

Machine-readable verification, shipped asset comparison, server smoke and deployment
records live in `evidence/corrections/R023/`. Verification explicitly checks no
protected mechanics/HUD/resources/CanvasUI source changed from R022 and no shipped
control asset was packaged as an override. CustomUI validation remains part of the build.

## Build and isolated-server results

| Gate | Result |
|---|---|
| Revision/version | R023 / 0.0.16 |
| Complete retained suite plus native control tests | 143 passed; 0 failures/errors/skips |
| Source / packaged RPG CustomUI validation | 16 / 9 documents passed |
| Protected mechanics, HUD, resources and CanvasUI source diff | No changes |
| Shipped control assets overridden in RPG JAR | None |
| Three-mod server discovery, setup, readiness and shutdown | PASS |
| Actual loaded shipped control | Hytale:Hytale / Rune_Fireball / Root_Ability_Fireball |
| Shipped compiled root operations | LabelOperation, ParallelInteraction, JumpOperation, SimpleInteraction |
| Existing R022 FirstClick bridge resolution | Still PASS; unchanged |
| Connected vanilla cast / watcher differential | REQUIRED — not performed by isolated smoke |
| RPG JAR SHA-256 | D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42 |

The four listed operations are the root's compiled operations, not an enumeration
of every operation in the parallel child roots. No inference about connected
traffic is made from this list. `verification.json` records the pre-commit HEAD
and explicitly marks the tested working tree dirty; deployment records identify
the implementation commit. Runtime artifacts are matched by SHA-256.

## Connected gate and next decision

Follow `R023-client-verification.md`. Both keys intentionally invoke **vanilla
Fireball** during the trial. A visual cast result from the owner is required; the
server-only smoke cannot synthesize it. Review the trial interval for zero RPG
activation/commit/executor events as the no-double-execution safety gate.

- A visible vanilla cast plus accepted Ability2/3 traffic validates the current
  watcher for that control and directs the next investigation to the custom asset path.
- A visible cast plus raw updates rejected by the initial/mapping filter directs
  investigation to those adapter assumptions, not the packet registration itself.
- A visible cast with other watcher traffic but no Ability2/3 updates directs
  investigation to AbilitiesPlugin/InteractionManager execution-side integration.
- No vanilla cast, or a wholly silent watcher, remains inconclusive; verify bindings,
  available Mana, native cooldown, slot acceptance and instrumentation first.

No branch is chosen and no further synchronization patch is attempted without the
connected result. Stage 04/05 mechanics and Stage 06 stay out of scope.

## Deployment record

Deployed at `2026-09-07T17:47:53.8928791Z` from implementation commit
`bad70b1926fe530753b1627521652a7759aa2b42`. No Hytale server process was running.
Only the RPG JAR was replaced; the active folder still contains exactly three mods:

| Installed file | SHA-256 |
|---|---|
| CanvasUI-0.1.0.jar | 218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6 |
| HYTALEDEVLIB-0.5.0.jar | DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230 |
| HytaleRPG-0.0.16.jar | D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42 |

R022 was copied to rollback storage and hash-verified before removal from the
active mod folder. Its hash remains
`27012D3095D450895D690EB00DD820BB79A971F52A0944156B6C4D8F5BF9B7D9`.
The real RPG world restart/rejoin and connected control remain owner QA requirements.

## Rollback

R023 uses version 0.0.16 and preserves R022 version 0.0.15 in
`evidence/corrections/R023/rollback/`. Stop the control and confirm restoration;
ensure `mods/InigmasGames_HytaleRPGPhase00Audit/diagnostics/native-rune-control/`
contains no pending JSON journal. Then stop the world, replace the R023 RPG JAR
with the retained R022 JAR, and restart. CanvasUI and HytaleDevLib remain unchanged.
No progression schema migration is introduced.
