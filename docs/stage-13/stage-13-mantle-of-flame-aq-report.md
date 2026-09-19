# R032-AQ — Mantle of Flame normalized-source QA candidate

Date: 2026-09-13. Branch: `RPG`. Base HEAD: `25b85cb2819351a0d727e33132b2f57013cda024` (AP). Local cumulative changes; **not committed or pushed**.

## Delivery status

- **IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION — COVERAGE-LIMITED QA**, not the completed universal weapon feature.
- **PACKAGED:** `evidence/stage-13/cohort-aq/artifacts/HyARPG.jar` and `HyARPG-R032-AQ-three-mods.zip` in that cohort directory.
- **DEPLOYED:** `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.
- **CONNECTED-VERIFIED: NO.** The exact candidate booted in the isolated three-mod server, not in the owner's connected single-player session. No rendering, native input/hit execution, actual conversion, or connected LOS acceptance is inferred from that smoke.
- The primary attacks of **Weapon_Longsword_Flame** are the only authored native weapon Fire leaves connected to this candidate's producer. Ranged and RPG weapon-skill Fire conversion are **not integrated**. Ordinary unsupported combat remains outside the adapter.

The current task explicitly permits a labeled coverage-limited candidate when a path cannot be safely integrated. This delivery uses that allowance. In particular, the requested real ranged producer gate and normal-attack native execution proof have **not** been satisfied. A native asset walk is binding evidence, not an execution certificate.

## Artifact identity, installation and rollback

| Artifact | SHA-256 |
|---|---|
| Built, archived and installed AQ HyARPG.jar | `43DA05C1031226EE34F0F8B34EAC2FEA255ACDFA34CCD072DEE703D854B9EC09` |
| AQ three-mod ZIP | `4781DF957521F78FF24FC7EB21BEDFBA4A9DF5ADE41C97A8D4F7EE419A6219DE` |
| Previous live / archived AP HyARPG.jar | `E11720D6AC54BFCD7FD294308E908E124967EFAE39B4E935C016AD2AA24CD285` |

Full pre-deployment backup: `evidence/stage-13/cohort-aq/before/save/20260913T175827Z/RPG` — 545 files, including the live JAR, player/world state, RPG mod data, and NPC mod data. Every backed-up file was compared by SHA-256 against its source. Inventory: sibling `backup-inventory.json`. Atomic replacement also retained `replaced-live.jar` beside that backup. No live progression/NPC/configuration files were edited by deployment.

Original AP artifact: `evidence/stage-13/cohort-ap/artifacts/HyARPG.jar`. A scratch **AP → AQ → AP** binary rollback was hash-verified. This is a binary rollback proof, not a claim that saves changed during future QA have been tested against AP.

Hytale and its server were stopped before backup and replacement. One active RPG JAR remains. CanvasUI, HYTALEDEVLIB and ImmersiveNPCs were preserved. The distribution archive contains exactly HyARPG, CanvasUI and HYTALEDEVLIB; each decompressed entry hash was verified. The internal manifest remains `InigmasGames:HytaleRPGPhase00Audit` to preserve mod-data ownership; only the output filename is `HyARPG.jar`.

To roll back, close Hytale completely, preserve any post-AQ QA save separately, and replace only the live HyARPG.jar with the archived AP JAR above. Do not indiscriminately restore the pre-QA world over newer progress. Restore the full matching backup only if deliberately rolling the world back too.

## Pinned native boundary

Installed Hytale: **0.7.0-pre.2**, not the historical pre.1 API.

- Server JAR SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets ZIP SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.
- Native disassembly and original Flame Longsword asset are retained under `evidence/mantle-of-flame-implementation/`.

`ManagedWeaponFireInteraction` extends the installed `DamageEntityInteraction` through its supported codec API. It does not copy or patch the native interaction manager. At decode it copies the authored calculator through the public codec into its specialized calculator. The native damage leaf still executes target validation, collision-derived target selection, target-dependent processing, knockback, stat-on-hit and damage submission.

The opt-in item override changes **only four damage-leaf Type fields**. A regression compares the complete native item JSON with the packaged override after removing those four additions; all remaining content must be equal. Native animation, charging, ammo/durability properties, and unrelated item behavior are not rewritten.

The first native smoke exposed a codec-default-instance issue: codec registration validates an unconfigured default before real asset decode. That was corrected without accepting malformed loaded damage leaves. The failed smoke remains in `evidence/mantle-of-flame-implementation/producer-binding-attempt-1/`.

## Who produces Fire, how often, and how duplicate Fire is prevented

Production path wired in AQ:

`Flame Longsword Primary root → native Replace/selection → four explicitly managed damage leaves → NativeWeaponFireProducer → root-owned WeaponExecutionLedger → WeaponFireDecision → inherited native direct damage OR deferred Mantle damage`.

`NativeWeaponFireProducer` owns **NORMALIZED_FIRE_V1**, a deliberately new source contract. It does **not** reconstruct the random number vanilla rolled for an earlier victim, and is not described as preserving vanilla's per-victim random sequence.

The ledger's `produce(identity, supplier)` calls the source supplier once for a new authored execution. Subsequent consumers reuse the immutable result. The source sample is the exact bound leaf's authored Fire base and variance plus source-only native attribute/support offensive factors. The current native definitions have no separate authored critical roll; the envelope records `critical=false`. Target armor, resistance, weak-point state and first-victim health do not enter S.

The root's context metadata owns the ledger, not a global timestamp cache. Actor, world, root and authored execution/selection identity are validated. Victim fork sub-index is excluded from source identity; distinct leaf/selection entries remain distinct. Nested/derived branches and unsupported owners are rejected from managed conversion. Bounds: 32 source components, 256 authored decisions per root, 64 accepted Aura recipients. Overflow is not handled by evicting an accepted decision and permitting reuse.

All eligible components aggregate into `S` once. The generic seam supports multiple explicit components; the **current production bindings contain one Fire component per selected leaf**, not an invented affix/imbue contribution. The supplier-count test repeats 64 consumers and requires one evaluation. Retained decision tests exercise multi-component aggregation, cleave reuse, duplicates, empty recipients, failed affordability, exceptional writes and root closure.

For managed direct output, the specialized calculator replaces the native Fire entry in the map. It never adds a second Fire packet. It retains the superclass's unrelated-channel outputs. The superclass's superseded random Fire entry is discarded and is never used as S or additionally delivered. Native source multipliers below the calculator are compensated so the stored canonical source is not multiplied twice. Native recipient processing remains downstream.

The native callback is scoped with `try/finally` and a thread-local invocation, never stored as an asynchronous command buffer. Simulation does not generate source damage or debit Mana.

The old metadata transport seam is retained and tested. It is not falsely presented as the new native leaf's integration point: the inherited native leaf submits canonical direct Fire itself. Existing native basic-hit observation therefore remains available for legitimate pure-Fire direct hits, rather than relabeling direct Fire as an Aura proc.

## Actual supported-path coverage

| Path | AQ status | Evidence / exclusion |
|---|---|---|
| Flame Longsword primary swing left | Wired, connected execution unverified | Authored Fire 31, variance 0.15; native installed root walk resolves managed leaf |
| Flame Longsword primary swing right | Wired, connected execution unverified | Fire 31, variance 0.15 |
| Flame Longsword primary swing up-left | Wired, connected execution unverified | Fire 31, variance 0.15 |
| Flame Longsword charged stab | Wired, connected execution unverified | Fire 39, variance 0.15; native walk classifies CHARGED |
| Other native melee items / signature / nested derived paths | Not managed | No global Fire suppression; retain ordinary combat |
| Flame Shortbow | Not managed | Its name does not establish authored Fire. Installed item inherits Shortbow without a Fire damage override |
| Native staff projectiles | Not managed | Installed Flame Crystal Staff has projectile/explosion, channel and trap branches; a direct leaf also exists in shared Stick assets. No verified launch-owned source receipt and complete per-execution branch mapping were installed. Overriding the shared leaf alone would not establish safe projectile lifetime/provenance or whole-execution coverage |
| Flamethrower | Not managed | Continuous/chained authored Fire requires distinct tick identity and provenance mapping; not treated as ordinary melee merely because its calculator says Fire |
| RPG weapon skills, including Quick Slash/Whirlwind/Flurry | Not managed by AQ | Existing scalar/contact execution owners are not replaced; no weapon Fire is inferred from total skill damage or converted from Physical |
| RPG weapon Fire affixes | Not integrated | Envelope capability is not a production affix producer |
| Flame Weapon imbue | Not integrated with Mantle | Its prior supported/unsupported contact capability remains unchanged; no capability flag was promoted |
| Fire Bolt / other Fire spells, Burn, traps, summons, environmental Fire | Excluded | No opted-in weapon producer; unchanged existing owners |

The ranged limitation is **not** a claim that public Hytale APIs make it impossible. It is an integration/verification gap in AQ. No positive ranged control is supplied and no ordinary bow is falsely described as managed Fire equipment. Further work must establish launch-to-impact ownership and complete source composition before enabling that path.

Machine-readable positive bindings: `src/main/resources/rpg/runtime/managed-weapon-fire-v1.json`. That registry describes the authored bindings; startup additionally resolves the actual installed assets through the native walker. The all-content generated coverage ledger also explicitly labels Mantle's limitation.

## Routing, Mana and target authority

One retained decision has exactly one outcome:

- `DIRECT`: Aura inactive; canonical direct Fire.
- `CONVERTED`: positive S, accepted visible recipients and aggregate affordability; one debit, suppress direct Fire, claim one pulse.
- `SUPPRESSED_EMPTY`: positive S and active Aura but no accepted recipients; suppress direct Fire, no fee and no pulse.
- `DIRECT_AFTER_MANA_FAILURE`: aggregate fee unaffordable; no partial payment, terminate Mantle, preserve the same canonical direct Fire.

Preflight uses `M_i = S * .25 * K_i` and `TotalMaxMana * .0025 * sum(K_i) * R` for the allowed proportional modifier contract. Formula-equivalence tests cover several positive S values, 0/1/4/8/20/30/64 recipients, mastery-like factors and compatible cost factors. The zero-source branch remains explicit. The native resource port returns **total maximum Mana before reservation**, not merely unreserved capacity; affordability still respects the existing resource/reservation owner.

The decision is taken synchronously inside the damage-producing leaf **before that leaf's same-hit basic recovery**. Later victims reuse it instead of rechecking the Aura and funding conversion from an earlier victim. No periodic debit or minimum one-Mana rounding was added.

Recipients come from the existing caster-centered native Aura cylinder, base radius 9 m. Native hostile/protection, loaded/alive and collision-LOS checks apply **before Mana cost**. A clear victim does not authorize an occluded one. The immutable recipient list is dispatched through `CommandBuffer.run`, not recursively from a damage filter. The deferred action retains no callback-scoped buffer. Immediately before application it rechecks world/actor/Aura ownership and each committed recipient's alive/hostile/protection/range/LOS state. It cannot expand the list or refund late-invalid targets.

Mantle damage uses the existing `HytaleDamageAdapter.applyResolved` Fire pipeline with the owning Aura's root/skill/correlation context, a weapon-execution-qualified effect ID, `Origin.TRIGGERED`, and `canProc=false`. It is not basic-hit recovery evidence and does not recursively trigger Mantle. Native mitigation, actual Health loss, encounter contribution and mastery hooks remain the existing owners. The live native behavior of all of these combined paths still requires QA.

## Skill lifecycle, Links, mastery and presentation

- Added Mantle to the canonical catalog, runtime profile, native zero-cost AbilitySlots bridge item, localization and icon index. Counts: **90 skills, 67 passives**, 6,030 skill/passive combinations and 2,211 passive pairs.
- Explicit `TRIGGERED_VARIABLE_MANA_SPEND` support resource mode. Its profile rejects reservation/upkeep/duration or periodic-damage fields. Activation fee 0, reservation 0, idle drain 0, no inherent Burn; indefinite Aura with a 3-second toggle lock.
- Reused the existing support session, nonblocking persistence handoff and owned Aura cleanup. No new database, persistence format or world-state architecture.
- Allowed Links only: Potency, Efficiency, Overcharge, Concentration, Expanded Radius. Existing compiler magnitude/cost separation retained. Concentration radius reduction is applied once, not twice. Unsupported Links such as Conservation/Resonance are rejected rather than silently doing nothing.
- Mastery uses the Aura's existing sustained root budget and actual meaningful result hooks (one eligible award per 5 seconds), not particle ticks. Existing mastery scaling remains the owner; no alternative XP path.
- Ember Golem assignment is **PROPOSED**, not implemented NPC combat behavior or publicly verified learning. Wall of Fire is UNASSIGNED. No NPC data was modified.
- Owner icon filename: `art/Skills/SkillMantleofflame.png`. No new icon art was invented. Until supplied, the existing rune/skilltree fallback is used. The owner icon updater now explicitly accepts the complete 90/67 catalog in addition to its retained older formats; all ID/path/hash/rollback validations remain.
- HUD revision now reads cumulative `BuildIdentity.REVISION = R032-AQ`, not the Healing renderer's older provenance constant. Health/Mana/Stamina and ability projection remain native-owned. Healing Beam and Blizzard gameplay/presentation code are unchanged.

Inspected stock `Fire_AoE2` constituents rather than applying the whole effect. Stock children have 3.5-second startup delays. Project-owned systems start immediately and reuse only the selected spawners:

| Ownership | Visual | Lifetime |
|---|---|---|
| Active Aura | `Fire_AoE_CirclesFloor` (RingFire/erosion-flow outward ring, yellow-to-red native color progression) | 0.75 s finite attached lease refreshed on existing 0.5 s presentation cadence |
| One successful weapon pulse | `Fire_AoE2_FireFloor2` radial/tangential spiral | 0.4 s caster effect, once per claimed pulse with actual affected recipients |
| Each actually damaged recipient | Body-attached `Impact_Fire` | 0.35 s |
| Each actually damaged recipient | Native model tint `#FF3030` | 0.12 s |

