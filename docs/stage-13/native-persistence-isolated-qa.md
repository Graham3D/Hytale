# Cohort J — single isolated connected-QA profile (NOT RUN)

Eligibility: **ELIGIBLE_FOR_ISOLATED_CONNECTED_QA** after local validation, not permission to deploy live. This procedure is for a subsequent explicitly authorized isolated session. Native tick performance, client behavior and copied-world reopening remain **UNVERIFIED**.

## Candidate and safe setup

Use the three archived JARs from `evidence/stage-13/cohort-j/artifacts`, verified against `checkpoint-manifest.json`. RPG SHA256 is `529F7601D69F9DBDA54ABAD87FBF2C674A31C3944E641602AE1BC79225A696AC`. The code label remains R032/0.0.25; identify the candidate by cohort and hash, not label alone. Use installed Hytale 0.7.0-pre.1 build `e8b4d191fc98a977bf5546a951a7b25473d323e3` and the manifest's server/assets hashes.

Create an isolated server/world under the GitHub checkout, never point it at live `Saves/RPG`. With all source writers stopped, retain a coordinated player/reward/encounter/world backup and exact Stage I JAR before copying state. Record machine/CPU/memory/storage, Java version/options, server tick rate, client count/network profile, content hashes and run timestamps. Do not regenerate or rewrite saved authorities to manufacture a passing fixture.

Record a matching native/base-game baseline separately. The candidate's `nonRpgRemainderMs` is useful context but is not a substitute for that control. Record whole-server tick health, process CPU, GC/memory and disk pressure throughout the actual battle; background work still shares those resources.

## One qualification scenario

Pinned master §12.3: **four connected players, 16 hostile targets, 24 projectiles per caster, eight fields per caster and eight summons per caster**. This is 96 projectile carriers, 32 fields and 32 summons at the declared load, inside unchanged global admission caps. Record actual simultaneous populations, not just spawn requests. Use the real native/RPG execution paths and eligible encounter identities; test-double objects, isolated Java loops or reward-ineligible spawned enemies do not substitute for connected proof.

After startup/readiness and a documented warm-up, propose **15 sustained minutes** of measurement. Fifteen minutes is this qualification procedure, not quoted master wording. Exercise deaths/replacement targets, earned rewards/mastery, shield hits/recharge, ordinary checkpoint work, support and cooldown writes concurrently. Keep four players active. If the required load cannot be maintained through supported native paths, record that earliest fixture/integration boundary and do not label a reduced scenario PASS.

During the same session verify:

1. Pending player recovery does not authorize casts. Once ready, native activation reaches the actual executor with exactly one resource payment and cooldown. Observe both successful and cancelled/stale-target pending completions.
2. Managuard/Shared Aegis do nothing before durable authorization. Authorized hits reduce actual native damage without waiting for writes. Recharge/late receipts never duplicate allowance; deactivation/reconnect cannot recover previously spent credit.
3. A death captures current presence/position, waits off-owner for prior durable contribution/mastery, then publishes one earned reward. Remove the entity or leave the world while work is pending; owed durable work must remain. Two worlds must not double the eight-attempt/second delivery budget.
4. On a copied world only, stop/restart and verify learned/loadout, cooldown, support debit, encounter and earned-reward persistence. Perform the coordinated Stage I rollback drill separately from forward recovery; never install an old JAR over a one-sided rewound authority. Crash/power-loss claims require their own appropriately controlled evidence.

## Capture and interpretation

The candidate writes the existing `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` in the isolated server directory. Collect the full server log and client recordings as well. Trace defaults remain 8 MiB with four retained rotations: continuously archive complete rotations during the run so a busy 15-minute capture is not overwritten. Do not edit the packaged trace configuration/JAR and still call it the exact candidate. Check `TRACE_GAP`, writer failures/dropped metrics and the collector's own continuity; loss invalidates coverage.

- `NATIVE_RPG_TICK_SAMPLE`: group by **world UUID + actual native tick**. Exclusive phase sum is the RPG callback wall total; nested spans must not be summed twice. Compare actual tick p95 to **4 ms**, p99 to **8 ms**. Record sample count and the declared tick rate; exclude only the explicitly incomplete first/terminal samples. Unknown/missing ticks are not zeroes. Adjacent whole-world duration pairing is marked explicitly.
- `PERSISTENCE_HANDOFF_METRICS`: chart pending counts/reserved bytes, oldest receipt, pending deaths/transitions, checkpoint/WAL backlog, rejections and failures. Record arrival/completion throughput over the whole interval and the final drain. Sustained growing backlog is a separate capacity blocker even if native tick time is low.
- `PROGRESSION_OWNER_PUBLISHED`: keep event→durable, durable→owner and end-to-end distributions separate, with origin-known counts. This proves owner-side observation only; correlate client recordings for visible updates. Unknown/recovered origins and dropped diagnostic samples must be reported, not excluded silently.
- Correlate existing cast, contribution, mastery, death, reward and shield events by their stable IDs. No durable-success event may precede its required receipt. Record all deadline/uncertainty incidents; the five-second operational deadline is not an acceptable normal latency target.

Do not add independent percentiles or compare sample maxima from unequal workloads. Keep the unchanged 64-update diagnostic in the handoff as storage evidence only. A native gate passes only with complete connected evidence for the stated scenario. Intended-deployment player-count scaling and the remaining Stage 13 release/visual/fault gates remain separate, even if this isolated profile passes.
