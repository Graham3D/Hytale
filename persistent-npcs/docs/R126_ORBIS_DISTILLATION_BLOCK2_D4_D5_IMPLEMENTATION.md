# R126 Orbis Distillation Block 2 — D4/D5 Implementation Report

Date: 2026-09-03  
Scope: D4 deterministic curation and D5 leakage-safe dataset construction/freeze only  
Production runtime: R124 retained; no gameplay JAR deployed  
Training mode: `OFF`  
Training, PEFT, LoRA, QLoRA, model conversion, packaging, shadow, canary, and promotion: not performed

## 1. Executive decision

| Gate | Result | Meaning |
|---|---|---|
| D4 deterministic curation | PASS | The bounded 22-example project fixture corpus passed the ordered deterministic oracle chain. |
| D5 dataset construction/freeze | PASS | Normalization, deduplication, semantic grouping, family-level splits, protected-set decontamination, coverage, immutable freeze, and idempotence passed. |
| G1 dataset integrity | PASS — bounded fixture dataset only | `ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86` is a valid frozen project-owned fixture dataset. This is not a production-scale training corpus. |
| G0 legal/lineage | BLOCKED | Exact upstream BF16 lineage and the installed/current NVIDIA license reconciliation remain unresolved. |
| D6 training readiness | BLOCKED | The Python/CUDA stack and Nemotron-H/Mamba/PEFT one-batch path are not installed or proven, and G0 is not clear. |

**D6 may not safely begin.** The D4/D5 data boundary is trustworthy for the bounded fixture dataset, but the separate legal, lineage, teacher, privacy, environment, and architecture preconditions remain blocking.

## 2. Guardrails preserved

- `TrainingMode` remains limited to `OFF` and `CORPUS_AUDIT`; neither mode permits model mutation.
- The D4/D5 packages have no imports from production runtime code. A source-tree test enforces this one-way boundary.
- No teacher was contacted. No real-player conversation was exported. No audio was exported.
- No Python packages, CUDA packages, Transformers, PEFT, TRL, or optimizer were installed or invoked.
- No model file, Ollama model, provider setting, NPC behavior, prompt template, or gameplay asset was changed.
- The deployed R124 JAR remained in place and byte-identical.
- CONNECTED and CANARY material exists only as protected manifest references; neither appears in trainer rows.

## 3. D4 — deterministic oracles, curation, privacy, and review

### 3.1 Canonical contracts

The D4 layer is under `com.inigmasgames.persistentnpcs.training.curation` and reuses the R125 candidate, provenance, production-input, teacher-policy, model-identity, prompt-identity, and canonical-hash contracts.

`CurationContracts` defines:

- `ReviewState`: `UNREVIEWED`, `ORACLE_ACCEPTED`, `HUMAN_ACCEPTED`, `NEEDS_REVIEW`, `REJECTED`, `FROZEN`;
- task and source types;
- output contract and structured-output constraints;
- typed required and forbidden propositions;
- answerability, temporal, evidence-source, claim-type, and action-truth targets;
- semantic-family metadata, negative evidence, contamination metadata, and artifact hashes;
- the immutable `CurationRequest` boundary.

`DistillationExample` retains the exact production `ProductionInputSnapshot`, epistemic target, chosen response, public critique, required/forbidden IDs, every oracle verdict, teacher identity where present, review state, semantic metadata, contamination state, artifact hashes, negative evidence, and deterministic creation time.

Student messages are never rewritten by curation. Teacher rubric markers and curation metadata remain outside `ProductionInputSnapshot.messages`.

### 3.2 Oracle order and authority

`DeterministicCurationEngine` executes exactly eleven checks in this order:

1. production parity;
2. output contract;
3. required propositions;
4. forbidden claims;
5. Answerability behavior;
6. source attribution;
7. action truth;
8. the existing `EpistemicClaimFirewall` when a live contract is supplied;
9. teacher policy;
10. privacy;
11. style.

Style is deliberately last. It cannot override an authority failure. An unexpected exception inside an authority check becomes a blocking `ERROR` verdict with `SOURCE_ARTIFACT_DEFECT`.

