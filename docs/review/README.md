# Owner / ChatGPT review packet

Current implementation: Stage 13 L, `cd7cdbd0299de4bf18740b2cca08756b64b77f69`. The owner reports that the mod now loads. Current gameplay/production acceptance is not inferred from that statement.

1. [Implementation history, Stages 00–13](implementation-history-00-13.md) — technical synthesis, corrections, failures, decisions, current state and unresolved gates.
2. [Complete historical record](implementation-history-full.md) — all 50 retained Markdown documents at the checkpoint plus full Git commit/stat chronology. Historical instructions are not the current test procedure.
3. [QA/QC checklist](qa-qc-checklist.md) — start here for the actual playtest, with exact commands, equipment, expected observations and safety notes.
4. [87-skill worksheet](qa-skills.csv) and [66-passive worksheet](qa-passives.csv) — open in a spreadsheet or text editor, save your results in a separate copy. All result cells start NOT_RUN.
5. [Manifest](review-manifest.json) — hashes, inventory counts and historical source Git identities.

For ChatGPT, provide the synthesis and QA checklist first. Upload the full historical record when detailed revision-level review is needed; it deliberately includes obsolete intermediate conclusions. It is approximately 1 MB and includes 140 commits; if GitHub does not render it, use Download raw file. Supply filled worksheets and complete session traces separately as QA is performed.

This packet does not contain private attachments, unrecorded conversation transcripts, or every binary/test-output file inline. Pinned links identify those retained repository evidence files and code diffs. Rebuild generated artifacts using `tools/Build-RpgReviewPacket.ps1`; this does not run gameplay tests or modify saves.
