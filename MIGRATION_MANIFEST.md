# Google Drive to GitHub migration manifest

Migration date: 2026-09-03

Source folders (left intact in Google Drive during migration):

- `Inigmas Games/Hytale Taverns`
- `Inigmas Games/Hytale Persistent NPCs`
- `Inigmas Games/Orbis Offline Training`

The three source folders contained 13,040,040,357 bytes across 171,146 files. GitHub is the authoritative home for durable source, authored assets, documentation, and reusable development files. A separate private archive holds the small subset of unique runtime, evaluation, provenance, and rollback material that does not belong in Git.

## Included in GitHub

- Application and plugin source code
- Hytale UI/resource definitions and authored mod assets
- Tests, build/install scripts, and development utilities
- Architecture notes and technical-design documents (`.md` and `.docx`)
- Training configuration, registry, candidate, dataset, report, export, and quarantine records
- Project README and license files
- Native Orbis LLM sidecar C++ source under `persistent-npcs/native/orbisllm/`
- Sanitized reusable configuration examples:
  - `hytale-taverns/config/server-config.example.json`
  - `persistent-npcs/config/runtime-config.example.json`
- The unique Google Drive document formerly titled `Untitled document`, exported as `persistent-npcs/docs/MARA_ORBIS_SPOKEN_VALIDATION_SCRIPT.md`

## Preserved outside Google Drive and GitHub

The private archive `C:\HytaleArchive\Pre-Git-Migration-2026-09-03\` contains:

- Non-default Mara/runtime profile state from `mods/InigmasGames_PersistentNPCs`
- Historical Orbis evaluation and benchmark evidence (`build/orbis-eval`, `build/benchmarks`, and `build/r038-qwen-results.json`)
- The compact Orbis upstream model/license/provenance snapshot from `Orbis Offline Training/models`
- Only the R123 and R124 rollback JARs
- A complete SHA-256 inventory for independent verification

## Deliberately excluded as reproducible or disposable

- Compiled output and ordinary deployable artifacts (`build/`, `dist/`, `*.jar`, `*.class`), except the explicitly archived R123/R124 JARs and evaluation evidence
- Vendored Hytale server/runtime binaries and local native libraries
- Downloaded third-party toolchains and runtimes (`.tools/`, `toolchains/`, and `persistent-npcs/tools/orbisllm/`)
- Python/Node environments and dependency caches (`envs/`, `.venv/`, `node_modules/`, and caches)
- Downloadable model weights and large ML runtime artifacts (GGUF, safetensors, ONNX, and PyTorch checkpoints)
- Runtime logs, telemetry, transient runs, teacher runs, and generated output
- Empty/default server and world scaffolding; the audit found no populated active world database requiring preservation
- The remaining historical JAR collection; only R123 and R124 were selected for retention
- Machine-local credentials, tokens, secret-bearing environment files, and non-reusable local configuration

## Audit corrections to the original manifest

- The earlier `~1.02 GB / 67,137 files` figure described only `Hytale Taverns`; it did not describe all three source folders.
- The audited total is 13,040,040,357 bytes across 171,146 files for all three folders.
- The broad `models/` exclusion accidentally covered a small, unique Orbis license/provenance snapshot; it is now retained in the private archive. Downloadable weights and caches remain excluded.
- Native `orbisllm` source and two reusable configuration templates are now tracked in GitHub.
- Runtime Mara state, evaluation evidence, and selected rollback artifacts are intentionally archived privately instead of committed.

No Google Drive content was deleted by this migration process.