Every `OracleVerdict` records oracle ID/version, status, canonical reason code, evidence references, blocking status, evaluated payload SHA-256, and timestamp. For reproducibility, verdict timestamps derive from the persisted candidate timestamp rather than wall-clock curation time.

### 3.3 Production-parity gate

Positive admission requires:

- `MODEL_TRAINING_ELIGIBLE` ownership and `ELIGIBLE_UNLABELED` candidate state;
- a recomputed provider-input SHA-256 equal to the captured hash;
- the expected prompt-template and base-model content IDs;
- an epistemic target matching the captured source artifact;
- no teacher-only rubric marker in production student messages.

The engine does not repair mismatches. It rejects them with `UPSTREAM_ORBIS_BOUNDARY_FAILURE`, `PRODUCTION_PARITY_FAILURE`, or `SOURCE_ARTIFACT_DEFECT`.

### 3.4 Semantic truth checks

- Output-contract validation enforces non-empty bounded dialogue or strict JSON shape and returns `CONTRACT_INVALID` on failure.
- Required propositions are wording-independent concept groups and return `ORACLE_FAIL_REQUIRED_PROPOSITION` when omitted.
- Forbidden patterns and superseded values return `ORACLE_FAIL_UNSUPPORTED_CLAIM`.
- Answerability produces distinct failures for over-abstention, false certainty, wrong uncertainty mode, missing clarification slots, and withheld disclosure.
- Source attribution distinguishes player testimony, NPC testimony, derived reflection, and direct/authoritative sources. Missing framing returns `ORACLE_FAIL_SOURCE_ATTRIBUTION`.
- Action language must match an authoritative `NpcActionResult` and committed scope. Rejected, absent, or partial actions cannot be promoted to success. Failure is `ORACLE_FAIL_ACTION_TRUTH`.
- When supplied, the production `EpistemicClaimFirewall` remains the final claim-authority implementation; D4 does not create a parallel world-truth system.

### 3.5 Teacher trust transition

Teacher text is only an unverified proposal. An approved teacher target must carry an exact identity and matching policy snapshot and still pass every objective oracle. Missing, unknown, rejected, or mismatched teacher policy fails with `TEACHER_TERMS_INELIGIBLE` or `TEACHER_POLICY_MISMATCH`.

The test matrix proves an approved fixture teacher cannot bypass deterministic truth. The frozen fixture dataset contains no teacher-generated rows and reports zero teacher snapshots.

### 3.6 Privacy and prompt-injection handling

`CurationPrivacyPolicy` is versioned and fail-closed. The checked-in policy sets:

- `realPlayerCorpusApproved=false`;
- `permitStablePlayerIdentifiers=false`;
- `permitRawAudio=false`.

It rejects raw audio, hidden-reasoning fields, credentials/secrets, private filesystem paths, and stable player identifiers. Player pseudonyms are deterministic when semantics allow them. A `REAL_PLAYER_PRODUCTION` source cannot become accepted without explicit approval; it routes to `NEEDS_REVIEW_REAL_PLAYER_CONSENT`.

Prompt-injection strings are retained only as inert content. Tests preserve an “ignore previous instructions” fixture in the user message and prove that obeying it is rejected by the forbidden-claim oracle. Content cannot change paths, policies, splits, teacher status, or execute commands.

### 3.7 Negative evidence

Rejected output never occupies `chosenResponse`; rejected examples hold an empty chosen target. Typed `NegativeEvidence` is separate from positive SFT data and cannot become world truth, memory, future teacher context, or trainer-loss text. DPO construction was intentionally not implemented.

### 3.8 D4 fixture evidence

All 22 required positive curriculum fixtures pass, covering identity, unknown/partial/conflicted knowledge, correction, current perception, episodic recall, expiry/supersession, clarification, committed/rejected actions, self-state, relationships, rumor/testimony, withheld content, persona variation, humor/metaphor, strict structured output, and multi-turn referents.

Negative tests cover omitted propositions, invented claims, wrong source framing, false certainty, false action completion, malformed JSON, hidden reasoning, private paths, unapproved teachers, source-artifact mismatch, prompt injection, unapproved real-player data, raw audio, and production-input mismatch.

D4 evidence:

