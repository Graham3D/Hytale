# R213-U7P5 — Trork QA spar route and affix display

Deployed to the active RPG save on 2026-10-07. `HyARPG.jar` SHA-256:
`8B210DA528D635B0BB1532711DBEF67B29A69143A12AF832930B5E52AF48C443`.
The replaced R212 JAR was copied outside Saves to
`C:\Users\Zemio\.codex\backups\HyARPG\HyARPG-before-R213-U7P5-22AB334B.jar`.

## Connected evidence from R212

The 2026-10-07 16:48:51 server log records acceptance of
`/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker` at
20:49:17 UTC with `[ME-002, ME-019, ME-025]`. At 20:49:36 it reports a
missing replacement for the generic `Root_NPC_Attack_Melee` interaction,
followed by two `ENEMY_ACTION_ACCEPTANCE_SNAPSHOT_MISSING` exceptions.
Hytale's installed Trork Spar component uses that generic melee root with
`DamageFriendlies: true`; the Master Enemies Trork binding certifies only
the five authored battleaxe roots. Spar can therefore reach the routed
damage leaf without an accepted Master Enemies strike snapshot. The
R212 log does not identify the exact actor removal cause.

R212 also suppressed the entire projected QA target card whenever the
native Healthbar was attached. That hid the leader's full affix list.
The overhead nameplate intentionally contains rarity, level, and name;
affixes are in the target card. Native Healthbar attachment had no
success log in R212, so its client visibility at full Health remains
unverified.

## Narrow changes

- Cancel only the uncertified generic Trork spar melee chain on an attached
  Master Enemies Trork before native interaction execution. Certified
  battleaxe attacks and the acceptance snapshot guard are unchanged.
- Keep the QA target card and affix text available on target, while hiding
  its projected health fill. The QA actor still carries Hytale's native
  Healthbar component. Production monster presentation is unchanged.
- Log the first cancelled spar chain per actor, native QA Healthbar
  attachment, QA actor removal reason, and one detailed missing-snapshot
  context if the guard fires again. These are bounded diagnostics.
- No affix implementation class, transient encounter backend, rewards,
  progression, native role asset, or command syntax changed.

## Verification and owner check

`compileJava`, the focused native-action and target-HUD tests,
`validateCustomUi`, and `verifyHyArpgJar` passed. The package validator
reported 18,761 entries, 2,067 classes, and zero ImmersiveNPCs payload
entries. The deployed SHA-256 matches the validated package. No standalone
Hytale server was started; connected acceptance is pending.

Restart the world and run
`/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker`.
Look at the Unique leader to check that its target card lists all three
affixes; a minion should show only its inherited Extra Strong tag. Check
whether the native bar appears at full Health or after damage, and whether
the pack stays present. If the Trorks still enter native spar movement,
their generic spar attack should be cancelled; this build does not alter
the native Trork spar AI. Relevant bounded log keys are
`RPG_ENEMY_NATIVE_SPAR_CANCELLED`,
`RPG_ENEMY_QA_NATIVE_HEALTHBAR_ATTACHED`,
`RPG_ENEMY_QA_ACTOR_REMOVED`, and
`RPG_ENEMY_ACTION_SNAPSHOT_MISSING`.
