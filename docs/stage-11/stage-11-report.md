# Stage 11 — remaining passive primitives

R030 / 0.0.23. Status: IMPLEMENTATION_IN_PROGRESS. No connected Stage11 PASS.
Start: Stage10 local closure `2e5d928`; player schema5, compiled-plan schema9.
Work and evidence remain in the GitHub folder, not Google Drive. Live R023 is
untouched. The owner's continuous implementation authorization permits local
stage advancement but does not waive connected evidence or safety boundaries.

## Cohort A — Efficiency, Long Reach, Rapid Invocation

Three passives, within the four-passive cohort limit. Exact master LP002–004
records were read before implementation. Lingering was considered for this cohort
but split out: inherited `HAS_FINITE_DURATION` tags include flight/warning-only
lifetimes, so a generic lifetime multiplier would be incorrect.

### Findings and changes

- Efficiency's 0.85 cost multiplier was already implemented by the existing
  kernel. Integer nonzero spend remains `max(1,ceil(base*factor))`; zero remains
  zero. Upkeep remains fractional. No second cost multiplier was added. The
  compatibility parser incorrectly required an upfront spend for the canonical
  “or continuous Mana upkeep” clause, excluding Void Beam/Life Drain. That
  clause now accepts a declared upkeep component. Imported finite-cost tags also
  mislabeled pure reservation Auras; Managuard, Emanatism and Pedanticism now
  reject Efficiency rather than purporting to reduce reservation percentages.
- Long Reach and Rapid Invocation previously appeared as compiler descriptions
  without effective runtime fields. New typed `FoundationModifiers` resolves an
  immutable execution profile before family preflight, target selection, windup,
  snapshot and dispatch. It does not replace any family executor. Only named
  component fields change, using canonical record validation after conversion.
- Long Reach multiplies declared reach/placement/travel by 1.25 once. It does not
  scale target or damage radii, widths, cone angles, heights, summon leash, or
  collision sizes. Pounce's landing strike uses a field named `range` as a radius;
  that field is explicitly excluded. Native Orbit's field named `range` is also
  an orbit radius, not reach. Eleven canonical radius-only skills with imported
  `HAS_RANGE` tags now reject the link instead of leaking geometry capability.
- Conversely, a cone stores its forward reach in `AreaSkillProfile.radius`: that
  field receives reach, but not its angle or inner status threshold. A placed
  wall receives extended placement distance, not extended wall length or width.
- A traveling wave's derived travel lifetime scales with reach to preserve its
  authored speed and actually reach the extended endpoint. This is not Lingering.
  Projectile derived travel time already follows distance/speed; independent
  projectile expiry caps remain unchanged. Beam upkeep, duration and cadence,
  trap arming/status durations, and summon lifetimes remain unchanged.
- Rapid Invocation scales nonzero precommit windup by 0.80 with a 0.05s minimum.
  Instant and reaction-only skills reject it. Skill Delay, postcommit timing,
  authored cooldown, and Wisdom recovery remain unchanged.
- Effective profiles are cached by immutable authored profile and typed modifier
  values, maximum1024 LRU entries, never recomputed in native victim/tick loops.
  The compiled-plan schema increments to10 and its hash includes the typed
  foundation record. Player schema5 and owned content are preserved.

### Verification and boundaries

25 new tests cover positive and at least two negative fixtures for each primitive,
component exclusion, original-profile immutability, repeat resolution, cross-skill
plan rejection, and a real `SkillExecutionService` windup/commit/dispatch fixture.
That fixture pays8 Stamina for Heavy Swing after Efficiency, uses0.36s windup and
3.75m reach, and preserves the existing Wisdom-adjusted cooldown calculation.

Initial test failures were diagnostic-fixture errors: an invented weapon tag was
replaced with the existing `RPG_WEAPON_HEAVY` tag; the cooldown assertion was
corrected to compare the existing Wisdom calculation instead of assuming zero
Wisdom. The retained schema assertion was updated from9 to10. These did not
justify weakening production equipment validation or changing cooldown formulas.

Full retained build and isolated network smoke are recorded separately below.
Neither proves connected reach, casting timing, native input, animation, resource
presentation or client rendering. Native Health/Mana/Stamina, native AbilitySlots,
Ability4 policy and XP controls/assets are untouched. R024 native entry still
requires owner connected verification. Bone Cage remains safety-gated.

Rollback: `evidence/stage-11/cohort-a/rollback/HytaleRPG-0.0.22.jar` preserves the
Stage10 final build; no new player-state migration is introduced in this cohort.

### Cohort A local gate

- `clean build`: **687 tests PASS**, zero failures/errors/skips, including retained
  Stage01B/combat/CanvasUI/native-codec suites.
- Exact final build: `evidence/stage-11/cohort-a/artifacts/HytaleRPG-0.0.23.jar`,
  SHA-256 `7558FABC74B0D9068E603330844FC2455836E5723D2ED71BD73BCC4A5288C1D0`.
- Normal isolated three-mod startup reached `Hytale Server Booted` and clean stop,
  exit0. All60 neutral native ability assets and all retained family assets/roles
  resolved. No player connected. The earlier smoke was rerun after the final
  cone/wall audit so archived evidence matches the final binary.
- Packaged CustomUI audit:9 RPG documents PASS; native resource/XP/ability HUD,
  existing projectile assets and baseline costs are unchanged.
- Rollback SHA-256: `DB7DACA4D309C7590A3FBD7D7E35F0F74D94CEEB5BCA91D5AB8A88B1973D9A41`.
- Machine evidence: `evidence/stage-11/cohort-a/verification.json`, test case list
  and exact-build server log alongside it. Local gate PASS; connected UNVERIFIED.
  The evidence script was corrected for PowerShell5.1's JSON array wrapping;
  actual catalog counts remain87/66, not a content change.

Next authorized work: remaining Stage11 primitives in bounded cohorts, then the
complete eligibility/pair matrix and graph-fuzz closure. Do not present these
three primitives as completion of all40 Stage11 passives.
