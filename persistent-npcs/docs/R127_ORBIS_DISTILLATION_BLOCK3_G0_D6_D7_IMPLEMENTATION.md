# R127 Orbis Distillation Block 3 — G0, D6, and D7 Implementation Report

Date: 2026-09-03 (America/New_York)
Repository baseline: `981f9aafefb6e974626a473020b4152251da9f1a`
Authorized scope: G0 resolution, D6 PEFT/LoRA/QLoRA preflight, and D7 only after G2.
Explicitly excluded: D8+, packaging, quantization, candidate import/deployment, shadow/canary/connected validation, and promotion.

## Executive decision

Block 3 stopped at the required fail-closed boundary:

| Gate/stage | Result | Meaning |
|---|---|---|
| G0 | **PASS** | A new exact official BF16 candidate lineage is pinned and the current NVIDIA terms permit this local, non-distributed experiment. |
| D6 | **FAIL — REMOTE_REQUIRED** | The required pinned Mamba/CausalConv kernels have no official Windows wheels, WSL/Linux is absent, and the local one-batch/round-trip proof cannot begin safely. |
| G2 | **FAIL** | No model load, forward pass, backward pass, optimizer update, adapter save/reload, disable, or merge proof occurred. |
| D7 SFT-0 | **NOT AUTHORIZED** | G2 failed; the only frozen dataset also has 22 rows, below the 32-row SFT-0 minimum. |
| D7 SFT-1 | **BLOCKED_INSUFFICIENT_APPROVED_DATA** | No legitimate 1K–5K approved corpus exists and no automated teacher is approved. |

No weight was downloaded, loaded, or modified. No gradient update occurred. Production R124 and the active Ollama GGUF remained byte-identical.

## G0 — base, lineage, and license

### Selected training base

- Repository: `nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16`
- Immutable revision: `dfaf35de3e30f1867dd8dbc38a7fc9fb52d3914f`
- Revision last modified: `2026-03-20T20:50:41Z`
- Architecture: `NemotronHForCausalLM` / `nemotron_h`
- Precision: BF16 Safetensors
- Weight: `model.safetensors`, 7,947,142,640 bytes
- Weight SHA-256: `55d4e2519456c4a9bddf596b0748d630e3b2ce6ff6f4c2b7ed3e07e2b00dad42`
- Parameters: approximately 3.97B
- Custom code: pinned to the same repository revision; `trust_remote_code=true`
- Model repository and revision were rechecked on 2026-09-03 and remained unchanged.

The local snapshot identity audit passed for `config.json`, `generation_config.json`, `tokenizer.json`, `tokenizer_config.json`, `special_tokens_map.json`, `chat_template.jinja`, `configuration_nemotron_h.py`, `modeling_nemotron_h.py`, `nano_v3_reasoning_parser.py`, and `README.md`. Exact hashes remain in `training/configs/g0-training-base.json` and the D6 run report.

### Production GGUF relationship

Classification: **COMPATIBLE_BUT_UNPROVEN**.

The active Q4_K_M artifact agrees in architecture, renderer family, parameter scale, and tokenizer family, but there is still no immutable conversion manifest mapping its SHA-256 to the pinned BF16 revision. The Technical Design permits a separately pinned candidate lineage while retaining the current GGUF solely as the production evaluation control. Therefore the candidate training-base approval is:

`TRAINING_BASE_APPROVED_SEPARATE_LINEAGE`

The report does not claim the BF16 and production GGUF are identical artifacts or proven descendants.

### License reconciliation

The selected revision is labeled `nvidia-nemotron-open-model-license`. The official NVIDIA terms were rechecked at:

- https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-nemotron-open-model-license/
- Last modified: 2025-12-15

Engineering classifications:

- `APPROVED_FOR_LOCAL_TRAINING`
- `APPROVED_FOR_DERIVATIVE_ADAPTER`
- `APPROVED_FOR_MERGE`
- `REDISTRIBUTION_NEEDS_REVIEW`

The official terms grant reproduction and derivative-work rights, including commercial use, subject to their conditions. Block 3 is local and non-distributed. Any later distribution still requires a license/NOTICE review. The exact required NOTICE text was corrected to:

