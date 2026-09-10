# Snipe connected rejection audit — after R032-S

Audit date: September 9 local / September 10 UTC, 2026.
Branch RPG; code checkpoint `219c335`. Investigation only: no correction JAR,
no deployment, no gameplay/profile/test edits and no live save changes.

## Connected evidence

The RPG world's `logs/2026-09-09_22-24-12_server.log` and
`mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`
show Snipe assigned to skill02 and projected into Ability3 at
2026-09-10T02:25:58Z. The compiled plan contains Homing and Fork.

Five subsequent Snipe roots, at 02:26:14.378, 17.945, 21.845, 23.612 and
23.978 UTC, each contain exactly:

`NATIVE_ABILITY_INPUT_OBSERVED -> SKILL_ACTIVATION_REQUEST ->
SKILL_VALIDATION_REJECTED -> SKILL_ACTIVATION_REJECTED`

All five reject with `NATIVE_BOW_MAX_RANGE_UNVERIFIED`. None of these roots
reaches validation PASS, commit, executor dispatch or projectile creation.
Example correlation: `98091dc2-ea43-4202-b02e-44672eda5be6`.
One intervening attempt rejects `INVALID_MAIN_HAND`; that is a separate
equipment rejection, not the cause of the five range-gated attempts.

The earliest failing boundary is an explicit authored capability gate, not
native input, power resolution, projectile allocation, Homing or Fork.
The server's startup audit also explicitly advertises the same Snipe gate.

## Contract and exact installed native audit

The owner's Markdown master specification, SK-035, requires audited native
fully-charged bow speed/range. It labels 48 m / 45 m/s / radius 0.10 / gravity 0
as a **development fallback**, not the production contract.

`stage-13-projectiles-cohort-c.json` contains those development values plus
the blocking gate. `Stage04SkillProfile.activationGate()` exposes it to shared
validation. The gate is retained through Orbit conversion. Removing its string
would enable the development profile without resolving its normative prerequisite.

Read the actual installed pre-release Assets.zip and HytaleServer.jar, version
0.7.0-pre.1, rather than guessing from item names:

- `Weapon_Shortbow_Primary_Shoot_Strength_4` inherits the native Projectile
  interaction and selects `Projectile_Config_Arrow_Shortbow_Strength_4`.
- That config supplies LaunchForce 85, gravity 25, terminal air velocity 50
  and native arrow model. The retained resolved-model audit establishes radius
  0.075. These are not the development profile's 45 / 0 / 0.10 values.
- Its parents, `Projectile_Config_Arrow_Shortbow` and
  `Projectile_Config_Arrow_Base`, do not supply maximum travel distance.
- ProjectileConfig and BallisticData API inspection does not expose a maximum
  travel-distance field/accessor. Speed and gravity alone are insufficient to
  assert a terrain-independent native maximum travel-distance contract.
- ProjectileModule bytecode adds DespawnComponent using a five-minute delay.
  This is a lifetime safety timer, not a per-equipped-bow maximum range.
- `Common_Projectile_Despawn` is RemoveEntity on the projectile user, reached
  from hit/miss paths; it likewise does not define maximum travel distance.

This is a bounded negative finding about these inspected paths, not a claim
that no other future/native implementation could ever provide that capability.
No connected flight/range experiment was run and no new range is invented.

## Related-skill scope

The runtime-profile scan found:

| Skill | Equipment | Same missing-native-range gate? |
|---|---|---|
| Snipe | Bow | Yes |
| Quick Shot | Bow, Crossbow | No; authored 28 m range |
| Crossbow Bolt | Crossbow | No; authored 32 m range |
| Hunter's Mark | Bow, Crossbow, Gun | No; entity-target mechanic |

Only Snipe declares this projectile native-capability gate. There is no evidence
for changing the entire ranged family or removing legitimate entity-target
requirements. This finding does not claim connected functionality for every
other ranged skill.

## Validation and disposition

Focused unchanged production-contract regressions passed: Stage05ProjectileTest
(17), Stage13AuthoredProjectileTest (40), Stage13CoverageLedgerTest (1): **58
tests, zero failures/errors/skips**. These tests confirm the existing fail-closed
contract, not a working Snipe cast. No full-suite or new-build success is claimed.

Installed RPG SHA-256 remains
`E3B6F793F66164807EFC53E992C9B68383E05569A580F2D32B92AE105160C3BC`.

Status: **BLOCKED at the native bow maximum-range contract**. To provide a
playable Snipe without falsely certifying native maximum range, the owner must
authorize promoting the already documented development fallback to an authored
playable profile, or provide an alternative authoritative range contract. No
assertion/gate was weakened to manufacture a fix. S remains installed, rollback
and owner artwork untouched. Stage 13 acceptance remains unchanged.
