# Hywind Engineering Guardian

Guardian is a local, deterministic safety check for engineering work. It does not call an AI model, use a network service, alter Git history, deploy a JAR, or touch a game save. It reads only the selected Hywind worktree, its linked Git metadata, and repository-local validation evidence. Its current gameplay revision remains R250-U7P5B; Guardian itself does not assign a new gameplay revision.

## Baseline and results

The locally known authoritative ref is `refs/remotes/origin/main`. Its commit advances when the owner fetches or pushes `main`; Guardian does not pin a historical SHA or infer currency from a revision number or commit date. A branch at or descended from that ref passes the ancestry check. A branch behind it or unexpectedly diverged is BLOCKED. A missing baseline, detached HEAD, missing or untracked root `AGENTS.md`, changed HyARPG identity/entrypoint, or inconsistent Gradle version/revision is also BLOCKED.

`origin/main` is local knowledge, not a live GitHub query. Guardian warns when its tracking-ref reflog is unknown or over 24 hours old. A newer remote change inside that interval cannot be detected offline. Fetch and review the remote branch before starting important work; a new feature branch should be based on the refreshed ref.

`Preflight` reports uncommitted or untracked work as WARNING and preserves it. `Changes` checks staged, unstaged, and untracked paths. With `-Scope`, any path outside the exact paths or directory prefixes supplied is BLOCKED. Twenty or more tracked deletions are BLOCKED by default; an explicitly reviewed refactor can use `-AllowMassDeletion` to downgrade that finding to WARNING. Architecture, engineering rules, revision records, the separate `persistent-npcs/` mod, and build configuration are flagged for review. No check resets, restores, cleans, or deletes work.

Normal output is one line: `GUARDIAN: PASS`. Warnings exit 0 so they remain advisory; blocked checks exit 2. Use `-Details` when the worktree path, branch, HEAD, and locally known baseline commit are needed. Review every diagnostic before continuing.

## Everyday commands

From any Hywind checkout or linked Git worktree:

```powershell
& .\tools\guardian\Invoke-Guardian.ps1 -Mode Preflight
& .\tools\guardian\Invoke-Guardian.ps1 -Mode Changes -Scope @('tools/guardian/', 'AGENTS.md', 'docs/GUARDIAN.md')
```

`-Scope` values are repository-relative exact files or directory prefixes ending in `/`. Omit `-Scope` only when no task scope has been agreed; Guardian will warn that it cannot classify out-of-scope paths. For a compact optional pre-commit check, use `-Mode Commit`; it examines staged paths, warns on protected files, and blocks suspicious staged mass deletions without treating ordinary staged work as a warning.

For a committed engineering revision, run the existing offline QA **explicitly** after the commit. This example captures the unmodified Gradle `check` log inside ignored `build/`, then records the JUnit reports and the existing package validator result:

```powershell
New-Item -ItemType Directory -Path build/guardian -Force | Out-Null
.\gradlew.bat --offline --no-daemon check --console=plain *> build/guardian/gradle-check.log
if ($LASTEXITCODE -ne 0) { throw 'Gradle check failed.' }
& .\tools\guardian\Record-Verification.ps1
& .\tools\guardian\Invoke-Guardian.ps1 -Mode Completion
```

`Record-Verification.ps1` does not start Gradle. It accepts only evidence inside the checkout, checks a successful log written after HEAD, four JUnit module report sets with zero failures/errors/skips, the built JAR's embedded source commit, and the existing `Test-HyArpgPackage.ps1` result. It writes an ignored `build/guardian/verification.json` receipt. `Completion` checks that receipt, its hashes, current test reports, the JAR, revision documentation, clean Git status, and observed upstream tracking ref. A local-only commit is reported as WARNING, not claimed as pushed. A receipt is local evidence, not a signed attestation or connected-game acceptance.

## Optional Codex launcher and hook

To guard a **CLI** task launch, run `& .\tools\guardian\Start-GuardianCodex.ps1 -Repository .`. It starts Codex only after a clean PASS; `-AllowWarning` requires a deliberate choice to proceed with warnings. The launcher protects only launches that use it and does not change Codex app project settings.

The tracked `tools/guardian/hooks/pre-commit` template is inert until the owner approves installation. It runs `-Mode Commit` for a baseline and staged-change check. An owner-approved installation for this clone would set `git config --local core.hooksPath tools/guardian/hooks`; this affects its linked worktrees and should be reviewed alongside any existing hook setup. No hook is installed by this implementation.

Git hooks guard supported Git operations, not arbitrary file edits, and can be bypassed. `AGENTS.md` is guidance rather than an independent permission boundary. The launcher only checks the initial state when used. Guardian does not fetch, prove live remote freshness, run full QA automatically, or replace a review of staged files, release gates, and connected-game testing.

## Tests and performance

Run `& .\tools\guardian\Test-Guardian.ps1`. It creates small, ignored Git fixtures under `build/guardian-fixtures/`, including local-only and pushed fixture commits; it does not change real Hywind history or contact a network. Fixtures remain for inspection. The initial 19-case run completed with a clean preflight near 0.6 seconds including Windows PowerShell startup; PASS output was 14 UTF-8 bytes. Timings vary by machine and checkout size.
