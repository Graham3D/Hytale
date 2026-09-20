# R070 Spark projectile-passive compatibility

Status: deployed; connected-client verification pending.

Spark's stable `charged_bolt` definition now exposes the projectile capabilities already owned by its compiled/runtime implementation. The shared compatibility authority therefore permits the Skill Tree to parent Piercing, Fork, Chain, Ricochet, Return, Volley, Barrage, Homing, Accelerant, Ballistics, Shrapnel, Splinterburst, and Orbit to Spark. This is a compatibility correction, not a new skill or balance pass.

Volley compiles to a three-projectile multiplier for every authored Spark. Since Spark selects three to five authored bolts per cast, Volley produces a bounded nine to fifteen carriers under the existing root-cast budget, cost, cooldown, magnitude, and hit-proc rules. Homing now feeds its validated target steering into Spark's manual ground movement and projects the result back onto the horizontal plane; terrain support, step limits, walls, range, and Spark's irregular steering remain authoritative.

Automated verification includes direct compatibility checks for every projectile-family passive, an authoritative Skill Tree graph link from Volley to Spark, compiled modifier assertions, and a nine-to-fifteen carrier batch assertion. `clean check`, the package audit, isolated smoke, deployment dry run, and two startup/restart cycles against the installed JAR all passed.

- Version/revision: `0.1.0-merge.25` / `R070`
- Implementation commit: `0aa56967be641d9cdb1ef6953887920ab2027bba`
- Installed SHA-256: `3E8C7A11A9E9DED4A107A8C7E85221862B748445071169311B7D2DAF53052AE3`
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T212930Z`
- Active first-party project mod: `Hywind.jar`

Connected QA should confirm that Volley can be dragged/parented to Spark in the Skill Tree, that Save preserves the link, and that Volley releases nine to fifteen visible Sparks. The other projectile passives should be sampled for their documented behavior, with particular attention to horizontal ground-constrained Homing, wall Ricochet, and continuation order.
