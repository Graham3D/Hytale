# Mantle of Flame — authoritative weapon-Fire boundary audit

> Historical pre-amendment audit. The latest owner instruction authorizes replacing eligible Fire production rather than capturing an old native roll. See the [deployed R032-AQ coverage-limited candidate](stage-13-mantle-of-flame-aq-report.md). The original findings below are retained for provenance.

Date: 2026-09-13. Branch: `RPG`. Audited HEAD: `25b85cb2819351a0d727e33132b2f57013cda024` (R032-AP).

**BLOCKED — SKILL NOT IMPLEMENTED. NOT PACKAGED. NOT DEPLOYED. NOT PUSHED.**

This is a local boundary audit and proposed integration contract, not a playable Mantle release. Production Java, content, gameplay, and live save/mod data were not changed. The existing AP deployment remains installed. No new revision label was assigned to a nonfunctional skill.

## 1. Why implementation stopped

The supplied Mantle task explicitly requires a stop at the earliest missing conversion boundary (§22), and permits proposing the narrow authoritative source-provenance seam first (§69). Both requirements matter here.

Hytale **can** change/cancel an individual damage event before Health application. The blocker is not scalar Fire suppression. The current RPG/native integration does not expose a complete, source-only set of direct weapon Fire components for one authored execution/tick, with stable component identities and one conversion decision shared across its victims. Existing observations arrive per victim, and some already include victim-dependent calculation/filtering.

Mantle cannot safely derive `S` from those observed scalar amounts. Doing so would make source Fire depend on the first victim, omit other components, double count multi-victim strikes, or misclassify spell Fire as weapon Fire. Suppressing the first observed Fire event before the complete Mana preflight would also violate the insufficient-Mana requirement to let the original weapon Fire continue normally.

No arbitrary FIRE hook, item-name inference, post-hit healback, private native reflection patch, or partly working Mantle catalog entry was introduced.

## 2. Inputs and reproducibility

Reviewed the supplied task, relevant v1.3 master sections covering runtime authority, damage channels, mitigation, Aura ownership, Flame Weapon, Links, mastery, and connected gates, the current RPG owners, and the installed native implementation.

| Input | SHA-256 |
|---|---|
| Installed pre-release server JAR, 0.7.0-pre.2 | `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E` |
| Installed Assets.zip | `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126` |
| Master Implementation Specification v1.3.docx | `DD45AB26E269A0949144581ED88D0691A07ABAF770A54F268DE5CDA49326FF5C` |
| Supplied Mantle task | `9FC940FA0618D49D64A0DC1AF1E7CBD50F1D2E967571D729A989D0A8453D45DA` |
| Current skills.json | `B1C330121D4137A0D5C1F31C6B762D0569AD36672F51BE590EB0BAA828DEA6E7` |

Local evidence: `evidence/mantle-of-flame-boundary/`. Reproduce the native API/asset inventory with `tools/Audit-MantleFireBoundary.ps1`. This script writes audit evidence only; it neither builds nor installs a JAR. Its `BLOCKED` fields record the audit conclusion, not an automatic proof of absence of every possible native extension point.

## 3. Production-path findings

### 3.1 Existing native basic-hit witness is narrower and later than Mantle requires

`execution/hytale/NativeBasicAttackObserver.java`:

- Uses `NativeItemPowerRegistry.loadCanonical()` and permits SWORD, LONGSWORD, DAGGER, BATTLEAXE, MACE, and SPEAR roots (lines 34–46). This is not the broad current equipment registry used for general skill validation.
- Tracks authenticated Primary interaction roots and checks source, held item, target and operation identity. Some genuine root provenance therefore already exists; the audit does **not** claim all native attribution is absent.
- Its `Before` system runs **after the native filter group**, before ApplyDamage (line 111).
- Requires the exact `Damage.EntitySource` class, excludes projectile sources and RPG-tagged damage, and accepts Primary only (lines 114–116).
- Its `After` system compares actual Health after ApplyDamage and supplies the existing basic-hit recovery/finisher path (line 133 onward).
- The witness does not contain an authored execution/tick's complete elemental components or source-only Fire basis. It does not alter native damage.

