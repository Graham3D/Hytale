# Hytale RPG

The `RPG` branch is currently at **R023 native Rune control diagnostics**, awaiting
connected evidence after R022 failed to restore native casting. Stage 06 has not begun.

- Current report: [`R023 native Rune control`](docs/corrections/R023-native-rune-control.md)
- Current test: [`R023 connected control checklist`](docs/corrections/R023-client-verification.md)

The repository also contains the standalone, RPG-agnostic **CanvasUI** library and its development
demo. CanvasUI is a separate jar; the dependency direction is
`consumer -> CanvasUI -> Hytale`.

- Phase report: [`docs/phase-00/phase-00-report.md`](docs/phase-00/phase-00-report.md)
- Client checklist: [`docs/phase-00/client-verification.md`](docs/phase-00/client-verification.md)
- Machine-readable capability matrix: [`evidence/phase-00/build-capabilities.json`](evidence/phase-00/build-capabilities.json)
- Installed-asset catalogs: [`evidence/phase-00/catalogs`](evidence/phase-00/catalogs)
- CanvasUI library: [`canvas-ui/README.md`](canvas-ui/README.md)
- CanvasUI development demo source: [`canvas-ui-demo`](canvas-ui-demo) (bundled into the CanvasUI development JAR)

Build with `./gradlew.bat clean build`. R023 verification, smoke and installation
use `tools/Verify-R023.ps1`, `tools/Run-R023Smoke.ps1` and `tools/Install-R023.ps1`.
Install only into the dedicated `RPG` save. Use the current report's rollback
instructions; the Phase 00 reports/tools above are historical.

The single deployable CanvasUI artifact is
`canvas-ui/build/libs/CanvasUI-0.1.0.jar`. Its R008 development build includes
the `/canvasui-demo` and `/canvasui-topology-proof` commands; no second demo mod
is installed.

The canonical revision-by-revision engineering record is
[`docs/canvas-ui/development-report.md`](docs/canvas-ui/development-report.md).
It must be updated after every deployed CanvasUI revision.
