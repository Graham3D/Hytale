# R063 Spark QA/QC pass

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

R063 reworks the existing `charged_bolt` implementation in place and adopts **Spark** as its canonical player-facing name. The stable skill ID, save/load identity, icon filename, damage, Mana cost, cooldown, bolt-count range, travel range, and root-cast Electrified ownership remain unchanged. `Charged Bolt` remains a legacy catalog alias for migration and command compatibility.

Implemented behavior:

- every cast releases the existing randomized three-to-five projectile batch from a ground-resolved point near the caster;
- launch ignores camera pitch and projects aim onto the horizontal plane;
- each Spark independently follows terrain at roughly half a block above the surface, climbs and descends bounded terrain changes, and terminates if ground support is lost;
- replay-stable piecewise randomized steering produces constant irregular lateral wander, occasional larger deviations, and a persistent forward bias without using clean cone rays or a sine wave;
- the six-meter budget is measured over actual traveled segments rather than straight-line displacement;
- enemy contact no longer terminates Spark, while each projectile's existing hit ledger still limits it to one hit per target and separate projectiles may hit the same target;
- solid terrain terminates Spark unless the existing Ricochet continuation owns a remaining bounce; existing passive precedence, bounce count, and speed penalty are retained;
- the existing charged-bolt art now renders as a compact horizontal ground-oriented particle plane with a hidden native carrier; and
- the previous cast-origin effect and strike/unarmed animation are suppressed only for Spark.

Validation completed:

- focused Spark, Lightning, projectile-continuation, passive, and presentation tests — PASS;
- `gradlew.bat clean check` — PASS;
- 59 CustomUI documents — PASS;
- unified package audit — PASS (5,864 entries, 2,209 classes, 2,090 UI documents);
- isolated unified-plugin smoke — PASS;
- deployment dry run and candidate/install hash equality — PASS; and
- two deployed startup/restart cycles — PASS, with Hywind and Taverns started, clean shutdown, no legacy project plugin discovered, and all persistent data roots retained.

Artifact: `Hywind.jar` (`0.1.0-merge.18`, `R063`)

SHA-256: `D0E6BE36FDD87323C83BC81650E95F6BDB79299BCD820BF90B7DFD6D5303B2EC`

Implementation commit: `cd52435c4f6494906c8feff1da925b621b749e6b`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T181744Z`

Connected-client verification remains pending for flat, uphill, and downhill travel; skyward camera aim; one-bolt enemy penetration through multiple targets; wall termination; Ricochet wall behavior; independent chaotic trajectories; compact horizontal VFX orientation; and absence of the old cast animation/effect.