Moving this recovery observer earlier would not manufacture the missing ranged provenance, channel composition, source-only values, or multi-victim aggregation. Existing 4%/12% recovery must remain intact.

### 3.2 Flame Weapon cannot be presumed to provide the missing precommit source

`execution/support/SupportWorldPort.java:29` defaults `rootWeaponContactAvailable()` to false. `SupportRuntime.java:101` rejects IMBUE with `NATIVE_ROOT_WEAPON_CONTACT_ID_UNAVAILABLE`; no production override was found.

`WeaponImbueContacts.Hit` requires authenticated, hostile, **already applied** contact evidence. Its target/contact ledger rejects duplicate applied contacts. That testable owner is not a precommit Fire candidate producer, and a production callback satisfying the full required contract is not wired here.

The existing Flame Weapon profile remains authored at coefficient .30, duration 12 seconds, with its existing Burn behavior. Those mechanics were not changed or silently enabled. Mantle's future interception must distinguish the direct imbue Fire component from subsequent Burn; it must not consume Burn as another eligible source.

### 3.3 Current RPG hit damage is scalar, not an authoritative weapon-component envelope

- `combat/power/NativeItemPowerRegistry.java` resolves audited basic/magic power scalars. It is not a source-component/channel ledger.
- `combat/hytale/HytaleDamageMetadata.java` carries actor/root/skill/correlation, pre-mitigation scalar, Health-before, effect identity, proc permission and origin. It has no explicit authored execution/tick ID, complete source components or source-only Fire basis. `DIRECT` does not distinguish a weapon hit from a direct spell.
- `HytaleSkillExecutionSystem.java:1472–1507` calculates damage for each victim. `FiniteSupportEffects.damageModifiers()` combines outgoing and victim modifiers before that calculation. Its pre-mitigation result therefore cannot generally serve as a victim-independent Mantle `S`.
- `hitIndex` exists in strike execution/tracing, but is not the complete native/RPG execution-wide component contract needed here.
- `HytaleDamageAdapter` sends one scalar/cause through `DamageSystems.executeDamage`. It does not stage all eligible Fire components and one aggregate resource decision before dispatching every victim's split hit.

Existing outgoing/victim modifier separation in `FiniteSupportEffects` is useful reuse material. The fix should consume that separation deliberately instead of reverse-engineering source damage from an already victim-adjusted result.

## 4. Exact installed native implementation

Saved `javap -p -c` evidence is under `api/`. The following offsets refer to `DamageEntityInteraction.attemptEntityDamage0` in that captured class, not source line numbers:

1. Victim angle/target details can select a different damage calculator before damage construction (approximately offsets 139–356).
2. Calculator evaluation, elemental-cause conversion and attribute scaling follow (429, 436, 443).
3. Knockback/armor preparation occurs (544), then a per-victim `Damage[]` is created (710).
4. `DamageSequence` metadata is attached to the first array entry (721–759), not an exported whole-execution envelope.
5. Individual entries receive further amount adjustment (870) and are invoked sequentially through `CommandBuffer.invoke(target, damage)` (966).
6. The context's private `QUEUED_DAMAGE` value is published after that loop (983–995).

`attemptEntityDamage0` and `QUEUED_DAMAGE` are private. `getDamageCalculator()` is public, but inspecting a calculator is not an execution-wide precommit callback. `DamageSequence` exports a hit count/calculator; its sequence owner is private and it exposes no public sequence-owner getter.

`Damage.getInitialAmount()` retains the initial scalar and `setAmount(float)` allows adjustment. Initial amount is not guaranteed to be source-only: victim-specific calculator selection has already occurred. Nor does it expose the complete set of distinct authored weapon components across recipients.