Vanilla particle assets were not edited. Effects are presentation-only, have no damage calculator or movement/ability mutation, and are finite. Aura teardown removes its lease and caster pulse; impacts/flashes expire independently. The native isolated effect audit creates, overwrites and removes all four through the real effect controller and checks no residue. **This does not prove the desired reddish squiggle/spiral appearance connected.** Color, scale, refresh continuity, tint restoration and multiplayer attachment remain visual QA items.

## Tests, failures encountered and evidence strength

Final retained command:

```powershell
.\gradlew.bat -PhealingProbeLiveTest=true :check :canvas-ui:check :jar --rerun-tasks --console=plain
```

Result: **2,355 passed; 0 failures, 0 errors, 0 skipped**:

- Main retained RPG: 2,270.
- Isolated native-codec/control JVM: 64.
- CanvasUI: 21.

New/retained Mantle-specific classes: `WeaponFireDecisionTest`, `MantleNativeSourceContractTest`, `MantleRuntimeTest` — 63 tests total. They cover immutable source/decision behavior, explicit exclusions, one evaluation, failed affordability, fractional resources, actual compiler/support Aura execution, Links, toggle/cleanup and pinned native codec/asset structure. They do **not** emulate a connected player pressing attack and must not be cited as such.

The first full run found 16 failures: new skill-count/capacity accounting, new ability JSON formatting expected by a retained source contract test, historical HUD provenance assertions, Wall of Fire reassignment count, and icon-updater version guards. All were addressed without removing tests. The cooldown bound now tests `EXPECTED_SKILLS + 1` rejection rather than mistakenly treating the now-valid 90-entry catalog as overflow. The icon updater still verifies exact catalog coverage and old-format support. Earlier focused tests also caught duplicated Concentration radius scaling and a harness assertion reading ally-query state for an Aura that does not query allies. No damage/resource balance expectation was relaxed.

