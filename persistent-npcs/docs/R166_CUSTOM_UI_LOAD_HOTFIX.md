# R166 Custom UI Load Hotfix

## Connected failure

The client rejected the packaged Profile Editor document while joining:

`Failed to parse file Pages/ProfileEditor/BasicInfo.ui (26:1) - Expected end of file`

The local single-player server then shut down because its client disconnected. This occurred before the R165 offline-equipment runtime path could execute.

## Repair

- Removed the premature closing brace from the Profile Editor Summary row.
- Added a release-wide build gate that checks every packaged Custom UI document for early closing braces, unbalanced blocks, and unterminated strings.
- Preserved the R165 offline equipment hydration and stat synchronization implementation unchanged.

## Validation

- Release-resource validation passed for all packaged Custom UI documents.
- The full deterministic Persistent NPC suite passed through R165, with historical R146/R147 coverage retained.
- Connected validation: restart the client/server, confirm the HUD reports `R166-CUSTOM-UI-LOAD-HOTFIX`, enter the world, then resume the Mara offline equipment lifecycle test.
