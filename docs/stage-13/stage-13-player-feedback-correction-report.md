# Stage 13 Correction P — player feedback and projectile lifetime

## Scope and evidence standard

Branch `RPG`, baseline Correction O commit `790121472246b160224de584ed699d6d5af624d0`.
HUD revision `R032-P`; plugin version remains `0.0.25`. This is a bounded testing
correction, not a new stage or production acceptance closure. Native resource
bars, XP composition, native ability input/projection, equipment power coverage,
resource/cooldown formulas, persistence/escrow/exact-once machinery and NORMAL
trace aggregation are not redesigned.

The owner requested two faster Quick Slash swings, Fire Bolt cleanup, five
supplied Chill icons in Hytale's native debuff HUD, and restoration of the
top-right revision badge. Two explicit owner decisions govern Quick Slash:
**each swing deals the previous full damage**, and **Quick Slash remains eligible
for Multistrike**. The latter is an explicit exception to the ordinary authored
multi-hit exclusion; it does not open Multistrike to Dagger Flurry or other
previously excluded authored sequences.

All work and evidence are in the C: GitHub checkout. No Google Drive files or
live player/world save data are implementation inputs to be modified. The five
owner PNG files are copied unchanged, not redesigned or rescaled.

## 1. Fire Bolt: connected failure and earliest boundary

Source session: `2026-09-09_19-00-11_server.log`, UTC window
`2026-09-09T23:00:11Z` through `23:06:10Z`. See
[captured correlated evidence](../../evidence/stage-13/cohort-p/connected-feedback-evidence.json)
for source hashes, event counts, identifiers and exact timestamps.

The successful Fire Bolt request reaches activation, validation, commit,
executor dispatch and one `PROJECTILE_SPAWN_REQUEST`. Approximately **7.5035 ms**
later it receives `PROJECTILE_CANCELLED` / `PROJECTILE_TERMINATED` with
`NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT`. There is no `PROJECTILE_SPAWNED`
for that request. The session also contains six other pre-spawn rejection
events; those must not be conflated with this committed projectile's lifecycle.

Correction O fixed native construction but its isolated audit did not run the
production advance loop before the native CommandBuffer drained. In connected
execution, spawn and `advanceProjectiles` can occur in the same owner tick.
The pending Ref is not valid until insertion. The old advance code treated that
pending Ref as a removed native projectile and deleted RPG tracking. Native
insertion then created an entity without its RPG lifetime/impact ownership.
The acknowledgement callback could no longer emit `PROJECTILE_SPAWNED` because
the tracked carrier was gone. This explains how the projectile could remain
visually present after RPG had already terminated it; the native fallback
lifetime is much longer than the RPG flight lifetime.

### Correction

`HytaleSkillExecutionSystem.ProjectileCarrier` now has owner-thread insertion
state. Advancement skips **pending insertion**, not all invalid entities.
The FIFO acknowledgement marks insertion complete. If the carrier was cancelled
or rolled back while pending, that callback queues removal instead of leaving an
orphan. After insertion, the existing invalid-Ref, distance, terrain and monotonic
lifetime rules still apply. There is no persistence wait or alternate damage path.

Ordinary terrain termination also requests the existing projectile-expiry VFX.
It uses existing assets, not a new graphic. A visual fizzle remains subject to
connected-client observation; a server-side VFX request is not rendering proof.

### Stronger native regression

The opt-in, isolated `NativeProjectileSpawnAuditCommand` uses the installed
`ProjectileModule.spawnProjectile` and the real
`Projectile_Config_RPG_Fire_Bolt`. It now:

1. Retains O's unmodified-config empty-EnumMap failure control.
2. Calls the actual production advancement path **before** the spawn buffer
   drains; pending insertion must not cancel ownership.
3. Verifies the inserted native Ref and physics/velocity components, speed 24,
   empty native gameplay roots, and one `PROJECTILE_SPAWNED`.
4. Retains a second pending carrier's atomic rollback test, with no spawn
   acknowledgement for the rolled-back carrier.
5. Sets the first native carrier to RESTING as a controlled stopped-flight
   fixture, waits asynchronously, then schedules the real owner advancement
   after its one-second monotonic lifetime and verifies native removal.

