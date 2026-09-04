# Orbis Distillation offline tools

These tools are offline-only and do not start training or call a teacher service.

- `preflight.ps1` resolves and initializes an artifact root outside the active save.
- `corpus-audit.ps1` validates exported candidate envelopes and rejects premature labels.
- `teacher-import-audit.ps1` validates reviewed-import shape and rejects hidden reasoning.
- `setup-training-env.ps1` validates the pinned Block 3 environment plan and fails
  closed before installation when required native kernels are unsupported.
- `peft-preflight.ps1` writes an immutable D6 run report and stops before model
  load or gradient execution when a required gate fails.
- `train-smoke.ps1` refuses SFT-0 unless G2 passed and a 32–128-row immutable
  smoke dataset is supplied.
- `eval-reference.ps1` refuses evaluation until an adapter round trip and SFT-0
  checkpoint exist.
- `report-training.ps1` verifies and summarizes a D6 run report.
- `test-block3.ps1` runs deterministic Block 3 fail-closed gate tests with the
  isolated external Python interpreter.

The authoritative D2/D3 writes are the Java `CorpusJsonlExporter`,
`ReviewedTeacherImport`, and `TeacherRunStore` paths. The committed configuration
defaults to `OFF`; an explicit offline process must select `CORPUS_AUDIT`.
