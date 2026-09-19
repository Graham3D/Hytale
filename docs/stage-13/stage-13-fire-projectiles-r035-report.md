# R035 Fire Bolt and Fireball Gameplay/Presentation Revision

Date: 2026-09-14 (America/New_York)  
Branch: `RPG`  
Source checkpoint at implementation start: `25b85cb2819351a0d727e33132b2f57013cda024`  
Pinned runtime: Hytale `0.7.0-pre.2`

## Delivery status

- IMPLEMENTED: **YES**
- PACKAGED: **YES**
- DEPLOYED: **YES**
- CONNECTED-VERIFIED: **NO — owner QA required**

The installed live-test JAR, packaged JAR, isolated-smoke JAR, and build output are byte-identical:

```text
HyARPG.jar SHA-256
B2C287533FC9F195153ECF5DAEE5D2DAD236F4BCB13DBDF36DFC303EDF27D593
```

The packaged build identifies itself as:

```ini
rpg.revision=R035
rpg.version=0.0.25
rpg.stage=13
hytale.version=0.7.0-pre.2
```

## Scope and reasoning

This correction implements the approved Fire Bolt and Fireball contracts without changing unrelated gameplay, HUD ownership, persistence, summon behavior, rewards, or skill-input projection. Shared projectile infrastructure was extended because Fireball's timed ballistic arc, terminal explosion, continuation ordering, and caster-owned Burn payoff are reusable projectile concerns. Fire Bolt and Fireball remain authored profiles rather than hard-coded replacements for the rest of the projectile family.

The installed Hytale asset archive was audited before authoring presentation references. The pinned build contains `Fire_Projectile`, `Fire_Charge1`, `Fire_Charge_Charging1`, `Fire_Staff_Activation`, `Impact_Explosion`, and `Explosion_Medium`. The requested charging concept maps to the actual installed identifier `Fire_Charge_Charging1`; an unnumbered `Fire_Charge_Charging` asset does not exist in the pinned build.

## Fire Bolt

`rpg.skill.fire_bolt` now has the following authoritative profile:

- projectile / Fire family; Staff or Wand required;
- 5 Mana, 0.45-second cooldown, no windup;
- 0.70 Magic Power direct-hit coefficient;
- 32 m/s straight aim flight, 26 m maximum distance, 0.22 collision radius;
- one-second authored lifetime and one direct target;
- four-second Burn only after an accepted entity hit;
- terrain contact terminates presentation without damage or Burn;
- cast, projectile, and impact presentation use `Fire_Charge_Charging1`, `Fire_Projectile`, and `Impact_Explosion` respectively.

Fire Bolt does not create a base area explosion. A valid cast remains paid if it misses or expires.

## Fireball

`rpg.skill.fireball` now has the following authoritative profile:

- projectile / Fire family; Staff or Wand required;
- 10 Mana, one-second cooldown, no windup;
- no separate direct-hit damage packet;
- one 0.90 Magic Power explosion with a 3.5 m radius;
- maximum horizontal targeting distance of 22 m and 0.45 collision radius;
- timed ballistic motion with 12 m/s² gravity;
- horizontal travel-time basis of 16 m/s, clamped to 0.65–1.40 seconds;
- the initial velocity is solved from the selected aim endpoint and the authored gravity, then native movement is swept continuously for contact;
- first hostile/entity contact, terrain contact, endpoint/range exhaustion, or lifetime exhaustion detonates once;
- explicit cancellation and unload paths terminate without fabricating an explosion.

Presentation uses `Fire_Staff_Activation` at cast, `Fire_Charge1` on the projectile with an authored visual-only scale of 4, and `Explosion_Medium` for impact. Visual scaling does not change collision or damage radius.

## Burn payoff and exact-once behavior

Fireball checks only Burn instances owned by the Fireball caster. A qualifying victim receives a 1.25 multiplier, producing a 1.125 Magic Power effective explosion coefficient. Burn is consumed only after native damage is accepted; a fully absorbed but accepted damage application still consumes it. A rejected/cancelled damage application releases the root victim claim and does not consume Burn. Burn belonging to another caster is preserved.

A root-owned victim ledger limits Fireball explosion damage to once per victim per root cast. This prevents Fork, Chain, Return, or other descendants from shotgun-damaging the same victim while still allowing each valid descendant to retain its own visible ballistic carrier and terminal presentation.

## Continuations

- Fork descendants retain Fireball's visual scale, timed-ballistic motion contract, snapshot, and root identity.
- Chain descendants solve a fresh timed ballistic leg toward the selected continuation target.
- Ricochet is evaluated on terrain before terminal Fireball detonation; a successful bounce spends its authored credit and continues instead of exploding at that contact.
- Return remains governed by the generic bounded Return lifecycle.
- Splinter/secondary carriers inherit the parent motion descriptor without restoring spent continuation credits.

## Runtime and diagnostic changes

The primary implementation is in:

- `Stage04SkillProfile` — validates authored projectile motion, presentation, and Burn-payoff data;
- `ProjectileMotion` — immutable linear/timed-ballistic descriptor;
- `ProjectilePresentation` — immutable cast/projectile/impact asset and scale descriptor;
- `ProjectileBurnPayoff` — immutable caster-owned Burn multiplier/consumption contract;
- `ProjectileBallistics` — deterministic timed-arc velocity and path-length calculations;
- `ProjectileExecutionPlan` — carries motion through root and descendant plans;
- `ProjectileContinuation` and `ProjectileSecondaryEffects` — preserve motion and ordering across descendants;
- `ProjectileExplosion` — records whether continuation precedes terminal detonation;
- `RootEffectBudget` — bounded exact-once Fireball victim ledger;
- `PeriodicStatusRuntime` — stable owner lookup and exact captured Burn consumption;
- `HytaleSkillExecutionSystem` — native spawn, swept contact, terminal ordering, explosion, payoff, presentation, and telemetry integration.

