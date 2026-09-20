# R068 Spark lifecycle, animation, and expiry correction

## Outcome

R068 corrects the three connected-QA failures without changing Spark's gameplay tuning.

- The trace identified `OWNER_PROJECTILE_BUDGET` as the no-cast cause. Spark's variable three-to-five-bolt pattern reserved the maximum five launches but decremented only the bolts selected for that cast, leaving one or two pending reservations after some casts ended. Root accounting now declares the exact deterministic selected count. A same-owner 32-cast regression verifies that every completed cast leaves zero active carriers and zero retained roots.
- The world quad's four-frame loop is now mapped to both `Idle` and `FlyIdle`. Spark's custom ground movement intentionally zeros native velocity, so the carrier remains in `Idle`; the previous FlyIdle-only mapping could display only the first texture frame. The animation uses a fresh R068 URI while retaining four 80x80 frames at six ticks, or 100 ms, per frame.
- Spark no longer emits the generic projectile-expiry presentation. This removes the short vertical line that appeared only when a Spark carrier terminated.

Spark remains a two-block horizontal quad travelling at 6 m/s over 15 m of actual path. Damage, 7 Mana cost, 1.25-second cooldown, bolt count, movement, collision, terrain following, penetration, Electrified ownership, and Ricochet behavior are unchanged.

## Verification and deployment

- Implementation commit: `869d484d01e1d82d1edf8aed9eb92942bedb744c`
- Package: `Hywind 0.1.0-merge.23`, revision `R068`
- Installed SHA-256: `7A5CAD3020BF53E7626F45DDF8D91E6ED4A4B90C5F94F1676D10416741D50353`
- Full retained Gradle validation: PASS (39 tasks; 2,378 RPG JUnit tests)
- CustomUI validation: PASS (59 documents)
- Package/hash audit: PASS (5,865 entries; 2,209 classes; 17 authored icon hashes)
- Isolated unified-plugin smoke: PASS
- Deployment dry run: PASS
- Post-deployment start/stop validation: PASS (two cycles)
- Active first-party project JARs: `Hywind.jar` only
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T202053Z`

Connected QA should confirm repeated Spark casting after multiple completed volleys, visible cycling through all four frames, and clean termination without the vertical expiry line.
