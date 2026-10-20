# Baseline native damage-leaf proc metadata

The installed stock `Assets.zip` SHA-256 is `C02E69A0FA4088B965D0CA9D01C6A55610229EFA3F78F91FB45463108F07D022`. `tools/New-NativeProcMetadata.py` refuses a different archive and pins the 2,040 packaged carriers in 18 families and the 4,181 managed damage leaves in 686 files. It traverses the installed roots, interactions, projectile configs, item variables, and packaged overrides, then audits literal item leaves and local Gear/Carriers damage assets. Missing or wrong metadata, an unexpected enumeration, or a new multi-projectile interaction fails verification.

Authored `RpgProcSelector` and `RpgProcCoefficient` on all 4,181 baseline managed damage leaves: 4,075 at `1.0` and 106 Shortbow Volley leaves at `1/3` (105 item leaves plus the literal fallback). All three installed Volley strengths launch exactly three projectiles. The other managed interaction graphs contain only single launches. Shared carrier leaves use stable asset IDs as selectors, so baseline focus, shield, and bomb attacks carry metadata without a WA-008, WA-014, or WA-141 action variant. `Complete-NativeGearCarriers.ps1` now emits the same metadata for its generated focus, shield, and bomb leaves, including the shield primary inline leaf. Its 24 graphs, stock timings, base damage, and non-consuming spellbook cost are retained.

`NativeAffixProcAssetMetadataTest` checks every packaged leaf and uses the offline native decoder on the emitted Volley and carrier metadata. It includes a wrong-selector control and the actual three-pellet budget at each strength. The JUnit class was added but was not executed because this worker was instructed not to run Gradle.

Commands executed from `C:\Users\Zemio\.codex\worktrees\hyarpg-separation`:

```powershell
Get-FileHash "$env:APPDATA/Hytale/install/pre-release/package/game/latest/Assets.zip" -Algorithm SHA256
& 'C:\OrbisBlock3\toolchains\python\cpython-3.11.13-windows-x86_64-none\python.exe' tools/New-NativeProcMetadata.py
& 'C:\OrbisBlock3\toolchains\python\cpython-3.11.13-windows-x86_64-none\python.exe' tools/New-NativeProcMetadata.py --verify
./tools/Complete-NativeGearCarriers.ps1 -VerifyOnly
& 'C:\OrbisBlock3\toolchains\python\cpython-3.11.13-windows-x86_64-none\python.exe' -m py_compile tools/New-NativeProcMetadata.py
git diff --check -- src/main/resources/Server/Item/Items/RPG/Gear src/main/resources/Server/Item/Interactions/RPG
```

Final audit output: `Verified 2040 carriers, 18 families, 4181 audited damage nodes, 686 files, 47 selectors; shortbow volley=3 pellets x 1/3`. Generator verification output: `Verified 450 managed focus/shield/bomb carriers and native graphs; promoted=False`. Python compilation and `git diff --check` succeeded. An independent JSON count found 4,181 leaves, no missing selector, 4,075 coefficients of `1.0`, and 106 of `0.3333333333333333`.

After any future carrier regeneration, rerun the exact Python author and `--verify` commands above. The generator fails if a new baseline family, damage leaf, or multi-projectile strike requires fresh authoring. No shared gameplay Java source, base skill, Actions3 class, action profile asset, native-primary template, binding disposition, or deployment state was changed. No Gradle, native game, server, auth, agent, deployment, commit, or push was run.
