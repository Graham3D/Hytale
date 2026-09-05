# R167 Stable Identity and Profile Editor Repair

Revision: `R167-IDENTITY-PROFILE-EDITOR-REPAIR`

Date: 2026-09-05

Scope: stable NPC storage ownership and Profile Editor navigation/data binding only.

## Outcome

R167 repairs the general legacy null-owner inventory migration that prevented Mara from reopening and replaces the Profile Editor's category-swapped fragments with one durable, vertically scrollable draft form. It does not change Appearance Editor, Voice Recorder, equipment container architecture, armor-stat formulas, or Orbis cognition.

## Mara identity root cause and repair

Mara's canonical profile has stable ID `3f84ec9e-37c5-4f11-9a74-106cd3bc04da`, and the restored runtime entity resolves to that same stable profile ID. The divergence occurred at the durable NPC storage owner: Mara's older `npc-inventory.json` predated the `stableNpcId` field and therefore loaded with a null owner. R165's fail-closed authority check correctly refused to bind that unidentified storage to a live NPC, but reported the generic `NPC_STABLE_PROFILE_ID_MISMATCH`.

R167 adds a bounded migration at profile-bound repository load:

- A missing/null durable storage owner is bound once to the already-resolved canonical `profile.stableId`.
- The migrated file is saved atomically, reread, and verified before it can become authoring authority.
- An existing non-null owner that differs from the canonical profile ID remains a hard conflict. It is logged and left unchanged; R167 never silently rebinds another NPC's storage.
- Every live authoring open now records requested name/profile ID, resolved entity UUID, resolved runtime stable profile ID, durable storage owner, and registry mapping before storage authority is admitted.

The invariant remains:

`profile.stableId = runtime managed-NPC stable profile ID = storage/equipment persistence owner ID`

Display names are lookup keys only and are never mutation authority.

## Profile Editor event-routing root cause

The old left rail sent category names through the authoring-domain action route. Those names are not authoring actions, so valid navigation clicks could produce `Unknown authoring action` and rebuild the center fragment.

R167 gives all eight rail buttons the dedicated `PROFILE_SECTION` event plus the `@ProfileSection` payload. The handler validates the section identifier and updates only the rail selection treatment. It does not mutate the profile, dispatch a domain action, or replace the mounted form.

The installed Hytale 0.6.3 UI resources expose reliable `TopScrolling`, scrollbar, and `KeepScrollPosition` behavior but no verified server-side anchor/scroll-offset command. Accordingly, the main form uses native mouse-wheel scrolling and the rail remains safe navigation/section indication without inventing a page-rebuild jump mechanism.

## Literal selector/value-binding root cause

The previous `ValueChanged` event appended selector expressions to non-output keys such as `ProfileFieldValue`. Hytale treated `#ProfileBiographyInput.Value` and peers as static strings. The same literal could then reach `NpcProfileDraft` and canonical JSON.

R167 uses typed output capture keys (`@ProfileFieldValue` and the individual `@Profile...` fields). More importantly, `SAVE PROFILE` captures all currently mounted form values in one event, validates them, updates the draft, validates the complete profile, and then uses the existing atomic profile commit. Save no longer depends on every prior keystroke event having reached the server.

Legacy stored selector literals are exposed as empty, repairable draft values and are never silently written back. Canonical data remains unchanged until the user explicitly saves a repaired profile. New selector-shaped input is rejected by draft validation.

## One scrollable form

`ProfileEditor/AllSections.ui` mounts these sections once, in order:

1. Basic Info
2. Background
3. Personality
4. Values & Beliefs
5. Motivations
6. Relationships
7. Speech Style
8. Notes

The form is not cleared or rebuilt during scrolling or rail selection. All editable selectors stay mounted, so draft values survive navigation and Save can capture the visible form atomically. Summary remains `MaxLength: 500`, retains its live `n / 500` counter, and remains subject to server-side length validation.

## Biography generation simplification

The normal Apply/Discard proposal controls are removed. `GENERATE BIOGRAPHY` still performs asynchronous generation and validation, but the accepted result is placed directly into the current in-memory draft and into the mounted Biography field. It remains non-authoritative until `SAVE PROFILE` succeeds. Cancel/Reset therefore discard/revert generated text under the same rules as manually entered draft text.

