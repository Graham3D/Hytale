# R054 Skill Tree density-reference hotfix

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED RECHECK REQUIRED**

## Connected root cause

R053 packaged the intended Hytale Inventory artwork byte-for-byte, including `Slot@2x.png` and `SpecialSlotTemporary@2x.png`, but referenced those physical density-variant filenames directly from CustomUI. Hytale UI documents reference those files through their logical names (`Slot.png`, `SpecialSlotTemporary.png`, and equivalent logical names for the copied frame/button assets). Direct `@2x` references do not resolve and produced the red-X fallback shown in connected QA.

R054 keeps the authoritative `@2x` files unchanged and corrects every static and runtime-assigned Skill Tree texture URI to its logical filename. The occupied/empty mapping remains occupied Skill -> `Slot.png`, empty Skill -> `SpecialSlotTemporary.png`.

## Verification and deployment

- focused Skill Tree presentation and asset-integrity tests: PASS
- 59-document CustomUI validation: PASS
- complete retained regression suite: PASS
- package/hash audit: PASS
- isolated unified-mod smoke: PASS
- two post-deployment startup/restart cycles: PASS
- connected client rendering: pending owner recheck

Artifact: `Hywind.jar` (`0.1.0-merge.9`, `R054`)

SHA-256: `D20A7E6C25989E28BF0D64DD313CEAF1708B357552B1CC2A607621F5549BD477`

Installed at: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T013406Z`
