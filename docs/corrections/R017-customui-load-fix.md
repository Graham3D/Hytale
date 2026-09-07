# R017 — CustomUI Load Fix

R017 is a deployment-blocking parser correction over R016. Stage 06 has not begun.

## Connected failure

The newest client log identifies the exact earliest boundary:

```text
Failed to load CustomUI documents
Failed to parse file RpgSkillTreeLibraryRow.ui (7:89) – Expected {, found =
```

The reviewed client log is `2026-09-06_21-46-57_client.log`, SHA-256
`E309F9906D9E1BF9CFDC109BF17ED7414B8AC0C03ABB9FB035E5C43007DA9531`.
Server startup itself completed and advertised R016 correctly; the client rejected the
RPG asset pack before completing world entry.

## Root cause and correction

R016's new Skill Tree documents placed imported-control macro assignment syntax after
a concrete property, for example:

```text
Anchor: (...); @Text = "Content - Category";
```

At that position the client grammar expects another control/block and rejects the
`=` token. The static validator only checked strings and balanced delimiters, so the
document incorrectly passed the predeployment gate.

R017 replaces every affected Skill Tree button assignment with the concrete supported
property:

```text
Text: "Content - Category";
```

The correction covers both `RpgSkillTreeLibraryRow.ui` and every corresponding
button in `RpgSkillTree.ui`, preventing the parser from merely failing on the next
document after the first error was removed.

The shared CustomUI validator now rejects the exact late-macro-argument grammar
pattern (`Property: value; @Argument = value`) in addition to invalid escapes,
unterminated strings, delimiter errors, and labeled plain Buttons. Both source trees
and the packaged RPG JAR are validated during the R017 gate and again immediately
before deployment.

## Verification and deployment

| Item | Result |
|---|---|
| Revision/version | `R017` / `0.0.10` |
| Hytale target | `0.7.0-pre.1` |
| Player schema | `3` (unchanged) |
| Implementation commit | `f374e7c1efd8a48b3a7a5439b4f97da09e5e5a50` |
| Evidence commit | `EVIDENCE_COMMIT_PENDING` |
| Clean aggregate tests | PASS — 125, no failures/errors/skips |
| Source CustomUI validation | PASS — 17 documents |
| Packaged RPG CustomUI validation | PASS — 10 documents |
| Late macro-argument findings | 0 |
| CanvasUI source | unchanged |
| Isolated server smoke | PASS — exactly three mods and clean shutdown |
| RPG JAR SHA-256 | `03ED36A06AD143FBB74CD591052E56CC035E9B295BEFE89BE2A05D224772BA89` |

The RPG save now contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.10.jar` | `03ED36A06AD143FBB74CD591052E56CC035E9B295BEFE89BE2A05D224772BA89` |

Machine-readable verification, smoke, and installation records are under
`evidence/corrections/R017/`. The deployed R016 JAR is retained at
`evidence/corrections/R017/rollback/HytaleRPG-0.0.9.jar`.

## Connected check

Fully restart and rejoin the RPG world. Successful world entry proves the corrected
asset bundle passes the client's load-time parser. Then run `/rpg skilltree` to test
the page itself. If another error occurs, preserve the newest client/server logs; the
next exact file/line boundary will be actionable.

```ini
R017 = DEPLOYED_AWAITING_CONNECTED_REJOIN
Stage06Started = false
```
