# Hytale RPG

This repository is intentionally stopped at **Stage 00 — Evidence & Feasibility**.
It contains an audit-only server plugin, reproducible evidence exporters, and a
client verification checklist. It does not contain RPG gameplay systems.

- Phase report: [`docs/phase-00/phase-00-report.md`](docs/phase-00/phase-00-report.md)
- Client checklist: [`docs/phase-00/client-verification.md`](docs/phase-00/client-verification.md)
- Machine-readable capability matrix: [`evidence/phase-00/build-capabilities.json`](evidence/phase-00/build-capabilities.json)
- Installed-asset catalogs: [`evidence/phase-00/catalogs`](evidence/phase-00/catalogs)

Build with `./gradlew.bat clean build`. Install only into the dedicated `RPG`
save with `./tools/Install-Phase00Probe.ps1`; roll back with
`./tools/Uninstall-Phase00Probe.ps1`.

