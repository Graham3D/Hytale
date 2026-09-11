# Stage 13 cohort V — Healing Beam, Blessing of Protection, Tether continuations

Baseline: `RPG` commit `5bbc346` (R032-U). Candidate: **R032-V / 0.0.25**.
Authority: owner cohort request and Master Implementation Specification v1.3.
The explicit new-content request supersedes the earlier 87-skill/66-passive limits
and illustrative-only Arc note. No other master rule is superseded.

**Package-only correction. No live deployment, connected QA, or live save mutation.**
Local tests and resolved native assets do not prove client rendering, held-input
delivery, multiplayer behavior, or native damage application to a connected player.
Stage 13 is not promoted to a fully verified release by this cohort.

## Implementation and reasoning

### Healing Beam — SK-088 / `rpg.skill.healing_beam`

The existing `ConnectionRuntime` owns the channel. `HEAL_TETHER` is an explicit
profile kind, not another scheduler; lifetime zero means indefinite only for this
kind. There is one committed SkillInstance and one field-budget reservation.
Existing finite connection lifetimes and simulation-gap limits remain in force.

Initial acquisition uses the existing aimed line geometry and LOS check, but an
explicit friendly query. It requires a loaded, living, permitted non-caster ally
within 18 m. After acquisition, the committed UUID is resolved afresh each tick;
crosshair movement does not retarget it. Native references are not retained in
the channel state. The existing native friendship policy is reused unchanged:
SELF or affirmative native FRIENDLY/REVERED attitudes, with self explicitly
excluded for Healing Beam. Neutrality/no-PvP is not invented party membership.

The runtime samples authoritative LOS, starts a 1.5-second timer on first loss,
continues healing/presentation through the grace interval, resets on recovery,
and terminates at `>= 1.5 s`. Range failure has no grace. Release, invalid actor,
invalid alliance, equipment change, world change, despawn and insufficient Mana
end the instance. Existing logout, death and control cleanup are retained.

Base pulses occur every 0.25 s with coefficient `0.45 * 0.25 = 0.1125`.
HealingPower uses the existing baseline 20 and derived WIS multiplier. Mastery
already resides in the committed snapshot's multiplicative modifiers and is not
applied again. `SupportMagnitude.tetherHealing` evaluates Triage separately for
each recipient. Native Health-before/after observations feed existing actual-heal,
Overflow and support-credit handling; missing healing is never counted as overheal.

Upkeep uses the existing reservation/payment service, 4 Mana per elapsed second.
Due intervals are paid before their healing; fractional inter-pulse time is paid
without rounding to an integer Mana or to the pulse cadence. Rapid Pulse changes
interval to 0.175 s and payload to 80%, without damage-per-second compensation or
extra Mana per second. Efficiency and Overcharge use existing cost modifiers.
No conventional cooldown is charged on entry; the existing channel-termination
cooldown ticket supplies the 0.25-second minimum re-entry lock durably. Failed
persistence remains fail-closed; there is no new blocking world-thread write.

Holding uses a narrow `ChargingInteraction` subclass. Its wire representation
is Hytale's supported Charging packet, not a new client opcode. Native simulation
owns key state; the server callback observes held/released state and enters the
existing exactly-once native activation queue. Both native server/client chain
states are checked. Cleanup cancels only the matching owned chain using the
installed `InteractionManager.cancelChains(chain)` API, never unrelated chains.
The generic Ability2/Ability3 bridge, Snipe release root and HUD projection are
not replaced. The new native ItemAbility still costs zero with no native cooldown.

### Blessing of Protection — SK-089 / `rpg.skill.blessing_of_protection`

Spellbook only; 30 Mana; instant; 30-second cooldown and duration. Existing
friendly Direct Target selection chooses an aimed legal ally or self. No new
self-cast binding or neutral-to-friendly shortcut was introduced.

`MaxHealthDamageCap` is a typed component exposed by the existing finite-effect
ledger, not a string comparison against a skill name in the damage callback.
Its coefficient remains exactly 0.10, independent of WIS, mastery and magnitude
modifiers. Each eligible hostile direct packet is clamped to 10% of the target's
current maximum Health. Small hits remain unchanged and multihit packets are
capped independently. Identical blessings never compound.

