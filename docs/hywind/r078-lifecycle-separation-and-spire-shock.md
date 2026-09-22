# R078 skill lifecycle separation and Lightning Spire Shock correction

R078 separates cast/action ownership, cooldown work, and active-effect lifetime. It also replaces the non-advancing Lightning Spire Shock animation carrier with deterministic eight-frame playback.

## Ownership map

| Concern | Authority after R078 |
|---|---|
| Validation, wind-up, COMMIT, release | `SkillExecutionService` |
| Resource reservation/payment | `RpgResourceService` |
| Persisted cooldown/recharge work | `RpgCooldownService` |
| Short action/cast locks | instance-keyed `SkillInstanceLifecycle` |
| Persistent areas/projectiles/constructs | their family runtime, keyed by skill instance |
| Lightning Spire health, hits, gauge, waves, expiry | `LightningSpireRuntime` and native visuals, keyed by Spire instance |
| HUD cooldown sweep | persisted cooldown ledger only, never active-effect lifetime |

Cooldown is durably submitted at COMMIT for every family, including channels. Release, impact, channel end, effect expiry, and construct destruction do not start, restart, clear, or refund it. A pre-COMMIT interrupted wind-up retains the existing no-cost/no-cooldown behavior.

## Concurrency and commands

Compiled plan schema 42 exposes `ConcurrentInstancePolicy`. Lightning Spire is explicitly `UNRESTRICTED`; Bomb Toss is `COMMAND_EXISTING`. The old owner-to-one-Spire map and native active-Spire admission checks were removed. One owner can now retain multiple Spires, and every hit, gauge, wave queue, health probe, expiry, and cleanup operation addresses an exact instance ID.

Bomb Toss resolves a valid existing grounded bomb command before ordinary new-cast cooldown validation. That command detonates the existing instance without a second resource payment or cooldown spend.

Global entity/effect budgets remain authoritative and reject new work instead of silently replacing another legal instance.

## Recovery and casting rate

New cooldown debts store required work separately from dynamic owner recovery. Owner recovery changes first advance work at the previous rate and then affect future work. The shared formula remains `Base * DurationFactors / (1 + clamp(Recovery, 0, .75))`; Alacrity remains in the same rate bucket. Legacy persisted cooldown entries retain their captured recovery until that old debt expires, avoiding an unsafe save migration.

`CastRateModifiers` is the typed input for passive, equipment, and temporary casting rate. It applies only to positive wind-up time using division by `1 + CastRate`; it has no cooldown, tick, projectile, or lifetime output.

The broader weapon-affix runtime is not present. `of Invocation` and `of Readiness` therefore remain definition/design baselines, with typed cast-rate and recovery inputs ready for the future affix resolver; R078 does not claim those item affixes are loot-runtime connected.

Second Wind continues to use the existing independent charge queue and recharge factor.

## Lightning Spire Shock

The client model carrier did not reliably advance its `.blockyanim`. R078 exports the owner-authored 448x49 `shock.png` into eight 56x49 frame textures and eight model resources. Every accepted allied melee hit creates one three-plane Shock carrier and advances frames server-side at 100 ms per frame for one non-looping 800 ms playback. Startup now fails loudly if any frame model is unresolved.

## Verification boundary

Automated coverage includes instance-exact Spire overlap/cleanup, nonblocking projectile ownership, typed concurrency policy, dynamic cooldown work-rate transitions, cast-rate math, and all eight packaged Shock frames. Packaging, bare-server smoke, install deployment, and post-deployment verification are required before release. Connected-client verification remains necessary for the two/three-Spire interaction matrix and visible Shock frame playback.

## Deployment record

R078 was packaged and deployed on 2026-09-22 as `InigmasGames:Hywind@0.1.0-merge.33`.

- Installed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- SHA-256: `BF7F10FE0FDD1A11E280F6753AAA70A2CA52C19356E1DAECE08F1D5026C314DF`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260922T142634Z`
- Package verification: PASS (5,953 entries, 2,234 classes, 2,090 UI documents)
- Isolated server smoke: PASS
- Live-save restart verification: PASS on two consecutive starts and clean shutdowns
- Full automated suite: 2,395 of 2,397 tests passed. The two failures are unchanged, unrelated byte-baseline checks for Mantle and Healing assets; neither asserted file changed in R078.

Connected-client acceptance remains intentionally open: each accepted Spire hit must visibly play exactly one complete eight-frame Shock animation, and multiple simultaneously active Spires must retain independent health, strike gauge, shockwave, expiry, and cleanup state.