The generation helper remains: `Generate a biography with AI after filling out all basic information.` Generation stays disabled until required Basic Info is valid.

## Files changed

- `src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileDraft.java`
- `src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryController.java`
- `src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui`
- `src/main/resources/Common/UI/Custom/Pages/ProfileEditor/AllSections.ui`
- `src/main/resources/Common/UI/Custom/Pages/ProfileEditor/Background.ui`
- `src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java`
- `src/main/resources/manifest.json`
- `build.ps1`, `install.ps1`, and `test.ps1`
- `src/test/java/com/inigmasgames/persistentnpcs/profile/R167StableIdentityAndProfileEditorTest.java`
- `src/test/java/com/inigmasgames/persistentnpcs/R164ProfileEditorPolishTest.java`

## Deterministic validation

`test.ps1 -SkipLive` passed in full after the final event-binding correction. The R167 gate verifies:

- null-only durable storage-owner migration, atomic reread verification, and the exact canonical stable ID;
- non-null mismatch refusal without rewriting the conflicting file;
- all eight section events are accepted as UI-only navigation;
- one mounted scrollable form contains every section and preserves its mounted state;
- every editable Save field uses an output-capture key and persists the supplied value;
- `Biography = "Hoit grew up near Sandsdeep."` persists exactly, never as a selector expression;
- selector-shaped legacy values are repairable and new selector literals are rejected;
- generated Biography changes the draft while canonical storage remains unchanged until Save;
- proposal Apply/Discard controls are absent.

All historical deterministic inventory, equipment, stat, profile, appearance, voice, persistence, cognition, and rollback gates also passed. Live local-model tests were intentionally skipped; the existing Hytale/JDK deprecation warnings remain warnings only.

## Deployment and rollback

- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R167-IDENTITY-PROFILE-EDITOR-REPAIR.jar`
- Active SHA-256: `CAF798D68942354578592BFBC25F77002520DB6E02DF25D12609A5625B29F8D8`
- Active project JAR count after deployment: `1`
- Immediate R166 rollback: `C:\HytaleRollback\IdentityProfileEditor-R166-2026-09-05-R167\ImmersiveNPCs-0.6.3-R166-CUSTOM-UI-LOAD-HOTFIX.jar`
- Rollback SHA-256: `F18477AAE445AD76C89241E5E814F692CC099D7FD9AC23ED1BBB73B75D584B12`
- Accepted R163 rollback remains preserved at `C:\HytaleRollback\ProfileEditor-R163-2026-09-05-R164\ImmersiveNPCs-0.6.3-R163-NPC-AUTHORITATIVE-ARMOR-STATS.jar`.

## Connected validation checklist

1. Start the NPC save and confirm the top-right revision reads `R167-IDENTITY-PROFILE-EDITOR-REPAIR`.
2. Run `/npc update Mara`; confirm the Studio opens without `NPC_STABLE_PROFILE_ID_MISMATCH`.
3. Open Profile Editor and mouse-wheel from Basic Info through Notes.
4. Click every left-side section button; confirm each is accepted and no `Unknown authoring action` appears.
5. Enter distinct text in several fields, including Biography `Hoit grew up near Sandsdeep.`, then select `SAVE PROFILE`.
6. Reopen Profile Editor and confirm every entered value is exact. Inspect `profile.json` and confirm it contains no selector literals such as `#ProfileBiographyInput.Value`.
7. Generate a Biography, confirm it appears in the Biography field, then Cancel (or Reset and Cancel). Reopen and confirm the generated text was not persisted.
8. Generate again, select `SAVE PROFILE`, reopen, and confirm the generated Biography persisted.
9. Despawn/spawn Mara and repeat `/npc update Mara`; then restart the server and repeat once more.
10. Open Jonalith and Hoit to confirm their profiles/storage remain isolated and unaffected.

Any legacy selector literal already stored in Hoit's profile will intentionally appear as an empty repairable field. It is replaced only after an explicit successful `SAVE PROFILE`.
