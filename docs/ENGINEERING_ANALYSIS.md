# Focused bug investigation and analysis

For a reproducible gameplay defect, inspect the relevant Trace, log window, production path, and existing tests. Add the smallest deterministic regression test that fails on the defective behavior where practical; then make the narrow fix and run that test plus neighboring tests. Assert the outcome that originally failed, not merely that a method ran. For stateful defects, cover the relevant admission, allocation, activation, persistence, cancellation, teardown, recovery, and repeat paths. Use controlled fault injection at transaction boundaries. If offline reproduction is impractical, record the narrow diagnostic evidence and the connected-game behavior still awaiting the user's QA. Coverage and static analysis help locate gaps; neither proves gameplay acceptance.

## Optional JaCoCo coverage

JaCoCo **0.8.15** runs only with `-PguardianCoverage=true`. This focused example instruments the existing Elite birth/persistence test and creates root HTML and XML reports:

```powershell
.\gradlew.bat --offline --no-daemon -PguardianCoverage=true :test --tests com.inigmasgames.hytalerpg.enemies.EnemyBirthPersistenceTest :jacocoTestReport --console=plain
```

The root reports are `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml`. For a combined root/native report, select relevant classes on both test tasks:

```powershell
.\gradlew.bat --offline --no-daemon -PguardianCoverage=true :test --tests com.inigmasgames.hytalerpg.enemies.EnemyBirthPersistenceTest :nativeControlTest --tests com.inigmasgames.hytalerpg.execution.hytale.R244NamesTest guardianCoverage --console=plain
```

The combined HTML/XML reports are under `build/reports/jacoco/guardianCoverage/`. To cover independent CanvasUI and Taverns tests, add `:canvas-ui:jacocoTestReport :hytale-taverns:jacocoTestReport`; their reports live under each module's `build/reports/jacoco/test/`. Reports measure production source sets only. Routine `check` attaches no JaCoCo agent and runs no coverage report. An exploratory full-suite instrumented run tripped the existing timing assertion in `Stage13V2RecoveryEdgesTest` (`maxCheckpointBacklog` 1 versus expected 2). Use focused coverage for diagnosis; the uninstrumented full `check` remains the release gate until that scheduling-sensitive test is made robust under instrumentation.

## Optional SpotBugs analysis

SpotBugs Gradle plugin **6.5.11**, with SpotBugs engine **4.10.2**, analyzes compiled HyARPG, CanvasUI, and Taverns production classes only. The independent ImmersiveNPCs mod is outside these tasks. An exploratory pass reports all findings without failing on the known legacy set:

```powershell
.\gradlew.bat --offline --no-daemon -PguardianSpotBugs=true -PguardianSpotBugsReport=true guardianSpotBugs --console=plain
```

Run the strict option, which compares against the reviewed baseline XML files in `config/spotbugs/`, with:

```powershell
.\gradlew.bat --offline --no-daemon -PguardianSpotBugs=true guardianSpotBugs --console=plain
```

Both modes write `build/reports/spotbugs/main.xml` and `main.html` in the root, `canvas-ui/`, and `hytale-taverns/` builds. Exploratory mode intentionally permits existing warnings so the full report can be read; strict mode fails if any finding outside the pinned baseline appears. New findings must be investigated, fixed or specifically reviewed before a baseline update. Normal `check` does not apply the SpotBugs plugin or schedule analysis. The baseline files retain SpotBugs finding records with local machine paths redacted; generated full reports stay ignored under `build/`.

The initial R250-U7P5B exploratory pass found **546** root, **32** CanvasUI, and **14** Taverns findings. Triage examples:

- **Likely genuine, needs a focused follow-up:** `ComfortRegistry.load` reports `NP_NULL_PARAM_DEREF` around line 76: an empty external JSON document can yield `null` overrides before `merge`. Keep the current fallback behavior under test before changing it.
- **Needs investigation:** `CanvasSession.recordPointer` and related counters report non-atomic updates. Verify the actual callback threading contract before choosing synchronization. Native Hytale nullability annotations also produce `NP_NONNULL_PARAM_VIOLATION` reports that require call-path review.
- **Understandable false positive or accepted API design:** `EnemyAffixRegistry` null-path warnings overlook its throwing `require` guard; `RpgCombatKernel` intentionally returns mutable service references to internal collaborators. These remain visible in the exploratory report.

The initial baseline records the current findings only; it is not a declaration that every warning is harmless. Do not change gameplay to silence analysis without a targeted defect and regression test. The user's in-game QA workflow remains unchanged.
