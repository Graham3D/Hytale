# Affix proof completion registration — 2026-09-30

## Scope and verification

Changed only `tools/affix-proof-map-v1.json`, `tools/Generate-AffixCoverage.py`, `tools/test_affix_coverage.py`, and this handoff. No Java, Gradle, game, server, authentication, deployment, commit, native asset promotion, or final audit action was performed. The production capability gate was not changed.

Read every newly supplied runtime test named in the request, including rank execution, resource, support, status, recovery, signature procs, summon leases, Sentinel consumers, item children and auras, native utility, and Advanced Stats. Registrations are tied to assertions and the called owner. The bounded rank map, computed loops, invoked same-file helpers, simulator rows, MethodSource provider, and inherited runtime chains are checked from source. Eligibility-only, metadata-only, combined-effect, QA shadow, and gate-conditional observations were not registered as isolated positive gameplay proof.

`python -m unittest tools/test_affix_coverage.py -q` using the local Python 3.11 executable: **14 passed**. Read-only `build()` summary: **160 rows, 0 FUNCTIONAL, 160 GATED**. No final audit was written.

## Registered fields

| Field | IDs registered | Review boundary |
| --- | ---: | --- |
| Positive runtime candidate | 135 | Owner invocation or reviewed runtime chain |
| Same-event/control candidate | 114 | Actual control assertion in source |
| Negative/condition candidate | 109 | Actual boundary assertion in source |
| Equipped authority | 160 | Explicit `@ValueSource` row, accepted frozen snapshot, unequip/broken/wrong-slot/requirements rejection |
| Legal carrier test | 160 | `@MethodSource` from `QA.coverage()` invokes production `GearDropGenerator.eligible` for the authored fixture |
| Sentinel behavior | 44 | Bound attack/status/signature/heal/displacement or owner-only exclusion; aura context alone excluded |
| Advanced Stats comparison | 6 | Existing exact UI/owner assertions only |

The simulator's equipment and legal registrations are **candidates until per-ID JUnit XML passes**. The legal test checks production eligibility on the QA-selected fixture, but its body does not assert the selected base's native mapping; only four IDs currently have a concrete mapped `legalCarrier`. Its lifecycle assertions do not count as a gameplay positive.

`WA-112` retains the generic successful-cleanse receipt, same-event control, and rejection paths. `WA-120` retains the finite **20 Mana** Simulacrum cast/payment and insufficient/unequipped cases. The rank map now binds each `WA-121..133` selector to its own learned execution and projection assertions. `WA-148..150` use the actual item aura runtime; their Sentinel context-only test was not promoted to an inherited-effect proof. `WA-113..119` retain lease controls, but gate-conditional assertions were removed as positive gameplay registrations. `WA-141` chance eligibility is kept as a candidate, pending a connected extra-hit contact.

## Remaining proof holes

Counts overlap by ID. Current report holes: **160** equipped XML, **160** legal test XML, **156** concrete mapped legal carriers, **139** closed production gates, **116** missing Sentinel dispositions, **42** Sentinel tests missing/unpassed, **30** Advanced Stats comparisons missing/unpassed, **25** positive sources missing, **46** control sources missing, **51** negative sources missing, and **6** runtime consumers missing. Registered gameplay methods additionally lack fresh passing XML for **68 positive**, **55 control**, and **59 negative** fields. Main must run its permitted tests and review current XML; no pass was fabricated.

### Genuinely missing or insufficient positive scenarios

| Affix IDs | Required runtime test before positive registration |
| --- | --- |
| WA-003 | Accepted contact on a **mapped legal focus** with this source alone changes the managed magic damage; same-base unrolled control. Existing focus test uses unmapped legacy bases. |
| WA-009 | Accepted cast with the affix changes actual cast release time against the same learned skill and unrolled item. |
| WA-157, WA-158 | Accepted native physical contacts separately prove the minimum and maximum range endpoints, with same-base unrolled controls. |
| WA-066, WA-067 | Accepted poison and bleed admissions separately prove status potency at the periodic owner, each against an unrolled matching hit. |
| WA-069, WA-070, WA-071 | Separate shield flat, shield increased, and global defense sources change the native defense filter's actual loss. The current Sentinel defense test combines all three. |
| WA-103, WA-104 | Separate committed heals prove Healing Power and Healing Done individually against same-skill unrolled controls. The current combined two-roll heal cannot attribute the difference. |
| WA-110 | An accepted finite friendly support effect proves extended duration at `HytaleSupportSystem`, with sustained aura exclusion and an unrolled control. |
| WA-113, WA-114, WA-115, WA-116, WA-117, WA-118, WA-119 | Separate actual summon attack, health, defense, resistance, attack interval, movement, and expiry outcomes from a valid owner source, each compared with a control while the gameplay effect is demonstrably active. Existing lease expectations depend on the closed `ENABLED` gate; movement asset selection alone is insufficient. |
| WA-141 | An accepted managed offhand contact queues the extra strike with correct damage and no-proc/root handling; same-event unrolled and rejected-source controls. `NativeTwinAssaultEligibility.accepts` alone proves only eligibility. |
| WA-151 | A production, non-QA source changes actual loot rarity weights/reward selection while preserving source attribution; the QA shadow test explicitly grants no drop. |
| WA-152 | A credited production currency reward changes awarded quantity once, with unrolled and cancelled/duplicate controls. Advanced Stats text alone is insufficient. |
| WA-154 | A lower-stat actor equips a legal reduced-requirements item that the otherwise identical unrolled item rejects, then loses authority on removal. |
| GA-159, GA-160 | Separate accepted native armor contacts prove flat armor and increased armor in the final defense loss calculation, each against its own unrolled carrier. |

