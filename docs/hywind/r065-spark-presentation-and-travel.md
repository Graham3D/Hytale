# R065 Spark presentation and travel correction

## Outcome

R065 replaces Spark's point-sized presentation with one horizontal one-block particle plane. The existing `chargedbolt.png` sheet remains canonical: its four 80x80 cells are displayed in order at 100 ms intervals over a 400 ms loop. The particle stays attached to the manually ground-resolved carrier instead of leaving world-space trail pixels.

Spark's authored travel is also slower and longer: 12 m/s over a 10 m actual-path budget (approximately 0.833 seconds). The matching native projectile launch and terminal velocities are 12 m/s, preventing startup audit drift. Damage, Mana cost, cooldown, bolt count, per-bolt hit rules, Electrified deduplication, wall termination, and Ricochet behavior are unchanged.

## Verification and deployment

- Implementation commit: `d220c2b80e26a0f69c1eb239d336a7fb8ece4450`
- Package: `Hywind 0.1.0-merge.20`, revision `R065`
- Installed SHA-256: `1D577A47E9475E4EA392AA837A82909FB915978724219CB8C09FC56C036C94C0`
- Full retained Gradle validation: PASS
- CustomUI validation: PASS (59 documents)
- Package/hash audit: PASS
- Isolated unified-plugin smoke: PASS
- Deployment dry run: PASS
- Post-deployment start/stop validation: PASS (two cycles)
- Active first-party project JARs: `Hywind.jar` only
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T190709Z`

Connected-client confirmation remains pending for the exact one-block visual footprint, horizontal orientation, four-frame cadence, terrain tracking, and the revised 12 m/s / 10 m travel feel.