Native dependency ordering is: native filter/mitigation group → HealthCap →
RPG Shield → Managuard absorption → ApplyDamage. No second mitigation pass is
performed. The existing escrow/debit and exact-once reward implementations are
unchanged. RPG PERIODIC, REFLECTED and REDIRECTED provenance bypasses the cap;
non-entity/environment sources and self damage bypass it as well.

An important installed-API audit finding: `ActiveEntityEffect.tickDamage` uses
`Damage.EntitySource` when it has a valid owner. Therefore source type and Fire
cause alone cannot distinguish native Burn from a direct Fire spell. Native
effect damage also carries `DamageCalculatorSystems.DAMAGE_SEQUENCE` with the
actual effect asset's calculator. The new filter checks that calculator identity
against loaded EntityEffect assets (weak-key cached, synchronized). It does not
guess from item names or exempt every Fire/Poison direct hit. Native unattributed
effect sources already fail the entity-source requirement. Explicit non-damage
Health writes/resource conversions never enter this cap.

### LP-067 Arc and explicit Tether composition

Compiler eligibility requires explicit TETHER plus scalable Heal/Damage payload,
not a beam-looking visual. Arc/Fork/Chain compile to terminal Tether operations.
Existing Projectile Fork/Chain modifier operations, executors and profile data
are retained. Unmodified Life Drain still uses its original runtime path.

Each pulse allocates one bounded visited set, containing the primary first:

| Selection order | Secondary coefficients | Origin for distance / LOS |
|---|---|---|
| Arc | 0.60 | Primary |
| Fork | 0.45, 0.45 | Primary |
| Chain | 0.70, 0.49 | Previous chain recipient |

Every segment is limited to 8 m and LOS. Candidates sort by distance then stable
ID. Selection excludes previously visited recipients, uses root polarity and
requires injured allies for friendly secondary healing. Maximum five secondary
recipients; every payload enforces `NoTetherFanout=true`. There are no persistent
child SkillInstances or secondary Mana charges. Catch-up healing pulses reassess
injury; candidate overflow is rejected before that pulse's payment/healing.

### Native presentation audit

`cohort-v-native-asset-audit.json` records the installed Assets.zip hash and exact
candidate contents/hashes, marked **VERIFIED_ASSET**, not connected proof.
Healing Beam uses the finite `Beam_Heal_Green` native system at bounded samples
along each authoritative segment. It is refreshed only while the runtime owns
the tether; no persistent particle entity is created. The native system's short
lifespan avoids an indefinite orphan. Whether these samples read as a continuous
beam, and their orientation/endpoints, remains a connected visual QA requirement.

`Healing_Totem_Heal` contains gameplay Health restoration but no useful particle
references; the whole effect is deliberately not applied. Long-lived/large-ground
totem effects were not selected. Blessing's small `RPG_Protection_Glow` wrapper
reuses the installed Crown gold glow spawner, attached via a cosmetic 0.2-second
renewable native effect. It has no damage, Health, stat or resource mutation.
Native Spellbook `CastPushCharging` / `CastPushCharged` animations are reused;
no player animation or texture was authored. Particle appearance, attachment,
scale, cleanup latency and animation quality still require connected testing.

## Catalog, persistence bounds and owner assets

Catalog: **89 skills, 67 passives**. Matrix: **5,963** skill/passive cells and
**2,211** passive pairs, evaluated against all 89 profiles. Historical named test
cases are retained even where their names contain old counts; assertions and
generated inventories now use the expanded exact counts.

Existing persisted maps whose capacity equals catalog cardinality now admit 89
skills / 67 passives. Formats, checksums, sequencing, escrow, durability and
recovery algorithms are unchanged. The boundary test now rejects the 90th
cooldown entry rather than the formerly invalid 88th. New skill learning sources
remain explicitly UNASSIGNED: 66 proposed sources and 23 unassigned skills,
with zero verified native learning bindings. No acquisition source was invented.

The optional icon index and CSV now include `SkillHealingbeam.png`,
`SkillBlessingofprotection.png` and `PassiveArc.png`. The owner-facing updater
accepts both exact catalog versions (87/66 and 89/67), still validating every ID,
filename, PNG, archive and rollback hash. No new owner artwork was invented;
absent new icons retain native fallback graphics. Existing four owner skill PNGs
are preserved in the candidate through the same importer.

