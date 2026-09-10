# Stage 13 Correction S — owner-managed skill and passive artwork

Baseline: RPG branch, R commit `a4335e3578d2ddd399509dc0941a4a1d391b9a1f`.
Revision badge R032-S; plugin version 0.0.25. Presentation/tooling only.

## Owner workflow

1. Save PNGs to `art/Skills` or `art/Passives`.
2. Choose the exact filename from `art/ICON-FILENAMES.csv`.
3. Close Hytale and any Hytale server.
4. Double-click `Update RPG Icons.cmd` at the repository root.
5. Restart Hytale and rejoin RPG.

Examples: `SkillFirebolt.png`, `SkillQuickslash.png`,
`SkillWhirlwind.png`, `PassivePotency.png`, `PassiveSharedaegis.png`.
Fireball and Fire Bolt remain separate canonical skills/files. The CSV explicitly
maps all **87 skills and 66 passives**; there are 153 unique names.
The stable filename index is checked into the RPG JAR, not inferred from a
player's equipped item or generated dynamically from a search query.

[Owner instructions](../../art/ADDING-ICONS.md) explain PNG requirements,
the exact directories, restart semantics, future builds and undo.
PNG source artwork remains in the owner's GitHub art folder; the updater
copies bytes unchanged. No JDK, Gradle, JAR editor, extra mod or network service
is required for subsequent artwork updates.

## Why a one-click import instead of live filesystem polling

The existing system serves native Item icons and CustomUI textures from
the loaded mod asset pack. Loose files in `art` or the save's `mods` directory
do not automatically become registered client textures.

The bounded solution is an offline asset-only import into the installed RPG
JAR, followed by a restart/rejoin. It avoids a fourth mod, runtime asset-pack
priority conflicts, world-thread filesystem reads, or unverified hot reload.
The updater uses Windows PowerShell 5.1 and built-in .NET facilities.
The user explicitly still needs to double-click the updater: copying a file
alone is not claimed to be a live-reload mechanism.

## Runtime and UI changes

`rpg/presentation/icon-index.json` maps each canonical kind/ID/name to its
exact filename; each Skill row also identifies the actual native bridge Item
asset. Filename casing preserves the requested `SkillFirebolt` convention:
prefix, then the punctuation/space-free display name with first letter
uppercase and the remaining letters lowercase. This convention is confined to
presentation filenames, never weapon-power or gameplay resolution.

`RpgSkillIcons` loads this index once and resolves only icons actually present
in the JAR. Missing PNGs keep the existing Tornado CustomUI fallback.
The existing native Item icon remains unchanged when no replacement is supplied.
This avoids packaging 151 duplicate placeholder files or referencing missing
textures. Unknown IDs also fall back safely.

Passive library and equipped-node projections now use `forPassive`.
The page updates the details icon and all six passive node previews through
the same background → transparent overlay → native frame composition used
for skills. Empty nodes hide their preview; selection/assignment controls are
unchanged. No status-effect/debuff image mapping is modified.

For every supplied Skill PNG the updater writes identical bytes to:

- `Common/Icons/Items/RPG/<filename>` for native Item.Icon.
- `Common/UI/Custom/Icons/RPG/<filename>` for Skill Tree views.

It changes only that Skill's native Item JSON `Icon` field, preserving all
other authored fields. Passive PNGs only need the second path.
PNG names are not used to invent content: the embedded catalog/index must
agree, including the native Item filename's canonical ID.

No resource, cooldown, targeting, skill mechanics, native input/AbilitySlots,
HUD resource ownership, XP, persistence, escrow or exact-once changes.
The only RpgHud change is the revision suffix R → S.

## Updater safety and rollback

- Finds exactly one installed `HytaleRPG-*.jar` in the RPG world mods folder.
  An ambiguous version inventory fails instead of choosing arbitrarily.
- Checks mod identity, index schema/counts, canonical membership, unique names,
  safe path syntax, and actual native Item targets.
