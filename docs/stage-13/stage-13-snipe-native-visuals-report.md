# Stage 13 Correction U — native Snipe presentation and straight flight

Baseline: RPG `9926653`, deployed R032-T. Candidate: **R032-U / 0.0.25**.
Scope: the owner's three connected observations about Snipe's drawn pose,
yellow arrow charge effect, normal arrow appearance and unwanted ballistic drop.

## Evidence and diagnosis

The owner reports that T fires, but its presentation does not match the desired
native charged bow and its projectile drops. This is connected owner evidence,
not an automated measurement of its trajectory or animation.

T deliberately selected the native charged Shortbow Strength4 **85 m/s,
gravity 25 m/s²** following the previous delegated interpretation of “straighter.”
Gravity was present in both the RPG profile and native projectile config. The
new explicit instruction requires a straight shot, so U sets both to **zero**.
It does not alter other projectile profiles or globally change native physics.

T's `RPG_Snipe` visual model copied the native carrier and arrow attachment but
omitted native `FlyIdle` animation and both Arrow trails. U removes that partial
model and references **Arrow_Crude**, the actual shipped model used by native
Shortbow Strength4. The normal native model itself also uses a projectile
carrier plus an arrow attachment; the presence of that carrier alone is NOT
proof of the reported black appearance. Missing animation/trails are verified
differences. Whether those differences fully explain the owner's rendered
black-arrow symptom still requires connected visual comparison.

The installed Shortbow `ShootChargingHold` is the native looping fully drawn
pose, including first-person, third-person and moving variants. Its first-person
animation explicitly makes `ARROW-PLACEHOLDER` visible at frame zero and poses
the bow/string. U preserves that exact animation instead of creating a new one
or reintroducing a timed draw ramp. The existing native `ShootCharged` release
animation remains unchanged in the executor.

T had no charge particles. The installed `Bow_Charging` effect uses native
`Bow_Charging_Circles` (yellow `#ffd323`) and `Bow_Charging_Sparks`
(gold `#ffad2f`). Its emitter start delays are 0.75 and 0.95 seconds, deliberately
tuned for the native timed draw. U's small `RPG_Snipe_Ready` particle-system
wrapper reuses those exact spawners and parameters, changing only their start
delays to zero. It supplies no new textures, geometry, colors or particle
animations. Native emitter-internal fade/spawn timing is retained; zero wrapper
delay is not a claim of frame-zero visible pixels.

The root attaches this effect to **PrimaryItem / Handle**, with the native
shortbow charge offsets, rotation and scale, explicitly choosing the equipped
bow rather than the ability-slot item. `ClearParticlesOnRemove=true` and the
existing interaction lifetime bound cleanup to release/cancellation. Native
draw sounds are reused with clear-on-finish. This is cosmetic reuse, not native
Signature Move activation or charge-stat manipulation.

## Exact scope / unchanged contracts

- Snipe flight: **85 m/s, zero gravity, 48 m authored range, 0.075 m radius**.
  Range remains RPG-authored, not a claimed measured native maximum.
- Payment remains 12 Stamina, one Crude arrow, 10 s cooldown, once on accepted
  release. Coefficient remains 2× audited uncharged weapon power.
- Hold/release root operations and callback are unchanged. No native damage,
  projectile, stat, cooldown or resource branch was added.
- Equipment validation, targetless paid misses, wrong-slot protection,
  once-per-chain delivery and linked-passive behavior remain unchanged.
- No executor, input adapter, persistence, escrow, reward, XP, resource formula,
  HUD layout or native HUD-ownership implementation changes. Only the existing
  top-right revision text advances to R032-U.
- Owner Fire Bolt, Quick Slash, Snipe and Whirlwind artwork is reimported through
  the established updater; no new icons are designed.

## Verification boundaries

`tools/Audit-SnipeNativePresentation.ps1` records native source hashes, animation
references, frame-zero arrow visibility, exact native model, yellow emitter
colors and delay-only particle-system equivalence in
`evidence/stage-13/cohort-u/native-presentation-sources.json`.

Native startup audit resolves the complete Charging root and verifies the
existing four compiled operations, one RPG callback, native animation assets,
PrimaryItem attachment, cleanup flag and native emitter IDs. Projectile audit
requires U's resolved model to equal the shipped Strength4 arrow model, including
FlyIdle and two trails, and requires zero gravity in both profile and serialized
native physics packet. Native source control still asserts gravity **25** for
the shipped bow; it is not falsified to match Snipe's explicit zero-drop override.

The native-codec regression decodes U's actual StandardPhysics configuration.
It checks zero gravity, velocity-facing rotation, no bounce and no native
gameplay interactions. It also invokes installed `PhysicsMath.computeDragCoefficient`
with the decoded values: native StandardPhysicsProvider derives this drag from
gravity, and the coefficient is zero. This is native construction/math evidence,
not a connected flight measurement.

