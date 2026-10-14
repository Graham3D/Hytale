import copy
import json
import unittest
import me12_content as m


class ContentTest(unittest.TestCase):
    def setUp(self):
        self.docs = copy.deepcopy(m.load_policies())

    def test_ad1_dead_fillers_and_duplicate_signatures_are_diagnosed_not_repaired(self):
        warnings = m.validate(*self.docs)
        self.assertEqual(["DEAD_FILLER ruinbone ME-003", "DEAD_FILLER ruinbone ME-027"],
                         [w for w in warnings if w.startswith("DEAD_FILLER")])
        self.assertEqual(3, sum(w.startswith("DUPLICATE_PAIR_SIGNATURE") for w in warnings))
        self.assertEqual(16, sum(len(self.docs[0][s]) for s in ("prefixes", "suffixes")))

    def test_unknown_fields_and_duplicates_are_rejected(self):
        self.docs[0]["unknown"] = 1
        with self.assertRaises(ValueError):
            m.validate(*self.docs)
        with self.assertRaises(ValueError):
            json.loads('{"schemaVersion":2,"schemaVersion":1}', object_pairs_hook=m.pairs_unique)

    def test_unknown_signature_and_zero_weight_do_not_change_original_content(self):
        before = m.load_policies()
        self.docs[0]["prefixes"][0]["signature"] = "ME-999"
        with self.assertRaises(ValueError):
            m.validate(*self.docs)
        self.docs = copy.deepcopy(before)
        self.docs[0]["suffixes"][0]["nameWeight"] = 0
        with self.assertRaises(ValueError):
            m.validate(*self.docs)
        self.assertEqual(before, m.load_policies())

    def test_nonactivating_scope_and_numeric_types_are_strict(self):
        for key, value in (("activation", "ENABLED"), ("maximumOwnAffixes", True), ("maximumOwnAffixes", 8.0)):
            docs = copy.deepcopy(self.docs)
            docs[1][key] = value
            with self.assertRaises(ValueError):
                m.validate(*docs)

    def test_quantity_cap_and_learning_protections_cannot_drift(self):
        for part, key, value in (("equipment", "maximumExpectedCount", 17),
                                 ("learning", "ordinaryCapNumerator", 100),
                                 ("learning", "pity", "BYPASS")):
            docs = copy.deepcopy(self.docs)
            docs[2][part][key] = value
            with self.assertRaises(ValueError):
                m.validate(*docs)

    def test_restricted_scout_cannot_satisfy_hell_and_no_count_downgrade_occurs(self):
        names = self.docs[0]
        pool = ("ME-002", "ME-003", "ME-004", "ME-017")
        normal = nightmare = hell = 0
        for p in names["prefixes"]:
            for s in names["suffixes"]:
                normal += m.witness(p, s, 0, pool) is not None
                nightmare += m.witness(p, s, 1, pool) is not None
                hell += m.witness(p, s, 2, pool) is not None
        self.assertEqual((5, 3, 0), (normal, nightmare, hell))

    def test_reflective_proposal_requires_distinct_non_survival_fourth_affix(self):
        names = self.docs[0]
        p = next(e for e in names["prefixes"] if e["id"] == "ironhide")
        s = next(e for e in names["suffixes"] if e["id"] == "stubborn")
        pool = ("ME-002", "ME-003", "ME-004", "ME-017", "ME-026")
        result = m.witness(p, s, 2, pool)
        self.assertEqual(4, len(result))
        self.assertIn("ME-026", result)
        self.assertNotIn("ME-003", result)
        self.assertFalse(m.legal(["ME-026", "ME-024"]))
        self.assertFalse(m.legal(["ME-026", "ME-016"]))

    def test_aura_selectors_share_one_id_and_survival_constraint(self):
        self.assertFalse(m.legal(["ME-023/MIGHT", "ME-023/HASTE"]))
        self.assertFalse(m.legal(["ME-004", "ME-023/WARD"]))
        self.assertFalse(m.legal(["ME-002", "ME-005", "ME-023/MIGHT"]))
        self.assertTrue(m.legal(["ME-004", "ME-023/HASTE"]))


if __name__ == "__main__":
    unittest.main()
