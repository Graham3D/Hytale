# R069 Spark triple-plane, block-step, and impact presentation

## Outcome

R069 applies the requested bounded Spark presentation and terrain pass:

- Each Spark carrier now renders three intersecting, mutually perpendicular planes: horizontal `+Y`, vertical `+X`, and vertical `+Z`.
- Every plane retains the prior plane's 80x80 authored dimensions, fullbright/double-sided material behavior, transparent blue/white texture, and synchronized four-frame animation at 100 ms per frame.
- A fresh R069 model/animation URI replaces the retired single-plane resources so clients cannot reuse the old geometry from cache.
- Ground resolution explicitly permits up to one block of elevation change in either direction. A two-block rise is a barrier impact; a two-block drop is unsupported and ends the projectile.
- Spark enemy contacts, wall contacts, and Ricochet contacts use the configured `Laser_Impact` particle. Range/lifetime expiry and unsupported-drop termination remain non-impact events.

No other Spark tuning changed: the skill remains 6 m/s over 15 m of actual path with its existing damage, 7 Mana cost, 1.25-second cooldown, three-to-five bolt count, steering, penetration, Electrified ownership, and Ricochet behavior.

## Verification and deployment

- Implementation commit: `6602ce642a32e465d2e70b25e96bf1e5ed7910ad`
- Package: `Hywind 0.1.0-merge.24`, revision `R069`
- Installed SHA-256: `B060542E6776364A639A89C83081031FD06C62AEFFA011FD0CE33DB74090AF26`
- Full retained Gradle validation: PASS (39 tasks; 2,379 RPG JUnit tests)
- CustomUI validation: PASS (59 documents)
- Package/hash audit: PASS (5,866 entries; 2,210 classes; 17 authored icon hashes)
- Isolated unified-plugin smoke: PASS
- Deployment dry run: PASS
- Post-deployment start/stop validation: PASS (two cycles)
- Active first-party project JARs: `Hywind.jar` only
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T205310Z`

Connected QA should verify the three-plane silhouette from multiple camera angles, synchronized animation on all planes, one-block ascent/descent, two-block termination, and `Laser_Impact` on enemy/wall/Ricochet contact.
