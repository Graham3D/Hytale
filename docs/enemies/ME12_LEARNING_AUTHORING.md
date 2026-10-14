# Owner-authored learning sources

Default server-authoritative file, created only when the candidate starts:

```json
{"schemaVersion":2,"assignments":[]}
```

No public mappings are enabled or selected by engineering. Appendix G's historical
131 assignments are reference data only. The redesigned Skills & Passives document
does not automatically change the executable catalog or make an ability ready.

## Entry schema

Every entry has exactly these fields. Unknown fields, null values, duplicate JSON
keys, malformed enum/boolean values, unknown abilities/sources and oversized documents
are rejected. The file limit is 256 KiB, 2,048 entries and nesting depth 16.

| Field | Meaning |
| --- | --- |
| `sourceId` | Stable lower-case progression/pity source key, chosen once by the owner. |
| `canonicalRoleId` | Exact canonical role from the existing enemy reward registry. |
| `combatIdentity` | Exact registry combat identity; independent of display name. |
| `nativeRoleIds` | Nonempty array of exact native aliases that resolve to that canonical role and combat identity. |
| `kind` | `SKILL` or `FIRST_COPY_PASSIVE`. |
| `abilityId` | Existing catalog Skill or Passive ID, not its display name. |
| `enabled` | Boolean; false permits a draft but produces no opportunities. |
| `rarity` | `COMMON`, `RARE` or `UNIQUE`; existing 15%, 5%, 2% base learning probabilities, before existing Wisdom/rarity/affix rules. |
| `minimumRank` | `COMMON`, `SPECIALIST`, `ELITE`, `MINIBOSS` or `BOSS`. Skill tier restrictions also remain enforced. |
| `difficulties` | Nonempty subset of `NORMAL`, `NIGHTMARE`, `HELL`. |
| `encounterRanks` | Nonempty subset of the rank enum above. |
| `allowInitialMinions` | Explicit permission for a source-qualified economic initial minion; does not override native eligibility/provenance. |
| `sourceAccepted` | Owner attestation that this exact source is accepted for acquisition. |
| `abilityReady` | Owner attestation that this existing ability is gameplay-ready. |
| `approvalEvidence` | Nonblank owner acceptance reference required for enabled entries. |

The command validates registry identity and catalog membership; enabled Skills also
need an existing Stage04 runtime profile. Source/ability readiness attestations are
owner decisions, not automated claims of gameplay acceptance. Existing Passive
definitions still require owner readiness; a catalog entry alone is not acceptance.
No TBD mechanics, new ability definitions or automatically chosen pairings are added.

Each live document allows one source per combat identity, one ability per source,
and one source per kind/ability. Aliases of the same source share pity and ownership.
Separate minion assignments need distinct authoritative combat identities; changing
a display name, role alias or Elite prefix cannot create a new source.

## Activation and recovery

1. Edit only `learning-sources.json`; keep desired entries disabled until the source
   and ability readiness requirements are satisfied by the owner.
2. `/rpg-learning validate` checks without activation.
3. `/rpg-learning reload` writes a SHA-256-addressed immutable archive, flushes it,
   atomically replaces `learning-sources.active`, then publishes the immutable in-memory
   snapshot. A validation, archive or IO failure retains the previous snapshot.
4. `/rpg-learning status` shows the active digest/count. Startup with a bad owner edit
   retains the last valid archived snapshot; missing/corrupt active history fails closed.

These commands require `inigmasgames.rpg.learning.author`, including server command
permission handling. IO uses the existing bounded executor; saturation rejects the
operation without changing the active mapping. See `docs/COMMANDS.md` for the exact path.

The existing death plan freezes the opportunity's assignment revision, source, kind,
ability, rarity, Wisdom and reward context. The existing player reward intent carries
the assignment revision with its progression delta. Delivery and restart recovery
validate that immutable archive, even after disabling/removing the live entry. Missing
or corrupt history produces `WAITING_FOR_LEARNING_ASSIGNMENT_ARCHIVE`; restore the
exact archive to resume. Do not delete journals, pity, player ownership or archives.

Previously enabled source identity, canonical combat identity, ability/kind and rarity
cannot be repurposed by reload. A new source key for the same historical combat identity
or ability is also rejected, preventing pity reset by renaming. Disabled drafts that
were never enabled may be revised. Alias and opportunity restrictions can change for
future opportunities while historical bytes remain authoritative. A deliberate identity
migration is separate engineering, not a reload workaround.

Archive scanning is bounded to 4,096 files / 64 MiB; exceeding that limit requires an
explicit retention review, never automatic history deletion. Current content definitions
must remain available to read old grants. Reloading a weaker/disabled mapping cannot
reroll a pending reward. Existing serialized reward ownership handles replay/concurrent
grant versus purchase; no second pity or progression ledger exists.

## Verification and release gate

`Me12LearningConfigurationTest` uses explicitly synthetic Trork fixtures for both kinds,
including every existing player reward write boundary, restart after mapping removal,
duplicate delivery, invalid edits, historical identity changes, missing/corrupt archives
and source restrictions. Those pairings are not shipped authoring data or owner choices.
`Me12LearningCommandTest` checks the actual command tree and explicit permissions.

ME12-R08 stays BLOCKED until real owner-selected Skill and first-copy Passive sources
are tested and accepted. The requested deferral of acquisition authoring is recorded
as a **proposed release-scope amendment**, not an amendment to AD1 or a passing gate.
