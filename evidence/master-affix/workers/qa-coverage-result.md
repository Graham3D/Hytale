# Master Affix QA coverage worker result — 2026-09-30

## Produced

- `GearAffixQaSuite` enumerates 160 deterministic single-affix/control pairs and combines only currently mapped, Java-legal, `GearAffixRuntime.ENABLED` affixes. The nine historical QA seed fixtures retain their IDs and protected provenance. Pending pairs remain inspectable and cannot be issued.
- `/rpg gear affixqa list` shows base, group, rarity, IDs, names, rolls and selectors. `spawn <fixtureId|armor|weapons|support|summons|all>` delivers no more than eight saved items per call and resumes in the same server process. A failed bag save leaves the cursor on the failed fixture.
- `tools/Generate-AffixCoverage.py` produces the 160-row JSON, CSV and Markdown audit from the current catalog, source, explicit proof registry and passing JUnit XML. `tools/Audit-GearAffixes.py` is its compatibility entry point. `tools/test_affix_coverage.py` checks exact IDs, duplicate rejection and the release gate.
- Each row records side, operator, authored families/armor extension, A/B fixture IDs, literal source/test references, a registered consumer and positive/control/negative tests when supplied, equipped-authority and carrier proof, Advanced Stats requirement, current Sentinel D12 policy/adapter set, result and explicit proof holes. Literal references are candidates only.

## Current observed result

The latest local generator run produced **160 distinct rows: 0 FUNCTIONAL, 160 GATED, 0 UNSUPPORTED**. No per-ID proof has been registered in `tools/affix-proof-map-v1.json` yet. The source scanner found literal candidate test methods for 116 IDs and none for 44; it found candidate production source references for 136 IDs. The current runtime gate contains 21 IDs, but a gate is not passing evidence. All 160 A/B descriptors exist. Thirty rows require Advanced Stats proof under the current operator map. No mapped legal carrier/test, positive/control/negative, equipped-authority, Advanced Stats or Sentinel test has been certified in the registry. This is an intentionally incomplete release result, not a claim about whether the workers' new mechanics function.

The carrier worker is still changing native bindings. The source file currently reports 318 mapped and 129 blocked bases; an existing test XML was produced against a different intermediate mapping and reports 408 mapped. Reconcile the final worker result before pinning a carrier count or accepting carrier evidence. `GearBindingReconciliationTest` now checks total partition and retained mappings instead of the stale 318/114/15 split. A final expected-count assertion, if desired, belongs after the carrier worker settles its stated 405 candidate set.

## Exact regeneration and release commands

From the repository root in PowerShell, after main runs the Java tests:

```powershell
& C:/Users/Zemio/.local/bin/python3.11.exe -m unittest tools/test_affix_coverage.py
& C:/Users/Zemio/.local/bin/python3.11.exe tools/Audit-GearAffixes.py
& C:/Users/Zemio/.local/bin/python3.11.exe tools/Audit-GearAffixes.py --require-complete
```

The second command writes `src/main/resources/rpg/gear/affix-qa-coverage-v1.json`, `docs/GEAR_AFFIX_RUNTIME_AUDIT.csv` and `docs/GEAR_AFFIX_RUNTIME_AUDIT.md`. The final command exits nonzero while any row remains GATED. The local five Python unit tests pass; the release command currently exits 1 as designed. No Gradle, game, server, auth, deploy, commit or push was run by this worker.

## Main integration holes

1. Review each gameplay test against its real production consumer and register exact test methods, legal carrier, equipped invalidity case, Advanced Stats projection where required and Sentinel inherited or owner-only case in `tools/affix-proof-map-v1.json`. Rerun Java tests so JUnit XML is newer than the tested source. `--require-complete` must pass with zero proof holes before a 160 FUNCTIONAL claim.
2. The current `tools/Test-HyArpgPackage.ps1` still asserts the old packaged 20 TEST / 140 GATED QA rows. Main must update that separate package gate for the new evidence schema. `docs/GEAR_AFFIX_QA_COVERAGE.md` also describes the old manifest and should be refreshed with final results.
3. Main owns `GearAffixRuntime.ENABLED` and the final full test/release validation. The fixture command only checks the current runtime capability gate and carrier legality; it does not open any new capability itself. The Java QA regression tests were edited for D05 five-band new-drop budgets while retaining saved-legacy legality, but were not rerun here under the no-Gradle instruction.