New event-driven trace types are:

```text
FIRE_BOLT_CAST
FIRE_BOLT_CONTACT
FIREBALL_CAST
FIREBALL_CONTACT
FIREBALL_EXPLOSION
```

They retain root cast, skill instance, and correlation identity through the existing trace envelope. Fireball explosion telemetry includes the trigger/contact type, explosion identity, pre-mitigation basis, multiplier/payoff state, Burn ownership/consumption outcome, victim count, and root-ledger outcome where applicable.

## Assets and content

Updated packaged content includes:

- `rpg/runtime/stage-05-projectiles.json`;
- `rpg/catalog/skills.json` and server language descriptions;
- `Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fire_Bolt.json`;
- `Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fireball.json`;
- `Server/Models/Projectiles/RPG_Fire_Bolt.json`;
- `Server/Models/Projectiles/RPG_Fireball.json`.

The native asset audit now resolves every authored projectile presentation reference against the pinned Hytale asset store during startup validation.

## Verification

Focused projectile and affected-regression runs passed, including the production profile/parser, ballistic math, targetless casting, continuation, secondary, resource/leech, vanilla weapon-power, and native construction controls.

The final retained command was:

```powershell
.\gradlew.bat clean check jar --no-daemon
```

Result: **PASS**.

- RPG tests: 2,343 passed, 0 failed, 0 skipped.
- CanvasUI tests: 21 passed, 0 failed.
- CustomUI validation: PASS.
- Native control test task: PASS.
- JAR build: PASS.

The exact built JAR then passed the isolated three-mod smoke with `CanvasUI-0.1.0.jar` and `HYTALEDEVLIB-0.5.0.jar`. The server discovered and enabled RPG, resolved the packaged root/rune and Stage 04–13 assets, passed the native projectile construction audit, booted networking, and shut down cleanly. Evidence is retained under `evidence/stage-13/cohort-r035-final/`.

One smoke-wrapper defect was corrected during closure: modern production cohorts were incorrectly required to auto-enable the retired R032-AP Healing Beam diagnostic command. R035 correctly keeps that diagnostic disabled. The wrapper now requires the AP production renderer/assets/audio gates for retained cohorts and reserves diagnostic registration enforcement for the AP diagnostic cohort itself.

## Packaging, backup, deployment, and rollback

Package directory:

```text
evidence/stage-13/revision-r035/package/
```

It contains exactly three JARs:

| Artifact | SHA-256 |
|---|---|
| `HyARPG.jar` | `B2C287533FC9F195153ECF5DAEE5D2DAD236F4BCB13DBDF36DFC303EDF27D593` |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

Three-mod archive:

```text
evidence/stage-13/revision-r035/HyARPG-R035-three-mod-test.zip
SHA-256 71BDF2678947B5BE3EC5D852FD140CEFF24E958A203AC0B8BBD18B6EA5ED5C99
```

Before replacement, the installed R034 JAR and matching RPG mod-data directory were copied to:

```text
evidence/stage-13/revision-r035/live-backup-20260914-212637/
```

The previous JAR is readable and has SHA-256 `437A998DA0B054652EA04EB7F051D95B541D63D6DCD7FCE0973C34E15B97E68E`. The rollback snapshot contains 32 mod-data files (35,749,289 bytes), including player state, encounter journals/checkpoint state, reward state, diagnostics, and traces.

Only `HyARPG.jar` was replaced in the live mods directory:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar
```

The deployed hash exactly matches the built and packaged hash. Live save data was not modified by the build/deployment operation.

## Connected QA checklist

1. Launch the normal RPG save and confirm the upper-right revision counter reads `R035`.
2. Open `/rpg skilltree`, equip Fire Bolt and Fireball into the two native RPG ability slots, and confirm their native HUD icons appear.
3. Fire Bolt into empty space: verify immediate cast, straight fast travel, no explosion/AoE, and expiry by roughly 26 m/one second.
4. Fire Bolt into terrain: verify `Impact_Explosion` presentation but no damage and no Burn.
5. Fire Bolt into one hostile: verify one direct hit at the authored coefficient and a four-second caster-owned Burn.
6. Verify each Fire Bolt cast consumes exactly 5 Mana and starts a 0.45-second cooldown.
7. Fireball at short, medium, and maximum range: verify a visible ballistic arc and one `Explosion_Medium` detonation at entity, terrain, or endpoint contact.
8. Verify Fireball has no separate direct-hit packet, damages valid targets once in a 3.5 m radius, consumes 10 Mana, and starts a one-second cooldown.
9. Apply your own Burn with Fire Bolt, then hit that victim with Fireball: verify the 25% Fireball payoff and that your Burn is consumed after accepted damage.
10. Repeat against a Burn owned by another caster: verify no payoff and that the foreign Burn remains.
11. Test Fireball with Fork, Chain, Ricochet, and Return. Verify descendants remain visible/ballistic and the same victim cannot take multiple Fireball explosions from one root cast.
12. Review `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` for the R035 Fire Bolt/Fireball trace events and confirm there are no `TRACE_GAP`, spawn rejection, executor error, duplicate damage, or duplicate resource/cooldown writes.

## Remaining evidence boundary

Local tests and isolated server smoke prove structure, deterministic behavior, asset resolution, packaging, and server startup. They do not prove connected client rendering, native input delivery, client physics presentation, collision feel, effect scale, or live HUD timing. Those items remain explicitly **CONNECTED-UNVERIFIED** until the checklist above is completed in Hytale.