Original failures are preserved under `iterations/full-1/`. Focused corrections passed, then the entire retained suite was rerun successfully; no patched-together XML result is presented as a complete final run.

Exact-JAR native smoke:

```powershell
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort aq -NativeProjectileSpawnAudit
```

It passed all retained three-mod registration/boot/shutdown checks, managed Fire codec/actual installed Primary-root resolution, Mantle native effect construction/removal, and retained AP Healing particle/audio, native Mana replication and projectile lifecycle audits. `server-smoke.txt` records `HYTALE_RPG_SETUP revision=R032-AQ`, all four managed leaves, and `RPG_MANTLE_NATIVE_EFFECTS result=PASS ... connectedProof=false`.

Package validation compared cumulative archive entries with AP, rejected removals, and required byte-identical native input, Healing renderer assets/classes, projectile family and selected persistence owners. Full entry differences and hashes are in `package-validation.json`.

**Existing release-performance limitations remain.** The final real-storage 64-update fixture reports p95 **8.3926 ms**, p99 **19.5061 ms**, against unchanged 4/8 ms targets (`withinNominalRpgTickBudget=false`). The sparse production-v2 fixture reports p95 **7.2707 ms**, p99 **7.4935 ms**. These are local storage/workload results, not native connected tick qualification. Passing the retained correctness suite does not promote these measurements to a release PASS. No persistence/escrow/exact-once assertions or thresholds were weakened for Mantle.

