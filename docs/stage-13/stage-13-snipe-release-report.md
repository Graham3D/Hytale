# Stage 13 Correction T — Snipe hold / charged release

Baseline RPG `0b03d92`, following S (`219c335`). R032-T, version 0.0.25.

## Owner-authorized contract change

The [connected range audit](snipe-connected-range-audit.md) identified five
Snipe roots rejected before payment with `NATIVE_BOW_MAX_RANGE_UNVERIFIED`.
The owner then specified a revised behavior: hold the skill key to instantly
ready a fully drawn bow; release to fire a farther, straighter, faster heavy
shot, with implementation details delegated to this correction.

This supersedes the earlier SK-035 instant-on-press/native-maximum prerequisite
for Snipe only. The chosen explicit interpretation is:

- Hold native Ability2/Ability3, depending on the assigned slot; full draw is
  immediate rather than a timed charging ramp.
- Release once to request the RPG cast; continuing to hold never pays/fires.
- Use the installed charged Shortbow Strength4 speed **85 m/s**, gravity
  **25 m/s2**, native arrow hitbox radius **0.075 m**.
- Retain **48 m** as an explicitly RPG-authored travel cap. It is not described
  as a measured native maximum. This exceeds Quick Shot's authored 28 m cap.
  Native uncharged arrow launch force is 30; native charged gravity is lower
  than the basic Shortbow config's 30. Do not claim arrow flight was measured
  on a connected client by these static values.
- Preserve **12 Stamina, 10 s cooldown, one Crude arrow, 2.00 times uncharged
  audited Weapon Power**, fully-charged Link metadata and no basic-hit recovery.
- Validate actor/equipment/resources/cooldown/ammunition on release. A valid
  empty-space shot remains a paid miss. Cancellation before release does not
  call the RPG commit path.

No global ranged-family redesign. Quick Shot, Crossbow Bolt, Hunter's Mark and
all other skills retain their profiles. Entity-target requirements elsewhere
remain intact. Existing gameplay authority and resource HUD ownership persist.

## Native implementation

Snipe's ItemAbility alone now references `Root_RPG_Snipe_Release`. Native
Cost=0, CostType=None, Cooldown=0 remain unchanged. Every other native skill
root remains `Root_RPG_Ability_Bridge`.

The new root uses the shipped **Charging** interaction with
AllowIndefiniteHold=true, DisplayProgress=false, a sole Next threshold **0**,
and a single RPG_ActivateSkill callback on release. There are no native
projectile, damage, resource, cooldown or stat-changing branches. Other-click
and damage cancellation are enabled. The installed Interaction constructor's
item-change default is Cancel; no custom key polling or packet sniffing is added.

Effects explicitly select native Shortbow / ShootChargingHold (first-person
and third-person looping full-draw pose), clearing the animation when the
interaction ends. The committed projectile path uses native Shortbow /
ShootCharged rather than the generic melee-swing projectile feedback. This
uses existing animation assets, not generated art or a new HUD.

NativeSkillActivationInteraction accepts the exact Snipe-root/item pairing.
The existing server callback, world-thread queue, queue bounds, suppression and
actual-chain deduplication remain the gameplay entry point. A Snipe release
request carries expectedSkill=snipe; the world-thread request refuses
HELD_SKILL_CHANGED if the slot was replaced while held. It cannot cast the new
slot contents using the old held chain. Other input requests remain unchanged.

NativeSnipeReleaseAudit verifies the resolved installed root/packet, not just
the source JSON. Its compiled operations resolve to:

`ChargingInteraction -> JumpOperation -> NativeSkillActivationInteraction -> JumpOperation`

It requires one charging operation, one server callback, no other operation
types except native jumps, client synchronization, zero threshold, indefinite
hold, cancellation flags, no Failed/fork gameplay branches, and the exact
native animation names. NativeProjectileAssetAudit compares Snipe's authored
flight values against the resolved charged native config and checks the
explicit 48 m RPG cap. This replaces the obsolete unavailable gate with
positive asset-contract validation; it does not manufacture native range proof.

## Failures caught during implementation

- The new pure root-selection helper initially initialized the native logger
  in a plain test JVM. Logger initialization is now lazy in the asset event
  path; root-name selection is safe without booting Hytale logging.
- A narrow native codec test cannot resolve contained animation assets without
  an AssetStore. That test explicitly verifies scalar Charging flags and raw
  references; complete contained-asset resolution is instead mandatory in the
  real three-mod startup audit. These scopes are not presented as equivalent.
- Native compilation wraps branch operations in LabelOperation. The first
  audit rejected that wrapper. The audit now unwraps through Hytale's public
  Operation.NestedOperation interface with a bounded depth, then enforces the
  concrete operation whitelist.
- The native absent-interaction index is Integer.MIN_VALUE, not -1. The audit
  rejected the initial assumption; the exact serialized resolved value and
  native configurePacket path were inspected before correcting that assertion.
- The old native Snipe tooltip still said unavailable. Its text was updated to
  the new hold/release contract before final packaging/native smoke.

Initial failed isolated startup logs are retained; no failed candidate was
deployed. Native root/asset validation is not relaxed to accept unknown fields
or additional gameplay branches.

## Regression scope

Six new production-path tests cover: paid targetless release; equipment,
resource and death rejection before payment; changed-slot rejection; existing
once-per-chain callback routing; Fork/Homing/Orbit acceptance without restoring
the obsolete gate; and Snipe-only root selection. One additional native codec
test covers Charging scalar flags and exact source threshold/effect references.

