# R053 Skill Tree Visual Polish Correction

Status: **IMPLEMENTED / PACKAGED / DEPLOYED — CONNECTED CLIENT QA PENDING**

Implementation commit: `a5f003857ade700844aabddfe6a98dbf3fa395e1`

## Delivered

- Corrected every RPG-owned Skill Tree texture URI to the client-resolvable path relative to `Common/UI/Custom`; the duplicated `Common/UI/Custom/...` prefix that produced connected red-X textures is gone.
- Corrected Skill-slot art: empty uses `SpecialSlotTemporary@2x.png`, occupied uses `Slot@2x.png`. Passive, Joint, and connector art retain the owner/native mappings required by the correction brief.
- Copied 18 Hytale Inventory/Common presentation assets into namespaced Hywind resources with pinned SHA-256 checks. These supply the primary frame, matching 252-pixel Library/Inspector frames, flexible patterned center frame, tabs, search field, separators, fallback icon, and native primary/destructive button art.
- Replaced the flat three-region overlay with four visibly distinct decorative frames: Primary, Library, Skill Tree, and Inspector. The centered title and revision remain in the primary header.
- Removed the top-right Close control. Added Save and red Exit together at bottom-right. Save invokes the existing authoritative graph commit/checkpoint path; Exit preserves the existing cleanup and exact HUD-restoration path.
- Replaced the unresolved native debug placeholder with a packaged Hytale `UnknownItemIcon` fallback, preventing missing authored Skill/Passive art from becoming a red-X texture.
- Preserved R052 search, scrollbar, inspector, drag/drop, exact connector-port persistence, right-click unequip, topology restoration, modal HUD lease, and white spline behavior.

## Verification

- RPG JUnit: 2,367 tests, zero failures/errors/skips.
- CanvasUI JUnit: 41 tests, zero failures/errors/skips.
- Tavern JUnit plus 8 retained executable gates: PASS.
- Persistent NPC retained executable gates and exact release-resource validator: PASS; live local-model tests remain intentionally skipped by that harness.
- CustomUI validation: 59 source documents PASS.
- Skill Tree source/hash and relative-URI regressions: PASS; 24 approved/Inventory-derived assets pinned.
- Unified package audit: PASS; 5,851 entries, 2,206 classes, and 2,090 UI documents.
- Isolated Hywind-only smoke: PASS.
- Installed restart validation: 2/2 PASS; all four subsystems started/shut down, no legacy first-party plugin loaded, and save-data roots remained present.

## Artifact and rollback

- Candidate and installed JAR: `Hywind.jar`
- Version/revision: `0.1.0-merge.8` / `R053`
- SHA-256: `62C33FC19C0D7FBCE2CD5EC484F366EABFA02796B89840D46E821737C26DB5F5`
- Installed path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- Full stopped-save rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T010442Z`

## Connected QA still required

Automated checks prove resource identity, URI structure, authority, packaging, startup, and rollback; they cannot prove client rendering. Open `/rpg skilltree` and verify: no red-X textures; correct empty/occupied Skill art; owner Passive/Joint art and connector arrows; four decorative frames; identical Library/Inspector widths; working Skills/Passives/search/scrollbar/inspector; Save checkpoint; red Exit and exact HUD restoration; drag/drop, links, right-click unequip, close/reopen persistence, and restart persistence.
