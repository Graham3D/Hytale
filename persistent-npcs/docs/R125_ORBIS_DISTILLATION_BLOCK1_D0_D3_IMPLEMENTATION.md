# R125 Orbis Distillation Block 1 — D0–D3 Implementation Report

Date: 2026-09-03  
Scope: D0 audit/feasibility, D1 domain foundation, D2 eligibility and exact-input
corpus export, D3 teacher interface and import-first path.  
Explicitly excluded: D4 curation, D5 split manifests, D6 training preflight,
D7 training, D8 evaluation, D9 packaging, and D10 promotion.

## Executive summary

Block 1 is implemented as an offline-only, fail-closed subsystem. The committed
default mode is `OFF`; the only other defined mode is `CORPUS_AUDIT`. Neither mode
permits model mutation. No gameplay service, plugin bootstrap, player command,
NPC behavior, provider selection, model tag, or deployed JAR was changed.

The D2 capture boundary is production-parity: `ExactInputCaptureProvider` wraps the
provider passed to `OrbisEvaluationHost` and observes the already-rendered
`LlmRequest` at provider dispatch. `ProductionInputSnapshot` stores the exact
`LlmRequest.canonicalMessages()` list used by `OpenAiCompatibleProvider`, along with
the tools, response format, provider settings, route, prompt identity, model
identity, epistemic targets, evidence, answerability, AnswerPlan, and profile
constraints. Evaluation metadata and teacher rubrics remain separate and cannot
alter the captured production messages.

The D3 teacher path is deliberately import-first. One source is approved:
operator-reviewed offline imports. OpenAI-hosted output is registered `REJECTED`
for automated cross-vendor model-training use unless a documented permitted
exception is added later. Unreviewed local open-weight teachers are `UNKNOWN`.
Both `REJECTED` and `UNKNOWN` block execution. Teacher outputs can only be stored as
`PROPOSED_LABEL` or `UNVERIFIED`; there is no `GOLD` state in the D3 contract.

The model/runtime audit is complete, but readiness to train is blocked. The
installed GGUF blob is pinned exactly, while the exact upstream BF16 revision from
which it was produced is unresolved. The installed Ollama artifact also embeds an
older NVIDIA Open Model License text while the current official upstream repository
points to the NVIDIA Nemotron Open Model License. The local Python/CUDA training
stack is not installed. These are downstream D6 blockers, not hidden assumptions.

## Stage status and exit evidence

| Stage | Implementation status | Exit evidence | Downstream readiness |
|---|---|---|---|
| D0 | Complete | Runtime, model, prompt boundary, hardware, software, licensing, and module inventory recorded in versioned JSON | Training remains blocked on exact upstream lineage/license reconciliation and a one-batch compatibility proof |
| D1 | Pass | Typed IDs, canonical JSON, immutable model/prompt identities, append-only registries, safe external root, modes `OFF`/`CORPUS_AUDIT`, JSON Schemas, preflight | No gameplay wiring exists; no model mutation path exists |
| D2 | Pass | Deterministic classifier routes only earliest-boundary provider realization failures to `MODEL_TRAINING_ELIGIBLE`; exact provider-boundary capture and append-only candidate export tested | Candidates remain `ELIGIBLE_UNLABELED`; no trainer view exists |
| D3 | Pass (import-first) | Approved-source gateway, policy snapshot checks, bounded retry/timeout/concurrency, reviewed import, recursive hidden-reasoning rejection/quarantine, duplicate rejection, proposed-label persistence tested | No live external teacher is approved or configured |
| D4+ | Not implemented | Enforced by scope, mode, and absence of code paths | Requires a separate authorized block |

## D0 — runtime, model, legal, and feasibility audit

### Active production provider

The active save configuration selects `NEMOTRON` through the local
OpenAI-compatible endpoint:

- Endpoint: `http://127.0.0.1:11434/v1/chat/completions`
- Model tag: `nemotron-3-nano:4b`
- Ollama: `0.33.2`
- Ollama model ID: `6cc467f05439`
- Installed model blob SHA-256:
  `527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970`
- Architecture: `nemotron_h`
- Quantization: `Q4_K_M`
- Renderer/parser: `nemotron-3-nano`
- Context length: 262,144
- Generation defaults: temperature 0.7, maximum 180 tokens, streaming enabled,
  reasoning `none`