- fixture count: 22;
- accepted: 22;
- rejected: 0;
- policy SHA-256: `51f82dd24139305c7359687dcf7039b5cec32fa2f63c6ddbac6355c8fa720a9c`;
- verdict-set SHA-256: `257fe10306ccbf41b5adb6490b3640283c4596b5df61387bc8b3a21be0d86b9a`;
- evidence file SHA-256: `d499d56ec222de5cd33844df1c8c0abc443ba0bfd141dc5eb413ce8636f8c895`.

## 4. D5 — normalization, grouping, decontamination, and freeze

### 4.1 Normalization and deduplication

`DatasetNormalization` implements `nfc-lf-trailing-ws-v1`:

- Unicode NFC normalization;
- CRLF/CR to LF;
- trailing horizontal whitespace removal;
- deterministic terminal newline handling;
- exact and entity-normalized fingerprints.

Exact dedup hashes canonical input + semantic target + canonical chosen response. Equivalent duplicates merge provenance into one retained row. Fuzzy comparison uses token-bigram Jaccard `token-bigram-jaccard-v1`, with `0.94` merge and `0.78` human-review thresholds. A fuzzy pair merges only when semantic family, target hash, and normalized response are compatible; otherwise it becomes an ambiguous collision blocker.

### 4.2 Semantic-family assignment

`SemanticFamilyAssigner` groups by authority-bearing lineage before surface form:

- explicit parent family;
- generation ancestor;
- conversation session;
- event timeline;
- otherwise entity-normalized semantic target/mechanism.

This keeps correction/supersession sequences, hidden-object statement/recall, testimony chains, action request/results, and multi-turn referents together. Entity values are normalized out so renamed characters can share a capability family, while distinct semantic states remain separate.

### 4.3 Split policy and profile holdout

Splits are assigned by semantic family, never by row. Protected requests and whole-profile holdouts are assigned first. Remaining families are ordered by the stable split seed and allocated approximately 80/10/10, with no-family-leakage taking priority over exact percentages.

The final bounded dataset contains:

| Split | Canonical rows | Protected references |
|---|---:|---:|
| TRAIN | 16 | 0 |
| DEV | 2 | 0 |
| TEST | 2 | 0 |
| CHALLENGE | 2 | 0 |
| CONNECTED | 0 | 1 |
| CANARY | 0 | 1 |

The authored `profile-holdout` profile is wholly outside TRAIN. Every semantic family occurs in exactly one row-bearing split.

### 4.4 Protected-set decontamination

`ContaminationChecker` checks six independent modes:

1. exact canonical input hash;
2. normalized fingerprint;
3. entity-normalized fingerprint;
4. semantic family ID;
5. generation ancestry;
6. bounded fuzzy similarity.

Exact, normalized, entity-normalized, family, ancestry, and fuzzy overlap are independently exercised. Any issue blocks approval/freeze. The final audit checked all 22 rows and reports zero contamination issues. CONNECTED and CANARY remain references only and are never written to an SFT directory.

### 4.5 Coverage

`CoverageReport` stores row counts and token-weighted counts for task type, Answerability, target source, evidence source class, temporal category, action outcome, memory type, relationship stance, uncertainty/refusal behavior, archetype, paraphrase template, teacher source, failure signature, and split.

Final bounded-fixture coverage:

- rows: 22;
- approximate tokens: 573;
- missing-dimension warnings: 0;
- contamination issues: 0.

This proves the reporting mechanism; 22 fixtures do not constitute a scale claim or production-corpus sufficiency claim.

### 4.6 Dataset state, review, license, and freeze

`DatasetAssembler` returns `APPROVED` only when every row is D4 accepted, no blocker exists, protected-set contamination is clean, and an affirmative scoped review approval is present. Otherwise the build is `REVIEW_REQUIRED`. `DatasetFreezer` accepts only an approved build and approved license manifest.

The frozen license manifest is deliberately restricted to project-owned fixtures. It does not assert that Nemotron model redistribution or third-party teacher/data terms are resolved.

Freeze behavior:

- content-derived `DatasetId` and `DatasetVersionId`;
- immutable dataset directory created once;
- canonical examples JSONL as source of truth;
- split SFT projections containing only exact messages + `chosenResponse`;
- manifest, coverage, contamination, policy, license, and protected-set artifacts;
- append-only `datasets.jsonl` registration;
- manifest written last as the local commit marker;
- existing identical content returns idempotently;
- changed content produces a different dataset ID;
- an existing identity with different manifest content fails closed.

