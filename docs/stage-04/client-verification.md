# Stage 04 R013 — Connected Client Verification

> Superseded input/slot notice: R016 reserves Ability1 for Hytale's native weapon
> Signature Move and maps Ability2/3/4 to RPG `skill01/02/03`; `skill04` no longer
> exists. Use `docs/corrections/R016-client-verification.md` for current testing. The
> historical R013 steps below are retained as evidence context only.

## Before testing

1. Fully stop and restart the `RPG` world, then rejoin. Confirm the HUD badge reads
   `R013`, all four skill cells appear, and no CustomUI/server error occurs.
2. Keep one ordinary damageable NPC nearby. For Shield Bash, also identify a protected
   or boss target if the test world provides one.
3. Open the server trace after each activation at:
   `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.
   Search by the newest `rootCastId`; every event for one activation must retain its
   `rootCastId`, `skillInstanceId`, and `correlationId`.

Use `/rpg equip skill01 "Skill Name"` to change the Ability1 test skill. Use
`/rpg loadout` after each change to confirm the authoritative slot and compiled plan.

## Required checks

| # | Action | Required evidence |
|---|---|---|
| 1 | Equip Quick Slash, hold a Sword/Longsword/Dagger, face an ordinary NPC within 2.6 m, and press Ability1 once. | Exactly one authoritative hit and visible Health loss. Trace: request -> validation -> commit -> dispatch -> strike query/accepted/hit -> Stage 02 `DAMAGE_GATHERED` -> `DAMAGE_FILTERED` -> `DAMAGE_APPLIED` -> `DAMAGE_INSPECTED` -> termination, with continuous IDs and no parallel native hit. |
| 2 | Replace the weapon with an invalid type and press Ability1. | `SKILL_VALIDATION_REJECTED` for weapon; no Stamina loss, cooldown, damage, movement, or world effect. |
| 3 | Equip Heavy Swing, hold a Longsword/Mace/Battleaxe, press Ability1 near a valid NPC, and observe timing. Repeat once while taking damage or changing weapon during the wind-up. | First hit begins about 0.45 s after activation. Interrupted attempt terminates/cancels without a hit and leaves no stuck active state. |
| 4 | Equip Shield Bash, put an actual Shield in the offhand, and hit an ordinary NPC. If available, repeat against a protected/boss target. | Ordinary NPC takes the hit and receives native Stun for 0.6 s. Protected/boss policy rejects or substitutes control exactly as traced; it never reports applied control when native effect application failed. |
| 5 | Equip Quickstep, face at least 5 m of open ground, press Ability1, then repeat facing a nearby solid wall. | Open-ground travel is approximately 4 m over about 0.22 s. Wall attempt is clamped before collision, records `MOVEMENT_CLAMPED`, never crosses the wall, and ends without a stuck movement lock. |
| 6 | Equip Pounce, target an ordinary NPC within 8 m, and press Ability1. | Server-authoritative leap arrives without clipping and the bounded 1.5 m landing attack resolves once through the full Stage 02 damage lifecycle. |
| 7 | Equip Riposte, hold a Sword/Longsword, press Ability1, then perform a real native block against an incoming NPC attack within 0.8 s. Repeat without rearming; also allow one armed window to expire. | One `Damage.BLOCKED` event produces exactly one `REACTION_TRIGGERED` and one counter on the next world tick. The duplicate event cannot counter twice. The unused window records `REACTION_EXPIRED`. |
| 8 | For every successful skill, press it again immediately, then after its cooldown. Also create an insufficient-Stamina case. | Successful attempts spend exactly the authored Stamina once and start the authored cooldown. Immediate retry is rejected without more cost. Post-cooldown retry works. Insufficient resource rejects without cooldown or world effect. HUD cost/cooldown state follows authority. |
| 9 | Equip skills across skill01 through skill04 and press Ability1 through Ability4. | All four HUD slots remain responsive and map to the correct authoritative equipped skill; unsupported or empty slots reject cleanly. |
| 10 | Review client/server logs, fully restart the world, rejoin, and run `/rpg loadout` plus `/rpg stats`. | No RPG-scoped exception. Stage 01B loadout/graph and Stage 03 attributes, point state, Character/HUD presentation persist; the R013 HUD recreates cleanly. |

## Evidence to retain

- screenshots or video for each visible behavior;
- the server log covering join, all tests, disconnect, restart, and rejoin;
- `skill-trace.jsonl` and `ui-trace.jsonl` from the test session;
- before/after target Health and player Stamina for the relevant checks;
- `/rpg loadout` and `/rpg stats` output before and after restart.

Record the first failing trace boundary exactly. Do not mark Stage 04 `PASS` unless all
required checks succeed; a failure remains `BLOCKED` or
`IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION` according to whether connected testing
found a real implementation defect.
