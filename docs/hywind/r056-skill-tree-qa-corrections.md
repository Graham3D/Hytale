# R056 Skill Tree QA Corrections

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

R056 removes the redundant red `BREAK SELECTED` action and retains only the bounded `Break Link?` Yes/No confirmation. The connected disconnect was traced to `RpgHudCoordinator` continuing to patch RPG HUD selectors while CanvasUI held exclusive CustomUI ownership; updates are now suspended until the exact RPG HUD instances are restored.

Compatible library drops may now replace occupied Skill or Passive node content atomically. `SKILL03` remains honestly labeled `UNBOUND` because the pinned client exposes no native Ability4 input, but it accepts and persists a Skill assignment. Existing links and layout topology remain authoritative.

Presentation changes include fifteen visible library rows, centered tab labels and instructions, inset connector ports, corrected header density scaling, vertically centered title, and nine-sliced tree artwork that preserves the ornaments while extending the dark center to the frame bottom. The search focus page now uses the same authored font, size, color, and transparent background as the passive HUD field. The pinned client still recenters the pointer when opening its required interactive text-input page; no supported cursor-position restoration API exists in `0.7.0-pre.3.1`.

Validation passed: focused interaction/presentation regressions, 59 CustomUI documents, complete retained `clean check`, unified package audit, isolated Hywind-only startup smoke, exact candidate/deployed hash comparison, and two deployed live-save startup/restart cycles. Connected rendering and input remain explicitly unverified.

Artifact: `Hywind.jar` (`0.1.0-merge.11`, `R056`)

SHA-256: `3E5B854FE001DF71F3C0779D76778AF1BA39E6575521F258A565D5602AFF690B`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T025740Z`
