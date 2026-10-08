# R231-U7P5 — Elite tint rollout and difficulty spawn rates

Date: 2026-10-08. Deployed to the active `RPG` save as `mods/Hywind.jar`.

## Production behavior

All production-certified promoted roles use the same R230 `HytaleEliteTint.applyEliteTint(actor, rarity)` native per-entity effect at birth or legitimate rebind. The call is role-independent; no role texture, mask, shader, or per-tick tint path was added. The existing native visual-scale projection remains Champion **1.15×**, Unique **1.30×**, and authored Super Unique **1.45×**. Rarity colors remain Champion blue `#1d4dff`, Unique purple `#a000ff`, and Super Unique orange `#ff9100`. Tint application is isolated from palette and weapon presentation failures so a separate presentation error cannot skip the tint attempt. Offline validation proves the shared server path and packaged effects; final pixels still require connected owner observation.

The natural production birth planner now uses one existing `me.rarity` RNG draw against the frozen source difficulty. It still uses the same eligibility, certified native bindings, affix selector, encounter persistence, and rewards. Super Unique is absent from all random rows and remains authored-only.

| Difficulty | Normal | Champion | Unique | Total Elite |
| --- | ---: | ---: | ---: | ---: |
| Normal | 92.0% | 1.6% | 6.4% | 8.0% |
| Nightmare | 86.0% | 2.8% | 11.2% | 14.0% |
| Hell | 80.0% | 4.0% | 16.0% | 20.0% |

Within each Elite result, Champion is 20% and Unique is 80%. The profile stores integer weights out of 1,000 to preserve deterministic threshold handling. Existing saved enemy descriptors retain their balance revision identifier (`master-enemies-v1.0`); this birth-odds change does not change their persisted mechanics.

The existing world mob-density multiplier scales native environmental spawn targets and caps before the natural Master Enemies promotion roll. More eligible natural spawns therefore create more Elite opportunities at the difficulty's fixed probability. This is not an uncapped guarantee of more simultaneously loaded Elites: native capacity and the existing Master Enemies pack limits still apply. No new spawn system or 27-affix implementation was changed.

## Verification and deployment

- Focused rarity, role-matrix, tint/scale, and world-admission tests passed.
- Offline `gradlew check` passed: **3,411 tests in 341 suites**, zero failures/errors; asset compatibility and packaging gates passed.
- No standalone server was launched. Connected appearance and spawn-rate sampling are for owner QA.
- Final `Hywind.jar` SHA-256: `EC57A87B72AFBF881C5AEC5A3D0964E31D708FB31CB38DF748CBAB57B426AB35`.
- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`; deployed checksum verified.
- Prior R230 JAR backup: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R230-U7P5-4EE2AC5D-20261008.jar`; SHA-256 `4EE2AC5D9D405E806C85CEED86F7DACB29F232B25603F0949A12AD962FB6E9D9`.
