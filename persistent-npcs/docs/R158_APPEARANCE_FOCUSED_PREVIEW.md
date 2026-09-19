# R158 — Appearance focused-preview repair

## Outcome

R158 fixes the connected case where a selected cosmetic and its color were
accepted but appeared not to change the NPC. This remains a recovery-stage
Appearance repair; it does not begin the thumbnail checkpoint or change the
Appearance Editor layout/backend.

## Connected diagnosis

R157 event delivery was working. The connected log proves:

- `APPEARANCE_OPTION` and `APPEARANCE_COLOR` arrived with concrete IDs and the
  current catalog hash;
- every selection passed registry validation;
- the draft skin fingerprint changed on each click;
- every newest generation reached `NPC_AUTHORING_APPEARANCE_PREVIEW_APPLIED`;
- there were no event rejections.

The screenshot was in `UNDERTOP`. Hoit's persisted skin contains
`overtop: Arctic_Scout_Jacket.Black`, which completely covers the selected inner
shirts and their colors. The selected card therefore moved correctly, but the
full-composition preview concealed the change.

## Repair

The Appearance preview is now category-focused:

- `UNDERTOP` temporarily suppresses `OVERTOP` in the client preview;
- `PANTS` temporarily suppresses `OVERPANTS`;
- `UNDERWEAR` temporarily suppresses pants/overpants/undertop/overtop;
- `HAIRCUT` temporarily suppresses a covering head accessory;
- face-detail categories temporarily suppress a covering face accessory.

This is presentation-only. The focused skin is a defensive copy. It never
changes the draft, saved NPC appearance, player appearance, ECS state, or any
inventory/equipment authority. Switching focus, saving, cancelling, or closing
restores the complete authored composition.

Preview request identity now includes the focus category, so category changes
cannot be incorrectly coalesced as an unchanged skin. Both the category and the
draft generation are captured and checked before asynchronous application.

## Deterministic validation

The full `test.ps1 -SkipLive` suite passed on 2026-09-05. The new R158 gate
verifies:

- every covered-layer mapping;
- source/draft immutability;
- focus-aware request hashing;
- category and generation rejection of stale preview work;
- preview-only telemetry and zero authoritative viewer mutation.

All R0–R157 persistence, inventory, gear, stats, Profile, voice, cognition,
static-resource, interaction-lifecycle, and event-payload gates also passed.

## Deployment

- Candidate: `ImmersiveNPCs-0.6.3-R158-NPC-APPEARANCE-FOCUSED-PREVIEW.jar`
- Candidate SHA-256:
  `883B563DACE1C12A3ED293EE686438248876D446EAB63CE0BC7FF2D7DE0468B3`
- Active project JAR:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R158-NPC-APPEARANCE-FOCUSED-PREVIEW.jar`
- Immediate rollback:
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R157-2026-09-05-R158FocusedPreview\ImmersiveNPCs-0.6.3-R157-NPC-APPEARANCE-EVENT-PAYLOAD-HOTFIX.jar`
- Immediate rollback SHA-256:
  `A6AD833401CD464D4351F65C454DBFE8FAD5ACB8AFA7F3916C24FEE3C97B524A`
- Exactly one project JAR is active in the save's `mods` directory.

## Connected validation

1. Restart the `NPC` world and confirm revision
   `R158-NPC-APPEARANCE-FOCUSED-PREVIEW`.
2. Open `/npc update Hoit` → Appearance → Clothing → Undertop.
3. Confirm Hoit's saved Arctic Scout Jacket is hidden only in this focused
   preview.
4. Select several Undertops and swatches; confirm each is immediately visible.
5. Switch to Overtop; confirm the complete outer-layer choices are visible.
6. Test Pants with and without Overpants, then test Haircut with a head accessory.
7. Save a change, close/reopen, and confirm both the selected inner layer and the
   original outer layer remain authored.
8. Discard a separate change and confirm exact restoration.
9. Confirm the player avatar/equipment and all non-Appearance Studio pages remain
   unchanged.

**STOP:** R158 awaits connected approval. The thumbnail checkpoint remains gated.
