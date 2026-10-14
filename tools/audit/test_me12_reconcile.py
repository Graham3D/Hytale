"""Evidence resolver regressions; these tests never grant gameplay certification."""
import io
import json
from pathlib import Path
import unittest
import zipfile
from me12_reconcile import NativeIndex, active_walk, expand, scope, build


class ReconciliationTest(unittest.TestCase):
    def archive(self, assets):
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for path, value in assets.items():
                archive.writestr(path, json.dumps(value))
        archive = zipfile.ZipFile(stream)
        self.addCleanup(archive.close)
        return archive

    def test_parameter_override_does_not_import_unused_parent_attack(self):
        archive = self.archive({
            "Server/NPC/Roles/Template_Flying.json": {"Type": "Abstract", "Parameters": {
                "Attack": {"Value": "WrongEyeAttack"}}, "Instructions": [{"Type": "Attack", "Attack": {"Compute": "Attack"}}]},
            "Server/NPC/Roles/Seeker.json": {"Type": "Variant", "Reference": "Template_Flying", "Modify": {"Attack": "Spit"}},
            "Server/Item/RootInteractions/Spit.json": {"Interactions": ["SpitLaunch"]},
            "Server/Item/Interactions/SpitLaunch.json": {"Type": "Projectile", "Config": "Spitball"},
            "Server/ProjectileConfigs/Spitball.json": {"Interactions": {"Hit": {"Type": "DamageEntity", "DamageCalculator": {"BaseDamage": {"Poison": 10}}}}},
            "Server/Item/RootInteractions/WrongEyeAttack.json": {"Interactions": []}})
        result = NativeIndex(archive).combat("Seeker")
        self.assertEqual(["Spit"], result["roots"])
        self.assertEqual(["Poison"], result["damageChannels"])
        self.assertIn("Projectile", result["graphTypes"])

    def test_unknown_expressions_and_cycles_stay_unproven(self):
        self.assertEqual({"Compute": "x + 1"}, expand({"Compute": "x + 1"}, {"x": 1}))
        self.assertEqual({"Compute": "x"}, expand({"Compute": "x"}, {"x": {"Compute": "x"}}))

    def test_disabled_actions_are_not_evidence(self):
        values = list(active_walk({"Instructions": [{"Enabled": False, "Attack": "Unused"}, {"Attack": "Active"}]}))
        self.assertNotIn("Unused", [v for _, v in values])
        self.assertIn("Active", [v for _, v in values])

    def test_native_reference_cycle_fails_instead_of_truncating(self):
        index = NativeIndex(self.archive({"Server/NPC/Roles/A.json": {"Reference": "B"},
                                          "Server/NPC/Roles/B.json": {"Reference": "A"}}))
        with self.assertRaisesRegex(ValueError, "ROLE_REFERENCE_CYCLE"):
            index.role("A")

    def test_scope_labels_do_not_invent_exclusions(self):
        row = {"productionRejection": "NATIVE_BINDING_NOT_CERTIFIED", "canonicalRoleId": "Antelope"}
        self.assertEqual("EXISTING_NONHOSTILE_CATEGORY_REVIEW", scope(row, {"attitude": "Neutral"}, [], "WILDLIFE")[0])
        self.assertEqual("RETIRED_ID_RECONCILIATION", scope(row, None, [], "WILDLIFE")[0])
        row["productionRejection"] = "AUTHORED_CAMPAIGN_ENCOUNTER"
        self.assertEqual("EXISTING_AUTHORED_EXCLUSION", scope(row, None, [], "WILDLIFE")[0])

    def test_world_marker_and_reference_are_separate(self):
        archive = self.archive({
            "Server/NPC/Roles/A.json": {"Type": "Variant", "DefaultPlayerAttitude": "Hostile"},
            "Server/NPC/Roles/B.json": {"Type": "Variant", "Reference": "A"},
            "Server/NPC/Spawn/World/A.json": {"NPCs": [{"Id": "A", "Weight": 1}]},
            "Server/NPC/Spawn/Markers/B.json": {"NPCs": [{"Name": "B", "Weight": 1}]}})
        coverage = {"rows": [{"roleId": x, "canonicalRoleId": "A", "productionRejection": "NOT_CERTIFIED", "scope": "DIRECT_SPAWN_ATTACKER"} for x in ("A", "B")]}
        result = build(archive, coverage, {})
        a, b = result["rows"]
        self.assertEqual(["World"], [s["kind"] for s in a["spawnDefinitions"]])
        self.assertEqual(["Markers"], [s["kind"] for s in b["spawnDefinitions"]])
        self.assertEqual("INHERITANCE", a["references"][0]["kind"])
        self.assertFalse(result["eligibilityChanged"])
        self.assertTrue(all(not r["productScopeChanged"] for r in result["rows"]))

    def test_installed_priority_routes_keep_native_channels_and_immunity(self):
        assets = Path.home() / "AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip"
        with zipfile.ZipFile(assets) as archive:
            index = NativeIndex(archive)
            turret, seeker = index.combat("Goblin_Turret"), index.combat("Scarak_Seeker")
            self.assertEqual(["Root_NPC_Goblin_Turret_Attack"], turret["roots"])
            self.assertEqual(["Scarak_Seeker_Spit"], seeker["roots"])
            self.assertEqual(["Physical"], turret["damageChannels"])
            self.assertEqual(["Poison"], seeker["damageChannels"])
            self.assertIn("EffectCondition", seeker["graphTypes"])
            self.assertIn("RemoveEntity", seeker["graphTypes"])
            self.assertIn("Immunity_Poison", seeker["nativeEffects"]["EntityEffects"])

    def test_installed_burner_primary_comes_from_held_weapon(self):
        assets = Path.home() / "AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip"
        with zipfile.ZipFile(assets) as archive:
            burner = NativeIndex(archive).combat("Goblin_Burner")
            self.assertIn("Primary", burner["itemActionTypes"])
            self.assertIn("Root_Weapon_Flamethrower_Primary", burner["roots"])
            self.assertTrue(any(p.endswith("Weapon_Flamethrower_Scrap.json") for p in burner["itemPaths"]))


if __name__ == "__main__":
    unittest.main()