- Rejects unknown/misplaced PNG names and duplicates, non-PNG bytes, malformed
  images, files over 4 MiB, linked files, and non-square/out-of-range dimensions.
  PNG IHDR dimensions are bounded before image decoding; accepted dimensions
  are 16–1024 square, with 128 square recommended.
- Validates the entire input batch before replacing the JAR. A typo does not
  produce a partial valid-image install.
- Refuses updates while a detected Hytale client/server runs and rechecks
  immediately before replacement. A lifetime sidecar file lock excludes
  concurrent updater processes.
- Writes a separate staged archive, forces it, verifies every original entry's
  uncompressed SHA-256, and permits only the explicitly selected PNG/Item.Icon
  changes and additions. Code, catalog and unrelated entries must match.
- Makes a hash-named backup before an atomic same-directory file replacement.
  Rechecks the installed source hash to avoid overwriting concurrent changes.
- Saves a per-update receipt and `last-update.json` in `icon-backups`, including
  source PNG hashes, changed entry paths and before/after JAR SHA-256.
- A repeat with identical inputs is byte-identical and does not overwrite the
  last useful undo receipt. Removing a source PNG does not erase its installed
  copy; the owner guide states this explicitly.
- `Undo Last Icon Update.cmd` verifies the current JAR still equals the last
  update's output and verifies its backup hash before restoration. It refuses
  to roll an unrelated/newer mod build backward.
- `-CheckOnly` validates and reports changes without writing a JAR, backup,
  or sidecar lock. No live save or mod configuration data is edited.

An icon update intentionally changes the artifact hash, not the gameplay
revision badge. Receipts identify custom artwork builds. After a future code
build, rerun the updater using the retained source art collection.

## Implementation failures caught by targeted tests

The Windows PowerShell subprocess tests and default-launch check exposed four compatibility
issues, corrected without weakening assertions:

1. An inherited PowerShell module search path did not expose Get-FileHash.
   The updater now hashes with built-in .NET SHA-256 streams instead.
2. .NET Framework ZipArchive Update changed a Java-produced empty deflated
   directory entry from empty bytes to nonempty bytes. The unchanged-entry
   hash gate rejected it. The tool now recreates the staged ZIP by streaming
   every original uncompressed entry and checks all hashes afterward.
3. Windows PowerShell converted null in File.Replace's backup filename to an
   invalid empty path. It now uses the existing tooling's NullString.Value
   convention. Atomic replacement and exact undo are exercised by subprocess tests.
4. Windows PowerShell did not populate PSScriptRoot while evaluating parameter
   defaults. Repository-relative defaults now resolve inside the script body.
   A new regression launches from an unrelated temporary working directory,
   omits ArtRoot/BackupRoot and validates the same defaults used by double-click.

## Validation and evidence

Twelve new tests cover:

- Complete canonical/index/CSV mapping and filename uniqueness.
- Optional artwork resolution for all 153 names; separate Fire Bolt/Fireball;
  missing-image fallback; unsafe index paths and duplicate IDs.
- Actual owner-facing PowerShell installation of Whirlwind and Potency images,
  byte-identical copies, native Item.Icon-only edits and unchanged sentinel
  code/directory entries.
- Actual production resolver discovery using a ClassLoader reading that
  updated JAR, without modifying Java for the new files.
- Repeat no-op, exact undo, typo batch rejection, malformed PNG rejection,
  oversized header rejection, read-only dry-run, changed-build undo refusal
  and corrupted-backup rejection.
- Windows PowerShell default-launch path resolution independent of caller directory.

The retained R icon layer test now requires **ten** Skill Tree surfaces
(three skills, six passives and details), instead of four. The badge assertion
advances to S. No retained test identity is deleted, skipped or renamed.

The final complete retained run passed **2,157 tests**: 2,091 root tests,
45 native-control tests and 21 CanvasUI tests; zero failures, errors or skips.
All R baseline case identities remain present. The initial coherent build run
passed 2,156 tests; the final run includes the additional default-launcher
regression after the Windows PowerShell parameter-default correction.

