# Stage 13 correction W — Healing Beam / ImmersiveNPCs interoperability

Follow-up: [correction X](stage-13-native-attitude-cache-correction-report.md)
addresses W's connected `VALIDATION_ERROR_NullPointerException`. W did not
establish successful connected casting and remains available for rollback.

Date: 2026-09-11. Baseline RPG `10bae69` / deployed R032-V. Candidate R032-W,
version 0.0.25. Scope: owner-requested Healing Beam NPC failure and deployment.
No icon investigation/redesign, native-input redesign, new content, or persistence changes.

## Connected failure evidence

Reviewed RPG save server logs `2026-09-11_17-15-21_server.log`,
`2026-09-11_17-17-15_server.log` inventory, and principally
`2026-09-11_17-18-07_server.log`, plus the current `skill-trace.jsonl`.
The relevant connected trace has `NO_VALID_AIMED_TARGET` at 21:16:21 UTC and
four times at 21:18:42–44 UTC. The native input reaches RPG activation; no new
packet/input hypothesis is needed. Four later Ability2 attempts at 21:18:49–54
are separately rejected with `INVALID_MAIN_HAND`; those equipment rules are
not changed. The 21:18:37 server log confirms `/npc spawn Jonalith`, native
Health/Mana/Stamina attachment and Health hydration to **100/100**.
Full Health means a valid future beam can run without increasing Health.

The spawned role is loaded from:
`mods/ImmersiveNPCs/profiles/jonalith/native-role/Jonalith.json`.
The pre-correction role SHA-256 is
`0B28BDC9F5AC0405F653DC4255CB0005ADB3D9C3F7E4DEC0240A28CD8619694B`.
It declares `Invulnerable: true` and omits `DefaultPlayerAttitude`.
Disassembly of the exact installed 0.7.0-pre.1 `SupportConfigBuilder.readConfig`
shows omitted **DefaultPlayerAttitude defaults to HOSTILE**, not Neutral.
`WorldSupport.getAttitude(target, actor, store)` is the RPG authoritative ally
source. There was no per-candidate attitude trace in this old session: the
record proves the rejection boundary, while role/API inspection establishes
the incompatible default; it does not claim a recorded live attitude value.

R170 `ImmersiveNpcRoleService.registerOrUpdate` reads an existing native-role
JSON instead of overwriting it with a template. Thus an explicit native
friendship field is a persistent configuration correction, not a transient
RPG attitude override or an item/NPC-name heuristic.

## Bounded correction

1. Set **only Jonalith's** native `DefaultPlayerAttitude` to `Friendly`.
   Leave `Invulnerable: true`, appearance, movement, instructions, Health, NPC
   inventory/stats and all other profiles unchanged. The spelling is also used
   by the installed `Template_Temple_Goblin_Lab_Repair_Static` native role.
   Other ImmersiveNPCs profiles are NOT silently promoted to friendly.
2. Add `eligibleHealingAlly` beside the existing strict support predicate.
   Only Heal Tether acquisition, UUID revalidation and secondary injury checks
   use it. It does not interpret **damage immunity** as immunity to healing.
   Living/loaded/native Health and affirmative FRIENDLY/REVERED requirements
   remain. Self exclusion, aimed bounds, range, LOS and healing clamping remain.
   No hostility, neutrality, missing attitude, or missing Health bypass exists.
3. Other support/auras/Blessing retain `eligibleAlly` and their old protection
   rules. Damage immunity is never cleared. Native damage, weapon requirements,
   resource costs, cooldown, held input, exact-once, escrow and persistence code
   are untouched. This is not a generic 'heal all NPCs' exception.

Changed production sources:

- `execution/hytale/HytaleSupportSystem.java`: separate healing/protection policy.
- `execution/hytale/HytaleSkillExecutionSystem.java`: use that policy in both
  friendly tether resolution and secondary injury eligibility.

## Validation and packaging

Focused tests passed before the full run. The final retained Gradle run passed
**2,197 tests**: 2,127 RPG, 49 native-control, 21 CanvasUI; no failures/errors/skips.
Four new production-policy/wiring tests cover invulnerable affirmative allies,
HOSTILE/NEUTRAL/IGNORE/null denial, retained strict support policy, and consistent
native tether revalidation. These are not a connected NPC integration test.
The retained Healing/Tether lifecycle tests cover acquisition, injury, self,
LOS, range, release, target loss, paid upkeep and continuations. All 32 CustomUI
documents passed validation. Retained compatibility, crash/fault, escrow and
nonblocking regressions passed unchanged.