Retained test identities remain. Four older gate/root expectations are updated
because the owner explicitly changed Snipe's contract, not to make an unchanged
requirement pass. The former development-fallback test now requires the actual
charged speed/gravity/hitbox and unchanged payment, rather than enabling the
old zero-gravity 45 m/s fallback. Bone Cage, Frenzy and Guard remain explicitly
gated; Snipe connected execution remains unverified, not falsely PASS.

The complete retained regression results, exact native smoke, archive hashes,
rollback and deployment receipts are recorded in
[evidence/stage-13/cohort-t](../../evidence/stage-13/cohort-t/).

### Final validation and deployment

- **2,164 tests PASS**: 2,097 root, 46 native-control, 21 CanvasUI; zero
  failures/errors/skips; all S baseline test identities retained.
- The full suite ran once after targeted corrections. A subsequent packaging
  pass corrected the native tooltip and imported the then-current owner Snipe
  image. Entry-by-entry comparison found only the tooltip and two Snipe PNG
  entries changed; all **760 compiled class hashes** match the full-run
  recompiled outputs. See `final-tooltip-differential.json`.
- The **exact final JAR** then passed three-mod native server startup, resolved
  Snipe root/packet/charged-physics/animation checks, retained actual native
  Fire Bolt spawn/pending-rollback/same-tick/resting-expiry checks, network boot
  and clean shutdown. This is not a connected Snipe flight or input test.
- Three-mod archive entry hashes, strict S-to-T JAR differential, unchanged
  non-Snipe profile/catalog rows, unchanged Snipe damage/cost/ammo, owner PNG
  hashes, and isolated atomic rollback/roll-forward all PASS. The retained
  real-storage 64-update benchmark and archived-reader tests ran in the full
  suite; their evidence is retained without changing performance thresholds.
- Deployed at **2026-09-10 02:59 UTC** to
  `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods`.
  Exactly three mods; both supporting JARs unchanged; all **18 non-JAR mod-data
  files** verified unchanged; no live save edits. S's JAR is in `cohort-t/rollback`.
- Default owner icon updater check passed after deployment: four supplied
  PNGs, zero pending changes, no write. No fourth mod is introduced.

| Artifact | SHA-256 |
|---|---|
| HytaleRPG-0.0.25.jar (R032-T) | `0082B9149831E9BDE13F82E32F2907FAC2EF943C14CC230A16BF3B54A77D77F9` |
| Hytale-RPG-Stage13-T-snipe-hold-release.zip | `8DC7CD905ACED9031DBA4F30438C2C393C04D38D02CE5D0B5859EA493DAB687F` |
| CanvasUI-0.1.0.jar | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| S rollback RPG JAR | `E3B6F793F66164807EFC53E992C9B68383E05569A580F2D32B92AE105160C3BC` |

Authoritative receipts: `snipe-hold-release.json`, `server-smoke-summary.json`,
`native-projectile-equipment-audit.json`, `native-spawn-integration.json`,
`jar-differential.json`, `test-results.json`, full validation output and the
focused Snipe/native-codec JUnit XML. No connected success is inferred from them.

## Artwork and preserved systems

The owner added `art/Skills/SkillSnipe.png` during work. The S updater imports
it byte-for-byte together with Fire Bolt, Quick Slash and Whirlwind. Snipe PNG
SHA-256: `D17952E698B1DA67E7D578D0F9B85459F352F23AF3BF2456BD40493FCC05B198`.
Original owner art is not regenerated or overwritten. Native item icon and
Skill Tree overlay receive the same PNG. Updater workflow remains unchanged.

No persistence, escrow, reward, XP, power-registry, resource/cooldown formula,
native resource-bar ownership, other skill mechanics or NORMAL trace changes.
No live save data changes. S is retained for rollback. Stage 13 remains **not
PASS**; existing post-lethal/encounter uncertainty and connected release gates
are not closed by this correction.

## Minimal connected checklist

1. Restart Hytale, rejoin RPG, confirm R032-T.
2. Equip a supported bow and carry Crude arrows. In `/rpg skilltree`, equip
   Snipe in skill01 (native Ability2, normally E) or skill02 (Ability3, normally R).
3. Hold that key for several seconds while aiming into empty space. Expect an
   immediately fully drawn bow and **no shot, ammo consumption or RPG payment**
   while holding. Then release: expect one charged arrow and one payment.
4. Repeat after the 10 s cooldown; aim at terrain and then an enemy. Verify
   carrier removal on collision/expiry and only authoritative hit damage.
5. Verify a short tap fires once, not once on press and again on release.
   Verify pressing again during cooldown produces no second paid cast.
6. Cancel a held draw with another action; switch weapon/loadout while holding;
   release with insufficient Stamina/ammo or wrong equipment. No free shot or
   cast of replacement slot content should occur. Check no stuck draw pose.
7. Repeat in the other native ability slot; check Snipe's new icon in the
   native slot and Skill Tree. Retest Quick Slash/Fire Bolt as unaffected controls.

Expected release trace: NATIVE_ABILITY_INPUT_OBSERVED ->
SKILL_ACTIVATION_REQUEST -> SKILL_VALIDATION_PASS -> SKILL_COMMITTED ->
EXECUTOR_DISPATCH -> PROJECTILE_SPAWN_REQUEST -> PROJECTILE_SPAWNED, with
one AMMO_COMMITTED per root and ordinary cooldown enforcement. No claim is
made that the connected client has yet delivered this new held release or
rendered its animation. If it fails, inspect that earliest boundary; do not
redesign native input again based only on static synchronization flags.
