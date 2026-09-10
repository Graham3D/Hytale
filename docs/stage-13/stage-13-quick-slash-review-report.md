# Stage 13 Correction Q — Quick Slash connected review and +200% speed

Branch `RPG`; baseline P `064bc4242780bfafd96323930285da9eb2948664`.
HUD identifier `R032-Q`, plugin version `0.0.25`. Work is confined to the C:
GitHub checkout; owner art and live save data are not edited.

## Connected findings: damage is occurring, but failures remain

Reviewed `2026-09-09_19-45-01_server.log`, `ui-trace.jsonl`, **both** current
`skill-trace.jsonl` and its `.1` rotation, for UTC 23:45:01–23:52:50 on September
9. Reading only the current trace would miss the earlier casts. Source hashes,
per-cast queries, receipts, terminal reasons and log excerpts are preserved in
[connected-log-review.json](../../evidence/stage-13/cohort-q/connected-log-review.json).

There are **13 committed Quick Slash casts**. Ten casts have no candidates in
any recorded strike query. They complete as paid misses, not validation
failures. The query still uses the authored **2.6 m / 120-degree arc**. The logs
do not show where every visible enemy appeared on the client's screen, so zero
query candidates alone does not prove the visual reach felt correct.

The other three casts reach native Gather → Filter → Apply → Inspect, with
authoritative Health loss:

| UTC cast time | Correlation ID | Native Health losses | Total |
|---|---|---|---:|
| 23:52:00.7765568 | `4fadd251-538a-4be7-a810-224c5b2b6bcd` | 27, 27, 18, 8 | 80 |
| 23:52:19.7345048 | `a531a4db-80a4-4001-bac8-b0ef0b290a37` | 25 | 25 |
| 23:52:33.6345951 | `1c796983-7c0b-4556-8ca4-07d127e6304a` | 27, 25 | 52 |

That is **157 Health actually lost**, not merely requested damage. Therefore
this is not a complete failure of native input, weapon validation, damage
registration, or damage application. No speculative changes are made to those
working paths or to target filtering/range.

### Post-lethal failure: unresolved exact throwing location

The three damaging roots end with `STRIKE_REPEAT_ADAPTER_FAILED` or
`EXECUTOR_ERROR_IllegalStateException` immediately after a lethal receipt.
Some final `STRIKE_HIT` events are missing even though `DAMAGE_INSPECTED` already
records the Health loss. The existing catch paths retain the paid root but
discard the exception's code location. That evidence localizes the error to
the synchronous hit/dispatch or repeat call, after native damage was observed;
it does **not** establish which exact subsequent callback threw.

Q adds `SKILL_EXECUTION_FAILED` at those existing catch boundaries. It includes
stage (`EXECUTOR_DISPATCH` or `STRIKE_REPEAT`), exception class and at most eight
bounded class/method/line frames, with the existing root/instance/correlation
IDs. It deliberately excludes arbitrary exception messages, filenames, locals,
tokens or save contents. Existing cost retention, cancellation and error
termination behavior remain unchanged. NORMAL tracing retains this event;
per-tick aggregation/rotation/gap behavior is unchanged.

This is improved failure evidence, **not a claim that the post-lethal exception
has been fixed**. Do not suppress it or fabricate hit/progression success.

### Separate encounter/reward safety failure

At 23:52:00 the server logs:
`RPG_ENCOUNTER_FAILURE boundary=NATIVE_CONTRIBUTION_INSPECT`
with `NoSuchElementException: No value present`, `awardsUnavailable=true`,
`nativeCombatUnchanged=true`. Shutdown also reports
`ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`, including the
`PersistentEncounterRuntime.attachPrepared` path.

This is a genuine unresolved progression/persistence safety failure, not a
reason to infer zero native damage. It must be separately diagnosed before
claiming reward/XP correctness. Q does not clear uncertainty, edit saves,
invent reward receipts, or alter persistence/escrow/exact-once rules. Do not
use this session as progression acceptance evidence. Use a backed-up testing
save and do not rely on awards until that boundary is resolved.

### Fire Bolt evidence

The session contains **15 native projectile spawn acknowledgements and 15
projectile terminations**, including terrain/range endpoints. This supports P's
server-side lifecycle correction. It does not independently prove every client
particle/fizzle rendered or that every projectile behaved correctly visually.

## Requested speed change

Interpreted **200% increased attack speed** as `base × (1 + 2) = base × 3`, not
2× base. This is twice P's 1.5× animation speed. Both native light-swing action
paths remain the same; only their animation speed and associated scheduling
interval change.

| Weapon | Native action speed | Q action speed | Swing interval | Two-swing window | Multistrike six-swing window |
|---|---:|---:|---:|---:|---:|
| Sword | 1.0 | 3.0 | 0.138889 s | 0.277778 s | 0.833333 s |
| Longsword | 0.8 | 2.4 | 0.173611 s | 0.347222 s | 1.041667 s |
| Daggers | 1.2 | 3.6 | 0.092593 s | 0.185185 s | 0.555556 s |