`Licensed by NVIDIA Corporation under the NVIDIA Nemotron Model License.`

This is an engineering compliance gate, not legal advice.

### Dataset license state

The R126 license manifest was revalidated:

- Dataset: `ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86`
- State: `FROZEN`
- License: `PROJECT_OWNED_FIXTURE`
- Training approved: true, only for the bounded project-owned fixture scope
- TRAIN/TEST/CHALLENGE overlap: 0
- Real-player rows: 0
- Teacher-generated rows: 0

G0 result: **PASS for local D6/D7 only**.

## D6 — environment and PEFT preflight

### Isolated environment

The pre-existing paused checkpoint contained:

- Python `3.11.13`
- uv `0.12.9`
- isolated environment: `G:\My Drive\Inigmas Games\Orbis Offline Training\envs\block3-py311`
- no installed Python training packages

`training/configs/d6-environment-lock.json` now pins the intended reference stack, including Transformers 4.48.3 (the model-card-tested version), PEFT, TRL, Accelerate, bitsandbytes, Safetensors, Tokenizers, Mamba SSM, and causal-conv1d. No package installation occurred after the fatal platform gate was established.

### Hardware baseline

- GPU: NVIDIA GeForce RTX 4070 Ti, 12,282 MiB VRAM
- Driver: 610.74
- At inspection: 2,421 MiB used, 9,574 MiB free, 40°C, approximately 26.12 W
- Hytale process: not running
- Ollama process: running
- WSL: not installed
- `nvcc`: not installed
- Disk headroom: greater than 795 GiB on the offline-root volume

Ollama was not terminated automatically. Its presence is recorded as a resource-isolation blocker, as required.

### Fatal compatibility finding

The pinned custom `modeling_nemotron_h.py` imports `mamba_ssm.ops.triton` and raises when its gated RMSNorm kernel cannot be imported. The pinned package indexes show:

| Package | Pinned version | Published files | Official Windows wheels |
|---|---:|---:|---:|
| `mamba-ssm` | 2.2.4 | one source distribution | 0 |
| `causal-conv1d` | 1.5.0.post8 | one source distribution | 0 |

The current machine has neither an approved Linux/WSL CUDA environment nor the compiler/kernel toolchain required to establish an equivalent reproducible build. Installing unrelated Python packages or downloading 7.95 GB of weights cannot resolve this first failing boundary, so the harness stopped before both actions.

### Module inventory and strategies

Only the pinned-code static inventory was permitted because the model could not be loaded:

- Attention candidates: `q_proj`, `k_proj`, `v_proj`, `o_proj`
- MLP candidates: `up_proj`, `down_proj`
- Mamba projections: `in_proj`, `out_proj` — research-only
- Selected strategy: none

No trainable-parameter count is claimed. ATTENTION_ONLY, MLP_ONLY, and ATTENTION_PLUS_MLP remain candidates for a supported Linux CUDA preflight. MAMBA_PROJECTIONS and ALL_LINEAR remain unapproved.

### Measurements

- Zero-adapter equivalence: not run
- 1024-token one-batch test: not run
- 2048-token test: skipped because D6 failed before model load
- Loss/gradient norm/adapter delta: not produced
- Base-gradient and base-mutation checks: not reached
- Save/reload/disable/merge: not run
- Local hardware classification: **REMOTE_REQUIRED**
- G2: **FAIL**

Primary immutable D6 evidence:

- Run: `preflight-20260904T002822Z-7b4f4f954af6`
- Directory: `G:\My Drive\Inigmas Games\Orbis Offline Training\runs\preflight-20260904T002822Z-7b4f4f954af6`
- `environment.json`: `b0c2f9a9717c97dd65f4e81a0bd1317d60ff841800b8bfbb7614baca4489cf22`
- `peft-preflight-report.json`: `3de309f9a7af50eaa0217e3a1a9961ec4b4a5b02134a3e3c562b05d8bdc6e02f`
- `run-manifest.json`: `cc8efa0b4ad0d7224fbeecd4a0d632fc1a17e0bb63d39b9a711d3ea4e794f61b`
- Registered in `registry/training-runs.jsonl`

## D7 — SFT readiness and enforced stop