- Provider request timeout: 12,000 ms
- Provider concurrency: 2

The production prompt path is:

1. `ConversationContextBuilder` and `ContractPromptBuilder` compile the grounded
   prompt and epistemic contract.
2. `LlmRequest` carries the immutable messages and turn plan.
3. `LlmRequest.canonicalMessages()` produces the provider-visible message list.
4. `OpenAiCompatibleProvider.OpenAiRequest` serializes that list.
5. Ollama owns the downstream `nemotron-3-nano` renderer/parser and its
   `{{ .Prompt }}` template.

The prompt identity manifest hashes all four Java boundary sources plus the local
Ollama template. Its aggregate SHA-256 is
`25e752561421b14e6fc61b33e1ef78cd8fe11efbb6df3474dad1a77dec9405ba`.

### Model lineage

The most likely upstream repository is the official
[`nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16`](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16).
Repository identity is high confidence because the architecture, parameter count,
context length, and renderer family agree with the installed Ollama artifact.

The repository HEAD observed during this audit was
`dfaf35de3e30f1867dd8dbc38a7fc9fb52d3914f`, last modified 2026-03-20. This is an
audit reference, not a claim that the installed GGUF came from that commit. No
local manifest maps the installed GGUF hash to an exact upstream BF16 commit, so
that edge of the artifact graph is `UNRESOLVED` and future adapter training is
blocked until it is pinned.

The current upstream model is `NemotronHForCausalLM`, approximately 3.97B
parameters, with 42 hybrid layers, 4 attention layers, hidden size 3,136,
intermediate size 12,544, 40 attention heads, and 8 key/value heads. Current custom
model code requires `trust_remote_code=True`, `mamba_ssm`, and `causal_conv1d`.

Observed standard linear module names:

- Attention: `q_proj`, `k_proj`, `v_proj`, `o_proj`
- MLP: `up_proj`, `down_proj`
- Mamba mixer: `in_proj`, `out_proj`
- Output: `lm_head`
- Embedding: `backbone.embeddings`

Possible future D6 experiments are ordered conservatively: attention projections
first, MLP projections second, and Mamba mixer projections only after a one-batch
proof. This is an inventory, not adapter-target approval.

### Licensing and teacher terms

The current official
[`NVIDIA Nemotron Open Model License`](https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-nemotron-open-model-license/)
allows use and modification and sets redistribution obligations, including license
and notice preservation. The installed Ollama artifact contains an older text
identified as the NVIDIA Open Model License Agreement, locally marked modified
2025-10-24. The current official Nemotron terms are marked modified 2025-12-15.
Because the exact upstream-to-GGUF lineage is not pinned and these texts differ,
redistribution/package approval is `NEEDS_REVIEW`.

