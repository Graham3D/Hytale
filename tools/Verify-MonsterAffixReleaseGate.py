"""Validate the persistent Monster Affix release gate without starting Hytale."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json"
REQUIRED = {"ENEMY_ACTION", "ORDINARY_DIFFICULTY", "SKELETON_BINDINGS",
            "CHAMPION_IDENTITY", "CHAMPION_COMPOSITION", "PLAYER_CAST",
            "ADMISSION", "PRESENTATION", "REWARDS", "LIFECYCLE"}
REQUIRED.update(f"ME-{number:03d}" for number in range(1, 28))
STATES = {"PASS", "FAIL", "BLOCKED", "NOT_RUN"}


def verify(acceptance: bool) -> None:
    gate = json.loads(GATE.read_text(encoding="utf-8"))
    rows = gate["requirements"]
    ids = [row["id"] for row in rows]
    assert len(ids) == len(set(ids)), "duplicate release-gate ID"
    assert set(ids) == REQUIRED, f"missing/unapproved requirement IDs: {sorted(REQUIRED ^ set(ids))}"
    assert gate["feature"] == "Monster Affix"
    assert gate["status"] in {"OPEN", "ACCEPTED"}
    assert gate["baselineSourceCommit"] and gate["installedServerSha256"] and gate["installedAssetsSha256"]
    source = gate.get("candidateSourceCommit", "")
    build = gate.get("candidateJarSha256", "")
    assert not source or re.fullmatch(r"[0-9a-f]{40}", source), "invalid candidate source commit"
    assert not build or re.fullmatch(r"[0-9a-fA-F]{64}", build), "invalid candidate JAR hash"
    for row in rows:
        assert row["status"] in STATES, row["id"]
        assert row["expected"] and row["missingOrFailure"], row["id"]
        assert isinstance(row["tests"], list) and isinstance(row["evidence"], list), row["id"]
        assert row["sourceCommit"] == source and row["buildSha256"] == build, row["id"]
        assert all(isinstance(path, str) and path and (ROOT / path).is_file() for path in row["evidence"]), row["id"]
        for field in ("implemented", "automatedPassed", "nativeConnectedPassed", "ownerAccepted"):
            assert isinstance(row[field], bool), (row["id"], field)
        if row["status"] == "PASS":
            assert all(row[field] for field in ("implemented", "automatedPassed", "nativeConnectedPassed", "ownerAccepted")), row["id"]
            assert row["tests"] and row["evidence"] and source and build, row["id"]
            assert any(not path.startswith("docs/") for path in row["evidence"]), row["id"]
    if acceptance:
        assert gate["status"] == "ACCEPTED" and gate["ownerAccepted"], "owner acceptance missing"
        assert all(row["status"] == "PASS" for row in rows), "open Monster Affix requirements"
    else:
        assert gate["status"] == "OPEN" and not gate["ownerAccepted"], "candidate must stay open"
    print(f"Monster Affix gate valid: {len(rows)} rows; status={gate['status']}; "
          f"acceptance={'PASS' if acceptance else 'NOT_REQUESTED'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--acceptance", action="store_true")
    verify(parser.parse_args().acceptance)