The SFT launcher was invoked only through its readiness gate. It returned:

`G2_NOT_PASSED: SFT-0 is not authorized without G2 PASS`

No smoke dataset was generated because the authorized sequence places D7 after G2. The existing immutable dataset remains unchanged at 22 canonical rows (16 TRAIN, 2 DEV, 2 TEST, 2 CHALLENGE plus protected CONNECTED/CANARY references). It is also independently below the 32-row SFT-0 minimum.

Consequently there is no SFT configuration execution, training curve, checkpoint, adapter hash, base/candidate DEV comparison, memorization result, or CandidateSelectionRecord. None may be fabricated from an unexecuted stage.

- SFT-0: **NOT_AUTHORIZED_G2_FAILED**
- SFT-1: **BLOCKED_INSUFFICIENT_APPROVED_DATA**
- Candidate state: none
- Independent Gate A audit: **not safe to begin**, because D6/G2 and D7 are incomplete

## Implementation

Added or changed within Block 3:

- `training/configs/g0-license-decision.json`
- `training/configs/d6-readiness-blockers.json`
- `training/configs/d6-environment-lock.json`
- `training/schemas/peft-preflight-report.schema.json`
- `training/schemas/training-run-manifest.schema.json`
- `tools/orbis-train/python/block3_gate.py`
- `tools/orbis-train/python/test_block3_gate.py`
- `tools/orbis-train/setup-training-env.ps1`
- `tools/orbis-train/peft-preflight.ps1`
- `tools/orbis-train/train-smoke.ps1`
- `tools/orbis-train/eval-reference.ps1`
- `tools/orbis-train/report-training.ps1`
- `tools/orbis-train/test-block3.ps1`
- `tools/orbis-train/README.md`
- this report

The gate validates base/tokenizer/config hashes, exact revision, license rights and NOTICE wording, dataset identity/license/protected splits, supported target strategies, base-freeze/gradient/mutation measurements, finite adapter updates, adapter round-trip evidence, and SFT readiness. It writes immutable run evidence and appends the primary preflight to the training-run registry.

## Tests and commands

Commands run:

```powershell
.\tools\orbis-train\test-block3.ps1
.\tools\orbis-train\setup-training-env.ps1
.\tools\orbis-train\peft-preflight.ps1
.\tools\orbis-train\report-training.ps1 -RunDirectory <run>
.\tools\orbis-train\train-smoke.ps1 -PreflightReport <report>
.\test.ps1 -SkipLive
```

Results:

- Block 3 gate tests: 15/15 pass
- R125 D0–D3 tests: pass
- R126 D4–D5 tests: 583 assertions pass
- Conversation Matrix: 8,100/8,100 terminal transitions
- Soak: 100 turns
- Stale commits: 0
- Malformed actions: 0
- Unspoken delivered text: 0
- Leaked resources: 0
- Full deterministic suite: pass

The Block 3 negative gates cover wrong identity/tokenizer hashes, revision mismatch, unresolved license/NOTICE, unapproved data license, protected-set overlap, unsupported targets, unexpected base gradients/mutation, non-finite values, absent adapter delta, incomplete save/reload/disable/merge proof, corrupted adapters, G2 refusal, and the 32-row smoke minimum.

## Production invariants

Before and after Block 3:

- Deployed R124 JAR SHA-256: `bbae9340409853eb8f5bd1661b7a7f43aefa64e29714e241f9df7ee417e3c830`
- Active GGUF/Ollama blob SHA-256: `527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970`
- Active Ollama model tag: unchanged (`nemotron-3-nano:4b`)
- Production provider configuration: unchanged
- NPC profiles, memories, beliefs, inventories, worlds, and saves: unchanged
- Candidate deployment/promotion: not attempted

## Required next boundary

Block 3 cannot continue on the current native-Windows backend. A separately authorized continuation would need a reproducible Linux CUDA environment—locally through a reviewed WSL2 installation or on a 24 GB+ Linux GPU host—then repeat D6 from the exact pinned revision. Only a complete one-batch adapter round trip can change G2 to PASS. D7 remains forbidden until that happens.

This report stops within Block 3 and does not authorize D8, D9, D10, packaging, quantization, candidate import, deployment, or promotion.
