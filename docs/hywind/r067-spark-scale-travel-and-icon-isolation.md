# R067 Spark scale, travel, and texture-isolation correction

## Outcome

R067 applies the connected QA tuning without changing Spark's combat rules:

- The horizontal world quad is now two blocks wide by two blocks deep.
- Travel speed is 6 m/s, half the R066 value.
- Actual-path range is 15 m, 50% farther than R066.
- Maximum flight time is 2.5 seconds.
- Damage, 7 Mana cost, 1.25-second cooldown, three-to-five bolt count, steering, collision, terrain following, penetration, Electrified, and Ricochet behavior are unchanged.

The E/R terrain-snapshot icons were not loadout misbindings. Runtime traces projected the intended `RPG_Ability_Charged_Bolt` and `RPG_Ability_Static_Field` items, while both packaged 128x128 authored icons passed exact hash verification. The new factor was Spark's horizontally animated 320x80 model texture. R067 replaces it with a fresh 80x320 vertical strip and native-style Y-axis UV offsets, removes the old horizontal URI from the package, and retains the authored HUD icons unchanged.

The detailed trace also explains the apparent no-cast event: activation was rejected as `INSUFFICIENT_RESOURCE` while the HUD displayed `LOW MANA`; Spark still costs 7 Mana. A later cast in the same trace passed validation and spawned its three projectiles after Mana recovered.

## Verification and deployment

- Implementation commit: `ea19efd0a6fe623c852fde2ebc977bb6e504b817`
- Package: `Hywind 0.1.0-merge.22`, revision `R067`
- Installed SHA-256: `50C0EACCD46F167AE9B24B010811C3A4732F7D3FAE831930E85098D7BD323B49`
- Full retained Gradle validation: PASS (39 tasks)
- CustomUI validation: PASS (59 documents)
- Package/hash audit: PASS (5,865 entries; 2,209 classes; 17 authored icon hashes)
- Isolated unified-plugin smoke: PASS
- Deployment dry run: PASS
- Post-deployment start/stop validation: PASS (two cycles)
- Active first-party project JARs: `Hywind.jar` only
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T200055Z`

Connected QA should confirm the larger/slower/longer Spark presentation, the four-frame animation, and correct authored ability icons in the E/R HUD slots.