Final row identity commits to the complete frozen canonical row payload—schema, example ID, family, split, frozen example, provenance, source hashes, normalization fingerprints, ancestry, and contamination metadata—excluding only self-referential row ID/hash fields. The test reads every written JSONL row back and recomputes both its SHA-256 and typed row ID.

Verdict/example timestamps derive from the candidate timestamp, making canonical rows reproducible across processes. Manifest creation/freeze timestamps remain audit timestamps and are reused on idempotent re-entry; they are not part of logical dataset identity.

### 4.7 Trainer-view boundary

Each `train/dev/test/challenge/sft.jsonl` record contains only:

```json
{
  "messages": ["exact production message objects"],
  "chosenResponse": "accepted target"
}
```

Oracle verdicts, answer grading, provenance, family/split rationale, teacher rubric, negative evidence, private metadata, and hidden reasoning do not enter model input or loss text.

### 4.8 Frozen dataset identity

Dataset directory:

`G:\My Drive\Inigmas Games\Orbis Offline Training\datasets\ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86`

Logical dataset SHA-256:

`4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86`

File SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| `manifest.json` | `d25356e02f89e0d3f5be178bcfa7b3a9efba7b6f3776c211132aa87fc3d1df0e` |
| `canonical/examples.jsonl` | `a14d0b455eaa325e318dfbeba96f8ca2f6c9df9b309e2670180da65db25dc060` |
| `coverage.json` | `695d5724f9f5cec89ec0856e073f290c6471a5977b288e8d346ee1fa7f9044e7` |
| `contamination-audit.json` | `c42f27e82ad5cff167a1b249a4b874ab075821f6256d904cb82d9fa76b6056fe` |
| `licenses/manifest.json` | `c7d050f63d5e38fc19b2c0337912cd7ea6e017e63011e2678a718104246ce599` |
| `policies/curation-policy.json` | `c71963eec984cff5d0f42ed71fb305752366c420851956822b457d74ea46ff34` |
| `policies/dataset-policy.json` | `c0281e489ebd9d90afa79593e2b34f014a3f65b25ba16b851558528ccc855756` |

The active dataset registry contains exactly one entry. Two separate JVM/tool invocations returned:

- first: same ID, `created=true`, `idempotent=false`;
- second: same ID, `created=false`, `idempotent=true`.

### 4.9 Pre-acceptance artifact handling

Intermediate freezes created while correcting determinism, fixture shape, split quotas, and final row commitment were not left active. They were moved—recoverably, not deleted—under:

- `quarantine/block2-preacceptance-nondeterministic-freezes`;
- `quarantine/block2-preacceptance-superseded-fixture`;
- `quarantine/block2-preacceptance-split-quota`;
- `quarantine/block2-preacceptance-row-hash`.

Each corresponding pre-acceptance registry snapshot was moved with its dataset. None is referenced by the active dataset registry.

## 5. Files added or changed

### D4 source

- `training/curation/OracleVerdict.java`
- `training/curation/CurationContracts.java`
- `training/curation/DistillationExample.java`
- `training/curation/CurationPrivacyPolicy.java`
- `training/curation/CurationPolicy.java`
- `training/curation/FilterReasonCodes.java`
- `training/curation/DeterministicCurationEngine.java`

### D5 source

- `training/dataset/DatasetContracts.java`
- `training/dataset/DatasetNormalization.java`
- `training/dataset/DatasetPolicy.java`
- `training/dataset/SemanticFamilyAssigner.java`
- `training/dataset/ContaminationChecker.java`
- `training/dataset/DatasetAssembler.java`
- `training/dataset/LicenseManifests.java`
- `training/dataset/DatasetFreezer.java`
- `training/dataset/Block2FixtureCatalog.java`
- `training/cli/Block2Cli.java`

### Reused/extended R125 source

- `training/registry/ArtifactIds.java`: added typed example, semantic-family, dataset-version identities and factories.
- `training/corpus/ProductionInputSnapshot.java`: added independent provider-input hash recomputation/validation.
- `test.ps1`: added the R126 matrix to the deterministic regression chain.

### Tools