**Conclusion:** native per-channel suppression is available. The audited integration lacks the earlier complete provenance/decision seam. This is not a claim that Hytale can never support such an adapter; it is a reason not to claim the current hooks already meet the task.

## 5. Proposed narrow seam — NOT IMPLEMENTED

Extend the existing execution/damage ownership, not a second combat system:

1. **Authoritative execution identity:** carry actor/world, root cast or native chain identity, authored hit/tick identity, source provenance and proc/derived flags. One Whirlwind tick and one authored multi-hit strike step must be distinguishable from their root and from another victim of the same step.
2. **Immutable source component candidates:** each eligible component has a stable identity, FIRE channel, direct weapon provenance, source-only amount and resolved source crit. Compose audited weapon/affix/imbue components before recipient-specific bonuses and mitigation. Spell/Burn/DoT/reflection/derived components are ineligible by explicit provenance, not names.
3. **Complete execution preflight:** aggregate distinct components once into `S`, query the existing caster-centered 9 m cylinder once, validate the accepted recipient set, and calculate recipient magnitudes using the existing compiler/damage/resource owners. Freeze that decision for this execution before any eligible direct Fire is committed.
4. **One conversion result:** store a bounded execution-owned decision shared by every component/victim. Insufficient Mana deactivates Mantle, spends zero, emits no proc, and leaves every original component untouched. Successful zero-recipient conversion suppresses eligible Fire with zero spend. Successful nonempty conversion atomically accepts the aggregate fractional Mana cost before suppressing original Fire; unaffected channels continue through their existing path.
5. **One derived pulse:** dispatch accepted recipients with existing damage, mitigation, protection, contribution and mastery owners. Tag the pulse NoProc/derived so it cannot re-enter conversion or generate basic-hit refunds. Preserve the task's late-target invalidation and no-duplicate/replay rules through existing ownership rather than per-target recharging.
6. **Bounded lifecycle:** use existing world-thread execution ownership and bounded ledgers. Never block native hit processing on persistence or alter Stage 13 escrow/exact-once durability. Cleanup on execution termination, actor/world removal and Aura shutdown.

The first implementation gate is an audited execution-side source adapter that produces that complete candidate envelope for required native melee **and ranged** paths, and an equivalent explicit component plan for RPG weapon-derived skills. Extending the existing interaction-root witness is a candidate direction, not a verified public API solution. A private reflection dependency or replacing shipped interactions merely to synthesize evidence is not proposed.

Before registering Mantle, prove that one authoritative authored execution with multiple Fire components and multiple victims produces one source-only aggregate and one decision, without ever consuming victim-adjusted damage. If an exact native execution owner cannot be reached safely, report that narrower missing API boundary rather than shipping partial universal conversion.

## 6. Content/math reconciliation after the seam is proven

These are future requirements, not active changes:

- Stable skill `rpg.skill.mantle_of_flame`; Tier III Aura; toggle lock 3 s; no toggle weapon requirement; zero activation/upkeep/reservation; explicitly versioned `TRIGGERED_VARIABLE_MANA_SPEND` mode.
- `M_i = S * .25 * eligible Mantle magnitude factors * recipient factors`; source crit is not rerolled. Mitigation is applied once by its existing authoritative owner.
- Aggregate cost: `TotalMaxMana * .01 * (sum(M_i) / S) * compiledCostFactors`, using fractional arithmetic. Spendability is checked against current spendable Mana, not by substituting spendable maximum for TotalMaxMana.
- Unmodified 1/2/4/8/20/30/64 recipients cost .25/.5/1/2/5/7.5/16 percent of total maximum Mana. Zero eligible source Fire cannot divide by zero or create a pulse.
- Keep Potency at +15%, and the specified Efficiency, Overcharge, Concentration and Expanded Radius behavior. Reject reservation/upkeep-only and other explicitly prohibited Links for the new resource mode; do not broaden generic Aura compatibility.
- Mastery scales magnitude only by 2% per level beyond 1, maximum +38%; actual eligible hostile damage is required, at most one XP per 5-second active-combat window.

