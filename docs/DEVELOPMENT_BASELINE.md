# HyARPG/Hywind development baseline

## Recovery candidate (2026-10-10)

The source starting point is `codex/teleport-r250-latest` at
`44184d936d750e0a6729a8f797e7f6a4fe30356f`, revision
`R250-U7P5B` (`0.2.0-R250-U7P5B`). The isolated reconciliation branch is
`codex/hywind-r250-recovery`. Until its reviewed commit is approved and made,
this branch is an **uncommitted recovery candidate**, not a new completed or
deployed revision. Before subsequent work, check its actual HEAD and status;
the commit named above is its parent source, not a permanent claim about HEAD.

The active RPG save's `mods/Hywind.jar` was inspected read-only. SHA-256
`F56BB5120BFA764088A30EBFB4ED62C067C1A4899E90A50B0A7B5DA5CBE14C30`
matches the R250 build from that starting worktree. Its embedded source commit
is `44184d936d750e0a6729a8f797e7f6a4fe30356f`. Its internal plugin name
and entrypoint are `HyARPG` and `com.inigmasgames.hywind.HyArpgPlugin`.
Neither this observation nor deployment alone establishes connected-game
acceptance. Before this recovery, the [R249B receipt](enemies/R249B_DEPLOYMENT.md)
recorded the last full offline `check`; R250 had recorded targeted Teleport
tests. This candidate's fresh checks are recorded below.

## Source and ownership decisions

- R250's gameplay source remains the base. The second Teleport commit
  `ea63fd8a6c70ca808c51dc3697b948133476e37c` has the same patch ID as
  `44184d93`; the latter also retains three R249B deployment and release-gate
  documents. The primary `main` HEAD is `209e38c0`, 80 commits behind R250,
  with substantial uncommitted work and R141 build metadata.
- The separate ImmersiveNPCs runtime boundary remains mandatory. R250's Tavern
  plugin extends `JavaPlugin`; its package validator excludes `persistentnpcs`
  code and NPC-owned resources. The older split's neutral `ReadyPathProbe`
  source and inventory grid textures are already present in R250 at different
  build and resource paths. The internal `HyARPG` identity stays unchanged;
  any migration requires separate approval and save compatibility proof.
- Keep the newer R249/R250 skill, gear, summon, enemy, inventory, character,
  cooldown, and Teleport implementations. Do not replace them with primary R141
  files. Retain unique primary history, QA rules, and original art only after
  file-level review. See the [historical split report](HYWIND_SPLIT_BOUNDARY_RECOVERY.md)
  and [deployment ledger](DEPLOYMENT_REVISIONS.md).
- The primary-only R199 footprint catalog is byte-identical to R250's active
  `footprints-v1.json`; its older `InventoryGrid` textures are identical to
  R250's `RpgInventory` textures. Of 166 primary-only UI art files, 153 match
  packaged R250 images by SHA-256. The remaining original PSDs and distinct
  PNGs are preserved as source art without changing the game package.
- Four primary-only regression suites for timed defense, Fortune escape,
  original-hit health grouping, and player hit identity have been ported to
  the R250 test tree. The native adapter test's 925-byte, five-entry pin list
  is now a checked-in test fixture instead of an ignored evidence dependency.
  Explicit LF checkout rules cover byte-pinned Mantle and healing assets and
  the generated elite matrix on Windows; `managed-weapon-fire-v1.json` keeps
  its pinned CRLF bytes. These are checkout and test-reproducibility changes,
  not gameplay asset edits.

## Offline verification and inherited gate

The six focused regression/Teleport classes passed 29 tests. A fresh full
`check` reached `auditU7P5AssetCompatibility`, which rejected R250's JSON
asset set against its frozen deployed R248 baseline. This is an inherited
revision comparison: no gameplay resource is changed in this recovery diff.
The R248 gate must receive a separately reviewed R250 baseline update before
the unqualified umbrella `check` can pass.

The offline `check -x auditU7P5AssetCompatibility` passed: 3,488 root tests,
452 native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, all
with zero failures or skips. The retained Tavern feature checks and resource,
native-proc, spatial-grant, CustomUI, campaign-biome, and package audits also
passed. The package validator reported 19,436 entries, 2,158 classes,
`InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and zero ImmersiveNPCs payload
entries. The candidate build SHA-256 is
`D57A803EA8D9519A12CD3EA5728D6D2698370C2D68F3EA1E77A382A95F5FB79D`;
it has not been deployed or accepted in game. The installed RPG save JAR
remains at the earlier SHA-256 above.

## Preservation and release gates

The full primary checkout and both Teleport worktrees, including untracked
files and Git metadata, have verified copies outside active checkouts and saves
under `C:\Users\Zemio\.codex\backups\hywind-recovery-20261010-111255-1589726b`.
All 297,892 copied files passed source-to-backup SHA-256 comparison. The
primary checkout, Teleport branches, and active RPG save remain untouched.

Do not stage archived saves, player or NPC data, generated binaries, caches,
large QA evidence archives, sensitive configuration, or unrelated primary
files. Before the recovery commit, review every staged path, run compilation,
relevant unit tests, and offline package validation. Do not launch a native
server, deploy a JAR, alter the save, push, or promote this branch to `main`
as part of that review. Record the exact approved commit and test outcome after
approval; assign a new revision only when an engineering pass actually meets
its completion gate.