Retained Snipe production-path tests still exercise paid empty-space release,
resource/equipment/death rejections, cooldown, stale-slot rejection, callback
deduplication and eligible continuations. Their gravity expectation changes
only because the owner explicitly changed the desired trajectory. Two new
native-control tests cover normal-arrow/zero-gravity construction and exact
native-emitter reuse. No tests were deleted, skipped or weakened for a pass.

The first full-suite attempt overlapped the isolated Hytale smoke server. Seven
owner-icon-updater tests correctly hit the existing “close Hytale and its
server” safety guard before reaching their fixture operations. This was a test
orchestration error, not a gameplay failure. The failed console/XML evidence
is retained. The smoke server finished normally; the complete suite was then
rerun with no Hytale client/server active. No guard or assertion was bypassed.

## Final build / deployment

**Deployed September 10 2026, 03:27:28 UTC** to
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods`.

- **2,166 tests PASS**: 2,097 root, 48 native-control, 21 CanvasUI; zero failures,
  errors or skips. All T baseline case identities retained. Full console and
  suite inventory: `full-validation.txt`, `test-results.json`.
- Exact final-JAR three-mod native startup / spawn / queued rollback / same-tick
  advance / resting expiry checks PASS. Model and particle references resolve.
- Complete three-JAR archive integrity and isolated atomic rollback/roll-forward
  PASS. Every packaged Java class matches the full-suite compiled candidate.
- JAR entry differential changes only the two native audits, revision badge,
  Snipe root cosmetics, Snipe gravity/catalog description, particle wrapper and
  removal of the incomplete Snipe model (plus two new ZIP directory entries).
  All other entries, including owner icons, are byte-identical to T.
- Exactly three live mods remain. CanvasUI and HytaleDevLib hashes unchanged;
  all **18 non-JAR mod-data files** verified unchanged. No live saves edited.
- R032-T is retained in `cohort-u/rollback/HytaleRPG-0.0.25.jar`.

SHA-256:

| Artifact | Hash |
| --- | --- |
| RPG `HytaleRPG-0.0.25.jar` | `BFF7421FA765834E32EEE819072AA9FF665C085FFCB0ECCFF9CBFF38BC3B965E` |
| Three-mod `Hytale-RPG-Stage13-U-snipe-native-visuals.zip` | `DC8BBAE5B39EF59472F37A5998B922CB17315AEA1044CBA06648481FA7EEDE6E` |
| CanvasUI | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HytaleDevLib | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| T rollback RPG | `0082B9149831E9BDE13F82E32F2907FAC2EF943C14CC230A16BF3B54A77D77F9` |

Full machine receipts are under `evidence/stage-13/cohort-u`, including
`snipe-native-visuals.json`, `jar-differential.json`, native audit JSON, importer
receipt, archive and rollback. Existing deprecation warnings remain; they did
not fail compilation. The package differential initially needed the two new
empty ZIP directory entries explicitly allowlisted; no unrelated file/content
allowance was added. No formal connected performance or production-acceptance
gate was declared passed by these local runs.

## Minimal connected checklist (still required)

1. Restart Hytale, join RPG and confirm **R032-U** at top right.
2. Equip a bow, carry Crude arrows, and equip Snipe to skill01 or skill02 using
   `/rpg skilltree`. Use `/rpg dev ability-status` to confirm projection if needed.
3. Hold its native ability key (E or R with the owner's current bindings).
   Check first-person full draw and yellow rings/sparks around the held arrow.
   Continue holding: no repeated projectile, resource charge or cooldown.
4. Release while aimed horizontally into open space. Check one ordinary native
   arrow with its normal flight appearance/trails and no ballistic drop. Aim
   uphill/downhill too: “straight” means along aim, not forced horizontal flight.
5. Confirm one arrow and 12 Stamina consumed and one 10 s cooldown. After it
   expires, repeat against terrain and a safe valid target; ensure termination
   rather than a persistent carrier. Empty-space expiry remains a paid miss.
6. Cancel a held shot by changing the held item/another native cancellation.
   Verify the pose/effect clears and no cast is paid or fired. Repeat in third
   person or with another observer to check held-arrow particle placement.

Skill traces prove input/commit/spawn/termination boundaries, not visual pose,
particle color or alignment. The standalone native spawn smoke retains its
Fire Bolt insertion/rollback/expiry proof; it is not claimed to be a connected
Snipe flight or rendering test. Existing post-lethal/encounter persistence
uncertainty remains unresolved and untouched. **Stage 13 is not PASS.**
