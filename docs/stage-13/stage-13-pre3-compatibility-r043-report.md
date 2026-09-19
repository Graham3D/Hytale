# R043 — Hytale 0.7.0-pre.3.1 compatibility correction

Date: 2026-09-19  
Branch: `RPG`  
Status: implemented, packaged, deployed, isolated-smoke verified; connected-client verification required.

## Incident

The first connected startup after Hytale updated to `0.7.0-pre.3.1` failed during RPG setup with:

```text
NATIVE_EQUIPMENT_FAMILY_UNRESOLVED:
Weapon_Battleaxe_Iron:
expected=:
actual={"Type=Weapon":[],"Family":["Battleaxe"],...}
```

The subsequent `ClosedFileSystemException` was shutdown fallout after plugin setup failed, not the initiating fault.

R042 had been built and smoke-tested against `0.7.0-pre.2`. In the newly installed assets, `Template_Weapon_Battleaxe` now authoritatively supplies `Type=Weapon` and `Family=Battleaxe`. The RPG power registry intentionally fails closed when its exact audited native family differs from the installed asset inheritance, so the stale empty-family record correctly prevented startup.

## Correction

- Advanced the numeric revision to `R043` and pinned compilation/packaging to `0.7.0-pre.3.1`.
- Updated the installed-assets audit metadata to the pre.3.1 archive SHA-256:
  `1A48A64DA959F1A1EBCCB461B6C518BF2AF94F1116DEBCE84F7CC1E0E95A89D9`.
- Updated every production item inheriting `Template_Weapon_Battleaxe` to require the exact native `Battleaxe` family, including `Weapon_Battleaxe_Iron` and the void scythe inheritor.
- Preserved each RPG combat kind and authored/native uncharged power source. No name heuristic, fallback averaging, family weakening, gameplay rebalance, or wildcard acceptance was introduced.
- Updated the production-path regression to accept only the exact installed type/family pair and reject missing or incorrect family tags.
- Extended R043 smoke/package tooling and changed the CanvasUI manifest gate to derive its exact Hytale version from `gradle.properties` rather than hard-coding pre.2.

## Installed native evidence

- Hytale server version: `0.7.0-pre.3.1`
- Hytale server revision: `ce0c3838c4222fba7aadb4189c8dc26893cd52dd`
- Server JAR SHA-256: `7928797E148E4B15F787E1449BF020FCA41A9BB2F605E464F6A0699484CE71BC`
- Assets archive SHA-256: `1A48A64DA959F1A1EBCCB461B6C518BF2AF94F1116DEBCE84F7CC1E0E95A89D9`

## Validation

- Focused exact-family and installed-inheritance tests: PASS.
- Installed-assets inheritance audit: all 197 production item records resolve their exact pre.3.1 inherited tags.
- Complete retained suite: PASS, 2,453 tests total.
  - RPG tests: 2,358
  - Native-control tests: 67
  - CanvasUI tests: 28
- CustomUI validation: PASS.
- CanvasUI verification and pre.3.1 startup smoke: PASS.
- Exact isolated three-mod startup smoke: PASS.
  - exactly three mods: true
  - RPG setup/ready: true
  - CanvasUI setup: true
  - native ability/projectile/support/summon/progression gates: true
  - clean shutdown: true
  - plugin-scoped failure: false

## Candidate artifacts

- `HyARPG.jar` SHA-256: `BB1CC4E8CD975077D96F01DFD03EDED5EC7F130E64D06705FFF3F9B035A9D32D`
- `CanvasUI-0.1.0.jar` SHA-256: `85E2193E310A9B9DE50A810113A35E076387FF496902034892810E02A4599175`

The deployment package preserves the existing third mod, `HYTALEDEVLIB-0.5.0.jar`, and backs up the complete RPG save/mod-data state before replacement.

## Deployment

- Live mods directory: `%APPDATA%/Hytale/data/pre-release/Saves/RPG/mods`
- Exact built/live `HyARPG.jar` hashes match.
- Exact built/live `CanvasUI-0.1.0.jar` hashes match.
- Previous live RPG SHA-256: `4574E0408D26A7B0D2420555977D2E6FADC1A0FF10BBD42BBC4BE66729DD0FBB`.
- Previous live CanvasUI SHA-256: `2FAEBFE07DD9A3855F676144463D2B0864DE82DCA5F517078F63CF270F5FC3B5`.
- Full save/mod-data backup: `evidence/canvas-ui/cursor-hud/R043/final/before/save/20260919T133032Z/RPG` (582 files).
- Exact three-mod archive: `evidence/canvas-ui/cursor-hud/R043/final/CanvasUI-Cursor-HUD-R043-three-mods.zip`.
- Archive SHA-256: `7836811DD4724E602FCEFF6BCE334CAFB41B6C92A4989E4121114231BB1F66A4`.
- Packaging rollback validation: PASS.

## Remaining connected gate

After deployment, join the normal RPG world and confirm that the top-right revision reads `R043` and startup no longer reports `NATIVE_EQUIPMENT_FAMILY_UNRESOLVED`. Local tests and isolated smoke do not constitute connected-client proof.
