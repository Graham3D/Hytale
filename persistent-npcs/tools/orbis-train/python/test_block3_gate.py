import json
import math
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import block3_gate as gate


class Block3GateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.project = self.root / "project"
        self.offline = self.root / "offline"
        self.base = self.offline / "models/base/pinned"
        (self.project / "training/configs").mkdir(parents=True)
        (self.base / "snapshot").mkdir(parents=True)
        self.identity = self.base / "snapshot/tokenizer.json"
        self.identity.write_text("tokenizer", encoding="utf-8")
        base = {
            "decision": "PASS",
            "approval": "TRAINING_BASE_APPROVED_SEPARATE_LINEAGE",
            "repository": "nvidia/test",
            "revision": "a" * 40,
            "architecture": "NemotronHForCausalLM",
            "identityFiles": [{"path": "tokenizer.json", "sha256": gate.sha256_file(self.identity)}],
            "weightFiles": [{"path": "model.safetensors", "sha256": "b" * 64}],
            "productionGguf": {"classification": "COMPATIBLE_BUT_UNPROVEN"},
        }
        license_decision = {
            "decision": "PASS_FOR_LOCAL_D6_D7_ONLY",
            "rightsForThisBlock": {
                "localUse": True,
                "modificationAndDerivativeAdapter": True,
                "mergeIntoCopy": True,
            },
            "requiredNotice": gate.REQUIRED_NOTICE,
        }
        self.write("training/configs/g0-training-base.json", base)
        self.write("training/configs/g0-license-decision.json", license_decision)
        self.dataset_id = "ds_" + "c" * 64
        ds = self.offline / "datasets" / self.dataset_id
        (ds / "licenses").mkdir(parents=True)
        self.write_path(ds / "manifest.json", {
            "state": "FROZEN",
            "datasetId": {"value": self.dataset_id},
            "canonicalSha256": "c" * 64,
            "licenseManifestSha256": "d" * 64,
            "rowIdsBySplit": {"TRAIN": ["r1"], "DEV": [], "TEST": ["r2"], "CHALLENGE": []},
        })
        self.write_path(ds / "licenses/manifest.json", {
            "approvedForTraining": True,
            "allowedLicenseIds": ["PROJECT_OWNED_FIXTURE"],
            "canonicalSha256": "d" * 64,
        })

    def tearDown(self):
        self.temp.cleanup()

    def write(self, relative, value):
        self.write_path(self.project / relative, value)

    @staticmethod
    def write_path(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding="utf-8")

    def test_g0_exact_base_and_license_pass(self):
        result = gate.validate_g0(self.project, self.base)
        self.assertEqual("TRAINING_BASE_APPROVED_SEPARATE_LINEAGE", result["approval"])

    def test_wrong_tokenizer_or_base_hash_rejected(self):
        self.identity.write_text("tampered", encoding="utf-8")
        with self.assertRaisesRegex(gate.GateError, "Identity hash mismatch"):
            gate.validate_g0(self.project, self.base)

    def test_revision_mismatch_rejected(self):
        path = self.project / "training/configs/g0-training-base.json"
        data = gate.read_json(path)
        data["revision"] = "main"
        self.write_path(path, data)
        with self.assertRaisesRegex(gate.GateError, "immutable"):
            gate.validate_g0(self.project, self.base)

    def test_unresolved_license_blocks_gradient_path(self):
        path = self.project / "training/configs/g0-license-decision.json"
        data = gate.read_json(path)
        data["decision"] = "UNRESOLVED"
        self.write_path(path, data)
        with self.assertRaises(gate.GateError) as caught:
            gate.validate_g0(self.project, self.base)
        self.assertEqual("LICENSE_UNRESOLVED", caught.exception.code)

    def test_notice_mismatch_rejected(self):
        path = self.project / "training/configs/g0-license-decision.json"
        data = gate.read_json(path)
        data["requiredNotice"] = "almost right"
        self.write_path(path, data)
        with self.assertRaises(gate.GateError) as caught:
            gate.validate_g0(self.project, self.base)
        self.assertEqual("NOTICE_MISMATCH", caught.exception.code)

    def test_nonapproved_dataset_license_blocks_execution(self):
        path = self.offline / "datasets" / self.dataset_id / "licenses/manifest.json"
        data = gate.read_json(path)
        data["allowedLicenseIds"] = ["UNKNOWN"]
        self.write_path(path, data)
        with self.assertRaises(gate.GateError) as caught:
            gate.validate_dataset(self.offline, self.dataset_id, {"PROJECT_OWNED_FIXTURE"})
        self.assertEqual("DATASET_LICENSE_NOT_APPROVED", caught.exception.code)

    def test_protected_train_overlap_rejected(self):
        path = self.offline / "datasets" / self.dataset_id / "manifest.json"
        data = gate.read_json(path)
        data["rowIdsBySplit"]["TEST"] = ["r1"]
        self.write_path(path, data)
        with self.assertRaises(gate.GateError) as caught:
            gate.validate_dataset(self.offline, self.dataset_id, {"PROJECT_OWNED_FIXTURE"})
        self.assertEqual("PROTECTED_SET_CONTAMINATION", caught.exception.code)

    @staticmethod
    def good_measurement():
        return {
            "targetStrategy": "ATTENTION_ONLY",
            "baseTrainableParameters": 0,
            "baseGradientParameters": 0,
            "baseMutationCount": 0,
            "loss": 1.0,
            "gradientNorm": 0.2,
            "adapterDelta": 0.001,
            "sequence1024Recorded": True,
            "sequence2048Status": "SKIPPED_UNSAFE",
            "adapterSaved": True,
            "freshProcessReload": True,
            "disableReturnsBase": True,
            "mergeCopyMatches": True,
        }

    def test_valid_one_batch_measurement_passes(self):
        gate.validate_measurement(self.good_measurement())

    def test_unsupported_module_target_rejected(self):
        value = self.good_measurement()
        value["targetStrategy"] = "MAMBA_PROJECTIONS"
        with self.assertRaises(gate.GateError) as caught:
            gate.validate_measurement(value)
        self.assertEqual("UNSUPPORTED_MODULE_TARGET", caught.exception.code)

    def test_base_gradients_or_mutation_rejected(self):
        for key in ("baseTrainableParameters", "baseGradientParameters", "baseMutationCount"):
            value = self.good_measurement()
            value[key] = 1
            with self.assertRaises(gate.GateError):
                gate.validate_measurement(value)

    def test_nonfinite_or_zero_adapter_values_rejected(self):
        for key, invalid in (("loss", math.nan), ("gradientNorm", math.inf), ("adapterDelta", 0.0)):
            value = self.good_measurement()
            value[key] = invalid
            with self.assertRaises(gate.GateError):
                gate.validate_measurement(value)

    def test_adapter_roundtrip_is_mandatory(self):
        for key in ("adapterSaved", "freshProcessReload", "disableReturnsBase", "mergeCopyMatches"):
            value = self.good_measurement()
            value[key] = False
            with self.assertRaises(gate.GateError):
                gate.validate_measurement(value)

    def test_corrupted_adapter_rejected(self):
        adapter = self.root / "adapter.bin"
        adapter.write_bytes(b"adapter")
        with self.assertRaises(gate.GateError) as caught:
            gate.verify_adapter_hash(adapter, "0" * 64)
        self.assertEqual("CORRUPTED_ADAPTER", caught.exception.code)

    def test_sft_refused_without_g2(self):
        preflight = self.root / "preflight.json"
        manifest = self.offline / "datasets" / self.dataset_id / "manifest.json"
        self.write_path(preflight, {"g2": "FAIL"})
        with self.assertRaises(gate.GateError) as caught:
            gate.smoke_readiness(SimpleNamespace(preflight_report=str(preflight), dataset_manifest=str(manifest)))
        self.assertEqual("G2_NOT_PASSED", caught.exception.code)

    def test_fewer_than_32_smoke_rows_rejected_after_g2(self):
        preflight = self.root / "preflight.json"
        manifest = self.offline / "datasets" / self.dataset_id / "manifest.json"
        self.write_path(preflight, {"g2": "PASS"})
        with self.assertRaises(gate.GateError) as caught:
            gate.smoke_readiness(SimpleNamespace(preflight_report=str(preflight), dataset_manifest=str(manifest)))
        self.assertEqual("SMOKE_DATASET_SIZE_INVALID", caught.exception.code)


if __name__ == "__main__":
    unittest.main(verbosity=2)