## Validation record

Focused Healing/Tether tests and the expanded compatibility matrix passed.
The first full attempt reached 2,123 root tests and exposed 16 failures: seven
old count/bound expectations plus nine icon-updater tests blocked by its old
153-entry whitelist. Original failed XML is retained under
`evidence/stage-13/cohort-v/initial-validation-failures/`. After updating explicit
catalog capacities and retaining the updater's old-version support, all affected
suites passed in a focused rerun. No assertion was deleted, no failing test was
disabled, and no resource, persistence or performance threshold was relaxed.

Final complete validation passed on 2026-09-11: **2,193 tests**, comprising
2,123 RPG, 49 native-control and 21 CanvasUI; zero failures/errors/skips.
All 2,166 U case identities are retained. V adds 26 support/Tether tests and one
native Charging codec test. CustomUI validation checked all 32 documents.
The complete matrix has no single- or pair-profile resolution failures.

The exact packaged JAR passed isolated three-mod boot and clean shutdown;
`RPG_SUPPORT_TETHER_ASSETS` resolved the native held root, animation references
and cosmetic assets. The retained actual native Fire Bolt construction test
produced one spawned carrier from two requests, validated pending-batch rollback,
same-tick advance and stationary lifetime expiry. These are isolated native
integration results, not connected rendering/input evidence.

`Verify-Stage13CastingPackage.ps1 -Cohort v` checks every packaged class against
the compiled classes used by full validation, permits only the bounded cohort
entry changes, verifies every old skill record and all non-Fork/Chain passive
records are identical, preserves Fork/Chain gameplay fields, checks zero native
cost/cooldown on both new items, and verifies all four owner icon byte hashes.
Snipe/Whirlwind Item JSON differs only in PowerShell 5 versus 7 serialization
whitespace; exact parsed field equality is required, including Icon and Ability.
Every other entry outside the recorded allowlist is byte-identical to U.

Archive verification confirms exactly the established three mods. Isolated
atomic JAR rollback and roll-forward passed. Archived-reader, crash/fault,
nonblocking handoff and shield-escrow regressions passed unchanged. Crash tests
simulate process halt, not physical power loss. Native HUD/input projection,
equipment power registry, gameplay resource formulas, XP and persistence formats
were not redesigned. The unchanged HUD badge still reads R032-U; identify this
package by the V startup audit and the exact JAR hash rather than that old label.

**Remaining release gate:** the retained real-storage benchmark (60 samples,
64 updates each) measured p50 **4.8042 ms**, p95 **8.3022 ms**, p99 **21.6764 ms**.
Its unchanged targets are 4 ms p95 / 8 ms p99; `withinNominalRpgTickBudget=false`.
Contributor recovery passed. This is persistence-only measurement, NOT native
ECS/physics/network/rendering timing. The formal performance gate remains unmet;
the passing regression suite does not erase it. Existing Frenzy/Guard/Bone Cage
capability gates also remain, as recorded in the generated coverage ledger.

### Artifacts and exact hashes

All receipts are in [`evidence/stage-13/cohort-v`](../../evidence/stage-13/cohort-v).

| Artifact | SHA-256 |
|---|---|
| `artifacts/HytaleRPG-0.0.25.jar` | `1103268939C69FA6B3D9E58DB2AE10F66A2412776AC00A43A9C13C64D24889FF` |
| `artifacts/CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `artifacts/HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `Hytale-RPG-Stage13-V-support-tethers.zip` | `FA96B4640349C5313517707C77941D49293DC94DEF0B30C369F4755F4D11C0EE` |
| Retained U RPG rollback JAR | `BFF7421FA765834E32EEE819072AA9FF665C085FFCB0ECCFF9CBFF38BC3B965E` |

The live RPG JAR still has that U hash. No deployment function was invoked and
no live save data was edited. V's publish tool explicitly refuses `-Deploy`.
The archive is a test candidate, not a declaration of connected acceptance.

