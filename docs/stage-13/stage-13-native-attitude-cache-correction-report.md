# Stage 13 correction X — native attitude cache / Healing Beam validation exception

Date: 2026-09-11. Baseline `81e2ab5`, R032-W / 0.0.25, RPG branch.
Owner authorization: “Deploy a fix!” after review of the latest connected failure.

## Connected evidence and earliest failing boundary

Reviewed `Saves/RPG/logs/2026-09-11_17-51-28_server.log` and the RPG
`skill-trace.jsonl` / `ui-trace.jsonl`. The installed W JAR hash matched its
deployment receipt. Jonalith's native-role JSON retained `Friendly` and
`Invulnerable: true`; the server recorded that exact role file as ready.
At player readiness the projected skills were Whirlwind → Ability2 and
Healing Beam → Ability3. Input delivery was working.

Between **21:51:49 and 21:52:00 UTC**, five Healing Beam requests reached
`NATIVE_ABILITY_INPUT_OBSERVED` → `SKILL_ACTIVATION_REQUEST` and then
`SKILL_VALIDATION_REJECTED(failureCode=VALIDATION_ERROR_NullPointerException)`.
There were no commits, executor dispatches, connection starts or healing
events. Five other attempts, for Whirlwind, were rejected with
`INVALID_MAIN_HAND`; that authored equipment rule is not altered.
Disconnect-only slot conflicts occurred afterward and are not this failure.

W did NOT establish successful connected Healing Beam casting. The new
exception is a different boundary from V's `NO_VALID_AIMED_TARGET`.
The old catch discarded the original message/stack, so the old trace alone
cannot identify the particular null reference. The installed API reproduction
below establishes a concrete defect consistent with the observed exception.

## Installed native API reproduction and correction

The exact installed **0.7.0-pre.1** `WorldSupport` constructor leaves its
`attitudeCache` uninitialized. Its `getAttitude(...)` immediately invokes
`attitudeCache.getOrDefault(...)` without a null check. Native AI sensors can
request this optional cache, but passive/social NPC roles need not have done so.
RPG previously called the getter directly, assuming that initialization had
already happened. The native API exposes **`requireAttitudeCache()`**, which
allocates the cache only if absent and leaves existing cache entries untouched.

`Stage13NativeAttitudeCacheTest` uses the actual installed `WorldSupport`
constructor, getter, initialization and tick methods. A minimal native
`SupportConfigBuilder` supplies constructor values without starting unrelated
NPC decision-maker/stat registries. The first test reproduces an NPE whose top
frame is `WorldSupport.getAttitude` and whose message identifies `attitudeCache`.
The corrected preparation creates an empty cache; the real native getter then
returns each explicitly seeded native attitude unchanged. Repeated preparation
preserves the same map and entries, and native tick still expires entries.
This is **native cache-contract proof**, not a connected NPC/Blackboard test:
the successful getter branch is a cache hit; a complete live uncached provider
lookup and Healing Beam cast still require connected QA.

The new `NativeNpcAttitudes.prepared(...)` calls this native initialization.
All **three** RPG native getter sites now use it:

- `HytaleSupportSystem`: friendly support/Healing Beam resolution.
- `HytaleAreaQueries`: hostile spatial target classification.
- `HytaleConversionSystem`: the existing conversion attitude-provider lookup.

No default attitude is substituted. No exceptions are swallowed to manufacture
friendship. This is initialization on the existing world-thread path, not a new
faction system or global attitude override. W's healing-versus-invulnerability
policy remains unchanged. No NPC role/profile/configuration is edited by X.

## Failure diagnostics

`SkillExecutionService` now attaches safe bounded diagnostics to its existing
`SKILL_VALIDATION_REJECTED` event when validation throws a RuntimeException:

- `failureCode` remains `VALIDATION_ERROR_<class>`.
- `stage=VALIDATION`, sanitized `errorClass`, and up to **8** class/method/line
  frames identify the code boundary.
- Each class/method token is bounded to 160 characters.
- No exception message, local variables, file paths, item payloads, or arbitrary
  user/NPC data are serialized.
- Existing rootCastId, skillInstanceId and correlationId remain attached.
- `paidRootRetained=false` correctly distinguishes this pre-commit failure from
  executor failures. Existing executor diagnostics retain their previous value.

Normal authored rejections (equipment, resources, cooldown, target rules) keep
their original behavior. The summary `SKILL_ACTIVATION_REJECTED` remains small;
frames are not duplicated there. NORMAL tracing already retains this event, so
no raw tick logging or trace-level changes are introduced.

