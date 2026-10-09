# R245-U7P5 — Elite birth transaction repair

## Evidence and root cause

Baseline: R244 runtime source `d6ae5862f9d9bd7a6cb8209de8327c77bc397033`; checkout started at its deployment receipt `c9ab577b3f50c07bdf6654cc239e1fa315785c28`.

Read-only evidence: `R245_EVIDENCE.json` records the same-session server log, trace and checksums of eleven relevant saved files. The trace ends at 17:37:54 EDT (21:37:54 UTC). The current configuration is 4x. No saved encounter or world configuration was edited for this repair.

At 21:35:58, Health attachment throws `DIFFICULTY_NATIVE_BASELINE_MISMATCH:36.0:74.0` for Cobra and `DIFFICULTY_NATIVE_BASELINE_MISMATCH:124.0:103.0` for Polar Bear. The installed SDK's native stat initializer uses `Role.getInitialMaxHealth()`. Resolved native Role values differ from the authored RPG profile baselines for these inherited variants. The adapter incorrectly treated an authored target baseline as evidence of native component readiness.

The durable compensated abort succeeded for both births. Native cleanup then threw `ENEMY_EXTENSION_FLOCK_ROLLBACK_IDENTITY`: native flock dissolution can invalidate the extension-created flock before the final cleanup. That second failure closed admission. The third root write completed, but its world callback then threw `ENEMY_BIRTH_ROOT_WORLD_CHANGED` against the closed gate. This was not an absence of Elite rolls, a density problem, or the old missing-environment NPE.

| Native job / encounter | Confirmed saved disposition before deployment | R245 handling |
| --- | --- | --- |
| 416 / `dba6b29e-b35e-3971-a820-bb78f4d5c47f` | Cobra Unique; ABORTED, BIRTH_COMPENSATED; root and compensation retained | Existing terminal reconciliation restores original actors; no reroll |
| 431 / `2891aefc-43e2-390a-b683-ab5b91f544fb` | Polar Unique; ABORTED, BIRTH_COMPENSATED; root and compensation retained | Same existing terminal reconciliation |
| 511 / `b418a61e-1dfd-325e-8b93-91c40de004e6` | Polar Champion; RESERVED, no compensation receipt | Resume the original frozen birth when its saved staged actors load |

These are read-only disk dispositions, not claims that connected actor recovery has already executed. No actor's current loaded state can be established from these offline records alone.

## Implementation

- `HytaleDifficultyCombat`: validate native Health against the actual resolved Role baseline, then use the existing managed modifier to reach the original frozen RPG/affix target. Retain foreign-modifier rejection and no-wound-refill behavior. No balance values change.
- `NativeEnemyFlockExtension` / `NativeEnemySpawnGroups`: carry the exact transient reference of a newly created flock across the asynchronous transaction. Cleanup tolerates native dissolution of that same flock and refuses a different replacement flock. Original actors and native population delta checks remain authoritative.
- `NativeBirthContinuation` / `NativeEnemyBirthOwner`: one completion owner chains reserve, attach, activate, publish and lifetime registration. Confirmed prepublication compensation completes normally; uncertain root/publication/compensation acknowledgments remain quarantined. This is orchestration around existing owners, not a second encounter or reward ledger.
- Reservation, attachment, publication and recovery callbacks carry the existing world recovery-stage lifetime token. World teardown completes pending futures exceptionally; obsolete completions cannot mutate a replacement world.
- Native publication releases only still-loaded actors. Unloaded actors retain saved staging/identity for LOAD recovery. All-unloaded publication persists SUSPENDED before completing lease registration. R240 loaded-capacity accounting and grandfathered reactivation remain in use.
- `EnemyBirthRoot`: new roots retain all frozen attachment sources as well as original-group compensation sources. Legacy checksummed roots remain readable without rewriting. Missing legacy addition sources may only be derived from an exact same-role, level, rank, profile and era original frozen source; no current tuning or reclassification is substituted.
- `NativeEnemyWholeBirthRecovery` / attachment: RESERVED/STAGED recovery uses that frozen source to complete missing attachment. Missing unpublished clock/shield state can be initialized; existing state is retained. Published recovery still requires saved combat state and cannot refill Health or shields.
- `EnemyWorldAdmission`: bounded diagnostic phase table and watched futures; `/rpg worldconfig status` reports admission, pending count and oldest age. Existing bounded priority trace records include birth stages, encounter/generation/job/pack, transition sequence and failure reason. No timeout frees a lease or assumes a write outcome.

## Outcome rules

| Outcome | Native group and durable state | Further admission |
| --- | --- | --- |
| Proven rejection before root submission | Restore original group once; discard only owned additions; release unsealed capacity | Open if restore succeeds |
| Prepublication failure with confirmed compensation | Existing durable compensated abort; exact native cleanup; release terminal lease | Open |
| Unknown root/write/publication acknowledgment or failed compensation | Preserve root/actors/lease and fail closed | Closed until authoritative recovery |
| Publication committed; callback interrupted | Replay same birth, pack, generation, actor IDs and saved combat state | Reconciliation required |
| Actor unload during attachment/publication | Retain same identity for existing LOAD owner; no synthetic defeat or reward | Loaded-capacity rules retained |
| Obsolete world callback | Complete old transaction exceptionally; ignore old-world mutations | Replacement admission unaffected |

## Verification

Offline verification covers the production continuation, actual durable file store, admission/capacity owners, production classifier/planner and installed native component fixtures. It includes Unique and Champion plans, 32 consecutive same-world births, stage fault injection, lost acknowledgments, stale callbacks, restart identity, legacy-root envelope compatibility, unload/reactivation, Health wound preservation, dissolved/replaced flock cleanup and trace-priority saturation. R240–R244 regressions remain in the full suite.

Native allocation/placement and client rendering are not exercised by launching a server. No standalone server or game was launched. Final counts, package gates, embedded source SHA, deployed hash and backup location are recorded in `R245_DEPLOYMENT.json` after deployment.

Diff/package checks preserve substantive affix implementations, rarity math, rewards, presentation, R244 biome/naming/aquatic/population changes and the installed native patch. `world-config.json` and the eleven inventoried evidence files must retain their recorded hashes.

## Connected acceptance — owner

1. Restart the main **RPG** save and confirm **R245**.
2. Run `/rpg worldconfig status`: retain 4x density, 65/35 balance, existing rarity and 12 active packs; note admission/pending age.
3. Run `/rpg spawntrace start`; travel through natural hostile spawn areas for two minutes.
4. Run `/rpg spawntrace status` and capture the same-session server log plus the new JSONL.
5. Correlate actual eligible opportunities with `BIRTH_ROOT_DURABLE`, `BIRTH_ATTACHMENT_READY`, `BIRTH_PACK_PUBLISHED_DURABLE`, `BIRTH_NATIVE_FINISH`, `PACK_LEASE_ACTIVATED`, and `ELITE_PUBLISHED`. Further births must continue admitting without a new quarantine, duplicate restoration, or reward.

A random two-minute sample has no promised Elite count. The three old records remain intact for normal load reconciliation; connected recovery and successful natural publication remain owner acceptance items.
