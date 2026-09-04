# R133 — NPC Authoring Studio A4: Profile Editor + Generate

## Scope

R133 implements only A4 from the authoritative NPC Authoring Studio Technical Design.
A5 (Appearance Studio) is not activated.

## Authoritative draft and save contract

- Opening Profile Editor creates a server-owned draft bound to the authoring session,
  stable NPC UUID, editor generation, base revision, and SHA-256 of the canonical file.
- Editable typed fields are grouped as Identity, Personality, Biography, Motivations,
  and Behavior & Speech. Relationships remain explicitly read-only.
- Stable identity and display-name/path identity are immutable in this transaction.
  A rename is rejected rather than partially migrating identity-keyed data.
- Save re-reads and validates the current file, rejects stale revision/hash conflicts,
  patches a deep copy of the raw JSON tree, validates the candidate, creates a rollback
  sibling, atomically replaces canon, re-reads canon, advances the revision sidecar,
  appends an audit event, updates the registry, and refreshes the NPC summary.
- Unknown root and nested JSON fields are retained because the save path patches the
  raw tree instead of serializing only the known Java record.
- Reset restores the persisted open-time values. Cancel/discard never writes canon.

## Generate contract

- Generate is admitted through `OrbisResourceScheduler` as non-foreground,
  `ResourcePriority.LOW` LLM work so conversation remains higher priority.
- The request contains bounded author-controlled profile fields only. It excludes
  private runtime state, logs, credentials, hidden reasoning, memories, beliefs,
  relationships, tasks, schedules, current world state, actions, and canonical speech.
- Output is strict structured JSON and is validated against the selected field allowlist.
- Results are field-level proposals only. They can be accepted in full, accepted by
  selected field, manually edited, or discarded. Acceptance changes only the draft.
- Canon changes only through **Save Profile**. Accepted generated fields record request,
  scope, provider, model, and reviewed provenance in the commit audit.
- Results are rejected if session, editor generation, draft identity, or draft hash became
  stale. Closing the editor cancels queued/active generation.
- Provider failure leaves all manual editing available and the canonical profile unchanged.

## Deterministic evidence

`R133NpcAuthoringStudioA4ProfileEditorTest` proves:

1. stable identity survives editing;
2. list and scalar fields validate and commit;
3. unknown JSON fields survive the raw-tree patch;
4. rollback, revision, and audit files are emitted;
5. concurrent external changes reject a stale draft without overwrite;
6. action order is Generate, Reset, Cancel, Save Profile;
7. generation is low-priority, reasoning-disabled, allowlisted, and proposal-only.

Connected validation is required before A4 is accepted. A5 remains unauthorized.
