"""Contract tests for the read-only Master Affix evidence generator."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
import json
import os
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).with_name("Generate-AffixCoverage.py")
spec = importlib.util.spec_from_file_location("affix_coverage", MODULE)
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)


class AffixCoverageTest(unittest.TestCase):
    def test_exact_catalog_and_no_fabricated_completion(self):
        rows = coverage.build()
        self.assertEqual(160, len(rows))
        self.assertEqual(160, len({row["affixId"] for row in rows}))
        self.assertTrue({row["result"] for row in rows} <= {"GATED", "FUNCTIONAL"})
        for row in rows:
            self.assertIn(row["prefixOrSuffix"], {"PREFIX", "SUFFIX"})
            self.assertTrue(row["operator"])
            self.assertTrue(row["eligibleFamilies"])
            self.assertEqual("ab-" + row["affixId"].lower() + "-affixed", row["simulatorFixture"])
            self.assertEqual("ab-" + row["affixId"].lower() + "-control", row["controlFixture"])
            if row["legalCarrierObservedTest"]:
                self.assertEqual(f'{row["legalCarrierTest"]}({row["affixId"]}, '
                                 f'{row["simulatorFixture"]}, {row["legalCarrier"]})',
                                 row["legalCarrierObservedTest"])
            self.assertIn(row["sentinelPolicy"], {"SELF_STAT", "ATTACK_PROC", "AURA", "OWNER_ONLY"})
            if row["result"] == "FUNCTIONAL":
                self.assertTrue(row["productionEnabled"])
                self.assertTrue(row["realRuntimeConsumer"])
                self.assertTrue(row["positiveTest"])
                self.assertTrue(row["controlTest"])
                self.assertTrue(row["negativeTest"])
                self.assertTrue(row["equippedAuthorityTest"])
                self.assertFalse(row["proofHoles"])
            else:
                self.assertTrue(row["proofHoles"])

    def test_catalog_duplicate_and_omission_fail(self):
        affixes = coverage.read_json(coverage.GEAR / "affixes-v1.json")["affixes"]
        with self.assertRaises(ValueError):
            coverage.require_catalog(affixes[:-1] + [affixes[0]])
        with self.assertRaises(ValueError):
            coverage.require_catalog(affixes[:-1])

    def test_sentinel_policy_is_read_from_current_java_source(self):
        policies, adapted = coverage.sentinel_policies()
        self.assertEqual(160, len(policies))
        self.assertEqual("OWNER_ONLY", policies["WA-003"])
        self.assertEqual("ATTACK_PROC", policies["WA-134"])
        self.assertEqual("AURA", policies["WA-148"])
        self.assertIn("GA-159", adapted)

    def test_unregistered_and_unpassed_proof_stays_gated(self):
        missing = coverage.check_proof("WA-001", {}, {}, {})
        self.assertIn("real runtime consumer missing", missing)
        self.assertIn("positiveTest source missing", missing)

    def test_reviewed_loop_is_bounded_to_its_actual_affix(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        method = proofs["WA-041"]["positiveTest"]
        self.assertIsNotNone(coverage.reviewed_source("WA-041", proofs["WA-041"], "positiveTest", method, tests))
        self.assertIsNone(coverage.reviewed_source("WA-053", proofs["WA-041"], "positiveTest", method, tests))
        altered = json.loads(json.dumps(proofs["WA-041"]))
        altered["sourceAnchors"]["positiveTest"]["loop"]["expression"] = '"WA-"+(53+i)'
        self.assertIsNone(coverage.reviewed_source("WA-041", altered, "positiveTest", method, tests))
        altered = json.loads(json.dumps(proofs["WA-041"]))
        altered["sourceAnchors"]["positiveTest"]["required"] = ["assertEquals(unrelated,metadata)"]
        self.assertIsNone(coverage.reviewed_source("WA-041", altered, "positiveTest", method, tests))

    def test_parameter_row_cannot_credit_neighbor(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        method = proofs["WA-085"]["equippedAuthorityTest"]
        self.assertIsNotNone(coverage.reviewed_source("WA-085", proofs["WA-085"], "equippedAuthorityTest", method, tests))
        self.assertIsNone(coverage.reviewed_source("WA-094", proofs["WA-085"], "equippedAuthorityTest", method, tests))

    def test_simulator_rows_are_bounded_and_legality_provider_is_real(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        for ident in ("WA-001", "WA-085", "WA-158", "GA-160"):
            proof = proofs[ident]
            for field in ("equippedAuthorityTest", "legalCarrierTest"):
                self.assertIsNotNone(coverage.reviewed_source(ident, proof, field, proof[field], tests))
        proof = json.loads(json.dumps(proofs["WA-001"]))
        proof["sourceAnchors"]["legalCarrierTest"]["required"] = ["assertTrue(unrelatedMetadata)"]
        self.assertIsNone(coverage.reviewed_source("WA-001", proof, "legalCarrierTest",
                                                   proof["legalCarrierTest"], tests))

    def test_rank_map_and_sentinel_loops_cannot_credit_unrelated_ids(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        rank = proofs["WA-121"]
        self.assertIsNotNone(coverage.reviewed_source("WA-121", rank, "positiveTest", rank["positiveTest"], tests))
        self.assertIsNone(coverage.reviewed_source("WA-134", rank, "positiveTest", rank["positiveTest"], tests))
        altered = json.loads(json.dumps(rank))
        altered["sourceAnchors"]["positiveTest"]["mapEntry"] = "wrong_skill"
        self.assertIsNone(coverage.reviewed_source("WA-121", altered, "positiveTest",
                                                   altered["positiveTest"], tests))
        sentinel = proofs["WA-053"]
        self.assertIsNotNone(coverage.reviewed_source("WA-053", sentinel, "sentinelTest",
                                                      sentinel["sentinelTest"], tests))
        self.assertIsNone(coverage.reviewed_source("WA-064", sentinel, "sentinelTest",
                                                   sentinel["sentinelTest"], tests))

    def test_new_method_source_sentinel_rows_are_bounded_to_provider_ids(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        for ident in ("WA-001", "WA-066", "WA-085", "WA-148", "GA-160"):
            proof = proofs[ident]
            method = proof["sentinelTest"]
            self.assertIsNotNone(coverage.reviewed_source(ident, proof, "sentinelTest", method, tests))
        owner = proofs["WA-085"]
        self.assertIsNone(coverage.reviewed_source("WA-094", owner, "sentinelTest",
                                                   owner["sentinelTest"], tests))
        altered = json.loads(json.dumps(owner))
        altered["sourceAnchors"]["sentinelTest"]["provider"] = "localPhysicalIds"
        self.assertIsNone(coverage.reviewed_source("WA-085", altered, "sentinelTest",
                                                   altered["sentinelTest"], tests))
        self.assertEqual({"WA-083", "WA-141", "WA-147"}, coverage.sentinel_inapplicable())

    def test_advanced_stats_requires_each_represented_id_and_exact_row(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        self.assertEqual(64, len(coverage.ADVANCED_IDS))
        self.assertTrue(all(proofs[ident].get("advancedStatsTest") for ident in coverage.ADVANCED_IDS))
        self.assertNotIn("WA-121", coverage.ADVANCED_IDS)  # Skill Tree, not Advanced Stats
        for ident in ("WA-009", "WA-110", "WA-023", "WA-029", "WA-072", "WA-078", "WA-120"):
            proof = proofs[ident]
            method = proof["advancedStatsTest"]
            self.assertIsNotNone(coverage.reviewed_source(ident, proof, "advancedStatsTest", method, tests))
        altered = json.loads(json.dumps(proofs["WA-110"]))
        altered["sourceAnchors"]["advancedStatsTest"]["uiMapEntry"] = "minionDamage"
        self.assertIsNone(coverage.reviewed_source("WA-110", altered, "advancedStatsTest",
                                                   altered["advancedStatsTest"], tests))
        altered = json.loads(json.dumps(proofs["WA-023"]))
        altered["sourceAnchors"]["advancedStatsTest"]["uiChannel"] = "penetration"
        self.assertIsNone(coverage.reviewed_source("WA-023", altered, "advancedStatsTest",
                                                   altered["advancedStatsTest"], tests))

    def test_registered_adapter_proofs_preserve_real_remaining_holes(self):
        proofs = coverage.registered_proofs()
        self.assertTrue(proofs["WA-155"].get("qualificationHoles"))
        for ident in ("WA-141", "WA-152", "WA-094", "WA-096"):
            self.assertFalse(proofs[ident].get("qualificationHoles"), ident)
            self.assertTrue(proofs[ident].get("resolvedQualificationHoles"), ident)
            self.assertTrue(proofs[ident].get("qualificationResolution"), ident)
        for ident in ("WA-069", "WA-070", "WA-071", "GA-159", "GA-160",
                      "WA-110", "WA-113", "WA-114", "WA-115", "WA-116", "WA-117", "WA-118",
                      "WA-119", "WA-154"):
            self.assertFalse(proofs[ident].get("qualificationHoles"), ident)
            self.assertTrue(proofs[ident].get("positiveTest"), ident)
        rows = {row["affixId"]: row for row in coverage.build()}
        for ident in ("WA-155",):
            self.assertEqual("GATED", rows[ident]["result"])
            self.assertTrue(rows[ident]["qualificationHoles"], ident)
            self.assertTrue(set(rows[ident]["qualificationHoles"]) <= set(rows[ident]["proofHoles"]))

    def test_helper_must_be_called_and_cannot_supply_unrelated_id(self):
        _, tests = coverage.source_tests()
        proofs = coverage.registered_proofs()
        method = proofs["WA-120"]["positiveTest"]
        self.assertIsNotNone(coverage.reviewed_source("WA-120", proofs["WA-120"], "positiveTest", method, tests))
        altered = json.loads(json.dumps(proofs["WA-120"]))
        altered["sourceAnchors"]["positiveTest"]["helpers"] = ["insufficientFinitePaymentDoesNotCreateDecoyAndUnequippedSourceDoesNotDiscount"]
        self.assertIsNone(coverage.reviewed_source("WA-120", altered, "positiveTest", method, tests))
        self.assertIsNone(coverage.reviewed_source("WA-121", proofs["WA-120"], "positiveTest", method, tests))

    def test_reviewed_runtime_chain_rejects_broken_or_metadata_links(self):
        _, tests = coverage.source_tests()
        proof = coverage.registered_proofs()["WA-120"]
        method = proof["positiveTest"]
        source = coverage.reviewed_source("WA-120", proof, "positiveTest", method, tests)
        self.assertTrue(coverage.reviewed_runtime_chain(proof, "positiveTest", source,
                                                        tests[method][0], proof["realRuntimeConsumer"]))
        altered = json.loads(json.dumps(proof))
        altered["sourceAnchors"]["positiveTest"]["runtimeChain"][1]["required"] = ["metadata.name()"]
        self.assertFalse(coverage.reviewed_runtime_chain(altered, "positiveTest", source,
                                                         tests[method][0], proof["realRuntimeConsumer"]))
        altered = json.loads(json.dumps(proof))
        altered["sourceAnchors"]["positiveTest"]["runtimeChain"][-1]["path"] = "src/main/java/unrelated.java"
        self.assertFalse(coverage.reviewed_runtime_chain(altered, "positiveTest", source,
                                                         tests[method][0], proof["realRuntimeConsumer"]))

    def test_wrapper_metadata_is_not_a_consumer_invocation(self):
        self.assertFalse(coverage.invokes_consumer('assertEquals("GearCombatEffects", metadata.name())', "GearCombatEffects"))
        self.assertFalse(coverage.invokes_consumer('assertTrue(GearCombatEffects.class.getName().contains("Effects"))', "GearCombatEffects"))
        self.assertTrue(coverage.invokes_consumer('assertEquals(10,GearCombatEffects.attack(hit))', "GearCombatEffects"))

    def test_parameter_xml_requires_matching_passed_id(self):
        with tempfile.TemporaryDirectory(dir=coverage.ROOT / "tools") as folder:
            target = Path(folder)
            (target / "TEST-synthetic.xml").write_text(
                '<testsuite><testcase classname="p.T" name="run(WA-085)"/>'
                '<testcase classname="p.T" name="run(WA-086)"><failure/></testcase></testsuite>',
                encoding="utf-8")
            with patch.object(coverage, "RESULTS", target), patch.object(coverage, "NATIVE_RESULTS", target / "none"):
                passed = coverage.passed_parameter_ids()
            self.assertIn(("p.T#run", "WA-085"), passed)
            self.assertNotIn(("p.T#run", "WA-086"), passed)

    def test_observed_legal_carrier_name_uses_passed_xml_base_and_id(self):
        with tempfile.TemporaryDirectory(dir=coverage.ROOT / "tools") as folder:
            target = Path(folder)
            name = "authoredCarrierIsProductionEligible(WA-001, ab-wa-001-affixed, gm.sword_iron.nm)"
            (target / "TEST-carrier.xml").write_text(
                '<testsuite><testcase classname="com.inigmasgames.hytalerpg.gear.MasterAffixEquipmentSimulatorTest"'
                f' name="{name}"/></testsuite>', encoding="utf-8")
            with patch.object(coverage, "RESULTS", target), patch.object(coverage, "NATIVE_RESULTS", target / "none"):
                observed = coverage.observed_legal_carriers()
                self.assertEqual("gm.sword_iron.nm", observed["WA-001"][0])
                row = next(r for r in coverage.build() if r["affixId"] == "WA-001")
            self.assertEqual(row["legalCarrierTest"] + "(" +
                             "WA-001, ab-wa-001-affixed, gm.sword_iron.nm)", row["legalCarrierObservedTest"])

    def test_failed_newer_native_xml_invalidates_older_test_pass(self):
        with tempfile.TemporaryDirectory(dir=coverage.ROOT / "tools") as folder:
            target = Path(folder)
            ordinary = target / "test"
            native = target / "native"
            ordinary.mkdir()
            native.mkdir()
            old = ordinary / "TEST-p.T.xml"
            new = native / "TEST-p.T.xml"
            old.write_text('<testsuite><testcase classname="p.T" name="run"/></testsuite>', encoding="utf-8")
            new.write_text('<testsuite><testcase classname="p.T" name="run"><failure/></testcase></testsuite>', encoding="utf-8")
            os.utime(old, (1000, 1000))
            os.utime(new, (2000, 2000))
            with patch.object(coverage, "RESULTS", ordinary), patch.object(coverage, "NATIVE_RESULTS", native):
                self.assertNotIn("p.T#run", coverage.passed_tests())
            os.utime(old, (3000, 3000))
            with patch.object(coverage, "RESULTS", ordinary), patch.object(coverage, "NATIVE_RESULTS", native):
                self.assertIn("p.T#run", coverage.passed_tests())

    def test_bracket_parameter_name_counts_as_passed_method(self):
        with tempfile.TemporaryDirectory(dir=coverage.ROOT / "tools") as folder:
            target = Path(folder)
            (target / "TEST-p.T.xml").write_text(
                '<testsuite><testcase classname="p.T" name="run[WA-094]"/>'
                '<testcase classname="p.T" name="other[WA-096]"><failure/></testcase></testsuite>',
                encoding="utf-8")
            with patch.object(coverage, "RESULTS", target), patch.object(coverage, "NATIVE_RESULTS", target / "none"):
                passed = coverage.passed_tests()
                parameter = coverage.passed_parameter_ids()
            self.assertIn("p.T#run", passed)
            self.assertNotIn("p.T#other", passed)
            self.assertIn(("p.T#run", "WA-094"), parameter)
            self.assertNotIn(("p.T#other", "WA-096"), parameter)

    def test_release_gate_rejects_missing_proof(self):
        rows = coverage.build()
        rows[0] = {**rows[0], "result": "GATED", "proofHoles": ["synthetic missing proof"]}
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)
            with patch.object(coverage, "build", return_value=rows):
                code = coverage.main(["--require-complete", "--json", str(target / "coverage.json"),
                                      "--csv", str(target / "audit.csv"), "--md", str(target / "audit.md")])
            self.assertEqual(1, code)
            self.assertTrue((target / "coverage.json").is_file())

    def test_supplemental_owner_is_required_and_not_satisfied_by_old_projection(self):
        proof = json.loads(json.dumps(coverage.registered_proofs()["WA-078"]))
        _, tests = coverage.source_tests()
        method = proof["finalOwnerTest"]
        self.assertIsNotNone(coverage.reviewed_source("WA-078", proof, "finalOwnerTest", method, tests))
        passing = coverage.passed_tests()
        passing.pop(method, None)
        holes = coverage.check_proof("WA-078", proof, tests, passing, coverage.passed_parameter_ids())
        self.assertIn("finalOwnerTest has no passing JUnit XML", holes)
        proof["sourceAnchors"]["finalOwnerTest"]["required"] = ["assertEquals(nonexistentRecipient, actual)"]
        self.assertIsNone(coverage.reviewed_source("WA-078", proof, "finalOwnerTest", method, tests))

    def test_final_native_channel_loop_cannot_qualify_unrelated_id(self):
        _, tests = coverage.source_tests()
        proof = coverage.registered_proofs()["WA-029"]
        method = proof["finalOwnerTest"]
        self.assertIsNotNone(coverage.reviewed_source("WA-029", proof, "finalOwnerTest", method, tests))
        self.assertIsNone(coverage.reviewed_source("WA-035", proof, "finalOwnerTest", method, tests))


if __name__ == "__main__":
    unittest.main()
