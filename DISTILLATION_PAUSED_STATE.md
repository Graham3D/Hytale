# Distillation Paused State

Date: 2026-09-03 (America/New_York)  
Repository: `Graham3D/Hytale`  
Branch: `main`  
Current commit at pause: `5ea4c424bb9e754c1a081e31dc9176b2d8674eaf`

## Stop point

Orbis Distillation Block 3 is paused at the Linux training-host prerequisite, before D6 model loading or any gradient step. No model weights, adapters, checkpoints, or production artifacts were created by the paused continuation.

## Gate state

| Gate or stage | State |
|---|---|
| D0–D3 harness and provenance | **PASS** |
| D4–D5 curation and split controls | **PASS** for the frozen fixture dataset |
| G0 base lineage/license | **PASS** for the authorized local D6/D7 experiment only |
| Linux CUDA backend | **HOST_SETUP_REQUIRED** |
| D6 / G2 one-batch PEFT proof | **FAIL / NOT RUN** |
| D7 SFT-0 | **BLOCKED** by G2 and insufficient TRAIN rows (16; minimum 32) |
| D7 SFT-1 | **BLOCKED** by insufficient approved data |
| Gate A | **BLOCKED** |

The SteamOS RX 9070 XT ROCm route was audited and rejected as a clean reproducible continuation: SteamOS is not an AMD-supported ROCm host, and the exact environment lock is CUDA-specific (`torch==2.6.0+cu124`, `linux-x86_64-cuda`). No SteamOS or system packages were changed.

## Required next prerequisite

Before resumption, an operator must provide and explicitly authorize a supported, reproducible Linux x86-64 NVIDIA CUDA environment that satisfies the pinned D6 lock. The recorded candidate is Ubuntu 24.04 under WSL2 with verified RTX 4070 Ti CUDA passthrough. D6 must then restart from dependency verification and pass the real one-batch adapter save/reload/disable round trip before G2 can become PASS or D7 can begin.

## Authorization boundary

No further distillation work is authorized until the user explicitly resumes it. This includes environment installation, model download, D6 execution, dataset expansion, D7 training, packaging, conversion, quantization, evaluation, deployment, or promotion. The distillation subsystem must otherwise remain unchanged.
