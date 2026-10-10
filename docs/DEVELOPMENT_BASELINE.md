# HyARPG/Hywind development baseline

## Recovery candidate (2026-10-10)

The source starting point was `codex/teleport-r250-latest` at
`44184d936d750e0a6729a8f797e7f6a4fe30356f`, revision
`R250-U7P5B` (`0.2.0-R250-U7P5B`). The isolated reconciliation branch is
`codex/hywind-r250-recovery`; its approved local recovery commit is
`e5ccefcb0fe228188c4f1d9d9b4aeee3dc9014c9`. The asset-audit correction
described below is recorded at
`c2f45417a53fe5ec5566f34d195bb88e491a4120`. Subsequent documentation
updates do not create a new completed or deployed gameplay
revision. Before subsequent work, check the branch's actual HEAD and status.

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
That failure is retained here as the original recovery-commit result. The
historical `tools/u7p5-r248-package-baseline.json` is unchanged (SHA-256
`69464E8C153519CA2F97CF76F9A6E17055401A3F2F1A18084C1672CA57E8C168`
with CRLF normalized to LF; the Windows checkout's raw SHA-256 is
`5A4F9012188CFD99E6BE6EF35C4E9EF91567AE32ABD2B12A29C2A64F843D2E4B`).
The audit checks the normalized file hash before using this baseline, as well
as its internal R248 JAR identifier.

The separately approved R250 audit correction uses the verified R249B JAR
(SHA-256 `A0EA6EEB80577AA30AA787475E7B7750417F5DE7530A57EC7DF72DE7D8E13101`)
as its reference. Of 5,014 JSON assets, 1,727 differ from the recovered R250
candidate only in CRLF/LF bytes. The only two semantic JSON changes are the
Teleport skill catalog and lightning runtime definitions. A separate reviewed
record pins the normalized hash of all 4,917 other guarded JSON paths and the
individual normalized SHA-256 values of the remaining 97: 94 R249 additions,
one R249 native-binding change, and two R250 Teleport changes. Normalization
replaces CRLF with LF only; all other bytes still affect the digest. Existing
raw package-to-source checks on reviewed assets and byte-sensitive non-JSON
checks remain in force. The plugin identity, asset count, reference, and
packaging checks also remain in force.

The full `check` runs `auditU7P5AssetCompatibility` after `jar`; `jar` consumes
the root and Tavern `processResources` outputs, whose JSON resources are Gradle
task inputs. The audit also compares every packaged JSON asset path with the
root and Tavern source trees, except the single stock prefab generated by
`processResources`. It checks normalized content for guarded assets and exact
bytes for the 97 reviewed assets. A direct audit of an older JAR therefore
rejects a changed, added, or removed source JSON file instead of trusting the
packaged aggregate hash alone. Non-JSON byte-sensitive checks remain exact.

Offline adversarial probes passed: an intentional line-ending-only variant
was accepted, while a guarded-content edit, Teleport-content edit, missing
asset, and unexpected asset were each rejected. This correction is offline-only;
connected-game acceptance is pending. A direct audit against an older JAR also
accepted a source-only CRLF/LF change, rejected a guarded source-content edit,
and rejected an R248 baseline-file edit that left its internal JAR ID intact.

The earlier offline `check -x auditU7P5AssetCompatibility` passed: 3,488 root tests,
452 native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, all
with zero failures or skips. The retained Tavern feature checks and resource,
native-proc, spatial-grant, CustomUI, campaign-biome, and package audits also
passed. The package validator reported 19,436 entries, 2,158 classes,
`InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and zero ImmersiveNPCs payload
entries. That pre-commit candidate build's SHA-256 was
`D57A803EA8D9519A12CD3EA5728D6D2698370C2D68F3EA1E77A382A95F5FB79D`;
it was not deployed or accepted in game.

Before the audit safeguard commit, the full unmodified offline `check` passed
with 3,488 root tests, 452 native-control tests, 49 CanvasUI tests, and 5
Tavern JUnit tests, all with zero failures or skips. The corrected asset audit
passed 5,014 JSON assets (4,917 aggregate-guarded and 97 individually pinned)
and 41,251 references. The package validator again passed with 19,436 entries,
2,158 classes, the same `HyARPG` identity, and zero ImmersiveNPCs payload
entries. That pre-follow-up **local, undeployed** JAR has SHA-256
`51574DA7B7006C550F0CB28F453767F41C8573FEF83E34A5EC2D2D63E081A919`.
The installed RPG save JAR remains at the earlier SHA-256 above.

After commit `c2f45417a53fe5ec5566f34d195bb88e491a4120`, the complete
unmodified offline `check` passed again: 3,488 root tests, 452 native-control
tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with zero failures or
skips. The corrected asset audit passed all 5,014 JSON assets and 41,251
references; direct package validation passed 19,436 entries, 2,158 classes,
the `HyARPG` identity, and zero ImmersiveNPCs payload. The JAR built from
that exact commit has SHA-256
`4D89CEC91E3B53C499624D155C88B48994771C86C742F441AB2D9AFF940B183C`.
It was not deployed or accepted in game. A later documentation commit changes
the embedded source-commit field and therefore produces a different JAR hash;
record that build separately rather than attributing it to `c2f45417`.

## GitHub integration status

As assessed on 2026-10-10, GitHub `main` was
`209e38c01ef5db153a18f723ddc3b05866844438`, an ancestor of the recovery
branch. Publication of `codex/hywind-r250-recovery` and its pull request is
authorized for review; promotion to `main` requires separate approval. The
original primary checkout remains on its older local `main` with modified and
untracked work. Future engineering must use a new worktree from the latest
verified remote baseline and verify its actual HEAD, rather than infer source
freshness from the primary checkout or a revision label. Recheck GitHub refs
before any integration.

## Preservation and release gates

The full primary checkout and both Teleport worktrees, including untracked
files and Git metadata, have verified copies outside active checkouts and saves
in a local recovery backup. All 297,892 copied files passed source-to-backup
SHA-256 comparison. The primary checkout, Teleport branches, and active RPG
save remain untouched.

Do not stage archived saves, player or NPC data, generated binaries, caches,
large QA evidence archives, sensitive configuration, or unrelated primary
files. Review every staged path and run compilation, relevant unit tests, and
offline package validation for subsequent work. Do not launch a native server,
deploy a JAR, alter the save, push, or promote this branch to `main` without the
appropriate approval and release gate. Assign a new revision only when an
engineering pass actually meets its completion gate.