The six missing runtime-owner registrations are `WA-066`, `WA-067`, `WA-110`, `WA-152`, `WA-154`, and `WA-158`; register each only after the corresponding called owner is fixed by a test.

### Controls, negatives, Sentinel, legality, and UI

The **46 missing control IDs** and **51 missing negative IDs** are listed below so main can assign tests without expanding a generic control across unrelated affixes. Each needs a matching runtime-owner assertion for its own effect: a same-event unrolled or empty-source control, then the condition/source boundary appropriate to the operator. Status procs need a wrong channel or no-proc accepted-contact case; signature procs need rejected source, protected target, duplicate/root, or cooldown cases; defense and damage need wrong source/channel and native hit controls; resources need unaffordable or unequipped payment; support and summon effects need expired, removed, ineligible, or gate-independent controls.

- Missing controls: `WA-003 WA-008 WA-009 WA-012 WA-014 WA-016 WA-157 WA-158 WA-053 WA-054 WA-055 WA-057 WA-058 WA-059 WA-060 WA-061 WA-062 WA-063 WA-066 WA-067 WA-069 WA-070 WA-071 WA-078 WA-082 WA-098 WA-103 WA-104 WA-109 WA-110 WA-115 WA-134 WA-135 WA-137 WA-138 WA-139 WA-140 WA-141 WA-142 WA-143 WA-144 WA-151 WA-152 WA-154 GA-159 GA-160`.
- Missing negatives: `WA-001 WA-002 WA-003 WA-006 WA-007 WA-010 WA-012 WA-013 WA-157 WA-158 WA-017 WA-018 WA-020 WA-021 WA-022 WA-029 WA-030 WA-031 WA-032 WA-033 WA-034 WA-065 WA-066 WA-067 WA-068 WA-069 WA-070 WA-071 WA-078 WA-082 WA-095 WA-097 WA-103 WA-104 WA-106 WA-109 WA-110 WA-113 WA-114 WA-115 WA-116 WA-117 WA-118 WA-141 WA-149 WA-150 WA-151 WA-152 WA-154 GA-159 GA-160`.

The **116 IDs without a Sentinel behavior registration** require either a bound, valid inherited effect at its native/runtime owner or an owner-only exclusion at that owner. A classification assertion or bound context alone is insufficient. In particular, `WA-148..150` need the bound aura to activate and affect a recipient; `WA-145..147` need bound child execution and adapter disposition; `WA-113..120` need explicit owner-only exclusion from the bound Sentinel effect. The full exact set is the read-only `sentinelTest` missing list from the proof registry; its count is 116.

The legal MethodSource has no fresh per-ID XML. For the **156 IDs without a concrete mapped base**, a test must bind its selected `ab-<id>-affixed` fixture to the exact base, assert `native-bindings-v1.json` disposition `MAPPED` and world-drop eligibility, and call `GearDropGenerator.eligible` for that same base and affix. Current registrations do not invent a base ID.

The **30 Advanced Stats required IDs** remain missing/unpassed as a group. Current exact UI registrations are `WA-010`, `WA-011`, `WA-012`, `WA-078`, `WA-085`, and `WA-091`, but none is a fresh passing comparison. Missing rows include cast speed `WA-009`, attributes `WA-086..090`, capacities `WA-092..093`, each named skill rank `WA-121..133`, Magic Find `WA-151`, and local armor `GA-159..160`; add a visible row/owner/unequip comparison for each where the product's Advanced Stats contract requires it.

Native asset bindings for the later 90-ID promotion and the production gate belong to main after actual proof completion. The current matrix deliberately remains GATED.