The owner added the actual `art/Skills/SkillWhirlwind.png` during implementation.
The new updater imported it into the compiled S candidate, alongside the already
identical Fire Bolt and Quick Slash PNGs. Only the two Whirlwind PNG entries and
Whirlwind Item.Icon changed in that import. Its source SHA-256 is
`60797552980435AA0E959269A0184DD1E2926C3173C7F525C11CCAA30281561D`.
The untouched compiled base JAR hash was
`EAAD9DFEC2111EE02BCC19C41F95DAEA15CAFE46B1FDA0C31DEAD80F322E976C`;
the image-imported artifact below is the actual test/deployment candidate.
The final regression rerun did not overwrite that imported JAR. Subsequent
changes were to the external updater/tests/reports, not packaged Java behavior.

Two isolated native integration checks passed:

- The exact final JAR booted the installed 0.7.0-pre.1 native server with the
  established three mods, passed asset checks and actual native projectile
  spawn/pending-rollback/resting-expiry checks, then shut down cleanly.
- A separate actual-JAR updater experiment imported Whirlwind and Potency
  test fixtures and also passed native startup/asset checks. These fixtures
  reused image bytes solely to test arbitrary optional filenames; they were
  never installed in the live world or presented as owner artwork. Evidence
  is nested under `fixture-icon-smoke` (its historical run directory names T).

Strict R-to-S entry differential, three-mod archive hashes and isolated atomic
rollback/roll-forward all passed. The default double-click script path was
checked against the installed JAR: three supplied icons, zero pending changes,
and no write. The deployment manifest verifies all **18 non-JAR mod-data files
unchanged**, no live save edits and exactly the established three mods.

### Deployed artifact and rollback

Deployment: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods`.
Manifest refreshed September 10, 2026 at 02:16 UTC after final validation.

| Artifact | SHA-256 |
|---|---|
| HytaleRPG-0.0.25.jar (R032-S) | `E3B6F793F66164807EFC53E992C9B68383E05569A580F2D32B92AE105160C3BC` |
| CanvasUI-0.1.0.jar (unchanged) | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar (unchanged) | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| Hytale-RPG-Stage13-S-owner-icon-workflow.zip | `A6A873D68BBA47AF50367AC9A57F2C70AD096CC5EF3C00E0ADE0B044BEB992F3` |
| R rollback RPG JAR | `8D0A2B286839B433D38DF436C0ADE0B307E1FB38B2653F251D387928BF1488F0` |

Retained [evidence directory](../../evidence/stage-13/cohort-s/) contains
`full-validation.txt`, complete `test-results.json`, focused updater/index XML,
`owner-icon-workflow.json`, `jar-differential.json`, `server-smoke-summary.json`,
native audit records, exact artifacts/archive, R rollback, candidate import
receipt/backup and `owner-default-check.txt`. Source artwork is not regenerated
or overwritten; the imported bytes are preserved in the archived candidate.

Client input, focus and rendering remain **UNVERIFIED**, pending the owner's
connected checks. Neither native asset startup nor classloader tests prove
the connected client rendered the replacement textures.

## Connected owner checks

1. Confirm R032-S after joining RPG.
2. Whirlwind is already included. To test the self-service path, close Hytale;
   add or replace a PNG such as `PassivePotency.png` using the CSV;
   double-click Update RPG Icons and restart.
3. In `/rpg skilltree`, search for the corresponding content. Inspect library,
   details, and an equipped node. Skills should also show in native ability
   slots when equipped.
4. Confirm existing background/frame remains visible around a transparent
   symbol. Check that other icons and native cooldown presentation are unchanged.
5. Reopen/search/switch tabs to retain R's UI regression check.
6. Close Hytale and repeat Update: it should say Already up to date.
   Optionally run Undo Last Icon Update; the prior icon build should return.

## Existing limitations

This is not a casting, reward, encounter-uncertainty or performance correction.
Q's post-lethal/encounter issues remain unresolved. No uncertain persisted
state is cleared and no durability contract changes. Stage 13 is **not PASS**.
The tool cannot certify that an owner's drawing is visually legible; it validates
format, bounds, packaging and byte preservation. In-game visual QA is required.
