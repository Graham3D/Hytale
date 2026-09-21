# R071 native ability icon repair

Date: 2026-09-21. Version: `0.1.0-merge.26`. Implementation commit: `242f9e27c097437b363cc6d4e529c11c8e58dae9`.

## Connected failure and correction

The connected trace proved that Skill Tree Reset cleared the RPG loadout, but Hytale rejected both native AbilitySlots removals as `NATIVE_CONTAINER_WRITE_REJECTED`. The stale `RPG_Ability_Charged_Bolt` and `RPG_Ability_Static_Field` stacks therefore survived Reset and reconnect, retaining corrupted client icon state. Re-equipping Spark reused the same native item ID, so the old projection path treated it as already correct and never issued a fresh client projection.

R071 keeps conservative ownership but moves Hywind-owned `RPG_Ability_*` removal and replacement to the native container's authoritative filter-bypass transaction overload. It verifies the resulting slot state, never clears or replaces a foreign/native rune, and performs one remove/re-add refresh when a matching owned item is encountered at `PLAYER_READY`. Periodic reconciliation does not churn matching items. This repairs already-affected saves on the next join and makes Reset → Save/Exit → re-equip use a fresh native HUD projection.

## Validation and deployment

- Focused R071 native-container regression: PASS, including clear/reinstall and foreign-rune preservation.
- Complete merged `clean check`: PASS; 2,381 RPG tests, 68 native-control tests, 47 Canvas tests, all retained Tavern and NPC gates, and 59 CustomUI documents.
- Package audit: PASS; `Hywind.jar` contains 5,866 entries and reports `R071 / 0.1.0-merge.26`.
- Isolated unified-plugin server smoke: PASS.
- Deployment dry run and full stopped-save backup: PASS.
- Exact installed-byte two-cycle startup/shutdown verification: PASS.

Installed JAR SHA-256: `C77AB5EA1B22CEBA254B3B3EF5F89FF7671735486F648FE8E605AE2FEED83606`.

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260921T125306Z`.

Connected verification remaining: join once so `PLAYER_READY` refreshes the existing Spark projection, confirm Spark's correct icon, then verify Reset → Save/Exit clears the HUD slot and re-equipping Spark restores the correct icon.