The fixture asynchronously loads and holds chunk (0,0) for the operation, then
releases that hold. It never sleeps on the owner thread. It cleans up its isolated actor
and carrier. It does not run against the RPG save. RESTING expiry proves native
construction/ownership/removal under a stopped-flight condition, **not a real
connected terrain collision, network delivery, or particle rendering**.

## 2. Quick Slash: two full hits, native light animations

The base profile now authors two hits, each at the existing **0.85 × Weapon
Power** coefficient. Cost remains **5 Stamina once** and cooldown remains
**0.8 seconds once**. Empty-space execution remains a paid miss. Existing
per-instance/per-hit target ledgers allow one target to receive each distinct
slash without accepting a duplicate of the same hit.

The native `SwingLeft` and `SwingRight` actions are copied from installed Sword,
Longsword and Daggers animation profiles. Both first-person and third-person
paths, moving variants, blends and other action fields are retained. Only action
speed changes, to **1.5 times native speed**. No native attack interaction roots
are invoked, so these visual animations do not add a second native damage path.

| Weapon | Native speed | Quick Slash speed | Interval between swings |
|---|---:|---:|---:|
| Sword | 1.0 | 1.5 | 0.2777778 s |
| Longsword | 0.8 | 1.2 | 0.3472222 s |
| Daggers | 1.2 | 1.8 | 0.1851852 s |

Intervals derive from installed animation duration and Hytale's 60 FPS animation
timebase, not a guessed attack average. Startup audits compare resolved native
and RPG action paths, speeds, looping flags and cached first/third-person
durations. See [native source capture](../../evidence/stage-13/cohort-p/quick-slash-native-source.json).
The existing repeat scheduler and action-lock lifecycle cover the complete
animation sequence. Actual client timing/blending still needs playtesting.

### Multistrike owner exception

The repeat unit is the **whole two-slash attack**. A linked cast contains six
swings: two primary hits at full coefficient, followed by two derived pairs at
65% magnitude per hit. Derived groups retain the existing two child identities,
root/correlation ownership, shared budgets and no-recursion rule. A child pair's
second hit uses its already-admitted authored component rather than trying to
admit the same child twice. One cost and one cooldown remain on the primary.

`ProfileComponentPolicy` permits only `quick_slash` as the authored-sequence
exception. `CompiledProfileResolver` resolves six swings and a bounded action
window covering the slowest supported weapon. The native repeat loop maps
scheduled hits 0–1 to the primary, 2–3 to child 1 and 4–5 to child 2, alternating
the two native light animation directions in every pair. Other skills retain
ordinary Multistrike's three-hit / 0.25-second cadence.

## 3. Chill uses native debuff presentation

The five supplied `art/StatusChill01.png` through `StatusChill05.png` files are
packaged byte-for-byte at `Common/UI/StatusEffects/RPG/`. Four presentation-only
EntityEffects expose native `Debuff=true` and `StatusEffectIcon` for actual Chill
stacks 1–4. They add no damage, stat, resource or movement effects.

`HytaleAreaStatuses.synchronize` selects the icon from actual Chill state,
independently of the strongest-only slow channel. Thus another stronger slow
does not erase the Chill stack indicator. Obsolete icons are removed when
stacks change or expire. The fifth icon is attached to existing Frozen and
protected Frozen-slow effects: the unchanged status kernel converts the fifth
Chill application to Frozen rather than maintaining a fifth persistent Chill
stack. Immunity/protection rules remain unchanged.

Hytale owns the buff/debuff HUD layout. There is no custom Chill overlay. Player
icon visibility, duration, scaling and interaction with other native debuffs
remain connected QA requirements. A kernel-only developer status command does
not prove native HUD projection; test through a legitimate Chill application
to the player under an allowed hostile-target setup.

## 4. Revision badge

The existing `Phase00RevisionHud.ui` is appended by `RpgHud`, whose lifecycle
already installs/removes the RPG HUD. Its label is `R032-P`, anchored Top 18 /
Right 18, using the existing badge design. No resource bar is hidden, replaced
or duplicated. Native Stamina's temporary visibility and all resource-bar
ownership remain unchanged. XP assets/composition are unchanged.

## 5. Validation chronology and retained assertions

