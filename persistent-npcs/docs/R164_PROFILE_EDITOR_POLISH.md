# R164 Profile Editor Polish

Date: 2026-09-05  
Revision: `R164-PROFILE-EDITOR-POLISH`  
Scope: NPC Authoring Studio Profile Editor only

## Outcome

R164 replaces the former all-fields-at-once database-style Profile Editor with a native framed editor containing a compact category rail, one category-local form, and the established Reset / Cancel / Save Profile action row. The existing server-owned `NpcProfileDraft`, writer/session lease, stable identity, optimistic revision/hash check, raw-tree extension preservation, and atomic save transaction remain authoritative.

No inventory, equipment, stat, appearance, voice, cognition, memory, relationship, or stable-identity authority was redesigned.

## Schema-field mapping

| Presentation category | Authoritative typed fields |
|---|---|
| Basic Info | read-only profile name; `role`; `speciesArchetype`; `ageCategory`; `home`; `summary` |
| Background | `biography`; `selfIdentity`; `workplace`; `knowledgeDomains` |
| Personality | `personality`; `personalityTraits`; `likes`; `dislikes` |
| Values & Beliefs | `values` |
| Motivations | `purpose`; `goals`; `fears` |
| Relationships | existing authored relationship count, read-only |
| Speech Style | `speakingStyle` |
| Notes | `creatorNotes` |

Every previously exposed authored field remains reachable exactly once. Name remains read-only because display-name changes require the existing dedicated identity migration rather than an editor-local rename.

## Summary and creator notes

`summary` is a new optional typed `NpcProfile` field and `NpcProfileDraft.Field` with a hard 500-character budget. The Basic Info fragment applies `MaxLength: 500`; every ValueChanged event also passes through `NpcProfileDraft.update`, whose server-side length rejection is authoritative. The UI counter is updated from the accepted server draft value without rebuilding the page. An over-budget value blocks draft acceptance and therefore cannot reach Save Profile.

`creatorNotes` is a separate optional typed field with a 3000-character authoring budget. It is deliberately excluded from `NpcProfileGenerationService.authoringInput`. No conversation, memory, belief, speech, or world-state code reads it, so it remains creator/developer metadata rather than NPC-facing canon.

Both fields are backward-compatible optional additions: schema-v1 files deserialize with empty values and are not rewritten merely by being read. Repository migration/copy paths and `OccupationCatalog` preserve both fields once present.

## Category/render strategy

The stable page owns the header, category rail, footer status, and action row. Eight small `.ui` fragments own their category fields. Category activation validates the current server draft, changes `ProfileCategory`, clears only `#ProfileForm`, appends the selected fragment, binds that fragment's ValueChanged events, and restores values from the same live draft. The entire page is not recreated during category navigation, so unsaved accepted values persist across switches.

The selected category has a gold rail and arrow treatment. The editor uses the established `DecoratedContainer`, Common.ui inputs/buttons, restrained navy panels, and gold hierarchy. Technical draft IDs and provider/model names remain hidden from the normal UI.

## Generate Biography pipeline

Basic Info exposes one `Generate Biography` action. It is enabled only when Name, Role, Species, Age, Home, and Summary are nonblank and Summary is within budget. The server repeats this validation and always forces `GenerationScope.BIOGRAPHY`; no client-supplied provider, model, or alternate generation scope is accepted for this path.

Generation still uses the existing low-priority `NpcProfileGenerationService` and Orbis resource scheduler. It receives the current revision-bound draft and approved authoring fields, but never creator notes. The result remains a structured `GENERATED_PROPOSAL`; the generation service has no canonical save path.

Stale-result protection is unchanged: session, draft ID, editor generation, and draft hash must all match before a completion is accepted. Provider failure leaves the current manual draft open and editable.

## Proposal review

A successful generation switches the editor to Background and mounts a proposed-biography review panel. `Apply to Draft` accepts only the allowlisted Biography change into `NpcProfileDraft`; `Discard Proposal` removes the proposal without changing the draft. Neither operation saves canon. Save Profile remains the sole promotion boundary.

