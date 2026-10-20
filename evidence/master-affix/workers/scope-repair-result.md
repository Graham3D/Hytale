# Scope audit repair: findings 1–4 and borrowed rank

## Changes

- **Finding 1:** Quick Slash now passes `!context.derivedRelease()` for every authored weapon damage component, matching HEAD. The existing per-hit affix receipt observer remains separate.
- **Findings 2–3:** Restored HEAD skill-caller eligibility and movement planning. Area pull again uses `AreaPullPlanner` with its authored core radius. Passive positioning and movement/projectile knockback again require an NPC, native transform/bounds, positive role knockback scale, the HEAD control profile, grounded state, collision sweep, and ground support. The skill callers no longer route these base mechanics through the broader `NativeAffixHostileDisplacement` helper. Each caller reduces only the requested distance by the live target's WA-082 percentage before its original planner; neutral gear supplies the HEAD request and geometry. Genuine affix displacement remains outside these callers.
- **Finding 4:** `SkillExecutionService` stores the item-granted skill levels in `Prepared` at validation. At release it recomputes the base learned rank through `loadouts.baseSkillRank` and combines it with that frozen item contribution. It does not read live equipment or item rank during release.
- **Borrowed rank:** `RpgLoadoutService.baseSkillRank` returns rank one for an unlearned skill only when `OwnershipEntitlementPolicy.temporaryCopies(player, skill)>0`. Other ranks, including development-mode behavior, follow the saved base-rank/mastery path.

Only the specified method regions in `HytaleSkillExecutionSystem`, `SkillExecutionService`, and `RpgLoadoutService` were edited. No skill asset was edited. `git diff --numstat HEAD` produced no content diff for `skills.json`, stage-09 support, or stage-10 summons.

## Offline checks

- `git diff --check` passed for the three edited Java files.
- Compiled the actual `Vec3`, `AreaPullPlanner`, and `AreaDisplacementPlanner` sources with `javac` in a temporary directory. JShell ran eight assertions: neutral and WA-082 reduced pull, authored pull core, zero control/role scale for pull, neutral and reduced push, zero control/role scale for push, and unsupported origin. All passed.
- Scoped source assertions passed for per-component Quick Slash proc eligibility; area pull planner/core and WA-082; NPC, role, and planner gates in passive positioning and knockback; release-time base-rank resolution using frozen item levels; and temporary-copy-only rank one.
- No Gradle, native game, server, auth, deployment, commit, push, or agent run. Native caller behavior was checked against HEAD source and planner simulations; no connected gameplay claim is made.

The audit's finding 3 described HEAD as using `AreaDisplacementPlanner` and stopping only at the center. The actual HEAD area-pull caller uses `AreaPullPlanner` with `payload.pullCoreRadius()`. This repair restores that exact authored geometry.