Focused implementation initially caught a missing non-null authored strike
details object; that was corrected explicitly. Changing Quick Slash from one
hit to a scheduled pair also invalidated old test-fixture assumptions that it
terminated immediately. Those fixtures now explicitly terminate their mocked
sequence before inspecting termination or starting another independent cast.
No failure assertions were suppressed.

Ordinary Multistrike fixtures were moved to canonical single-hit Spear Thrust
or Shield Bash, retaining their original three-hit timing, child-magnitude,
one-payment, recursion and secondary-effect assertions. Expected resource and
coefficient values in those fixtures match the selected canonical skill. New
Quick Slash tests cover the separate pair exception rather than redefining
every ordinary Multistrike test as six hits. No retained test identity is removed.

A first complete run passed 2,134 tests before the owner's Multistrike answer.
That intermediate result is retained as
`full-validation-before-owner-multistrike-choice.txt`, not used as final candidate
proof. After the owner selected eligibility, the explicit exception and new
regression passed focused tests; a fresh complete retained run validates the
resulting candidate. The release scripts require all baseline case identities,
zero failures/errors/skips, exact-JAR native smoke, archive integrity and rollback.

The final smoke initially rejected an unloaded-chunk fixture: its PASS message
only checked that the native Ref was gone, while the new trace assertion correctly
required `MAX_LIFETIME` from production advancement. Native chunk eviction could
empty the fixture's store before its delayed callback. The fixture now holds its
chunk loaded and explicitly asserts that the owner advancement callback ran.
The rejected smoke output is retained; its false-positive message is not used
as proof. This fix affects only the opt-in isolated audit, not live chunk policy.
The native controls and full suite are rerun on the corrected candidate.

### Final candidate and deployment

**DEPLOYED_FOR_CONNECTED_TESTING**, 2026-09-09 23:42 UTC. Full retained run:
**2,135 tests = 2,077 root + 37 native controls + 21 CanvasUI**, zero failures,
errors or skips. All O test case identities retained. The complete run includes
retained persistence, escrow, nonblocking, exact-once, crash/fault recovery and
archived-reader regressions. CustomUI structural validation checked 32 documents;
this is not client-rendering proof.

Exact-JAR three-mod smoke, native construction/same-tick/resting-expiry/rollback
integration, ZIP entry hashes and isolated atomic rollback/roll-forward all
passed. Packaging initially flagged the two new empty ZIP directory entries for
the Chill icons; the differential now explicitly permits those exact directories,
not arbitrary extra resources. All entries outside the recorded correction
allowlist remain byte-identical to O.

| Artifact | SHA-256 |
|---|---|
| HytaleRPG-0.0.25.jar (P) | `1C02F192F50F9D699372EDB6D74A0F3FDAE0A0A85C512780C3B280BA0EE7FD21` |
| CanvasUI-0.1.0.jar (unchanged) | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar (unchanged) | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| Three-mod ZIP | `FFD6515E68E2187105C151D1732BEC6C8610983BA61A2BEF86EB2559AD324F58` |
| O rollback RPG JAR | `B8BCCF7DCAA4E7949A32961F261504A0604E7A5671F8E32FBF1BEBBD0CEF0400` |

