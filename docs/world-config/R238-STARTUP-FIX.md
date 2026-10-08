# R238 startup fix: save-root resolution

## Connected failure

The RPG save's `2026-10-08_19-36-38_server.log` reports `RPG_SAVE_ROOT_UNAVAILABLE` from `HyArpgPlugin.setupRpg` during R237 plugin setup, before world entry. Hytale supplies the plugin data path as `mods\InigmasGames_HytaleRPGPhase00Audit` relative to the active save. R237 called `getParent().getParent()` on that relative path, producing `null` and aborting plugin setup.

## Correction

R238 resolves the plugin data path against the Hytale process directory before finding its `mods` parent and RPG save root. It still rejects a path outside an existing `mods` directory. No world configuration values, encounter logic, or packaged game assets were changed for this fix.

## Offline verification and delivery

- `HywindWorldConfigurationTest` passes, including a regression for Hytale's relative `mods\...` path and an absolute-path case.
- `:jar --offline` and `Test-HyArpgPackage.ps1` pass for `0.2.0-R238-U7P5`.
- The R237 and R238 JARs contain the same 16,768 packaged `Common/`, `Server/`, and `Client/` asset paths and sizes.
- Deployed to the active RPG save as `mods\Hywind.jar`. SHA-256: `6A7FF41FEF07733636E10A7B34DA011309CB5315E0D86A3504544E9361B55D49`.
- The prior JAR is backed up outside `Saves` at `C:\Users\Zemio\.codex\deploy-backups\R238-20261008T234341601Z\Hywind-before.jar`.

Connected acceptance remains for the owner: restart Hytale, join the RPG save, and confirm `/rpg worldconfig status` shows the preserved configured density.
