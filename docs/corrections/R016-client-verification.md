# R016 — Connected Client Verification

Use this checklist after fully stopping and restarting the `RPG` world.

1. Rejoin and run `/rpg loadout`. Confirm the HUD identifies R016, only `skill01`–
   `skill03` exist, and any former `skill04` content is unequipped rather than removed
   from learned/mastery state. Reopen/rejoin once and confirm no second migration.
2. Use the weapon Signature Move with native Ability1. Confirm it still works and no
   RPG activation trace is created for Ability1.
3. Equip one valid Skill in each slot. Activate `skill01`, `skill02`, and `skill03`
   with Ability2, Ability3, and Ability4 respectively. Confirm each trace names the
   expected slot and Skill. An empty or invalid slot must reject cleanly.
4. Confirm exactly three custom RPG cells appear, away from the native bottom hotbar.
   No Quick Slash text/cell may overlap the hotbar, and the native Signature UI must
   not be obscured or duplicated.
5. Run, in order:

   ```text
   /rpg dev xp-display 0
   /rpg dev xp-display 9.9
   /rpg dev xp-display 10
   /rpg dev xp-display 50
   /rpg dev xp-display 99.9
   /rpg dev xp-display 100
   /rpg dev xp-display clear
   ```

   Confirm ten segments are visible: empty; 99% of the first; one full; five full;
   nine full plus 99% of the tenth; all full; then the real XP projection restored.
6. Run `/rpg skilltree`. Do not expect K: R016 reports
   `SkillTreeOpenHotkey = BLOCKED_PUBLIC_API` on Hytale `0.7.0-pre.1`.
7. Switch Skills/Passives tabs. Search by partial name, description, and keyword using
   mixed case. Open the filter panel, toggle a derived weapon filter, and test
   compatible-with-current-weapon with a recognized equipped weapon.
8. Confirm the center shows exactly 3 rectangular Skills, 6 circular Passives, and 2
   Joints. Joint A must receive Passive 1 left, Passive 2 right, and Passive 3 below,
   then feed only Skill 1. Joint B must receive Passive 4 left and Passive 5 below,
   retain one unused input capacity, and feed only Skill 2. Passive 6 must feed Skill
   3 directly. No branches may cross and no Joint may share two Skills.
9. Select a library Skill, library Passive, occupied Skill node, and occupied Passive
   node. Confirm the right details dock updates with canonical facts and compile/
   assignment state for each selection.
10. Select a Skill node, choose a compatible Skill from the library, and press Equip.
    Select a Passive node, choose a compatible Passive, and press Assign. Verify the
    implicit Joint/direct edges with `/rpg loadout` and `/rpg compile`.
11. Prove rollback: equip Quick Slash in `skill01`, then try assigning Fork to
    `passive01`. The page must show the compiler's incompatibility reason, and the
    previous valid tree must remain unchanged in both `/rpg loadout` and
    `/rpg compile`.
12. Clear one occupied Skill and Passive from the page and confirm the command view
    immediately matches. Make one command-side equip/link change and reopen the page;
    it must immediately match the same authoritative state.
13. Fully restart, rejoin, and run `/rpg loadout`, `/rpg compile`, and
    `/rpg skilltree`. Confirm schema v3, assignments, implicit graph edges, attributes,
    points, and HUD presentation persist with no RPG/CustomUI exception.

Retain the server/client logs plus `skill-trace.jsonl` and `ui-trace.jsonl`. For any
failure, record the exact action and earliest missing/incorrect trace event. Do not
begin Stage 06 from this checklist.
