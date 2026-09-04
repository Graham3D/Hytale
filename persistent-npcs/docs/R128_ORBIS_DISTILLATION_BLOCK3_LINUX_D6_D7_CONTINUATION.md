# R128 Orbis Distillation Block 3 — Linux D6/D7 Continuation

Date: 2026-09-03 (America/New_York)
Authorized repository baseline: `eee1f2f2b05fbbd532d2df7a542c329ed1cec2fb`
Implementation checkpoint: `56f2206` (`Prepare Block 3 Linux backend checkpoint`)
Scope: Linux CUDA environment decision, D6/G2 retry only when supported, and D7 SFT-0 only after G2.

## Executive decision

The continuation stopped at the required operator boundary:

| Gate/stage | Result | Meaning |
|---|---|---|
| Repository safety | **PASS** | `main`, clean starting tree, and local HEAD matched `origin/main` at the authorized baseline. |
| Non-Drive offline root | **PASS** | A minimal verified checkpoint exists at `C:\HytaleTraining\Orbis`; no bulk Drive copy occurred. |
| Linux CUDA backend | **HOST_SETUP_REQUIRED** | `wsl.exe --status` returned exit code 50 because WSL is not installed. Enabling WSL2/Virtual Machine Platform and installing Ubuntu requires an elevated operator action and may require a restart. |
| D6/G2 retry | **NOT RUN — G2 remains FAIL** | The supported Linux architecture stack could not be installed or imported before the host setup boundary. No model weight was downloaded. |
| D7 SFT-0 | **SFT0_NOT_RUN_G2_FAILED** | G2 did not pass. The frozen dataset also remains below the minimum with only 16 TRAIN rows. |
| D7 SFT-1 | **BLOCKED_INSUFFICIENT_APPROVED_DATA** | No approved 1K–5K corpus exists. |
| Gate A | **BLOCKED** | D6/G2 and D7 are incomplete, and the required independent evidence is not complete. |

No OS feature, WSL distribution, Python environment, CUDA package, model weight, adapter, checkpoint, or remote compute resource was installed or created. Production was not modified.

## Host and backend

### Observed host

- Windows build: `26200`, x86-64
- Firmware virtualization: enabled
- Windows hypervisor: present
- GPU: NVIDIA GeForce RTX 4070 Ti
- Driver: `610.74`
- VRAM: 12,282 MiB
- Host probe sample: 2,501 MiB used, 9,494 MiB free, 40°C, 28.36 W
- Hytale processes: not running
- Ollama processes: running and deliberately left untouched
- WSL status: not installed, exit code `50`
- Required distribution: Ubuntu 24.04 under WSL2
- Selected backend status: `HOST_SETUP_REQUIRED`

The read-only host probe is implemented by `tools/orbis-train/linux-backend-preflight.ps1`. Its evidence is:

- `C:\HytaleTraining\Orbis\evidence\r128\host-backend-preflight.json`
- SHA-256: `9bfb7054381f946affb0fc7f279d66e342df619afe91c91a64c00b446a292ccd`

### Required operator actions

The following steps must be performed manually before this Block 3 continuation can resume:

1. Open PowerShell as Administrator.
2. Run `wsl.exe --install -d Ubuntu-24.04`.
3. Restart Windows if requested.
4. Launch Ubuntu 24.04 once and create the Linux user/password.
5. Run `wsl.exe --update` from Windows.
6. Confirm Ubuntu uses WSL2 with `wsl.exe --list --verbose`.
7. Inside Ubuntu, run `nvidia-smi` and confirm the RTX 4070 Ti is visible.
8. Stop and return to this harness for dependency preflight. Do not install a Linux NVIDIA display driver inside WSL.

If the named distribution is unavailable, first run `wsl.exe --list --online` and select the supported Ubuntu 24.04 entry. Do not select a remote/cloud backend automatically.

Microsoft documents that `wsl --install` must be run from an administrator terminal and may require a restart. NVIDIA documents that CUDA under WSL uses the Windows NVIDIA driver and that a Linux display driver must not be installed inside WSL:

- https://learn.microsoft.com/windows/wsl/install
- https://docs.nvidia.com/cuda/wsl-user-guide/

## Offline root

Active model-training artifacts now default to:

`C:\HytaleTraining\Orbis`

The tracked Block 3 configuration and launchers no longer default to Google Drive. The local bootstrap is idempotent and refuses to overwrite an existing file whose SHA-256 differs from its source.

Bootstrap evidence:

- Manifest: `C:\HytaleTraining\Orbis\bootstrap-manifest.json`
- Manifest SHA-256: `a3fe876a9531867ee647f1e1469c12a4fb45229c1300a8385b8ab7c081799f60`
- Verified payload files: 27
- Verified payload bytes: 941,547
- Total checkpoint after bootstrap and R128 evidence: 29 files / 949,668 bytes

Imported or reconstructed:

- frozen dataset `ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86` from the Git repository;
- dataset registry identity from the Git repository;
- G0 base/license and D6 configuration records;
- the R127 implementation report;
- the three verified R127 preflight JSON artifacts;
- the pinned Hugging Face revision response and NVIDIA license/Trustworthy AI snapshots.

Explicitly excluded:

- old Python environments;
- uv/PyTorch/package caches;
- downloaded toolchains and CUDA packages;
- model weights and the old model/tokenizer snapshot;
- obsolete or temporary runs;
- trainer scratch, adapters, and checkpoints.

No 6+ GB directory was copied. Google Drive was read only for the seven small R127 evidence/provenance files that were not already reconstructed from Git.

## D6 and G2

The exact base remains pinned:

