# R235-U7P5 — editable world spawn density setting

Date: 2026-10-08. Deployed to the active RPG save as `mods/Hywind.jar`.

## Connected R234 failure

The latest server log, `2026-10-08_17-56-08_server.log`, fails during HyARPG setup with `Refusing to reset world spawn density setting` caused by `WORLD_SPAWN_DENSITY_CHECKSUM_OR_SCHEMA`. The active save's `world-spawn-density.json` contains a valid `worldSpawnDensityMultiplier: 8.0`, but its legacy checksum was generated for the previous `4.0` value. The owner changed a user-facing setting; the checksum gate rejected that valid manual edit before the world could load.

## Repair

- Read both the earlier checksummed envelope and a simple JSON setting. In either format, schema version 1 and a finite multiplier from 0.25 to 8.0 are authoritative. A stale legacy checksum no longer blocks a valid owner edit.
- Future `/rpg spawns <multiplier>` writes use simple JSON without a checksum. Command changes still apply to loaded spawning worlds; edits to the file while the world is stopped apply on restart.
- Malformed JSON, unsupported schema, and out-of-range values remain rejected without silently resetting or overwriting the setting.
- No spawn algorithm, Master Enemies rarity/packs/affixes, or save data was changed. The active save's manually edited 8.0 setting was preserved byte for byte during deployment.

## Verification and deployment

- Focused `WorldSpawnDensitySettingsTest` passed, including the exact stale-checksum 8.0 regression, simple JSON manual edit, invalid-value rejection, and native density target behavior.
- Offline JAR/package and U7P5 asset validation passed. No standalone Hytale server was launched.
- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`; SHA-256 `6C1C631E3F933C33967A45176D49F0C20190FC97B7C1630072378360084528ED`.
- Backup of R234: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R234-U7P5-8D099DB1-20261008.jar`; SHA-256 `8D099DB16E4AFE20535FF7370A06AAD17D7E5D8BABA2C6F859ECFFA325A4E90C`.
- Connected acceptance pending owner QA: restart the same RPG save, join, and run `/rpg spawns status`. It should report `8.00x`; compare effective versus native target/cap. Deployment and offline tests do not establish connected acceptance.