### Current catalog differs from the task's historical count assumptions

Actual HEAD has **89 skills and 67 passives**, not 87/66. Healing Beam and Blessing of Protection are the two added skills; Arc is the additional passive. There are 23 current UNASSIGNED skills.

Preserving accepted content while adding Mantle would yield **90 skills, 67 passives, 6,030 skill/passive pairs, 2,211 unordered passive pairs, and 24 UNASSIGNED skills**. Current pair count is 5,963. The task's 88/5,808/2,145/22 figures cannot be imposed without deleting accepted content or making counts false. No catalog/matrix was regenerated in this blocked audit. Historical 87/66 freeze statements remain historical facts.

## 7. VFX inventory, not visual acceptance

The pinned archive contains `Server/Particles/Combat/Fire_Stick/Fire_Trap/Fire_AoE2.particlesystem`, its constituent spawners, and `Server/Particles/Combat/Impact/Misc/Fire/Impact_Fire.particlesystem`. Full paths, entry hashes, textures, UV motion, attractors, delays and budgets are recorded in `native-vfx-inventory.json`.

Fire_AoE2's six bindings have 3.5-second start delays. Its constituent systems include Sparks, FireFloor, FireFloor2, Circle/Circle2 and CirclesFloor. `Fire_AoE_CirclesFloor` references RingFire with flow-map UV motion; FireFloor2 has radial/tangential motion. These are inspection candidates for the requested persistent squiggle/finite spiral separation, **not connected-verified selections**. Duplicate `_Test/Fire` paths also exist; archive inventory does not establish runtime duplicate-ID resolution.

No derivative particle, standing Aura, proc VFX, target tint or impact attachment was created. Asset presence and native tint API inspection do not prove client appearance. Do not play the full stock delayed Fire_AoE2 as though it already implements the requested two-layer lifecycle.

## 8. Changes and validation

Added only:

- `tools/Audit-MantleFireBoundary.ps1` — reproducible pinned API, asset and input-hash inventory.
- `src/test/java/com/inigmasgames/hytalerpg/MantleFireBoundaryAuditTest.java` — six characterization tests for the current boundary; these deliberately describe the audited baseline and must be revised when the seam is implemented. They are not Mantle acceptance tests.
- This report and local evidence files, including the relevant master excerpts and focused JUnit results.

Focused command:

```powershell
.\gradlew.bat -PhealingProbeLiveTest=true :test --tests '*MantleFireBoundaryAuditTest' --tests '*Stage09BarrierSupportTest' --tests '*Stage13NativeBasicHitTest' --tests '*Stage11ConditionalDamageTest' --console=plain
```

Result: **77 tests passed, zero failures/errors/skips**: boundary audit 6, Stage09 barriers/support 26, Stage13 basic-hit 22, Stage11 conditional damage 23. CustomUI validation checked 34 documents. Production compile/resources were up-to-date; test compilation and execution succeeded. Gradle emitted its existing restricted native-access warning.

Not run: full retained suite, new Mantle mechanics/matrix tests, isolated smoke, packaging/archive validation, dense-pack performance, connected rendering/combat QA. Those remain required for an actual candidate; a stopped audit is not a coherent skill candidate or a release-gate pass.

## 9. Deployment and rollback status

No new JAR was packaged or installed, no live save was written, and no Git commit/push was made for this task. Existing user art/unrelated untracked evidence was preserved. No rollback operation is needed for runtime because runtime was not changed.

Unchanged installed AP `HyARPG.jar` SHA-256:

`E11720D6AC54BFCD7FD294308E908E124967EFAE39B4E935C016AD2AA24CD285`

**IMPLEMENTED:** boundary audit only; Mantle no. **PACKAGED:** no. **DEPLOYED:** no. **CONNECTED-VERIFIED:** no Mantle evidence. The next bounded work item is the authoritative pre-victim execution-wide weapon-Fire candidate/decision seam described above, before Aura/content/UI implementation.