Deployment destination:
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods`.
The server was stopped; only the RPG JAR was atomically replaced. Exactly three
mods remain. All **17 live non-JAR mod-data files** were hashed before/after and
unchanged. No live world/player saves were edited. Rollback is a stopped-server
replacement of only `HytaleRPG-0.0.25.jar` with the retained O JAR in
`evidence/stage-13/cohort-p/rollback/`; do not restore or overwrite player data.

Artifacts and machine-readable evidence:

- [Three-mod testing archive](../../evidence/stage-13/cohort-p/Hytale-RPG-Stage13-P-player-feedback-correction.zip)
- [Deployment/hash manifest](../../evidence/stage-13/cohort-p/player-feedback-correction.json)
- [Test inventory](../../evidence/stage-13/cohort-p/test-results.json)
- [Native integration](../../evidence/stage-13/cohort-p/native-spawn-integration.json) and [correlated records](../../evidence/stage-13/cohort-p/native-spawn-records.json)
- [JAR differential and rollback validation](../../evidence/stage-13/cohort-p/jar-differential.json)
- [Retained real-storage benchmark](../../evidence/stage-13/cohort-p/durability-load.json)

The unchanged 60-sample / 64-update storage acknowledgement diagnostic measured
**4.4939 / 8.1554 / 17.3542 ms p50/p95/p99**, with restored contribution counts.
Its nominal 4/8 ms comparison remains false. It is not a native tick latency
measurement and cannot establish that release gate. No threshold or assertion
was relaxed to make this correction pass.

### Changed-file map

| Area | Files / responsibility |
|---|---|
| Projectile ownership | `execution/hytale/HytaleSkillExecutionSystem.java`: insertion acknowledgement, pending-cancellation cleanup, terrain expiry presentation |
| Native regression | `execution/hytale/NativeProjectileSpawnAuditCommand.java`: same-tick advance, loaded-chunk fixture, resting expiry, existing rollback control |
| Quick Slash definition | `rpg/catalog/skills.json`, `rpg/runtime/stage-04-skills.json`: two full-power hits, explicit finite details |
| Multistrike exception | `execution/ProfileComponentPolicy.java`, `execution/CompiledProfileResolver.java`, `rpg/catalog/passives.json`: Quick Slash-only eligibility, complete-pair repeats |
| Native strike visuals | `execution/hytale/NativeStrikeFeedback.java`, three `Server/Item/Animations/RPG_QuickSlash_*.json` assets |
| Chill visuals | `execution/hytale/HytaleAreaStatuses.java`, four `RPG_Chill_Icon_*.json`, Frozen/Frozen-slow icon metadata, five unchanged PNG copies |
| Revision badge | `ui/hud/RpgHud.java`, `Common/UI/Custom/Phase00RevisionHud.ui` |
| Tests | New seven-case `Stage13PlayerFeedbackCorrectionTest`; retained Stage04/Stage11 fixture corrections described above |
| Evidence/tooling | `Capture-Stage13PlayerFeedbackEvidence.ps1`; P support in existing smoke, publishing and JAR differential tools; this report and review indexes |

Java paths above are relative to `src/main/java/com/inigmasgames/hytalerpg/`;
asset paths are relative to `src/main/resources/`. Test paths are under
`src/test/java/com/inigmasgames/hytalerpg/`. The archive differential records
every changed packaged entry and rejects all entries outside this correction's
allowlist. Canonical inventory remains **87 skills / 66 passives**.

## 6. Connected checklist — still required

1. Restart/rejoin RPG after deployment; verify **R032-P** at top right and native
   resource bars unchanged. Run `/rpg skilltree`, equip Quick Slash into skill01
   and Fire Bolt into skill02; confirm with `/rpg dev ability-status`.
2. With an allowed sword/longsword/daggers, press native Ability2 once. Verify
   two opposite light swings at 1.5× native animation speed. Repeat into empty
   space and against a valid enemy. Require one committed cast/cost/cooldown;
   distinct hit indices 0 and 1 may each damage an intersecting target.
3. Link Multistrike to Quick Slash in the skill tree. Cast once: expect three
   complete alternating pairs, primary full damage and two child pairs at 65%,
   still one cost/cooldown and no recursively repeated child.
4. With an audited staff/wand equipped, press Ability3 once toward nearby ground,
   then separately into open space. Require `PROJECTILE_SPAWNED` followed by
   terrain termination or bounded expiry, disappearance and fizzle. The old
   same-tick `NATIVE_PROJECTILE_REMOVED_WITHOUT_IMPACT` cancellation must not recur.
5. Receive legitimate Chill applications as a player. Observe native debuff
   icons 1–4, conversion to icon 5 for Frozen/protected Frozen-slow, and removal
   on expiry. Check coexistence with another slow. Do not infer this from a
   kernel-only status command or an NPC with no player HUD.
6. Restart/rejoin once more and check saved loadout/passive links. Preserve the
   complete server log and `skill-trace.jsonl` / `ui-trace.jsonl` for review.

No connected P pass is claimed. Formal native tick p95/p99 performance remains
unverified; the retained storage acknowledgement benchmark is not that gate.
Historical native slot-clear conflicts at disconnect are not resolved by this
bounded correction. Stage 13 is **not PASS** merely because this testing build
packages and boots.
