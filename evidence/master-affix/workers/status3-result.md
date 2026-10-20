# Status continuation result — 2026-09-30

## State and ownership

Implemented production status paths in the status2 owned files and the permitted status portions of `HytaleSkillExecutionSystem` and `SkillExecutionService`. **The capability gate remains in place.** No Gradle, game/server/auth launch, deployment, commit, push, or connected QA was performed. This is not a 160/160 completion claim.

No edits were made to `HyArpgPlugin`, `GearAffixRuntime`, `GearEffectSnapshot`, shared equipment, combat damage calculation, summon configuration, item trigger children, recovery, or cleanse/resource code.

## Production changes

- `GearStatusBindings` is a concrete `NativeGearStatusObserver.Opportunity`: native hostility and boss/protection classification, valid target gear, current non-gear resistance baseline, deterministic root/target/status draws, accepted proc coefficient, source-owned periodic profiles, and current channel state. It exposes `observer()`, `tickSystem()`, `accept(NativeGearContactContext)`, `attempted(...)`, `periodicView(...)`, and `forget(...)`.
- `NativeGearContactContext` is immutable accepted action metadata. Its basic factory uses the frozen channel vector, the existing Hemorrhage 12% Bleed profile, 10% Burn and 6% Poison coefficients, canonical Burn/Poison durations, and the existing three-stack ordinary Poison source cap. The skill form retains the committed `SkillExecutionContext`, `HitProcRuntime.coefficient`, and compiled projectile DoT profile. The skill system attaches it immediately before the native gear channel loop. The observer rejects contacts without this accepted identity.
- Status chance admission now claims one root/victim/status across channel components and pellets. Eligible skill and item chances use the existing D04 union with a single deterministic draw; supported authored strike/projectile status callbacks yield to that claimed result. Chill retains authored stack count and Deep Freeze. The bounded contact ledger expires after 120 seconds and clears by actor lifecycle.
- Burn/Poison/Bleed admission queues to the next world tick before calling `PeriodicStatusRuntime`. Skill sourced packages enter the skill system's existing periodic runtime; basic attack packages use the same production runtime class and native periodic damage path. Both runtime views compose into the canonical `StatusService` projection and native visual timer. Source ticks are `PERIODIC` and `canProc=false`. Failures emit native warnings.
- The existing victim death callback also clears queued and active gear periodic packages. The facade's actor cleanup clears accepted contacts, pending packages and source timers.
- WA-080 and WA-109 share one additive hard-control duration bucket. WA-109 reads `ConnectionRuntime.channelActive` at admission only. Existing skill status sites now pass valid target equipment into the canonical status owner, including projectile Root policy.
- `NativePlayerStatusMovement` publishes the exact strongest WA-081 adjusted slow through public `MovementManager.getSettings/update`. The existing projection tick restores native speed after expiry; discrete Chill speed assets are removed for players and NPCs. NPC steering retains its exact path. Native control evidence reads the canonical exact slow.
- `NativeAffixHostileDisplacement` now handles grounded hostile players through public `Player.moveTo` after the existing swept, collision and support planner. Area push/pull, passive vacuum/repulsion and skill knockback use it. Self movement retains its own path.
- Manual skill validation rejects `SILENCED` before payment. Triggered skill origin and ordinary native Primary/Secondary attacks are unaffected by that validation gate. The Blind asset now uses the installed `ScreenEffects/Spyglass.png` native screen overlay in addition to its tint and physical miss owner.
- Existing status removal remains in `StatusService`; the existing `AreaStatusProjectionSystem` synchronizes native effects from that owner, including after a cleanse removal. No separate gear cleanse/status state was introduced.

## Exact main registration and acceptance seam

The facade is ready for main's existing registration boundary. Construct **one** `GearStatusBindings(statuses, bosses, skillSystem)`, call `skillSystem.configureStatusBindings(bindings)`, register `bindings.tickSystem()`, and add `bindings.observer()` to the existing post-ApplyDamage `AppliedObserver` composite. Retain the existing `GearAppliedHitRecovery` observer. Register `NativeStatusPhysicalMiss` instead of the old Electrified-only miss filter, and `NativeAffixFearBreak` once. Existing `AreaStatusProjectionSystem` and `AreaNpcControlSystem` remain the projection owners. Call `bindings.forget(actor,store,buffer)` on actor death/logout/world transfer.

For native managed basic attacks, at the exact `GearCombatEffects.Hit` acceptance point **before** its first native channel, call `bindings.accept(NativeGearContactContext.basic(hit, actorId, worldId, authoredRootShotProcCoefficient, balance.burnDurationSeconds, balance.poisonDurationSeconds))`. This must be the same `Hit` object attached to `HytaleDamageAdapter.GearHitSource`, from a valid equipment snapshot. The accepted coefficient must come from the actual authored shot/area budget. Skill hit acceptance is already inside `HytaleSkillExecutionSystem.applySkillGear`. A native attack that never creates a gear hit envelope cannot reach this observer; combat2/main must retain its managed envelope for status-only WA-134 as well.

The new APIs require no invented `Eligibility`, RNG, target resistance, control, or periodic `Port` callbacks in main.

## Evidence

- Direct `javac` against the pinned public Hytale server JAR and current classes passed for owned production sources. A full sourcepath compilation briefly encountered a concurrent combat2 edit in `NativeGearAcceptedContext.java` (`ForkedChainId` passed to `Integer.toString`); compiling the owned sources with the existing classpath and sourcepath disabled passed. This concurrent file was not edited here.
- Direct execution of focused JUnit methods without Gradle: **18 passed, 0 failed**. Tests cover real `StatusService`/`PeriodicStatusRuntime` admission, D04 positive/control/negative cases, source classification, root dedup, WA-109 channel boundary, exact player slow math, hostile displacement planning, NPC controls, Blind/Electrified physical miss, and the actual `SkillExecutionService` manual silence validation path.
- `git diff --check` passed for edited tracked files (Git reported only existing line-ending normalization warnings). JSON screen effect path was verified against the installed assets ZIP.

## Classification and limits

The owned implementation routes WA-053–068, WA-079–082, WA-109 and WA-134 through production status owners, with WA-013/065–068 DoT modifiers preserved. WA-053–063 remain **gated pending main registration and main's complete native/integration tests**. No native connected behavior is asserted. Gear-only native basic attack acceptance is deliberately a precise main/combat2 hook because those files are outside this worker's ownership. Specialized authored projectile Root boss opt-in and Electrified chance/ICD remain on their existing skill paths; the ordinary item opportunity uses the shared status owner and may require final main integration evidence before those combinations are classified functional. No full 160-affix audit or server smoke was run here.