## Exact connected checklist

Start the normal pre-release **RPG** single-player world, not the disposable Direct Connect probe. Confirm top-right **R032-AQ**. Development entitlements are the existing test configuration; the isolated startup reported DEVELOPMENT. No public learning grant was fabricated.

Run:

```text
/rpg equip skill01 mantle_of_flame
/rpg dev ability-status
/give Weapon_Longsword_Flame
/rpg-trace normal
/rpg-trace status
/rpg stats
```

The `give` item argument and default quantity 1 were checked against the installed native GiveCommand. The other commands are existing project commands. Equip the granted Flame Longsword in your hand. Mantle in skill01 uses your existing **Ability2 (E by default)**. If that key is rebound, use the native Ability2 binding. Do not press Quick Slash to test native primary coverage.

1. With Mantle off, make a normal left-click primary attack against a legitimate hostile NPC. Check ordinary direct Fire. The trace should report `WEAPON_FIRE_SOURCE_ROUTED route=DIRECT`.
2. Press Ability2 once to enable Mantle. Allow the existing asynchronous activation acknowledgement to finish. Observe the standing Aura and no activation/idle Mana cost. The 3-second lock is a toggle lock, not periodic damage/cost.
3. Use the **normal weapon primary attack** near one visible hostile within 9 m. Require `WEAPON_FIRE_SOURCE_ROUTED route=CONVERTED`, then `MANTLE_PULSE_RESOLVED` and ordinary RPG/native damage traces with actual Health loss. At base factors, the event receipt should be 0.25% **total maximum** Mana for one accepted recipient.
4. Repeat near a pack: 4 accepted recipients = 1% max Mana; 8 = 2%. Native weapon recovery/regeneration can also change the HUD, so inspect the event's `manaCost` rather than estimating the debit solely from the final bar value. One source/route event and one claimed pulse per authored execution; not one debit per cleave victim.
5. Test a charged Flame Longsword stab. It has a separate 39-base authored source, not a reroll of the 31-base swing. Repeated callbacks for one execution must share its decision.
6. For LOS, use ordinary building tools to place an opaque solid wall, floor/ceiling, closed doorway and corner between caster and a hostile. Those targets must be absent from preflight cost/damage. Open the doorway and repeat with a clear collision ray. Keep one other visible enemy as a positive control. No diagnostic command spawns or alters NPCs for this test.
7. Insufficient Mana: inspect current Mana with `/rpg stats`, then use the existing `/rpg dev resource mana spend <current-amount>` immediately before the hit. Example **only if current Mana is at least 100**: `/rpg dev resource mana spend 100`. The command spends, not sets; an unaffordable spend rejects. Native regeneration continues normally, so use enough legitimate hostiles that the aggregate trigger fee exceeds the remaining Mana. Expect one `DIRECT_AFTER_MANA_FAILURE`, no Mantle pulse/fee, Aura off, and canonical direct Fire. Do not use a large guessed number and assume it set Mana to zero.
8. Toggle off after 3 seconds, then verify cleanup. Rejoin/world-change/death QA must not leave an active Aura or reuse an old root's decision.
9. Negative controls: Fire Bolt, Quick Slash and ordinary bow attacks retain their existing behavior and must not silently enter this managed conversion. `/give Weapon_Shortbow_Flame` obtains the shipped bow **as an unsupported negative control only**, not a managed ranged Fire test. A positive ranged Mantle test is unavailable in AQ.
10. Check stationary Aura color/scale, one short caster spiral per successful pulse, recipient attached impact/red flash, and finite cleanup. Do not count particle appearance as proof of damage.