The reviewed
[`OpenAI Services Agreement`](https://openai.com/policies/services-agreement/)
restricts using output to develop competing AI models except under a permitted
exception. No such exception is recorded for this project, so automated OpenAI
teacher use is `REJECTED` by policy. This is a fail-closed engineering decision,
not legal advice.

Approved D3 path: human/operator-authored or explicitly reviewed offline imports.  
Rejected D3 path: OpenAI-hosted output for automated cross-vendor training.  
Unknown D3 path: any local/remote teacher whose exact model, revision, license, and
allowed use have not been reviewed.

### Hardware and software feasibility

Audited host:

- NVIDIA GeForce RTX 4070 Ti, 12,282 MiB VRAM, compute capability 8.9
- Driver 610.74; CUDA UMD reported 13.3
- AMD Ryzen 9 7900X, 12 cores / 24 logical processors
- 63.15 GiB system RAM
- Windows 11 Home build 26200 x64
- At audit time about 6.9 GiB VRAM was already occupied by interactive applications;
  Hytale and training must not share the GPU during a future run.

Full BF16 fine-tuning is not locally credible: the upstream safetensors artifact is
about 7.95 GB before gradients, optimizer state, activations, and workspace. A
QLoRA-style experiment may fit only with short sequences, micro-batch 1, gradient
accumulation/checkpointing, and confirmed Windows kernel compatibility. The
installed Q4_K_M Ollama blob is about 2.84 GB, but it is an inference artifact and
is not assumed to be a training base.

Current Hugging Face
[`bitsandbytes` documentation](https://huggingface.co/docs/transformers/main/quantization/bitsandbytes)
lists Windows/NVIDIA support and CUDA 11.8–13.0, with NF4/FP4 available on Pascal or
newer hardware. The reported driver capability is not proof that a compatible
PyTorch/bitsandbytes runtime is installed. No Python interpreter, PyTorch,
Transformers, PEFT, TRL, bitsandbytes, CUDA toolkit (`nvcc`), `mamba_ssm`, or
`causal_conv1d` environment is currently installed for this project.

Feasibility classification: `LOCAL_EXPERIMENTAL_REMOTE_RECOMMENDED`. A future D6
one-batch experiment must prove forward pass, backward pass, adapter injection,
checkpoint save/reload, and held-out inference before any real run is authorized.

## D1 — domain foundation

Implemented under `com.inigmasgames.persistentnpcs.training` without production
imports:

- `TrainingMode`: only `OFF` and `CORPUS_AUDIT`; both reject model mutation.
- Strong artifact IDs for candidates, rows, datasets, teacher runs, training runs,
  evaluation runs, model bundles, and promotions.
- `CanonicalJson`: sorted object keys, NFC Unicode normalization, LF normalization,
  compact UTF-8 serialization, SHA-256 IDs.
- Immutable `ModelIdentity` and `PromptTemplateIdentity`.
- `ArtifactRoot`: rejects active-save overlap and path traversal.
- `AppendOnlyJsonlRegistry`: idempotent identical append; rejects identity/content
  collisions.
- Named registries for models, prompt templates, datasets, training/evaluation runs,
  bundles, promotions, and teacher sources.
- JSON Schemas for each D1–D3 cross-language boundary.
- Dry-run/operator tools in `tools/orbis-train`.

The external root was initialized at:

`G:\My Drive\Inigmas Games\Orbis Offline Training`

It is outside the active save. It contains `registry`, `candidates`, `teacher-runs`,
`datasets`, `runs`, `models`, `reports`, and `quarantine`. The registry currently
contains one local production-model identity, one prompt identity, three teacher
source policies, and empty append-only files for future stages. No player/NPC save
data was copied there during Block 1.

## D2 — eligibility and production-parity candidate export

The classifier consumes explicit stage-exit evidence and the existing
`EvaluationContracts.RootCauseDiagnosis` produced by the earliest-boundary system.
Its ordering is fail-closed:

1. Missing/incomplete artifacts → `NEEDS_REVIEW`.
2. Oracle or data failure → `ORACLE_OR_DATA_REPAIR_REQUIRED`.
3. Unknown pre-provider stage exit → `NEEDS_REVIEW`.
4. Failed routing, retrieval, answerability, AnswerPlan, turn plan, or context
   rendering → `ORBIS_SOURCE_REPAIR_REQUIRED`.
5. Runtime-only issues without connected evidence → `CONNECTED_VALIDATION_REQUIRED`.
6. Lifecycle/resource/cleanup issues → `NOT_TRAINABLE` after validation.
7. No diagnosed defect → `NOT_TRAINABLE`.
8. Only an earliest `PROVIDER` / `PROVIDER_REALIZATION` failure with all upstream
   exits proven green → `MODEL_TRAINING_ELIGIBLE`.

The exact-input capture wrapper can be supplied to `OrbisEvaluationHost` in an
offline campaign. It captures at `generateResponse`/`stream`, immediately before
delegating to the production provider. This makes Orbis evaluation reports,
Conversation Matrix cases, Sentinel-derived scenarios, and manually promoted
fixtures usable as provenance while preventing their oracle/grading fields from
changing model input.

`DistillationCorpusCandidate` stores original model output and firewall outcome
separately. An eligible candidate is still `ELIGIBLE_UNLABELED`; it is not a
training example. `CorpusJsonlExporter` requires `CORPUS_AUDIT` and writes through
the immutable candidate registry. `corpus-audit.ps1` rejects candidate rows that
already contain a target output.

## D3 — teacher interface

`TeacherProvider` exposes the required provider-neutral capabilities:

- `generateTarget`
- `critiqueStudentOutput`
- `rankPreference`
- `healthCheck`

`TeacherGateway` verifies exact teacher source/policy identity and terms-snapshot
hash before execution. It enforces capability checks, a semaphore concurrency
bound, a 1–2 attempt ceiling, timeout/cancellation, request/response identity, and
content-hashed run manifests.

Each teacher run manifest records:

- exact input snapshot hash
- teacher source/provider/model/revision identity
- legal-policy snapshot
- task and rubric config
- retry, timeout, and concurrency config
- determinism metadata
- response hash, attempt count, elapsed time, and completion time

The response schema has no chain-of-thought field. `ReviewedTeacherImport`
recursively rejects `reasoning`, `chainOfThought`, `chain_of_thought`,
`hiddenReasoning`, and `hidden_reasoning`; malformed, unknown-candidate, and
duplicate inputs are quarantined by content hash. Accepted conclusions remain
`PROPOSED_LABEL`. `TeacherRunStore` is append-only and requires `CORPUS_AUDIT`.

## Files added or changed

### Java production source (offline package only)

- `training/TrainingMode.java`
- `training/registry/{ArtifactIds,CanonicalJson,ModelIdentity,PromptTemplateIdentity,ArtifactRoot,AppendOnlyJsonlRegistry,TrainingArtifactRegistries}.java`
- `training/candidate/{TrainingEligibility,EligibilityEvidence,TrainingEligibilityClassifier}.java`
- `training/corpus/{ProductionInputSnapshot,ExactInputCaptureProvider,DistillationCorpusCandidate,DistillationCorpusBuilder,CorpusJsonlExporter}.java`
- `training/teacher/{TeacherSourcePolicy,TeacherPolicyRegistry,TeacherContracts,TeacherProvider,TeacherGateway,ReviewedTeacherImport,TeacherRunStore}.java`
- `training/cli/Block1Bootstrap.java`

### Tests and test runner

- Added `R125OrbisDistillationBlock1Test.java`.
- Added its deterministic invocation to `test.ps1`.

### Config, schemas, and tools

- `training/configs/block1.json`
- `training/configs/d0-runtime-audit.json`
- `training/configs/nemotron-module-inventory.json`
- `training/configs/production-model-identity.json`
- `training/configs/production-prompt-identity.json`
- `training/configs/teacher-source-policies.json`
- Nine JSON Schemas under `training/schemas`.
- `tools/orbis-train/preflight.ps1`
- `tools/orbis-train/corpus-audit.ps1`
- `tools/orbis-train/teacher-import-audit.ps1`
- `tools/orbis-train/README.md`

No NPC Profile, inventory, mesh preview, voice, equipment, persistence, Orbis
production coordinator, or provider implementation file was edited.

## Validation evidence

Commands executed:

```powershell
.\test.ps1 -SkipLive
.\build.ps1
# targeted recompile/run after final Block-1-only additions
java --add-modules jdk.httpserver -ea -classpath <test;main;server> `
  com.inigmasgames.persistentnpcs.training.R125OrbisDistillationBlock1Test
.\tools\orbis-train\preflight.ps1
.\tools\orbis-train\corpus-audit.ps1 -CandidateJsonl <generated-test-candidate.jsonl>
```

Results:

- Full deterministic Persistent NPC suite: PASS.
- Conversation Matrix: 8,100 scenarios / 8,100 terminal transitions, 100 soak turns,
  0 stale commits, 0 malformed actions, 0 unspoken delivery, 0 leaked resources.
- R125 D0–D3 gate: PASS.
- Final source build: PASS (one pre-existing Hytale deprecation warning).
- Final targeted R125 gate after all source changes: PASS.
- Preflight: PASS and idempotent (`teacherPoliciesAppended=3`, then `0`).
- Corpus audit against the generated eligible fixture: 1 row,
  `MODEL_TRAINING_ELIGIBLE=1`.

R125 test coverage includes mode inertia, save-root separation, traversal rejection,
canonicalization, stable IDs, registry idempotency/collision rejection, every major
eligibility route, exact provider-boundary capture, rubric/input separation,
candidate export, approved/rejected teacher policies, manifest trust state,
append-only teacher storage, unknown candidate rejection, duplicate rejection,
hidden-reasoning quarantine, schema presence, and the absence of production imports.

## Artifact and configuration hashes

Deployed production artifacts were not written:

| Artifact | SHA-256 | Last write (UTC) |
|---|---|---|
| Deployed R124 JAR | `bbae9340409853eb8f5bd1661b7a7f43aefa64e29714e241f9df7ee417e3c830` | 2026-09-02T14:48:57Z |
| Active `config.json` | `1055c3b37778671ab6fd6c83b6ba895460e9241d2a6f4ab26cc3fb8b1a483d5e` | 2026-08-25T22:31:26Z |
| Active `llm-providers.json` | `f60c7ff5dcf3f6bfdcae1801d4a31bedfb68e3ff1fa056723d9b0eddc1ff828b` | 2026-09-03T00:56:01Z |

The final local source build, not deployed, is:

`dist/ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`  
SHA-256: `2b4d1116d0c7ba75b39ceffbf9ea990642b3756fe479b71f81ae0a03367c38f9`  
Size: 2,495,640 bytes.

It differs from the deployed R124 JAR only because the build includes the new
offline Java package. There is no runtime import or activation path, and it was not
copied to the save.

Important committed config hashes:

| File | SHA-256 |
|---|---|
| `block1.json` | `97f094424ece371591d2114438c42915a87010bc03a334caeabf81a3bcca325a` |
| `d0-runtime-audit.json` | `8a55060557175c5139e2cd3432507da0da066cd3c9cdf60279c644fbbf199a90` |
| `nemotron-module-inventory.json` | `01f7820c28298e401487610e6db8f27204f7ced3514ecf325e5fc49df200b912` |
| `production-model-identity.json` | `44227c8541b6b39ad9458278c4eeb6604e7e635074ec936a7ed2a6caf5c9ec2b` |
| `production-prompt-identity.json` | `c771867533ae3f41966f8355e46bfad5be48d85d906177ad6c2923f30b99d357` |
| `teacher-source-policies.json` | `91788a7482745bc3dd2224c24fb835ee4f8f26e88830e326aec5b823c066b6b5` |

External registry hashes after idempotent bootstrap:

- `models.jsonl`: `5777f57840822a68a34a8836cb083266c35cb4b876d5c73e00c9032671418b6c`
- `prompt-templates.jsonl`: `bf77909c5ae1851b97e28df6e4ac82c015cd517ac274737fa6753ecdaabc3e86`
- `teacher-sources.jsonl`: `3a47a759cb4b268cb66752313346709cea05a0194e3f7b366f31c1c0a671295b`
- Empty future-stage registries use the standard empty SHA-256
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

## Remaining blockers and risks

1. The installed Q4_K_M GGUF has no proven mapping to an exact upstream BF16 commit.
2. The installed embedded NVIDIA license text and current official Nemotron license
   need a redistribution/derivative-work reconciliation.
3. No approved automated teacher exists. Only operator-reviewed import is enabled.
4. No real corpus candidate has been exported from production player data; Block 1
   validates the exact capture/export mechanism with deterministic fixtures.
5. Redaction/privacy policy for any future real-player corpus must be approved before
   export.
6. No Python/CUDA training environment exists.
7. The custom Nemotron-H architecture, Mamba kernels, Windows bitsandbytes path, and
   LoRA target names require D6 one-batch proof.
8. Dataset curation, near-duplicate grouping, leakage-safe splits, training,
   evaluation, packaging, and promotion remain absent by design.

## Exact next commands

Safe Block-1 verification:

```powershell
Set-Location 'G:\My Drive\Inigmas Games\Hytale Persistent NPCs'
.\tools\orbis-train\preflight.ps1
.\test.ps1 -SkipLive
```

To audit a corpus produced later by an explicitly configured offline Orbis run:

```powershell
.\tools\orbis-train\corpus-audit.ps1 `
  -CandidateJsonl 'G:\My Drive\Inigmas Games\Orbis Offline Training\candidates\distillation-candidates.jsonl'
```

Do not set up a trainer or begin D4+ from this report. The next implementation block
should first resolve or explicitly accept the lineage/license blockers, approve the
real-data redaction policy, and then implement D4 curation and D5 leakage-safe split
manifests under separate authorization.
