# New candidate proof registration — 2026-09-30

Changed only `tools/affix-proof-map-v1.json`, `tools/Generate-AffixCoverage.py`,
`tools/test_affix_coverage.py`, and this handoff. This lane ran Python unit tests
only: `python -B -m unittest tools/test_affix_coverage.py` passed 18 tests.
The Java outcomes below come from the combat, resource, Sentinel, and UI worker
handoffs; this lane did not run fresh Java tests, produce XML, change ENABLED,
build, or make a FUNCTIONAL claim.

## Registered candidate evidence

- Added 47 per-ID positive/control/negative owner registrations from the new
  combat, resource, and economy methods. Anchors identify a literal ID, bounded
  six-channel loop, or the exact helper and assertion. `WA-141` has no new
  positive registration because its two contacts are manually built after the
  eligibility decision. `WA-069..071` and `GA-159..160` retain only reviewed
  projection evidence; they do not assert final native HP loss.
- Registered 115 bounded Sentinel parameter rows from the new tests, with
  provider membership and per-ID XML required. `WA-094` and `WA-096` remain
  unregistered because their Sentinel recovery positives failed. The existing
  `WA-083`, `WA-141`, and `WA-147` Sentinel evidence is explicitly
  **INAPPLICABLE on the current chassis**, not disabled.
- Required Advanced Stats proof for exactly the 64 represented affix IDs,
  including the 16 accepted comparison rows, global rows, cost/channel rows,
  resistance rows, and pickup reach. Bounded map and channel anchors connect
  each ID to its visible row and assertions. `WA-121..133` skill ranks are not
  Advanced Stats rows; their UI is the Skill Tree.
- Added `legalCarrierObservedTest`, which includes method, per-ID fixture, and
  mapped base only when the existing `observed_legal_carriers()` sees a passing
  legal-carrier XML row. That parser was preserved. No such fresh XML was
  created here.
- Added explicit `qualificationHoles` to the generator's gate. Owner-stage
  candidate assertions cannot become FUNCTIONAL while their listed native
  boundary remains open, even if later XML passes and ENABLED changes.

## Exact owner paths now recorded

| ID | Production consumer | Evidence limit |
| --- | --- | --- |
| WA-066, WA-067 | `GearStatusRuntime` | Isolated Poison/Bleed admitted tick, not native target HP loss |
| WA-110 | `HytaleSupportSystem` | No accepted finite friendly expiry positive; current harness bypasses native duration adapter |
| WA-152 | `EncounterContributions` | Durable award and duplicate checked, but no exact boosted-minus-base Gold assertion or native death freeze |
| WA-154 | `GearEquipmentResolution` | Legal QA reduced item equips; non-QA generated reduced item still open |
| WA-158 | `ManagedGearDamageInteraction` | Local maximum sampled and hit owner observed; native ECS contact still open |

## Remaining ID holes for main

- **WA-094, WA-096:** Sentinel NPC recovery positives fail at the production
  capability gate. No Sentinel proof was registered from these failures.
- **WA-110:** Move or expose the finite friendly duration owner so an accepted
  finite effect expiry, unrolled control, and sustained-aura exclusion can be
  observed without mirroring the math in a harness.
- **WA-113..117, WA-119:** Six unconditional summon positives fail at the
  closed gate. Lease coefficient/health, resistance projection, cadence claim,
  and expiry claim are not final native attack, HP, incoming loss, movement, or
  actor lifetime effects. `WA-118` still lacks a native movement effect check.
- **WA-141:** Run the Twin gate, selector collision, and accepted native target
  contact. The current extra contact is manually built. `WA-083` and `WA-147`
  remain current-chassis INAPPLICABLE Sentinel cases.
- **WA-152:** Assert the exact credited Gold delta over the same-base unrolled
  award, then exercise the native equipped-source death freeze. `WA-151` still
  needs the native equipment reader-to-death-freeze handoff. `WA-154` still
  needs a non-QA reduced-requirements production item.
- **WA-003, WA-001/002/006/007/010, WA-017..022, WA-029..034,
  WA-157/158:** Owner calculations have isolated contacts or samplers, but
  native ECS accepted contact and final target damage remain open.
- **WA-013, WA-065..068, WA-134:** Isolated periodic/status owners are tested;
  native applied status contact and final recipient effect remain open.
- **WA-069..071, WA-078, GA-159/160:** Native BeforeArmor/FilterDamage and
  final HP loss remain open. Managed defense and resistance projections do not
  supply that result. **WA-082** has a swept plan but no applied displacement.
- **WA-135..143:** Proc owner outcomes and exclusions need connected native
  child/contact target outcomes. **WA-145/146** need final native projectile
  spawn/contact after the proven queued canonical child context.
- **WA-148..150:** Bound Sentinel aura recipient fields and teardown were
  observed offline; native recipient effect and valid equipment lifecycle are
  open. `WA-149/150` also lack a registered gameplay negative test.
- **WA-155:** Measured native light calibration and an effective-radius
  assertion remain open; Advanced Stats intentionally has no light-radius row.
- **WA-008, WA-014, WA-016, WA-053..055, WA-057..063, WA-144:**
  The existing registry still lacks a same-event control registration. The
  status rows `WA-053..055` specifically need same-hit unrolled and chance-miss
  controls; `WA-144` needs a same-request unrolled control.

Main owns fresh per-ID JUnit/XML, legal carrier XML, capability qualification,
native gates, and full build. No generated coverage artifact was written here.
