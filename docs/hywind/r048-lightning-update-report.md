# Hywind R048 Lightning skill update

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED QA PENDING**  
Version: `0.1.0-merge.3`  
Revision: `R048`  
Final implementation commit: `b4340fbd99aa416353709c2a72b7e027e9e37295`  
Roster implementation commit: `9584ffcdedaf058ef77277e3028aac5516b05eb1`  
Deployed SHA-256: `90BD356B8D4DB2C2E6B4E473BD185614EFE648FDAF57C71F90EDE4AC4C1443E7`

## Scope completed

The player-facing Lightning roster is now exactly nine active skills: Charged Bolt, Lightning Bolt, Ball Lightning, Lightning Coil, Teleport, Static Field, Storm Strike, Lightning Arrow, and Mantle of Thunder. Spark migrates to Charged Bolt. Standalone Chain Lightning is retired while the chain continuation machinery remains available to Lightning Bolt. Electrify and Battery are not exposed as standalone player skills.

The canonical catalog now contains 96 skills and 67 passives. ItemAbility projection, localization, skill-tree metadata, icon indexing, migration aliases, runtime profiles, tests, and packaged assets use the same roster.

## Runtime implementation

- Added a bounded `LightningRuntime` for per-root Charged Bolt stack ownership, Ball Lightning target locks, Storm Strike target/global ICDs, Static Field exposure, Coil capacity, and Mantle charge/Alacrity state.
- Added Electrified (maximum five stacks, shared six-second refresh), Lightning Vulnerability (five-second non-stacking refresh), and Alacrity (six-second cooldown-recovery effect) to the status authority and native projection path.
- Charged Bolt creates three-to-five independently aimed cone bolts and enforces one Electrified attempt per root/target.
- Lightning Bolt uses an instant authored line, one hit per target, first-wall termination, custom strike presentation, and passive-only two-target chain continuation at the authored coefficient.
- Ball Lightning uses the authored moving orb/pulse cadence and per-target lock.
- Lightning Coil snapshots caster Magic Power, admits only observed direct native Physical weapon-root Health loss from its owner or confirmed allies inside the field, and caps charge at three times that snapshot. Reaching capacity queues exactly one caster-owned discharge outside the native Damage callback, applies the authored Lightning wave and two Electrified stacks, then cleans up. Expiry produces no wave.
- Teleport uses bounded safe destination validation, preserves persistent summon state, relocates owned summons, and emits authored origin/arrival presentation.
- Static Field tracks continuous exposure independently per field/target and resets incomplete exposure on exit.
- Storm Strike observes only the zero-to-one Electrified transition and applies both per-target and global internal cooldowns without recursive triggering.
- Lightning Arrow retains actual bow/ammunition execution while converting its authored hit to Lightning and applying its status chances.
- Mantle of Thunder reserves Mana, charges only from qualifying damage to already-Electrified enemies, suspends charging during Alacrity, and applies the completed buff to current aura members.
- Added native post-filter Physical miss handling for Electrified while preserving the strongest-of Blind/Electrified rule.

## Presentation and content

Packaged derivative particle/model assets use the supplied `chargedbolt.png`, `lightning.png`, and `staticfield.png` artwork. The update also removes obsolete standalone Spark/Chain Lightning ItemAbility and localization entries, preserves the generic chain continuation capability, and updates the self-service icon index/list to include the final roster.

## Validation

- Focused `LightningSkillUpdateTest`: PASS.
- Complete `gradlew check`: PASS in 3m10s.
- RPG/native JUnit: 2,362 tests, zero failures/errors/skips.
- CanvasUI JUnit: 28 tests, zero failures/errors/skips.
- Tavern JUnit plus eight retained executable gates: PASS.
- Persistent NPC retained suite: PASS; live local-model tests remain intentionally skipped by that harness.
- CustomUI validation: 58 documents, PASS.
- Unified package audit: PASS (5,771 entries, 2,176 classes, 2,089 UI documents).
- Final isolated Hywind smoke: PASS; RPG, CanvasUI, NPC, Tavern, plugin start/shutdown, and single-first-party-JAR checks all passed.
- Post-deployment startup/restart: two of two cycles passed with no legacy plugin discovery or scoped startup failure.

## Deployment and rollback

Installed artifact: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/Hywind.jar`  
Installed SHA-256: `90BD356B8D4DB2C2E6B4E473BD185614EFE648FDAF57C71F90EDE4AC4C1443E7`  
Rollback directory: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale-rollback/hywind-deploy-20260919T185742Z`  
Verified full-save backup: 606 files / 507,948,061 bytes.  
Retired interim R048 SHA-256: `3A4CB230AD75AC0E6181B2F439BD44C4AA4C95734C07D7991DD934A8336E2FB9`.

Only `Hywind.jar` remains active as the first-party project mod. `HYTALEDEVLIB-0.5.0.jar` was preserved as an external development dependency, and all four existing writable data roots were preserved.

## Remaining evidence boundary

Connected Hytale client behavior is not inferred from local tests or headless startup. The nine skills still require connected QA for rendering, input, animation, collision, status feedback, resource/cooldown HUD synchronization, Coil ally charging, migration/loadout presentation, and restart/rejoin behavior.