## Validation

Focused tests passed, then one coherent full retained validation ran:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --console=plain
```

**2,203 tests passed**, zero failures/errors/skips: 2,129 RPG, 53 native-control,
21 CanvasUI. Four new native cache/wiring tests and two validation-diagnostic
tests were added; no retained test or assertion was removed. The production
coordinator test injects a validation NPE and verifies correlation/code frames,
no Mana charge, no cooldown persistence and no dispatch/commit. Diagnostic
bounds/redaction have an explicit test. All W healing-policy and V tether tests
remain passing, along with persistence, exact-once, escrow, fault/crash, skill
compatibility, targetless casting and UI regressions. All 32 UI documents passed.

During focused fixture development, compilation caught access through the tracer
interface; the test now uses the existing recording tracer. An initial full
BuilderRole fixture required unrelated native stat registries and failed before
reaching the cache. It was replaced by the minimal SupportConfigBuilder fixture
with the required abstract methods implemented. The actual native getter and
cache assertions were retained. These were fixture setup failures, not suppressed
gameplay failures. No failed attempt was deployed.

The exact candidate is checked by the inherited isolated three-mod smoke,
including native projectile construction and clean shutdown. Connected NPC
healing, uncached Blackboard/provider lookup, animation, and four-mod gameplay
are not inferred from these results.

The retained real-storage performance measurement remains **UNMET**:
p50 **4.5347 ms**, p95 **8.1533 ms**, p99 **19.8667 ms**, versus unchanged
4 ms p95 / 8 ms p99. This correction does not optimize persistence or claim
native-tick qualification from storage-only results.

## Packaging, hashes and scope verification

The package starts with W's exact resource/archive entries and replaces only
full-build compiled classes. Five existing classes change implementation and
one native cache helper is added. Five SkillExecutionService nested classes
have shifted debug line tables: their `javap -c -p` output must match W exactly.
Every replacement is checked against the compiled class file. All other entries
are byte-identical to W, including icons, HUD, gameplay data, native ability roots,
projectiles, resource/cooldown implementations and persistence classes.

| Artifact | SHA-256 |
|---|---|
| X RPG JAR | `EA254981EF05FA2FB93F7E5095A935BF9114E0E46F65CD270B9EA58CD63EA4EF` |
| X three-mod ZIP | `0CB808903E6A9EFCFAE3E75F498E0E94CDB0CCF80D0DDF687B2F226464548488` |
| W rollback RPG JAR | `585E0A32B9682B5BE06FF8202EF39D9D88302E8FF178457A894440402E560EA3` |

The ZIP contains the established three RPG distribution mods. The user's
ImmersiveNPCs R170 remains a fourth live mod, not redistributed or replaced.
Isolated atomic roll-forward/rollback and exact archive hashes passed.

Evidence: [cohort-x](../../evidence/stage-13/cohort-x), with grouped full-suite
XML, native receipts, package verification, archive and deployment receipt.

## Deployment and connected recheck

**Deployed successfully at 2026-09-11 22:08:42 UTC.** Full backup: 477 files;
all 476 non-target files verified unchanged. Four live mods retained. The
installed RPG JAR hash matches the X artifact above. See
[deployment receipt](../../evidence/stage-13/cohort-x/deployment.json).
Isolated native smoke passed with process exit 0, all inherited asset checks,
native projectile construction and clean shutdown.

`Deploy-R032XTestBuild.ps1 -Deploy` requires Hytale/server stopped, validates the
installed W JAR, all three supporting mod hashes and unchanged Jonalith role,
and backs up/hash-verifies the full save before replacing **only the RPG JAR**.
Every non-target file must compare unchanged afterward. W remains the paired
rollback JAR; no save schema or configuration migration is involved.
The user-specific full save backup remains local-only.

After deployment, restart Hytale and join RPG. Keep a staff or Spellbook in the
main hand, aim at Jonalith within 18 m with clear LOS, and **hold R** if Healing
Beam is still in skill02. Require validation pass → committed → connection
started → `HEAL_APPLIED`; release must terminate it. At full Health, zero actual
Health increase is normal, but the channel must still start. Actual healing
requires a below-max-Health friendly target. Do not remove NPC invulnerability
or edit saved stats merely to manufacture a test result.

If another validation exception occurs, its new `codeFrames` should identify
the next exact boundary without changing faction, equipment, input or healing
rules speculatively. Connected acceptance remains **UNVERIFIED** until that run.
The old visible revision badge/startup content-cohort label remains unchanged;
identify X with the deployed JAR hash rather than those labels.
