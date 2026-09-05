"""Offline renderer regression gate; no Hytale process or player data required."""
import hashlib
import inspect
import json
from pathlib import Path
import unittest
import numpy as np
from PIL import Image
from bake_appearance_thumbnails import Baker, CATEGORIES, CATEGORY_RIG, RIGS, W, H, projection, rig_hash

ROOT = Path(__file__).resolve().parents[1]
THUMBS = ROOT / "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails"
SHEETS = ROOT / "docs/R153_CATEGORY_CONTACT_SHEETS"


class CategoryRigTest(unittest.TestCase):
    def test_complete_fixed_contract(self):
        self.assertEqual(set(CATEGORIES), set(CATEGORY_RIG))
        self.assertEqual(list(inspect.signature(projection).parameters), ["category"])
        for category in CATEGORIES:
            config = RIGS[CATEGORY_RIG[category]]
            camera, center, scale = projection(category)
            self.assertEqual(config["cropPixels"], [0, 0, 184, 298])
            self.assertEqual(config["safetyExpansion"], 0)
            np.testing.assert_allclose(camera @ camera.T, np.eye(3), atol=1e-12)
            np.testing.assert_allclose(np.array(config["target"]) @ camera.T, center)
            self.assertEqual(scale, H / config["verticalSpan"])

    def test_clothing_context_excludes_head_and_face(self):
        for category in ["OVERTOP", "UNDERTOP", "GLOVES", "PANTS", "OVERPANTS", "SHOES"]:
            config = RIGS[CATEGORY_RIG[category]]
            self.assertNotIn("Head", config["bodyNodes"])
            self.assertTrue(set(config["context"]) <= {"BODY_CHARACTERISTIC", "UNDERWEAR"})
        self.assertEqual(CATEGORY_RIG["OVERTOP"], CATEGORY_RIG["UNDERTOP"])
        self.assertGreater(RIGS["rear_body"]["yaw"], 90)

    def test_outlier_geometry_cannot_reframe_actual_renderer(self):
        # Render a stable landmark plus an enormous distant cosmetic fragment.
        # Per-item AABB fitting changes the landmark. Fixed rigs clip the fragment
        # and yield identical pixels, for every category, not just one helper call.
        tex = np.full((2, 2, 4), [170, 120, 80, 255], dtype=np.uint8)
        uv = np.array([[0, 0], [0, 1], [1, 1], [1, 0]], dtype=float)
        for category in CATEGORIES:
            fake = object.__new__(Baker)
            fake.parts = {c: [{"Id":"neutral"}] for c in CATEGORIES}
            camera, center, scale = projection(category)
            p = np.array([[-8, 8, 0], [-8, -8, 0], [8, -8, 0], [8, 8, 0]])
            world = (p + center) @ camera
            base = (world, uv, tex)
            far = ((p * 1000 + center + [100000, 100000, 0]) @ camera, uv, tex)
            calls = []
            def geometry(part, **kwargs):
                calls.append(kwargs)
                return [base, far] if part["Id"] == "outlier" else [base]
            fake.geometry = geometry
            first = np.array(fake.render(category, {"Id":"normal"}))
            second = np.array(fake.render(category, {"Id":"outlier"}))
            np.testing.assert_array_equal(first, second, err_msg=category)
            if category != "BODY_CHARACTERISTIC":
                self.assertTrue(any("body_nodes" in k for k in calls))

    def test_packaged_provenance_and_all_contact_sheets(self):
        provenance = json.loads((THUMBS / "provenance.json").read_text())
        self.assertEqual(provenance["renderer"], "R152 fixed category rigs v2")
        self.assertTrue(provenance["referenceColors"])
        self.assertIn("not active draft", provenance["colorPolicy"])
        self.assertEqual(provenance["unavailable"], [])
        self.assertEqual(provenance["categoryRigs"], CATEGORY_RIG)
        self.assertEqual(provenance["rigs"], json.loads(json.dumps(RIGS)))
        script = ROOT / "tools/bake_appearance_thumbnails.py"
        self.assertEqual(provenance["rendererSha256"], hashlib.sha256(script.read_text(encoding="utf-8").encode()).hexdigest())
        records = [line.split("\t") for line in (THUMBS / "index.tsv").read_text().splitlines()]
        self.assertEqual(len(records), 590)
        keys = set()
        for key, filename, digest in records:
            keys.add(key)
            self.assertEqual(provenance["entryRigHashes"][key], rig_hash(key.split(":")[0]))
            self.assertEqual(hashlib.sha256((THUMBS / filename).read_bytes()).hexdigest(), digest)
            with Image.open(THUMBS / filename) as im:
                self.assertEqual(im.size, (92, 149))
        self.assertEqual(set(provenance["entryRigHashes"]), keys)
        covered = []
        for sheet in json.loads((SHEETS / "index.json").read_text()):
            self.assertEqual(hashlib.sha256((SHEETS / sheet["file"]).read_bytes()).hexdigest(), sheet["sha256"])
            self.assertEqual(len({key.split(":")[0] for key in sheet["keys"]}), 1)
            covered.extend(sheet["keys"])
        self.assertEqual(len(covered), 590)
        self.assertEqual(set(covered), keys)


if __name__ == "__main__":
    unittest.main()
