# R064 Spark native-course hotfix

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

Connected R063 telemetry proved that every Spark batch spawned correctly but all native visual carriers emitted a contact-free `NATIVE_COURSE_ENDED` roughly 65 ms later. Because Spark intentionally gives its native carrier zero velocity while Hywind owns terrain-following movement, the generic native callback incorrectly cancelled all projectiles before visible travel.

R064 gives that exact contact-free condition to the Spark ground crawler. The delayed callback removes the native queued despawn, restores the authoritative manual position/physics state, and leaves the projectile registered for normal ground traversal. Real entity contacts, real block contacts, and every non-Spark projectile retain the existing native-impact behavior. A one-time `PROJECTILE_NATIVE_COURSE_IGNORED` event now records this recovery in the skill trace.

Validation completed:

- focused Spark and Lightning regression tests — PASS;
- `gradlew.bat clean check` — PASS;
- 59 CustomUI documents — PASS;
- unified package audit — PASS (5,864 entries, 2,209 classes, 2,090 UI documents);
- isolated unified-plugin smoke — PASS;
- deployment dry run and candidate/install hash equality — PASS; and
- two deployed startup/restart cycles — PASS, with Hywind and Taverns started, clean shutdown, no legacy project plugin discovered, and all persistent data roots retained.

Artifact: `Hywind.jar` (`0.1.0-merge.19`, `R064`)

SHA-256: `AD3D9D87CE0BECAA392AB158B9C7EFB15DC45FC86B9F8C774AF369A0C4AE6B01`

Implementation commit: `c5b44f47a3c35eb1d7ab8591eedcd0b0e38df16d`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T183655Z`

Connected verification should confirm that Spark now travels visibly instead of flashing once, then continue through the R063 terrain, penetration, wall/Ricochet, steering, and presentation matrix.
