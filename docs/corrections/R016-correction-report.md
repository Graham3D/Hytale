# R016 — Three-Slot Skill Tree Correction

R016 is a bounded correction/frontend pass over Stages 03–05. It does not begin
Stage 06. The implementation is committed as
`613b0c2d6e6715fba522964b27fc01cd00edd63e`. The primary evidence/deployment commit
is `252ad6acab4d37e166e6bcf2124ff382b7427395`.

## Connected R015 evidence reviewed

The connected session in `2026-09-06_20-03-27_server.log` proves that the owner
joined R015 and exercised loadout, equip/unequip, character allocation, XP-display
fixtures, compile success/failure, and persistence-related save paths. It also proves
the defects reported by the owner:

- startup advertised the obsolete `Ability1..Ability4` RPG mapping even though
  Ability1 belongs to the native weapon Signature Move;
- routine HUD refreshes repeatedly printed the literal placeholders
  `revision={0} event={1} player={2} correlation={3}`;
- all seven XP presentation fixtures were projected correctly in structured trace,
  but the owner observed no visible XP pips;
- the four-cell RPG skill strip visibly overlapped Hytale's native hotbar.

The skill trace contains compile/loadout activity but no activation, executor,
strike, projectile, or damage events for this session. R015 therefore did not test
the connected Stage 04/05 activation boundary. It is not valid evidence that those
systems failed. The retained review and source hashes are in
`evidence/corrections/R016/connected-r015-log-review.md`.

## Permanent runtime topology

The RPG loadout now contains exactly three Skill slots, six Passive slots, and two
Joint nodes. The static frontend and generic graph service use this exact adjacency:

```text
passive01 ─┐
passive02 ─┼─> joint01 -> skill01
passive03 ─┘

passive04 ─┐
           ├─> joint02 -> skill02
passive05 ─┘

passive06 ----------------> skill03
```

A Joint accepts up to three Passive inputs and has exactly one Skill path. Attempts
to share a Joint across two Skills are rejected. There are no cross-branch
connections. CanvasUI remains frozen and its generic graph backend was not replaced
with page-specific persistence.

## Schema v3 migration

Player schema v2 is migrated deterministically to v3 on load:

1. retain `skill01`, `skill02`, and `skill03` assignments;
2. safely unequip the former `skill04` assignment;
3. preserve learned/owned Skill state and mastery/progression;
4. remove graph edges that reference `skill04`;
5. append an explicit `MIGRATION_V2_TO_V3` degradation reason describing the removed
   slot/edges;
6. immediately persist schema v3 so the migration runs once.

Slot parsers, commands, graph validation, persistence, HUD projection, and activation
mapping now reject `skill04` as an active slot.

## Native ability and HUD audit

The installed Hytale `0.7.0-pre.1` server API exposes `HudComponent.Abilities`.
`InventoryComponent.AbilitySlots` is an item-container projection, while the protocol
surface exposes only `Primary` and `Support`; no supported arbitrary RPG state
projection into the native Abilities component was found. The server JAR audit also
found no supported public global-key/hotkey registration surface.

The resulting ownership is:

```text
Hytale Ability1 -> native weapon Signature Move (never intercepted by RPG)
Hytale Ability2 -> RPG skill01
Hytale Ability3 -> RPG skill02
Hytale Ability4 -> RPG skill03
```

`HytaleAbilitySkillInputAdapter` deliberately returns no RPG slot for Ability1. The
native Abilities component remains visible. Because the public API cannot safely
project arbitrary RPG Skill state into it, R016 supplies exactly three compact RPG
cells at the upper-right, away from the native bottom hotbar.

```ini
SkillTreeOpenHotkey = BLOCKED_PUBLIC_API
```

No client settings or input files are patched, and no native binding is stolen.
`/rpg skilltree` is the guaranteed entry point.

The machine-readable API findings are retained in
`evidence/corrections/R016/native-ability-hud-audit.json`.

## Static Skill Tree frontend

`/rpg skilltree` opens one large centered CustomUI window with three fixed docks:

- a left, vertically scrollable Skills/Passives library with case-insensitive search;
- canonical weapon filters derived from `WeaponRequirement`, plus a compatible-with-
  equipped-weapon filter when the equipped weapon can be identified;