## Persistence and conflicts

Candidate construction still begins from the complete raw JSON tree and patches typed draft fields, preserving unknown root extensions. Save still performs stable-ID and name checks, base revision/hash comparison, schema/semantic validation, temporary sibling write, reread validation, rollback copy, atomic replacement (with supported fallback), revision increment, registry refresh, and audit append. A concurrent writer causes a revision conflict and leaves the draft intact.

Reset restores the persisted/base draft and clears a proposal. Cancel prompts through the established dirty-editor confirmation before discarding. Category changes never save implicitly.

## Files changed

- `build.ps1`, `install.ps1`
- `src/main/resources/manifest.json`
- `src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfile.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileDraft.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileGenerationService.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/ProfileRepository.java`
- `src/main/java/com/inigmasgames/persistentnpcs/profile/OccupationCatalog.java`
- `src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui`
- `src/main/resources/Common/UI/Custom/Pages/ProfileEditor/*.ui`
- `src/test/java/com/inigmasgames/persistentnpcs/R164ProfileEditorPolishTest.java`
- `src/test/java/com/inigmasgames/persistentnpcs/R129NpcAuthoringStudioA1Test.java`
- `test.ps1`

## Deterministic validation

`test.ps1 -SkipLive` passed in full after the final build. R164 specifically verifies typed Summary/Notes round-trip, 501-character Summary rejection, schema-v1 compatibility, unknown-field preservation, all eight packaged category fragments, removal of the old global proposal panel, the 500-character UI cap/counter, partial `#ProfileForm` replacement, generated proposal routing to Background, and creator-notes exclusion from generation input.

The full suite also re-passed all earlier inventory, equipment, native stats, appearance, voice capture/isolation, profile persistence, revision conflict, stable identity, Orbis cognition, and conversation matrix gates.

## Connected validation required

Restart the NPC save/server before testing.

1. At 1920x1080, run `/npc update Hoit`, open Profile Editor, and confirm the R164 HUD/revision.
2. Visit all eight categories. Confirm only the selected category's fields appear and the gold selected rail follows selection.
3. Enter unsaved values in multiple categories, switch repeatedly, and confirm each value remains.
4. In Basic Info, confirm Name is read-only. Fill Role, Species, Age, Home, and Summary; verify the live counter and that generation enables only when all are valid.
5. Generate Biography. Confirm the UI moves to Background, displays only a Biography proposal, and the persisted profile remains unchanged before Apply/Save.
6. Test Discard Proposal, then generate again and Apply to Draft. Close with Cancel and discard; reopen and confirm canon did not change.
7. Generate/apply again, Save Profile, close/reopen, restart the server, and confirm exact Summary, Biography, and other field persistence.
8. Create or retain an unknown root field in a test profile, save through the editor, and confirm it survives.
9. Exercise a concurrent profile write and confirm Save reports a conflict instead of overwriting it.
10. Confirm provider failure leaves every manual field usable and a generation result from a closed/reopened or different NPC editor is rejected.
11. Save a unique Biography, converse with the NPC, and confirm Orbis sees only the saved canonical Biography—never an unapplied proposal or creator Notes.
12. Repeat layout checks at 2560x1440, including long Summary/Biography/Notes text, proposal review, status messages, and the bottom action row.

## Deployment and rollback

Active JAR:

`C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R164-PROFILE-EDITOR-POLISH.jar`

SHA-256: `345CD2FA147396FF3D65FC61D07DD0A3F418E34F98D614D2A6BD8FA8A8CF582C`

Exactly one project JAR is active.

Immediate rollback:

`C:\HytaleRollback\ProfileEditor-R163-2026-09-05-R164\ImmersiveNPCs-0.6.3-R163-NPC-AUTHORITATIVE-ARMOR-STATS.jar`

SHA-256: `BC75F941B9B39EA5DE04E2F89E2ABCAF3B94B83DE27288D8E67F234B53D60DF3`
