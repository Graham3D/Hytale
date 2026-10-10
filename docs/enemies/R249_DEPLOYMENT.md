# R249 deployment receipt

- Active save: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG`
- Active mod: `mods\Hywind.jar`
- Revision: `R249-U7P5A` (`0.2.0-R249-U7P5A`)
- Deployed JAR SHA-256: `8666C66C3127C4E0593A2263CC5729C850E43A5F135071A21E144FCBEFD46E7E`
- Source commit embedded in JAR and pushed to `codex/checkpoint-c-optional-bridge`: `2f2f74098f70c38ed3a77da5b010e83ece58817c`
- Previous active JAR SHA-256: `B68C61C4CA1A40B75D6238B604B1E71E69CE5028FCF062E5FD4D49A490251803`
- External recovery copy: `C:\Users\Zemio\.codex\recovery\Hywind\Hywind-R248-20261009T230819.jar`
- Installed native server SHA-256 (unchanged): `A032A64E03390CA0AEB6C03C3B1600EAA892C8BC833D1BF3AC39307CCDF3CB20`
- Active `world-config.json` SHA-256 (unchanged): `3D7E86AB1B97F36E41F124FD3E03C682923F77F59B898FCE8CA0221542DF7986`
- Offline `:check` and package asset audit: **PASS**; details in [verification](R249_VERIFICATION.json).
- Connected-game acceptance: **NOT_RUN**. Monster Affix status: **OPEN**; owner acceptance: **false**.

The deployment replaced only the active `Hywind.jar`. The save, encounter records and world configuration were not rewritten. No game or standalone Hytale server was launched. Follow the [connected QA run sheet](R249_RELEASE_CANDIDATE.md) after restarting the world.