- `tools/curate-dataset.ps1`
- `tools/freeze-dataset.ps1`
- `tools/report.ps1`

### Schemas

- `oracle-verdict.schema.json`
- `epistemic-target-snapshot.schema.json`
- `distillation-example.schema.json`
- `curation-privacy-policy.schema.json`
- `canonical-dataset-row.schema.json`
- `protected-set-manifest.schema.json`
- `coverage-report.schema.json`
- `contamination-audit.schema.json`
- `dataset-manifest.schema.json`
- `dataset-policy.schema.json`
- `license-manifest.schema.json`

### Checked-in policies and goldens

- `training/configs/d4-curation-policy.json`
- `training/configs/d5-dataset-policy.json`
- `training/configs/d6-readiness-blockers.json`
- `src/test/resources/training/golden/oracle-verdict.json`
- `src/test/resources/training/golden/privacy-policy.json`
- `src/test/resources/training/golden/dataset-policy.json`

### Tests

- `R126OrbisDistillationBlock2Test.java`

The workspace is not a Git repository, so no commit or dirty-state claim can be produced. Frozen manifests truthfully record `NO_GIT_REPOSITORY`.

## 6. Verification evidence

Commands exercised:

```powershell
.\test.ps1 -SkipLive
.\tools\freeze-dataset.ps1
.\tools\freeze-dataset.ps1
```

R126 result:

```text
D5_MATRIX_PASS cases=30
R126 Orbis Distillation Block 2 D4-D5 tests passed: 583 assertions.
```

Full deterministic regression result:

```text
Gate 1 conversation matrix passed: scenarios=8100 terminalTransitions=8100
soakTurns=100 staleCommits=0 malformedActionExecutions=0
unspokenDeliveredText=0 leakedResources=0
All deterministic Persistent NPC tests passed; live local-model tests skipped.
```

One first full-suite attempt stopped in the legacy mock HTTP reachability assertion. An immediate full rerun passed that assertion and every subsequent gate; the D4/D5 focused suite also passed independently. No source change was made to suppress or bypass the failure.

Existing compiler warnings remain limited to the deprecated Hytale `WorldChunk.getFluidId` call and legacy test use of `sun.misc.Unsafe`.

## 7. Runtime and model invariants

Deployed production artifact before and after this block:

- path: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`;
- SHA-256: `bbae9340409853eb8f5bd1661b7a7f43aefa64e29714e241f9df7ee417e3c830`;
- size: 2,417,989 bytes;
- deployed modification time: 2026-09-02T10:48:57.201-04:00.

The source build creates a larger offline-development JAR because it contains the D4/D5 classes. It was **not deployed**.

- source-build path: `G:\My Drive\Inigmas Games\Hytale Persistent NPCs\dist\ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`;
- source-build SHA-256: `b330e3bd3f10687354397355db68e3d35aa826f9d41ea1f178029c281ce02ff7`;
- source-build size: 2,643,319 bytes.

Active runtime model artifact identity before and after:

- installed GGUF/blob SHA-256: `527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970`;
- configured architecture: `nemotron_h`;
- configured precision: `Q4_K_M`;
- exact upstream BF16 revision: `UNRESOLVED`;
- training base approval: `BLOCKED`.

## 8. Remaining blockers and next-stage decision

The following R125 blockers remain visible and unresolved:

1. The installed Q4_K_M GGUF is not proven to map to an exact upstream BF16 commit.
2. The installed embedded NVIDIA license snapshot and current Nemotron license require human legal/redistribution reconciliation.
3. No automated external teacher source is approved.
4. No real-player production corpus is approved.
5. No real-player redaction/privacy/consent policy is approved.
6. No Python/CUDA training environment is installed or validated.
7. Nemotron-H/Mamba/PEFT target-module, save/reload/disable, and merge compatibility lack the D6 one-batch proof.

Therefore:

- D4: **PASS**.
- D5: **PASS**.
- G1: **PASS for the bounded project-owned fixture dataset only**.
- G0/D6 training readiness: **BLOCKED**.
- Production runtime/model promotion: **not authorized and not attempted**.
- Safe next action: review/resolve G0 lineage and license evidence, then separately authorize a bounded D6 preflight. Do not start D6 automatically.