- the exact three-branch Figma topology in the center;
- a right details dock populated from canonical Skill or Passive definitions.

Library and tree selections update the details dock. Equip/Assign and Clear/Unequip
operations pass through `RpgSkillTreeMutationService`, which translates the fixed
node into the same `RpgLoadoutService` compile-and-persist transaction used by the
command frontend. Passive parenting is implicit from the fixed topology. Failed
compatibility is atomic: the prior valid tree is preserved, the compiler's typed
reason is displayed, and the failure is traced. The UI never becomes authoritative
for ownership.

The frontend is intentionally click-driven and static. Free dragging, panning,
zooming, and spline manipulation remain deferred until a suitable Noesis UI surface
exists. Confirmed bundled stand-in art is used; final Skill/Passive art is outside
this pass.

## HUD, XP, and UI trace corrections

- The obsolete four-wide bottom skill strip was removed.
- Exactly three RPG cells are rendered away from the native hotbar and labelled for
  Ability2, Ability3, and Ability4.
- Ability1 is neither duplicated nor represented as an RPG cell.
- Character XP now uses ten explicit fill groups. Each fixture adjusts the visible
  width of each fill, including the fractional tenth-pip cases, without mutating
  authoritative XP.
- Level-up notification and Character attribute allocation behavior remain intact.
- normal server-log UI trace messages now concatenate actual values and include page
  or component context;
- routine five-second `HUD_REFRESHED` records remain in bounded `ui-trace.jsonl` but
  no longer spam the normal INFO log.

## Verification

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Hytale target | `0.7.0-pre.1` |
| RPG revision/version | `R016` / `0.0.9` |
| Player schema | `3` |
| Implementation commit | `613b0c2d6e6715fba522964b27fc01cd00edd63e` |
| Evidence/deployment commit | `252ad6acab4d37e166e6bcf2124ff382b7427395` |
| RPG JAR SHA-256 | `17CDEE20BF04E6F2C4F66C7CB9F9CB884C98FDF09142CB969E08E895A9E0BFDE` |
| Clean RPG + CanvasUI build | PASS |
| CustomUI validation | PASS — 17 documents |
| Retained aggregate tests | PASS — 125 tests, 0 failures/errors/skips |
| Canonical catalogs | PASS — 87 Skills, 66 Passives |
| Topology | PASS — 3 Skills, 6 Passives, 2 Joints; Joint input cap 3 |
| CanvasUI source | PASS — unchanged |
| Isolated server smoke | PASS — exactly three mods, R016 ready/mapping, clean shutdown |
| Connected R016 client | PENDING OWNER QA |

The clean-gate record is `evidence/corrections/R016/verification.json`; the server
smoke transcript and summary are retained beside it. The first smoke attempt exposed
only a harness omission—the temporary save lacked the existing permissions profile.
After the harness copied that profile, the unchanged production code passed startup
and clean shutdown. No gameplay conclusion is based on the failed harness attempt.

## Deployment and rollback

The installation record is `evidence/corrections/R016/installation.json`. Deployment
contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.9.jar` | `17CDEE20BF04E6F2C4F66C7CB9F9CB884C98FDF09142CB969E08E895A9E0BFDE` |

The prior `HytaleRPG-0.0.8.jar` is preserved under
`evidence/corrections/R016/rollback/`. To roll back, fully stop the RPG world, remove
`HytaleRPG-0.0.9.jar`, restore the retained `0.0.8` JAR, and leave the other two mods
unchanged. A schema-v3 player save should not be manually downgraded; restore matching
pre-R016 player data if a full schema rollback is required.

## Known limitations and stop condition

- Connected R016 rendering, click bindings, native Signature behavior, Ability2–4
  activation, and schema persistence still require owner QA.
- The R015 connected session did not enter the RPG activation adapter, so it provides
  no new Stage 04/05 combat proof.
- Current CustomUI provides a static editor, not the intended freeform CanvasUI.
- Arbitrary global K registration and native RPG ability-slot projection are blocked
  by the audited public API.
- Stand-in icons are intentional for this revision.

```ini
R016 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage06Started = false
```

Work stops for owner QA.