- repository: `nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16`
- revision: `dfaf35de3e30f1867dd8dbc38a7fc9fb52d3914f`
- weight: `model.safetensors`
- expected bytes: 7,947,142,640
- expected SHA-256: `55d4e2519456c4a9bddf596b0748d630e3b2ce6ff6f4c2b7ed3e07e2b00dad42`

Because the Linux backend is absent, the required dependency-first proof could not begin. The model weight was not downloaded merely to rediscover this blocker.

Consequently:

- Linux distribution/kernel/WSL versions: not available
- Linux Python/PyTorch/Transformers/PEFT/bitsandbytes/Triton/Mamba/CausalConv versions: not installed
- compiler/toolchain lock: not produced
- real model load: not run
- runtime module inventory: not produced
- adapter strategies tested: none
- selected strategy: none
- trainable parameters: not measured
- zero-adapter equivalence: not run
- tokenizer/template runtime parity: not run
- 1024-token one-batch test: not run
- 2048-token resource probe: not run
- loss/gradient/adapter delta: not produced
- VRAM/RAM/timing measurements: not produced
- base-freeze proof: not run
- save/reload/disable proof: not run
- merge-copy proof: not run
- G2: **FAIL**

The previous static candidate names remain research inputs only. No runtime claim is made for `q_proj`, `k_proj`, `v_proj`, `o_proj`, `up_proj`, `down_proj`, `in_proj`, or `out_proj` until the exact model is loaded on the supported backend. Mamba projections and all-linear targeting remain unapproved.

## D7

No new dataset version was created and no row was generated, duplicated, relabeled, or moved between splits.

Current immutable dataset:

- dataset ID: `ds_4eb80ca1033afcefee8bc0344fcb76cb4ef7d247f5deaa6d629f9ce62af43b86`
- total canonical rows: 22
- TRAIN: 16
- DEV: 2
- TEST: 2
- CHALLENGE: 2
- CONNECTED/CANARY: preserved protected references
- new training rows: 0

SFT-0 requires G2 PASS and at least 32 legitimate TRAIN rows. Neither condition is met. Therefore there is no SFT-0 configuration execution, curve, checkpoint, adapter hash, DEV comparison, or memorization/collapse result.

D7 status: **SFT0_NOT_RUN_G2_FAILED**.

SFT-1 remains **BLOCKED_INSUFFICIENT_APPROVED_DATA** and was not attempted.

## Production invariants

Before and after this continuation:

- Deployed R124 JAR SHA-256: `bbae9340409853eb8f5bd1661b7a7f43aefa64e29714e241f9df7ee417e3c830`
- Active Ollama/GGUF SHA-256: `527db2cf6c705d8fabb95693d038d9c06b4a2b0b8b0a4bbdbd01212d37242970`
- Hytale runtime and saves: unchanged
- NPC profiles, memories, beliefs, inventories, and worlds: unchanged
- production provider/model configuration: unchanged
- model deployment, packaging, quantization, shadow, canary, connected validation, and promotion: not attempted

The deterministic suite built the existing R124 source artifact in the ignored `dist` directory as part of regression validation. It did not deploy it.

## Regression validation

Commands run:

```powershell
.\tools\orbis-train\initialize-local-offline-root.ps1
.\tools\orbis-train\linux-backend-preflight.ps1 -EvidencePath C:\HytaleTraining\Orbis\evidence\r128\host-backend-preflight.json
.\tools\orbis-train\test-block3.ps1 -PythonPath <existing pinned Python 3.11.13>
.\test.ps1 -SkipLive
```

Results:

- R127 Block 3 gate tests: 15/15 pass
- R125 D0–D3: pass
- R126 D4–D5: 583 assertions pass
- Conversation Matrix: 8,100/8,100 terminal transitions
- Soak: 100 turns
- Stale commits: 0
- Malformed actions: 0
- Unspoken delivered text: 0
- Leaked resources: 0
- Full deterministic suite: pass
- Live local-model tests: deliberately skipped

Only pre-existing Java deprecation/Unsafe warnings were emitted.

## Git

Implementation checkpoint pushed to `origin/main`:

- commit: `56f2206` (`Prepare Block 3 Linux backend checkpoint`)
- baseline parent: `eee1f2f2b05fbbd532d2df7a542c329ed1cec2fb`
- remote push: verified

Tracked changes include:

- the non-Drive Block 3 root configuration;
- the Linux backend decision record;
- the read-only WSL/GPU/process host probe;
- the minimal hash-verifying local-root bootstrap;
- non-Drive defaults in the Block 3 PowerShell launchers;
- the updated offline-tooling documentation;
- this R128 report.

The commit containing this self-referential report is recorded in the final operator handoff and repository history.

## Gate A

Gate A is **BLOCKED**, not READY.

Remaining prerequisites include:

- install and validate WSL2/Ubuntu 24.04 CUDA passthrough;
- prove exact pinned Linux dependency installation/imports;
- download and hash the exact BF16 weight only after dependencies pass;
- complete the real one-batch adapter update and complete round trip;
- change G2 from FAIL to PASS with reproducible evidence;
- create a new immutable dataset with at least 32 legitimate TRAIN rows without using protected sets or unauthorized labels;
- run and evaluate SFT-0;
- complete the independent D0–D7 audit, including at least 50 reviewed exports and a measured pilot time/cost estimate.

## Stop boundary

This checkpoint stops at `HOST_SETUP_REQUIRED`. It does not authorize or begin D7 while G2 is failed, and it does not implement or run D8, D9, D10, packaging, GGUF conversion, quantization, shadow, canary, connected deployment, promotion, or D12.
