# Google Drive to GitHub migration manifest

Migration date: 2026-09-03

Source folders (left intact in Google Drive):

- `Inigmas Games/Hytale Taverns`
- `Inigmas Games/Hytale Persistent NPCs`
- `Inigmas Games/Orbis Offline Training`

## Included

- Application and plugin source code
- Hytale UI/resource definitions and authored mod assets
- Tests, build/install scripts, and development utilities
- Architecture notes and technical-design documents (`.md` and `.docx`)
- Training configuration, registry, candidate, dataset, report, export, and quarantine records
- Project README and license files

## Deliberately excluded

The source folders contained roughly 13 GB and 171,000 files. Most of that footprint was generated or machine-local material unsuitable for normal Git history. The following remain in Drive and are ignored here:

- Compiled output and deployable JARs (`build/`, `dist/`, `*.jar`, `*.class`)
- Vendored Hytale server/runtime binaries and local native libraries
- Downloaded third-party toolchains and runtimes (`.tools/`, `toolchains/`, `persistent-npcs/tools/orbisllm/`)
- Python/Node environments and dependency caches (`envs/`, `.venv/`, `node_modules/`, caches)
- Model weights and large ML runtime artifacts (`models/`, GGUF, safetensors, ONNX, PyTorch checkpoints)
- Runtime logs, telemetry, transient runs, teacher runs, and generated output
- Active server/world state (`universe/`) and rollback JAR archives
- Machine-local server configuration, credentials, tokens, and secret-bearing environment files

These exclusions prevent credentials, mutable game state, generated binaries, and oversized artifacts from being published. They can be migrated later to a release registry, artifact store, or Git LFS after an explicit audit.

