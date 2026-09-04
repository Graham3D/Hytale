#!/usr/bin/env python3
"""Fail-closed G0/D6/D7 gate for the isolated Orbis training harness."""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.metadata
import json
import math
import os
import platform
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


RUN_STATES = {"PLANNED", "PREFLIGHT", "RUNNING", "COMPLETED", "FAILED", "INVALIDATED"}
SUPPORTED_TARGETS = {"ATTENTION_ONLY", "MLP_ONLY", "ATTENTION_PLUS_MLP"}
REQUIRED_NOTICE = "Licensed by NVIDIA Corporation under the NVIDIA Nemotron Model License."


class GateError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def read_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GateError("INVALID_JSON", f"Cannot read {path}: {exc}") from exc


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise GateError("MISSING_ARTIFACT", f"Cannot hash {path}: {exc}") from exc
    return digest.hexdigest()


def verify_adapter_hash(path: Path, expected: str) -> None:
    require(sha256_file(path) == expected, "CORRUPTED_ADAPTER", "Adapter hash does not match its manifest")


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        raise GateError(code, message)


def validate_g0(project_root: Path, base_root: Path) -> dict[str, Any]:
    base = read_json(project_root / "training/configs/g0-training-base.json")
    license_decision = read_json(project_root / "training/configs/g0-license-decision.json")
    require(base.get("decision") == "PASS", "G0_BASE_NOT_APPROVED", "Pinned training base is not approved")
    require(base.get("approval") == "TRAINING_BASE_APPROVED_SEPARATE_LINEAGE",
            "G0_LINEAGE_NOT_APPROVED", "Separate candidate lineage is not explicitly approved")
    revision = str(base.get("revision", ""))
    require(len(revision) == 40 and all(c in "0123456789abcdef" for c in revision),
            "MODEL_REVISION_INVALID", "Pinned base revision is not an immutable SHA-1")
    require(license_decision.get("decision") == "PASS_FOR_LOCAL_D6_D7_ONLY",
            "LICENSE_UNRESOLVED", "Local D6/D7 rights are not approved")
    rights = license_decision.get("rightsForThisBlock", {})
    for right in ("localUse", "modificationAndDerivativeAdapter", "mergeIntoCopy"):
        require(rights.get(right) is True, "LICENSE_UNRESOLVED", f"Required right is not approved: {right}")
    require(license_decision.get("requiredNotice") == REQUIRED_NOTICE,
            "NOTICE_MISMATCH", "NVIDIA NOTICE wording does not match the pinned official license")

    snapshot = base_root / "snapshot"
    verified: list[dict[str, Any]] = []
    for item in base.get("identityFiles", []):
        path = snapshot / item["path"]
        actual = sha256_file(path)
        require(actual == item["sha256"], "BASE_IDENTITY_HASH_MISMATCH",
                f"Identity hash mismatch: {item['path']}")
        verified.append({"path": item["path"], "sha256": actual})
    return {
        "repository": base["repository"],
        "revision": revision,
        "architecture": base["architecture"],
        "weightFiles": base["weightFiles"],
        "verifiedIdentityFiles": verified,
        "productionGgufLineage": base["productionGguf"]["classification"],
        "approval": base["approval"],
        "licenseDecision": license_decision["decision"],
        "redistribution": "NEEDS_REVIEW",
    }


def validate_dataset(offline_root: Path, dataset_id: str, allowed_licenses: set[str]) -> dict[str, Any]:
    dataset_root = offline_root / "datasets" / dataset_id
    manifest = read_json(dataset_root / "manifest.json")
    license_manifest = read_json(dataset_root / "licenses/manifest.json")
    require(manifest.get("state") == "FROZEN", "DATASET_NOT_FROZEN", "Dataset is not frozen")
    require(manifest.get("datasetId", {}).get("value") == dataset_id,
            "DATASET_ID_MISMATCH", "Dataset directory and manifest identity differ")
    require(manifest.get("canonicalSha256") == dataset_id.removeprefix("ds_"),
            "DATASET_HASH_MISMATCH", "Dataset logical hash does not match its ID")
    require(license_manifest.get("approvedForTraining") is True,
            "DATASET_LICENSE_NOT_APPROVED", "Dataset license manifest is not approved")
    ids = set(license_manifest.get("allowedLicenseIds", []))
    require(bool(ids) and ids <= allowed_licenses, "DATASET_LICENSE_NOT_APPROVED",
            f"Unapproved dataset license IDs: {sorted(ids - allowed_licenses)}")
    require(license_manifest.get("canonicalSha256") == manifest.get("licenseManifestSha256"),
            "DATASET_LICENSE_HASH_MISMATCH", "Dataset and license manifests disagree")
    splits = manifest.get("rowIdsBySplit", {})
    train_ids = set(splits.get("TRAIN", []))
    protected = set(splits.get("TEST", [])) | set(splits.get("CHALLENGE", []))
    require(train_ids.isdisjoint(protected), "PROTECTED_SET_CONTAMINATION",
            "TRAIN overlaps TEST or CHALLENGE")
    row_count = sum(len(value) for value in splits.values() if isinstance(value, list))
    return {
        "datasetId": dataset_id,
        "canonicalSha256": manifest["canonicalSha256"],
        "licenseIds": sorted(ids),
        "rowCount": row_count,
        "trainRows": len(train_ids),
        "devRows": len(splits.get("DEV", [])),
        "testRows": len(splits.get("TEST", [])),
        "challengeRows": len(splits.get("CHALLENGE", [])),
        "protectedTrainingOverlap": 0,
    }


