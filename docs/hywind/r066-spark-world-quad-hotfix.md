# R066 Spark world-quad visual hotfix

## Outcome

R066 replaces Spark's failed particle presentation with a native model-backed visual. The connected yellow point was not the intended artwork: the projectile model's `0.06` parent scale collapsed the particle plane to roughly six percent of its authored size, while a separate `#fff36a` model light remained visible as the apparent fallback.

Spark now uses a fixed horizontal `+Y` fullbright quad sized to exactly one world block. The canonical transparent blue/white `chargedbolt.png` sheet is packaged under the model-valid `Common/VFX/RPG/Spark` root, and a looping UV animation advances its four 80x80 frames every six 60 Hz ticks (100 ms per frame). The obsolete particle emitter, inherited `0.06` scale, Fireball carrier model, and yellow light are removed. No movement, targeting, collision, damage, lifetime, resource, cooldown, or passive behavior changed.

## Verification and deployment

- Implementation commit: `ac7a77d25a1a9db4a80134d4824573e2ec4f3278`
- Package: `Hywind 0.1.0-merge.21`, revision `R066`
- Installed SHA-256: `5541596CF3EDA107658EAFB897A1449A69972F170FC6C56286335570464B9FE0`
- Full retained Gradle validation: PASS (39 tasks)
- CustomUI validation: PASS (59 documents)
- Package/hash audit: PASS (5,865 entries; 2,209 classes; 17 authored icon hashes)
- Isolated unified-plugin smoke: PASS
- Deployment dry run: PASS
- Post-deployment start/stop validation: PASS (two cycles)
- Active first-party project JARs: `Hywind.jar` only
- Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T193703Z`

Connected-client rendering remains the acceptance boundary. QA should confirm that each Spark is a clearly visible blue/white one-block horizontal plane, remains world-oriented while the camera moves, follows its existing authoritative projectile, animates all four frames, and disappears with that projectile.
