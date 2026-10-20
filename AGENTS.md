# User QA workflow

- Do not start standalone native/local Hytale servers for QA, smoke tests, or build validation. The user tests new builds in the main game. This supersedes older disposable-server instructions and scripts.
- Use compilation, unit tests, and offline package/asset validation. Do not run server-launching checks such as `Run-HywindSmoke.ps1`, `Test-HywindDeployment.ps1`, or `Start-InventoryUiProbe.ps1`.
- When asked to deploy a build, back up the existing mod JAR, deploy the new `Hywind.jar` to the main RPG save, verify its checksum, and give the user the short in-game test steps. Do not launch the game or an authentication flow.
- Preserve existing save data and unrelated workspace changes. Deployment alone is not connected-client acceptance.
- Use `Hytale/data/pre-release/Saves/RPG` as the only active RPG save and Hywind deployment target. Keep recovery copies outside `Saves`; do not create or update a second RPG save for routine QA. The spatial-inventory player data from the former R132 copied save is now in `RPG`.
- When adding, renaming, changing, or removing an essential player-facing or gameplay/QA command, update `docs/COMMANDS.md` in the same change. Record exact syntax, permissions, effects, and any save/build gate; omit internal one-off probes.
