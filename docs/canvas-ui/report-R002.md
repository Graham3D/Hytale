# CanvasUI R002 implementation report

`exitGate = BLOCKED`

Reason: mandatory real-client interaction and responsiveness evidence is pending.

## Identity

- Starting commit: `5622df1a2b92fadd717e54e10d8eca7dfa385745`
- Implemented source commit: `01caefed8ef7d9772be2b1c148c00d793d66df6d`
- Branch: local long-lived `RPG`
- Library: `CanvasUI` `0.1.0`, development revision `R002`
- Runtime: Hytale `0.7.0-pre.1` / `e8b4d191fc98a977bf5546a951a7b25473d323e3`

## Module layout

`canvas-ui` is the standalone reusable library/plugin jar. Its `api`, `runtime`,
and `rendering` packages separate graph state from current Hytale UI commands.
`canvas-ui-demo` is a development-only third-party-style consumer jar. Neither
module depends on HytaleRPG or HTDevLib; the demo depends on CanvasUI and
CanvasUI depends only on Hytale.

## Implemented public behavior

- stable fixed-zoom canvas coordinates and independent viewport offsets;
- arbitrary node definitions, renderers, metadata, selection, drag threshold,
  and grab-offset preservation;
- explicit input/output/bidirectional ports with semantic types and limits;
- stable edges, consumer connection policies, duplicate/dangling/direction/
  limit/cycle validation, and explicit rejection reasons;
- connection previews and replaceable edge geometry with the current
  three-segment orthogonal Hytale fallback;
- snapshot DTO/codec, consumer persistence hooks, typed events, metrics, and
  idempotent session cleanup;
- global mouse routing hidden behind `CanvasService`; and
- small geometry updates during drag, with UI transmission capped at 10 Hz.

## Automated and server results

- Sixteen headless tests: pass, zero failures/errors/skips.
- Library/demo compilation, jars, sources jar, Javadocs: pass.
- Static separation checks: pass; no RPG-specific source terms or demo imports
  from internal packages.
- Hytale bare-server smoke: CanvasUI and CanvasUIDemo discovered, set up, and
  enabled; no plugin-scoped error.
- Exact hashes and logs: `evidence/canvas-ui/R002/`.

## Current limitations

Hytale exposes no native spline/path or pointer-capture API. CanvasUI therefore
uses logical press-to-release capture and an edge-renderer abstraction over
axis-aligned segments. The server can measure processing latency and update
rates, not presentation latency. Most importantly, delivery of world mouse
events while a CustomUI page owns the cursor still needs real-client proof.

## Remaining probe code and rollback

`canvas-ui-demo` and its file persistence adapter are development-only and must
not ship as an RPG dependency. Remove both deployed test jars with
`tools/Uninstall-CanvasUIDemo.ps1`; this leaves HTDevLib and save data intact.

CanvasUI is not approved for the production RPG Link Tree until all real-client
checks in `client-verification-R002.md` pass. No RPG skills, passives,
attributes, XP, combat, acquisition, or production Link Tree were implemented.