The exact W artifact passed isolated three-mod native server boot/clean shutdown,
all inherited V smoke assertions, and the actual native projectile construction
audit. This does **not** run ImmersiveNPCs' AI workers or establish connected
four-mod interoperability. That remains owner QA.

Packaging takes V's exact resources and replaces only the classes compiled by
the successful full build. Two classes change implementation. Two nested
classes (`Absorb`, `Removal`) also change debug line tables because source lines
moved; their `javap -c -p` outputs are required to be identical to V. **Every
other archive entry is byte-identical to V**, including all icons, HUD, data,
projectile roots and persistence classes. All replacement bytes are checked
against Gradle's compiled class files. The archive contains exactly the three
RPG distribution mods. ImmersiveNPCs is an existing fourth live mod and is not
redistributed or replaced. Atomic isolated rollback/roll-forward and archive
hash checks passed.

Two initial packaging attempts correctly stopped at the allowlist: first the
anonymous implementation name included `$Port`, then unchanged nested systems
had shifted debug lines. Neither attempt was deployed. The allowlist now names
the exact four entries and requires bytecode equivalence for the two debug-only
changes; no production test or gameplay assertion was weakened.

| Artifact | SHA-256 |
|---|---|
| W `HytaleRPG-0.0.25.jar` | `585E0A32B9682B5BE06FF8202EF39D9D88302E8FF178457A894440402E560EA3` |
| W three-mod ZIP | `2C44FBA7B2BDE71AB0D49AB8806B7E70F04641B57C24F6489CFEA3A92053822E` |
| V rollback RPG JAR | `1103268939C69FA6B3D9E58DB2AE10F66A2412776AC00A43A9C13C64D24889FF` |
| Unchanged ImmersiveNPCs R170 | `0E231487E8BAE985E32488A082E5B6E592B897F6DA7E74D82356EFBC22C15814` |

Evidence: [cohort-w](../../evidence/stage-13/cohort-w), including `package.json`,
native smoke receipts and archived validation outputs. The existing formal
performance gate remains **UNMET**: this run's real-storage benchmark measured
p50 4.5515 ms, p95 7.9814 ms, p99 18.7677 ms versus unchanged 4/8 ms thresholds.
These are persistence-only numbers, not connected native-tick qualification.

## Deployment and rollback

**Deployed successfully at 2026-09-11 21:35:26 UTC.** The verified backup
contains 473 files; all 471 non-target files were unchanged. All four live
mod JARs remain installed. [Deployment receipt](../../evidence/stage-13/cohort-w/deployment.json).
The corrected role SHA-256 is
`060476A2AAFBE35E12B54B6E8B0B2D00829EF6012FEB7F2B297151FB08F852E9`.

The owner expressly requested deployment. `Deploy-R032WTestBuild.ps1` requires
the game/server stopped; validates all four installed JARs and the audited role;
backs up and hash-verifies the **complete RPG save**; and installs only the W
RPG JAR and one-field Jonalith role correction. All other files must compare
identically afterward. A failure while replacing the pair restores both files.
The deployment receipt records execution outcome, exact hashes and backup path.
Player/NPC stats and world data must not be edited to manufacture a healing test.
Restore the paired V JAR and old role from that backup to undo this correction.
The user-specific complete save backup remains local-only, not published.

## Connected checklist — still required

1. Restart Hytale and join RPG so the changed native role is loaded. If the
   already-spawned NPC retains old role state, use the NPC mod's supported
   despawn/respawn workflow for Jonalith; do not spawn duplicates blindly.
2. `/rpg equip skill02 healing_beam`, then `/rpg loadout`. Equip a staff or
   Spellbook in the main hand. Aim at Jonalith within 18 m with clear LOS;
   **hold R / the assigned Ability3 binding**, then release.
3. Require activation → validation pass → committed → connection started,
   `HEAL_APPLIED`, and release termination. On an NPC below maximum Health,
   require actual Health increase. At 100/100, zero actual healing is correct;
   don't disable invulnerability just to make the test possible. Use the NPC
   mod's supported stat authoring on a test NPC if an injured target is needed.
4. Confirm immunity to hostile damage is retained. Hostile/neutral NPCs must
   still be rejected. Test secondary injured friendly healing if using Arc,
   Fork or Chain. Confirm other NPC profiles and native HUD remain unchanged.

Icons were deliberately not diagnosed or altered: the trace does not tie their
appearance to the targeting failure. The prior HUD badge/startup cohort label
is unchanged; identify W by its JAR hash/deployment receipt, not the old label.
