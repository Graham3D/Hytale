# User QA workflow

- Do not start standalone native/local Hytale servers for QA, smoke tests, or build validation. The user tests new builds in the main game. This supersedes older disposable-server instructions and scripts.
- Use compilation, unit tests, and offline package/asset validation. Do not run server-launching checks such as `Run-HywindSmoke.ps1`, `Test-HywindDeployment.ps1`, or `Start-InventoryUiProbe.ps1`.
- When asked to deploy a build, back up the existing mod JAR, deploy the new `Hywind.jar` to the main RPG save, verify its checksum, and give the user the short in-game test steps. Do not launch the game or an authentication flow.
- Preserve existing save data and unrelated workspace changes. Deployment alone is not connected-client acceptance.
- Use `Hytale/data/pre-release/Saves/RPG` as the only active RPG save and Hywind deployment target. Keep recovery copies outside `Saves`; do not create or update a second RPG save for routine QA. The spatial-inventory player data from the former R132 copied save is now in `RPG`.
- When adding, renaming, changing, or removing an essential player-facing or gameplay/QA command, update `docs/COMMANDS.md` in the same change. Record exact syntax, permissions, effects, and any save/build gate; omit internal one-off probes.

# Development baseline and revision safety

- Read `docs/DEVELOPMENT_BASELINE.md` before starting engineering work. Verify its source commit against the actual branch, `git worktree list`, `git status`, build metadata, and any newer committed or uncommitted work. A revision label or an old chat's worktree path alone does not establish the latest source.
- Start new work from the latest verified committed development baseline. Inspect and preserve modified, staged, and untracked files in every relevant worktree before creating another worktree or applying changes. Never reset, clean, restore, overwrite, or remove unrelated work as a shortcut.
- Run `tools/guardian/Invoke-Guardian.ps1` preflight before engineering edits and review its change check before staging. See `docs/GUARDIAN.md`; Guardian supplements the QA and preservation rules below.
- Keep this `AGENTS.md` in every new development worktree. Preserve the separate HyARPG/Hywind and ImmersiveNPCs runtime boundary; the internal plugin identity, entrypoint, and save compatibility require a deliberate review before any migration.
- After each completed engineering pass, record the exact source branch and commit, build revision and version, verification performed, and deployment or connected-QA status in the baseline record. Update `gradle.properties` and generated build identity inputs consistently when a new revision is actually completed. Do not label an uncommitted candidate, a build, or a copied JAR as a new completed or accepted revision.
- Review the complete staged inventory, including newly added files, before committing. Exclude generated binaries, caches, archived saves, player or NPC data, large QA archives, secrets, and unrelated work unless each is specifically reviewed and authorized. Do not force push or destructively clean up a checkout.
