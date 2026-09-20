# R060 Skill Tree HUD Restoration Hotfix

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

Connected R059 traces showed that `/rpg skilltree` successfully created the graph-editor session and camera, then the transparent Escape-capture `CustomUIPage` displaced the `CanvasGraphEditorHud`. Its immediate dismiss callback closed the session with `ESCAPE_DISMISS` and could also produce `Client sent unexpected acknowledgement`.

R060 removes that competing page and restores the graph HUD as the editor's sole presentation surface. Search retains its existing temporary page lifecycle, while the red `EXIT` control continues to use the established exact-once cleanup path. A regression test rejects any return of the competing Escape page, its open helper, or its dismiss reason.

`gradlew.bat clean check`, all 59 CustomUI document validations, package verification, and two deployed startup/restart cycles pass. Connected verification that `/rpg skilltree` visibly opens the editor remains pending. Escape-key parity is not claimed in R060; the unsafe sidecar implementation was removed to restore the functioning editor.

Artifact: `Hywind.jar` (`0.1.0-merge.15`, `R060`)

SHA-256: `67713D1DB43D1661DE9EAA94DE76434CBECDC01ECF40B9F0FEBB92FE8E50ABBD`

Implementation commit: `b77ac826a5f69aa4cefa3488b1f208c73e89719f`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T142106Z`
