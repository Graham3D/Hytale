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
- CanvasUI development demo source: [`canvas-ui-demo`](canvas-ui-demo) (bundled into the CanvasUI development JAR)

Build with `./gradlew.bat clean build`. Install only into the dedicated `RPG`
save with `./tools/Install-Phase00Probe.ps1`; roll back with
`./tools/Uninstall-Phase00Probe.ps1`.

The single deployable CanvasUI artifact is
`canvas-ui/build/libs/CanvasUI-0.1.0.jar`. Its R004 development build includes
the `/canvasui-demo` and `/canvasui-topology-proof` commands; no second demo mod
is installed.
