# Orbis Distillation Block 1 tools

These tools are offline-only and do not start training or call a teacher service.

- `preflight.ps1` resolves and initializes an artifact root outside the active save.
- `corpus-audit.ps1` validates exported candidate envelopes and rejects premature labels.
- `teacher-import-audit.ps1` validates reviewed-import shape and rejects hidden reasoning.

The authoritative D2/D3 writes are the Java `CorpusJsonlExporter`,
`ReviewedTeacherImport`, and `TeacherRunStore` paths. The committed configuration
defaults to `OFF`; an explicit offline process must select `CORPUS_AUDIT`.