def installed_versions(package_names: list[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for name in package_names:
        try:
            result[name] = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            result[name] = "NOT_INSTALLED"
    return result


def command_output(args: list[str]) -> str:
    try:
        return subprocess.run(args, check=False, capture_output=True, text=True, timeout=15).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def hardware_snapshot(offline_root: Path) -> dict[str, Any]:
    query = command_output([
        "nvidia-smi", "--query-gpu=name,memory.total,memory.used,memory.free,temperature.gpu,power.draw,driver_version",
        "--format=csv,noheader,nounits",
    ])
    processes = command_output(["tasklist", "/fo", "csv", "/nh"]) if os.name == "nt" else command_output(["ps", "-eo", "comm"])
    lowered = processes.lower()
    disk = shutil.disk_usage(offline_root)
    return {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": platform.python_version(),
        "gpuCsv": query,
        "hytaleRunning": "hytale" in lowered,
        "ollamaRunning": "ollama" in lowered,
        "offlineRootFreeBytes": disk.free,
        "wslAvailable": bool(command_output(["wsl.exe", "--list", "--quiet"])) if os.name == "nt" else None,
    }


def validate_measurement(measurement: dict[str, Any]) -> None:
    require(measurement.get("targetStrategy") in SUPPORTED_TARGETS,
            "UNSUPPORTED_MODULE_TARGET", "Adapter target strategy is unsupported or research-only")
    require(measurement.get("baseTrainableParameters") == 0,
            "BASE_PARAMETERS_TRAINABLE", "Base parameters must remain frozen")
    require(measurement.get("baseGradientParameters") == 0,
            "BASE_GRADIENT_DETECTED", "Unexpected base gradients detected")
    require(measurement.get("baseMutationCount") == 0,
            "BASE_MUTATION_DETECTED", "Unexpected base-weight mutation detected")
    for key in ("loss", "gradientNorm", "adapterDelta"):
        value = measurement.get(key)
        require(isinstance(value, (int, float)) and math.isfinite(value),
                "NON_FINITE_TRAINING_VALUE", f"{key} is missing or non-finite")
    require(measurement["gradientNorm"] > 0 and measurement["adapterDelta"] > 0,
            "ADAPTER_NOT_UPDATED", "Intended adapter gradients/update are absent")
    require(measurement.get("sequence1024Recorded") is True,
            "SEQUENCE_1024_MISSING", "1024-token measurement is mandatory")
    require(measurement.get("adapterSaved") is True and measurement.get("freshProcessReload") is True,
            "ADAPTER_ROUNDTRIP_FAILED", "Adapter save/reload proof is incomplete")
    require(measurement.get("disableReturnsBase") is True and measurement.get("mergeCopyMatches") is True,
            "ADAPTER_ROUNDTRIP_FAILED", "Disable or merge-copy proof is incomplete")


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run_preflight(args: argparse.Namespace) -> tuple[dict[str, Any], Path]:
    project_root = Path(args.project_root).resolve()
    offline_root = Path(args.offline_root).resolve()
    config = read_json(project_root / "training/configs/block3.json")
    environment_lock = read_json(project_root / "training/configs/d6-environment-lock.json")
    base_root = Path(args.base_root).resolve()
    failures: list[str] = []
    g0: dict[str, Any] = {}
    dataset: dict[str, Any] = {}
    try:
        g0 = validate_g0(project_root, base_root)
        dataset = validate_dataset(offline_root, config["sourceDatasetId"], set(config["allowedDatasetLicenseIds"]))
        g0_status = "PASS"
    except GateError as exc:
        failures.append(f"{exc.code}: {exc}")
        g0_status = "FAIL"

    hardware = hardware_snapshot(offline_root)
    package_versions = installed_versions(list(environment_lock["packages"]))
    if g0_status == "PASS":
        if os.name == "nt" and any(not item["officialWindowsWheelAvailable"] for item in environment_lock["requiredNativeKernels"]):
            failures.append("NATIVE_KERNEL_PLATFORM_UNSUPPORTED: pinned mamba-ssm and causal-conv1d publish no official Windows wheels")
        if hardware["hytaleRunning"]:
            failures.append("GPU_IN_USE_BY_HYTALE: Hytale must be stopped before model loading")
        if hardware["ollamaRunning"]:
            failures.append("GPU_RUNTIME_NOT_ISOLATED: Ollama is running; no training/model load was attempted")
        weight = base_root / "snapshot" / g0["weightFiles"][0]["path"]
        if not weight.exists():
            failures.append("BASE_WEIGHT_NOT_DOWNLOADED: the pinned 7.95 GB Safetensors file is absent")

    now = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    seed = canonical_bytes({"g0": g0, "dataset": dataset, "environment": environment_lock, "hardware": hardware, "at": now})
    run_id = f"preflight-{now}-{sha256_bytes(seed)[:12]}"
    run_dir = offline_root / "runs" / run_id
    environment = {
        "schemaVersion": 1,
        "lock": environment_lock,
        "installed": package_versions,
        "hardware": hardware,
    }
    write_json(run_dir / "environment.json", environment)
    report = {
        "schemaVersion": 1,
        "runId": run_id,
        "state": "FAILED" if failures else "PREFLIGHT",
        "g0": g0_status,
        "g2": "FAIL" if failures else "NOT_REACHED",
        "hardwareClassification": "REMOTE_REQUIRED" if failures else "LOCAL_EXPERIMENTAL",
        "base": g0,
        "dataset": dataset,
        "moduleInventory": {
            "status": "STATIC_ONLY_MODEL_NOT_LOADED",
            "attention": ["q_proj", "k_proj", "v_proj", "o_proj"],
            "mlp": ["up_proj", "down_proj"],
            "mambaResearchOnly": ["in_proj", "out_proj"],
            "selectedStrategy": None,
        },
        "measurements": {
            "zeroAdapterEquivalence": "NOT_RUN",
            "sequence1024": "NOT_RUN",
            "sequence2048": "SKIPPED_D6_BLOCKED",
            "adapterRoundTrip": "NOT_RUN",
        },
        "failures": failures,
        "decision": "STOP_BEFORE_MODEL_LOAD_OR_GRADIENT" if failures else "READY_FOR_MODEL_PREFLIGHT",
    }
    write_json(run_dir / "peft-preflight-report.json", report)
    run_manifest = {
        "schemaVersion": 1,
        "runId": run_id,
        "state": report["state"],
        "baseRevision": g0.get("revision", "0" * 40),
        "datasetId": config["sourceDatasetId"],
        "environmentSha256": sha256_file(run_dir / "environment.json"),
        "configSha256": sha256_file(project_root / "training/configs/block3.json"),
        "preflightReportSha256": sha256_file(run_dir / "peft-preflight-report.json"),
    }
    write_json(run_dir / "run-manifest.json", run_manifest)
    registry_payload = dict(run_manifest)
    envelope = {
        "schemaVersion": 1,
        "id": run_id,
        "contentHash": sha256_bytes(canonical_bytes(registry_payload)),
        "payload": registry_payload,
        "recordedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    registry = offline_root / "registry/training-runs.jsonl"
    registry.parent.mkdir(parents=True, exist_ok=True)
    with registry.open("a", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(envelope, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
    return report, run_dir


def smoke_readiness(args: argparse.Namespace) -> dict[str, Any]:
    report = read_json(Path(args.preflight_report))
    require(report.get("g2") == "PASS", "G2_NOT_PASSED", "SFT-0 is not authorized without G2 PASS")
    dataset = read_json(Path(args.dataset_manifest))
    rows = sum(len(v) for v in dataset.get("rowIdsBySplit", {}).values() if isinstance(v, list))
    require(32 <= rows <= 128, "SMOKE_DATASET_SIZE_INVALID", f"SFT-0 requires 32-128 useful rows; found {rows}")
    return {"authorized": True, "rows": rows}


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    preflight = sub.add_parser("preflight")
    preflight.add_argument("--project-root", required=True)
    preflight.add_argument("--offline-root", required=True)
    preflight.add_argument("--base-root", required=True)
    smoke = sub.add_parser("smoke-readiness")
    smoke.add_argument("--preflight-report", required=True)
    smoke.add_argument("--dataset-manifest", required=True)
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            report, run_dir = run_preflight(args)
            print(json.dumps({"report": report, "runDirectory": str(run_dir)}, indent=2))
            return 0 if report["g2"] == "PASS" else 2
        print(json.dumps(smoke_readiness(args), indent=2))
        return 0
    except GateError as exc:
        print(json.dumps({"error": exc.code, "message": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
