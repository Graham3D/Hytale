# Hytale RPG

The `RPG` branch is at **Stage 13 / R032-AH**, deployed for connected testing.
The distributable is **HyARPG.jar**. AH fixes Healing Beam's missing carrier texture,
makes staff-head sparkles channel-only, and orders skill resource writes before native
stat replication. All 2,246 retained tests and installed-byte isolated checks pass.
Connected beam rendering and immediate Mana-bar synchronization remain unverified.

- Current report and test checklist: [R032-AH Healing presentation and Mana synchronization](docs/stage-13/stage-13-presentation-ah-report.md)
- Spell-color editing: [owner guide](docs/owner-spell-color-guide.md)

The repository also contains the standalone, RPG-agnostic **CanvasUI** library and its development
demo. CanvasUI is a separate jar; the dependency direction is
`consumer -> CanvasUI -> Hytale`.

- Phase report: [`docs/phase-00/phase-00-report.md`](docs/phase-00/phase-00-report.md)
- Client checklist: [`docs/phase-00/client-verification.md`](docs/phase-00/client-verification.md)
- Machine-readable capability matrix: [`evidence/phase-00/build-capabilities.json`](evidence/phase-00/build-capabilities.json)
- Installed-asset catalogs: [`evidence/phase-00/catalogs`](evidence/phase-00/catalogs)
- CanvasUI library: [`canvas-ui/README.md`](canvas-ui/README.md)
- CanvasUI development demo source: [`canvas-ui-demo`](canvas-ui-demo) (bundled into the CanvasUI development JAR)

Build with `./gradlew.bat clean build`; the RPG artifact is `build/libs/HyARPG.jar`.
Use the current correction report for its exact tested/deployed hashes and validation commands.
R023 verification/install tools are historical and must not be used to deploy the current candidate.
Install only into the dedicated `RPG` save. Use the current report's rollback
instructions; the Phase 00 reports/tools above are historical.

The single deployable CanvasUI artifact is
`canvas-ui/build/libs/CanvasUI-0.1.0.jar`. Its R008 development build includes
the `/canvasui-demo` and `/canvasui-topology-proof` commands; no second demo mod
is installed.

The canonical revision-by-revision engineering record is
[`docs/canvas-ui/development-report.md`](docs/canvas-ui/development-report.md).
It must be updated after every deployed CanvasUI revision.
