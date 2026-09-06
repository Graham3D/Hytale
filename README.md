# Hytale RPG

This repository remains stopped before RPG gameplay implementation. It now also
contains the standalone, RPG-agnostic **CanvasUI** library and its development
demo. CanvasUI is a separate jar; the dependency direction is
`consumer -> CanvasUI -> Hytale`.

- Phase report: [`docs/phase-00/phase-00-report.md`](docs/phase-00/phase-00-report.md)
- Client checklist: [`docs/phase-00/client-verification.md`](docs/phase-00/client-verification.md)
- Machine-readable capability matrix: [`evidence/phase-00/build-capabilities.json`](evidence/phase-00/build-capabilities.json)
- Installed-asset catalogs: [`evidence/phase-00/catalogs`](evidence/phase-00/catalogs)
- CanvasUI library: [`canvas-ui/README.md`](canvas-ui/README.md)
- CanvasUI development demo: [`canvas-ui-demo`](canvas-ui-demo)

Build with `./gradlew.bat clean build`. Install only into the dedicated `RPG`
save with `./tools/Install-Phase00Probe.ps1`; roll back with
`./tools/Uninstall-Phase00Probe.ps1`.

CanvasUI artifacts are `canvas-ui/build/libs/CanvasUI-0.1.0.jar` and
`canvas-ui-demo/build/libs/CanvasUI-Demo-0.1.0.jar`.
