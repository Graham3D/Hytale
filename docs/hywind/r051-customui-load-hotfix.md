# R051 CustomUI Load Hotfix

The R050 connected client rejected `CanvasGraphEditorHud.ui` before joining because two labels used the CSS-like `HorizontalAlignment: Right` token. The installed `0.7.0-pre.3.1` client expects the `LabelAlignment` enum values `Start`, `Center`, or `End`. R051 changes both right-aligned labels to `End` and extends the package-time CustomUI validator to reject `Left`/`Right` alignment values with the corresponding supported replacement.

The focused editor regression, all 59 source CustomUI documents, the complete 39-task retained suite, exact package audit, isolated Hywind smoke, and two post-deployment restart cycles pass. The deployed `Hywind.jar` SHA-256 is `5AF009B3E833DD38DFFE965FCE2479A94F9C5EDCD4241D5A99EF0C9298BEE4C3`. The R050 save/JAR rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T220308Z`.

Connected client rejoin remains required because the original failure occurs in the client CustomUI parser and cannot be proven by the headless server smoke.
