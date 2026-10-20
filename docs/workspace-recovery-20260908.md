# GitHub workspace recovery — 8 September 2026

## Owner request and outcome

Resume at Stage 10, retain unsynced files, and write all subsequent project work
under `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale`. This directory is on
OneDrive, but it is the owner's explicitly requested GitHub checkout; no further
writes go to Google Drive. The live game save/mods are not part of this migration.

## Reconciliation and reasoning

The destination initially had a local branch named RPG at `9033cbf`, tracking
`origin/main` and containing unrelated Tavern/Orbis work. It was not the RPG
implementation history. Fetching origin found the completed Stage 09 commit
`837ed80`. The unrelated branch was renamed locally to
`recovered-main-before-stage10-20260908`, preserving its history. A new local
RPG branch now tracks `origin/RPG` at the Stage 09 commit. No reset, force-push,
merge of unrelated histories, or deletion of owner files was performed.

`art/lost and found` contains 18 Java class files, including time-stamped compiler
outputs. Those are not source patches. They remain untouched and are inventoried
with size and SHA-256 in the recovery evidence. They are not placed in the runtime
classpath or used to overwrite newer Java source. The other art files remain
untouched too; this workspace recovery does not change XP or resource HUD assets.
The tracked Stage 09 source and archived rollback JAR were recovered from GitHub.

## Validation and limits

An unchanged `clean build` in the destination passed all 516 retained tests,
including native-codec and CanvasUI suites, and validated 15 CustomUI documents.
This proves the source builds in the destination without the recovered class
files. It is not connected Hytale gameplay or rendering evidence. The Stage 09
rollback artifact retains SHA-256
`50ED7ACA31BFD78125B1762D9A80FDC463C747A84C3C09DBEFC04BE933B8FE3A`.

No live deployment, world restart, player-state migration, or Stage 10 gameplay
implementation is included in this recovery commit. Stage 10 begins from the
verified Stage 09 baseline. The continuous implementation program and its
connected-evidence restrictions remain in force.

Machine-readable evidence: `evidence/workspace-recovery-20260908/verification.json`.
