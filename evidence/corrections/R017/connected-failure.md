# R016 connected CustomUI failure

- Client log: `2026-09-06_21-46-57_client.log`
- SHA-256: `E309F9906D9E1BF9CFDC109BF17ED7414B8AC0C03ABB9FB035E5C43007DA9531`
- Failure line: 1217
- Earliest boundary:
  `RpgSkillTreeLibraryRow.ui (7:89) – Expected {, found =`
- Outcome: client disconnected during GameLoading with
  `Failed to load CustomUI documents`.
- Diagnosis: imported-control `@Text =` appeared after concrete `Anchor:` property;
  R017 uses the supported concrete `Text:` property for every affected button.