NORMAL already retains the event-driven Mantle telemetry and bounded/verified rotation; do not enable PERFORMANCE for this short casting check. Use DETAILED only if compiler failure needs inspection. Trace files remain under `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` and `ui-trace.jsonl`. No existing live trace history was deleted during this task; filter the new session by `R032-AQ` startup time and new event root IDs.

## Remaining acceptance / next real boundary

AQ is delivered for testing, not Stage 13 closure. Required connected evidence still includes actual managed normal/charged attack entry, cleave identity, no duplicate native Fire, direct fallback equality, same-hit recovery ordering, target LOS/cost, actual Health damage/mitigation/contribution/mastery, Aura cleanup and visual fidelity. Native assets resolving successfully do not prove the callback's runtime eligibility guards will accept the owner's attack.

The next boundary on a missing conversion is the actual managed leaf → producer eligibility/ownership path, not input projection, Healing Beam or persistence redesign. If the skill toggles but no `WEAPON_FIRE_SOURCE_ROUTED` event appears while using the exact supported weapon Primary attack, inspect that boundary first. Ranged/affix/RPG-source expansion must not be claimed from the existing one-weapon demonstration.

Evidence root: `evidence/stage-13/cohort-aq/`. No GitHub push, connected QA automation, live server restart, NPC spawning or save migration was performed by this delivery.