The compiled Multistrike action-window bound is reduced from 2.1 to 1.05
seconds to cover the slowest new sequence; native scheduling uses the actual
weapon-specific duration. The base resource/cooldown contract is unchanged:
**two hits at 0.85 coefficient each, one 5-Stamina payment, one 0.8-second
cooldown**. Multistrike still repeats the entire pair twice at 65% per child
hit, with no second charge/cooldown. Native basic attacks are not sped up.

The native animation audit now requires 3× base speed. The exact JSON structural
comparison initially caught floating-point representation differences for 0.8×3
and 1.2×3; asset numeric literals now match the source multiplication exactly,
without loosening the comparison. The retained P test method name still mentions
its original 1.5× checkpoint for test identity continuity; its active assertion
and native audit now verify 3×. New tests explicitly verify the +200% arithmetic
for all three weapons and diagnostic bounds/privacy.

## Validation and artifact result

**DEPLOYED_FOR_TESTING** at 2026-09-10 00:06 UTC (September 9, 20:06 local).
One complete retained validation after focused tests: **2,137 tests = 2,079 root
+ 37 native controls + 21 CanvasUI**, zero failures/errors/skips, all P case
identities retained. The exact resulting JAR passes three-mod isolated boot and
the retained native projectile same-tick insertion/resting-expiry/rollback
fixture. Native startup validates the new 3× animation asset contract.

Archive entry hashes and isolated atomic rollback/roll-forward passed. The
packaged differential rejects changes outside speed/badge/exception telemetry;
all other entries are byte-identical to P, including native power/input,
projectile configuration, Chill assets, resource/cooldown implementation and
persistence/escrow machinery. The unchanged 64-update real-storage diagnostic
restored contributions and measured approximately 4.71/8.12/18.81 ms p50/p95/p99.
Its nominal timing comparison remains false; this is not native tick proof.

| Artifact | SHA-256 |
|---|---|
| Q RPG JAR | `D811E68C7D8E2DE1421EC1E893F597B5DE0B979D5DEDE4470DB430768D0DED73` |
| Three-mod ZIP | `FFC6B57AAE7932799BA2FBAD388851BF597FACF25EEAC640628CC1689A81A220` |
| P rollback JAR | `1C02F192F50F9D699372EDB6D74A0F3FDAE0A0A85C512780C3B280BA0EE7FD21` |

Installed in `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods`.
Only `HytaleRPG-0.0.25.jar` was replaced, with the server stopped. The same CanvasUI
and HytaleDevLib JARs remain. All **18 live non-JAR mod-data files** were hashed
before/after and unchanged. No player/world saves were modified. P is retained
in `evidence/stage-13/cohort-q/rollback/`; rollback requires stopping the server
and replacing only the RPG JAR, not restoring or deleting player data.

Evidence: [deployment manifest](../../evidence/stage-13/cohort-q/quick-slash-speed.json),
[test inventory](../../evidence/stage-13/cohort-q/test-results.json),
[native integration](../../evidence/stage-13/cohort-q/native-spawn-integration.json),
[JAR differential](../../evidence/stage-13/cohort-q/jar-differential.json),
[three-mod archive](../../evidence/stage-13/cohort-q/Hytale-RPG-Stage13-Q-quick-slash-speed.zip).

Connected Q animation speed, hit feedback and new exception frames remain
unverified. Stage 13 is not PASS; the encounter safety issue above is unresolved.

## Minimal connected retest

1. Restart/rejoin and confirm **R032-Q** at top right. Use `/rpg skilltree` to
   equip Quick Slash; confirm `/rpg dev ability-status` shows skill01/Ability2.
2. Equip a supported sword/longsword/daggers, approach a valid enemy within the
   authored arc, press Ability2 once. Check two opposite fast slashes. Separately
   test empty space; it remains a paid miss. Do not confuse animation-only
   visibility with proof that a target was inside the authoritative query.
3. If testing Multistrike, link it and expect three fast alternating pairs,
   one cost/cooldown. Test both surviving targets and a lethal hit.
4. Preserve the complete server log and **all rotated skill traces**, plus
   `ui-trace.jsonl`. For a lethal error, the new `SKILL_EXECUTION_FAILED`
   `codeFrames` should reveal the exact throwing location. Compare
   `DAMAGE_INSPECTED.healthBefore/healthAfter/actualHealthLoss` instead of relying
   solely on `STRIKE_HIT`, which can be skipped by a later exception.
5. Stop progression acceptance testing if encounter uncertainty/award failure
   recurs. Do not delete encounter data or bypass the fail-closed gate.
