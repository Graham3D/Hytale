# Main development housekeeping — 2026-10-20

The owner requested that main become the current development baseline after
preserving local development content. This task proposes the existing, offline
verified QA5 candidate at `f3c1433f1758817c6e66966dc81c6abe73780043`, plus two
original source-art files and this inventory. No new gameplay behavior is added
beyond QA4/QA5; no new R revision or gameplay acceptance is assigned.

`codex/main-housekeeping-20261020` is explicitly stacked on
`codex/me12-072-modonly` at that exact commit. The remote main observed before
this operation is `96d748e09cc86aa6fc8015c47ae08ffa92f09c06` (QA3), four commits
behind QA5. The source version stays `0.2.0-R262ME12S3B-072-QA5-CANDIDATE`.
Final exact-commit build/package evidence and integration status are recorded in
the linked housekeeping PR and Guardian receipt, after committing this record.

## Branch decisions

| Branch | Decision and evidence | Next action |
| --- | --- | --- |
| `codex/me12-072-modonly` | Integrate the four reviewed QA4/QA5 commits as owner-requested development source. They fix failed hostile-combat attachment cleanup and cold-recovery reward contention. | Run final exact-commit gates and serialize main publication. Gameplay remains QA_PENDING. |
| `codex/hro-finalization` | QUARANTINED historical branch; selective fixes already included in main. HroComparison, HroContracts, HroTool, player-state persistence, and the three original regression classes match; Recorder/Runtime/Bundle retain those fixes plus later ME12 and owner-scoped-stop extensions. | Retain original commits; no whole-branch merge. |
| `codex/elite-natural-hookup` | QUARANTINED historical branch; title ownership, native name, inventory handoff, gear action publication and their regression code already present. Newer BuildIdentity fields and 0.7.2 hook verification supersede old candidate metadata. | Retain the exact successor `3327a565` on the preservation branch; no whole-branch merge. |
| `codex/r237-source-reconstruction` | QUARANTINED obsolete reconstruction. Its unique commit removes the relative save-root resolver and regression test, deletes the R238 fix document and restores older revision metadata. | Preserve for historical recovery; do not roll current main backward. |
| 24 `codex/preserve-20261020*` branches | QUARANTINED recovery snapshots, not independent gameplay features. | Retain as immutable recovery references. |
| Other 40 remote branches | Their exact branch tips were already reachable from pre-housekeeping remote main. | No merge needed; no branch or worktree deletion in this task. |

The HRO/Elite selective integration is traceable to
`c37a10408501b11c49fd273eeec39725158c863d` and its successors, as recorded in the
development baseline. Whole-commit patch IDs differ because the earlier source
was selectively ported and then extended; this audit does not claim that the old
branch SHAs are reachable from main. Per-file old/new blobs and the complete
remote branch inventory are in [git-inventory.json](housekeeping/2026-10-20/git-inventory.json).

## Source artwork and protected local changes

The updated `art/Skills.psd` and high-resolution
`art/Skills Alpha Art/CycloneArmor.png` are copied byte-for-byte from preservation
commit `e82e15002ad09232df388c401c58612685f2c539`. The packaged 128-pixel icons
already match the preserved versions, the icon updater has the same substantive
source, and the Spirit Shield JSON differs only in formatting. Those production
assets and tooling are not overwritten during housekeeping.

The primary checkout is still protected at `1dfa2cea` with its eight local edits.
They remain intact and backed by the preservation branch. Updating GitHub main
does not synchronize that dirty checkout. The clean isolated housekeeping
checkout is the safe current-source location; do not reset or clean the primary.
The inherited tracked AGENTS.md is older than the current owner-supplied policy;
it is not rewritten in this task. Current owner instructions govern this work.

## Verification, release state and recovery

The existing exact-QA5 receipt at `f3c1433f` records all 4,422 offline tests,
package validation and JAR SHA-256
`850419ADC7DD5AB51419C0A42A233961C0E0CDF86FA767AE62278A80179BB647`.
That checksum is not attributed to a later documentation/artwork commit. The
housekeeping commit requires its own exact-source verification before publication.
The full offline rerun is gate-driven because the build embeds its Git identity;
it is not evidence of new runtime changes in the housekeeping commit.

Guardian preflight is available and passed. Existing change, exact-commit receipt,
publication and completion checks are required. Its comprehensive integration
inventory enforcement remains an UNVERIFIED code-gate gap; this manual inventory
does not silently implement or claim that separate enforcement change.

Connected Unique spawning, HRO/Elite and remaining ME12 acceptance stay QA_PENDING.
Monster Affix release gate remains OPEN with `ownerAccepted=false`, production v2
stays disabled, and no save/configuration/native-server mutation, deployment or
game/server launch is authorized by this source-housekeeping task.

The complete source/artwork recovery guide remains on
[codex/preserve-20261020](https://github.com/Graham3D/Hytale/blob/codex/preserve-20261020/docs/PRESERVATION_20261020.md).
Archived old code is retained separately instead of being introduced into main.
