# R059 CustomUI Load Hotfix

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

The R058 client log identified an exact load failure in `CanvasGraphEditorHud.ui`: the mod document imported `Common/Container.ui`, which is visible in Hytale's base UI source tree but is not resolvable from the client-side CustomUI package namespace.

R059 removes that cross-package import and defines the Skill Tree title style locally using the exact installed `Container.@TitleStyle` properties. A regression assertion now rejects reintroduction of `Common/Container.ui` while retaining the native Secondary font, uppercase rendering, sizing, weight, color, and shrink behavior.

`gradlew.bat clean check`, all 60 CustomUI document validations, package verification, and two deployed startup/restart cycles pass. Connected client join remains pending.

Artifact: `Hywind.jar` (`0.1.0-merge.14`, `R059`)

SHA-256: `2072D6AAD3E834A6FAFA4A7E9BB2E73289E27A6D6953C36A6D3FBC9CE06DBEC8`

Implementation commit: `55d724418f5907cca2be9b26f364c33fafee47f0`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T140851Z`