Reproduction commands from the GitHub repository (Java 25 installed):

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test --rerun-tasks --console=plain
.\gradlew.bat :jar --console=plain
.\tools\Update-RpgIcons.ps1 -JarPath build/libs/HytaleRPG-0.0.25.jar -BackupRoot evidence/stage-13/cohort-v/candidate-icon-backups
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort v -NativeProjectileSpawnAudit
.\tools\Publish-Stage13StartupHotfix.ps1 -Cohort v
.\tools\Verify-Stage13CastingPackage.ps1 -Cohort v
```

Archived evidence is immutable: a rebuild with a different hash must use a new
evidence directory/cohort, not overwrite the completed V archive. Close Hytale
before running icon-updater validation; never run an isolated server in parallel
with tests that correctly require the game/server to be stopped.

## Connected checklist — NOT RUN

Do not replace live mods until the owner authorizes testing. Before any future
installation, back up the RPG save and its mod data as a pair with R032-U. A JAR
rollback drill alone is not proof that U can read saves containing V-only skill
IDs or more than its old catalog bounds. Restore the matching pre-V save when
rolling back newly authored content; never delete player data to force a load.

In an authorized test copy, use the existing skilltree or commands:

```text
/rpg-trace normal
/rpg equip skill01 healing_beam
/rpg equip skill02 blessing_of_protection
/rpg loadout
/rpg dev ability-status
```

1. Equip a staff or Spellbook for Healing Beam. Aim at an injured, affirmative
   native friendly ally within 18 m; hold the assigned native skill input.
   Check one activation/commit, four baseline pulses per second, continuous
   4 Mana/sec debit, actual Health increase, locked target and held presentation.
   Player-party support remains unavailable in the existing native policy;
   use an actually friendly native NPC, not a neutral/no-PvP player as a substitute.
2. Release; verify healing and beam stop, native animation clears, and re-entry
   is locked for 0.25 s. Repeat with equipment changes, death/logout, invalid
   alliance, world transfer, range break and insufficient Mana. No unpaid heal.
3. Break LOS for 1.49 s, then recover; verify continuity and timer reset. Keep
   it broken for at least 1.5 s; require termination. Move beyond 18 m: immediate
   termination independent of LOS grace. Do not substitute visual timing for
   authoritative trace timestamps at the exact boundary.
4. Test Arc/Fork/Chain individually and together with six injured allies:
   `/rpg equip passive01 arc`, `/rpg link passive01 skill01`; use passive02/Fork
   and passive03/Chain similarly. Verify coefficients, five-secondary maximum,
   no repeated recipient, per-hop LOS/range, no child root activation, and no
   extra upkeep. Repeat with Rapid Pulse, Triage, Overflow, Potency/Overcharge.
5. Equip a Spellbook. Cast Blessing on self, then an aimed friendly ally. Verify
   30 Mana, one 30 s cooldown, a restrained actor-attached glow and 30 s expiry.
   Hostile/neutral actors must not receive it. Test measured post-mitigation
   direct hits of 80 and 7 against 100 Max Health: capped amounts 10 and 7 before
   barriers. Five separate large packets may each cap at 10. Duplicate blessings
   must not lower the cap; changing max Health must change the threshold.
6. Repeat with native/RPG periodic Burn/Poison/Bleed, reflected damage,
   environmental damage and Health costs: no cap. Verify barrier/Managuard
   consumes only the capped direct amount, with existing durable escrow intact.
7. Retest ordinary Life Drain, Projectile Fork/Chain, Quick Slash, Fire Bolt,
   Snipe hold/release, native resource bars, and restart/rejoin loadout persistence.

Read `skill-trace.jsonl` for `CONNECTION_STARTED`, `CONNECTION_TICK`,
`HEAL_APPLIED`, `CONNECTION_TERMINATED`, `FINITE_SUPPORT_APPLIED` and
`FINITE_SUPPORT_RESOLVED` (`component=MaxHealthDamageCap`). Correlate rootCastId,
skillInstanceId and correlationId. Check `TRACE_GAP` and dropped/failed metrics.
Only `/rpg-trace performance` raw samples can qualify the unchanged 4 ms p95 /
8 ms p99 native-tick gate; NORMAL aggregation is not performance certification.
