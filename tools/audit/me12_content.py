"""Stage 1 offline content lint and existential coverage audit; no RNG or runtime loader.

Uses current Java eligibility, immunity and inheritance owners. Complete static
plans are conditional on the explicitly specified admitted homogeneous native roster;
connected path execution and ephemeral world admission are separate Stage 4 gates.
Never grants production eligibility, changes a binding, or writes encounter data.
"""
import argparse
import hashlib
import itertools
import json
from collections import Counter
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICIES = ROOT / "src/main/resources/rpg/enemies"
IDS = [f"ME-{n:03}" for n in range(1, 28)]
ERAS = ("NORMAL", "NIGHTMARE", "HELL")
AD1_NAMES = {
  "prefixes": [
    {
      "id": "ashclaw",
      "text": "Ashclaw",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-005",
      "fillers": []
    },
    {
      "id": "grimtooth",
      "text": "Grimtooth",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-002",
      "fillers": []
    },
    {
      "id": "ironhide",
      "text": "Ironhide",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-004",
      "fillers": []
    },
    {
      "id": "gorefang",
      "text": "Gorefang",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-002",
      "fillers": [
        "ME-001",
        "ME-004",
        "ME-015",
        "ME-017",
        "ME-018",
        "ME-019",
        "ME-025"
      ]
    },
    {
      "id": "duskmaw",
      "text": "Duskmaw",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-014",
      "fillers": [
        "ME-001",
        "ME-011",
        "ME-012",
        "ME-017",
        "ME-018",
        "ME-025",
        "ME-026"
      ]
    },
    {
      "id": "stormscar",
      "text": "Stormscar",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-007",
      "fillers": [
        "ME-001",
        "ME-002",
        "ME-017",
        "ME-018",
        "ME-025",
        "ME-026"
      ]
    },
    {
      "id": "dreadtusk",
      "text": "Dreadtusk",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-025",
      "fillers": [
        "ME-002",
        "ME-004",
        "ME-010",
        "ME-015",
        "ME-017",
        "ME-018",
        "ME-026"
      ]
    },
    {
      "id": "ruinbone",
      "text": "Ruinbone",
      "tier": 3,
      "nameWeight": 1,
      "signature": "ME-004",
      "fillers": [
        "ME-002",
        "ME-003",
        "ME-017",
        "ME-018",
        "ME-026",
        "ME-027"
      ]
    }
  ],
  "suffixes": [
    {
      "id": "cruel",
      "text": "the Cruel",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-002",
      "fillers": []
    },
    {
      "id": "stubborn",
      "text": "the Stubborn",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-017",
      "fillers": []
    },
    {
      "id": "hollow",
      "text": "the Hollow",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-013",
      "fillers": []
    },
    {
      "id": "ashen",
      "text": "the Ashen",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-005",
      "fillers": []
    },
    {
      "id": "scarred",
      "text": "the Scarred",
      "tier": 1,
      "nameWeight": 1,
      "signature": "ME-026",
      "fillers": []
    },
    {
      "id": "relentless",
      "text": "the Relentless",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-018",
      "fillers": [
        "ME-001",
        "ME-002",
        "ME-017",
        "ME-019",
        "ME-025"
      ]
    },
    {
      "id": "watchful",
      "text": "the Watchful",
      "tier": 2,
      "nameWeight": 1,
      "signature": "ME-003",
      "fillers": [
        "ME-001",
        "ME-002",
        "ME-017",
        "ME-018",
        "ME-025",
        "ME-026"
      ]
    },
    {
      "id": "unbroken",
      "text": "the Unbroken",
      "tier": 3,
      "nameWeight": 1,
      "signature": "ME-017",
      "fillers": [
        "ME-001",
        "ME-002",
        "ME-003",
        "ME-004",
        "ME-018",
        "ME-019",
        "ME-025",
        "ME-026",
        "ME-027"
      ]
    }
  ]
}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def pairs_unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"),
                      object_pairs_hook=pairs_unique,
                      parse_constant=lambda v: require(False, f"non-finite JSON: {v}"))


def exact(obj, keys):
    require(isinstance(obj, dict) and set(obj) == set(keys.split()), f"unexpected shape: {keys}")


def integer(value, minimum, maximum):
    require(type(value) is int and minimum <= value <= maximum, "integer bounds")


def ids(values):
    require(isinstance(values, list) and len(values) == len(set(values))
            and set(values) <= set(IDS), "unknown or duplicate affix IDs")


def groups(choice):
    ident, _, selector = choice.partition("/")
    n = int(ident[3:])
    g = []
    if 5 <= n <= 12:
        g += ["ELEMENTAL", "OFFENSE"]
    if n in (20, 21, 22, 23, 24):
        g += ["LEADER"]
    if n in (3, 4, 16, 24, 27) or selector == "WARD":
        g += ["SURVIVAL"]
    if n in (6, 15):
        g += ["CONTROL"]
    if n in (2, 19, 20) or selector == "MIGHT":
        g += ["OFFENSE"]
    if n in (13, 14):
        g += ["DENIAL"]
    return g


def legal(choices):
    bases = [c.split("/")[0] for c in choices]
    if len(bases) != len(set(bases)):
        return False
    counts = Counter(g for c in choices for g in groups(c))
    if any(counts[g] > 1 for g in ("ELEMENTAL", "LEADER", "SURVIVAL", "CONTROL", "DENIAL")):
        return False
    if counts["OFFENSE"] > 2:
        return False
    if {"ME-019", "ME-020"} <= set(bases):
        return False
    return not ("ME-026" in bases and ({"ME-016", "ME-024"} & set(bases)))


def validate(names, selection, reward):
    def scalar_types(value):
        if isinstance(value, dict):
            for child in value.values():
                scalar_types(child)
        elif isinstance(value, list):
            for child in value:
                scalar_types(child)
        else:
            require(type(value) in (str, int), "policy scalars must be strings or integers")
    for document in (names, selection, reward):
        scalar_types(document)
    exact(names, "schemaVersion revision format prefixes suffixes")
    exact(selection, "schemaVersion revision scope activation tierWeights difficultySlots maximumOwnAffixes genericCandidates affixWeights legality tierDraw nameDraw setDraw ownership plannerLimits")
    exact(reward, "schemaVersion revision scope activation threatUnits threatScale baselineThreatUnits xp equipment learning")
    for document in (names, selection, reward):
        require(document["schemaVersion"] == 2 and type(document["schemaVersion"]) is int, "schema version")
        require(isinstance(document["revision"], str) and document["revision"].startswith("ME12-ARCH-AD1-"), "revision")
    require(names["format"] == "{prefix} {suffix}", "format")
    warnings = []
    for side in ("prefixes", "suffixes"):
        entries = names[side]
        require(isinstance(entries, list) and len(entries) == 8, "AD1 requires eight entries per side")
        seen = set()
        for entry in entries:
            exact(entry, "id text tier nameWeight signature fillers")
            require(isinstance(entry["id"], str) and entry["id"].isascii()
                    and entry["id"].isalpha() and entry["id"].islower(), "name ID")
            require(entry["id"] not in seen, "duplicate name ID")
            seen.add(entry["id"])
            require(isinstance(entry["text"], str) and 0 < len(entry["text"]) <= 32
                    and all(c.isprintable() and c not in "{}" for c in entry["text"]), "name text")
            integer(entry["tier"], 1, 3)
            integer(entry["nameWeight"], 1, 1)
            require(entry["signature"] in IDS, "unknown signature")
            ids(entry["fillers"])
            require((entry["tier"] == 1) == (len(entry["fillers"]) == 0), "filler slots")
            require(entry["signature"] not in entry["fillers"], "signature duplicated in own filler")
            dead = [c for c in entry["fillers"] if not legal([entry["signature"], c])]
            for candidate in dead:
                warnings.append(f"DEAD_FILLER {entry['id']} {candidate}")
            require(len(set(entry["fillers"]) - set(dead)) >= entry["tier"] - 1, "impossible component")
    for p, s in itertools.product(names["prefixes"], names["suffixes"]):
        require(len(p["text"] + " " + s["text"]) <= 32, "name exceeds existing display cap")
        if p["signature"] == s["signature"]:
            warnings.append(f"DUPLICATE_PAIR_SIGNATURE {p['id']}/{s['id']}")
    require(all(names[side] == AD1_NAMES[side] for side in ("prefixes", "suffixes")),
            "AD1 fixed name/signature/filler mapping changed")
    for doc in (selection, reward):
        require(doc["scope"] == "NEW_NATURAL_PROCEDURAL_UNIQUE" and doc["activation"] == "DISABLED",
                "Stage 1 cannot activate v2")
    require(selection["tierWeights"] == [70, 25, 5], "tier weights")
    require(selection["difficultySlots"] == dict(zip(ERAS, (0, 1, 2))), "difficulty slots")
    require(selection["maximumOwnAffixes"] == 8, "own cap")
    ids(selection["genericCandidates"])
    require(selection["genericCandidates"] == IDS, "generic allowlist must explicitly pin all 27")
    required_strings = {
        "affixWeights": "CAPTURE_EXISTING_REGISTRY_AND_ALLOWED_OVERRIDES",
        "legality": "PRESERVE_V1_NATIVE_ROSTER_IMMUNITY_SEMANTIC_RULES",
        "tierDraw": "JOINT_CONDITION_ON_COMPLETE_FEASIBILITY",
        "nameDraw": "PRODUCT_NAME_WEIGHTS_INDEPENDENT_OF_COMPLETION_MASS",
        "setDraw": "EXACT_PRODUCT_DISTINCT_ID_SELECTOR_SET",
        "ownership": "SIGNATURES_THEN_CANONICAL_PREFIX_SUFFIX_DIFFICULTY",
    }
    require(all(selection[k] == v for k, v in required_strings.items()), "selection contract")
    require(selection["plannerLimits"] == dict(memoStates=100000, sharedAndInflightBytes=67108864,
            builders=2, outstandingPerWorld=32, outstandingPerServer=256, coldDeadlineMs=500,
            warmP95Micros=500, warmP99Micros=1000), "planner bounds")
    expected = {}
    for score, members in ((100, (2,3,4,17)), (115, (1,5,9,10,12,13,18,25)),
                           (130, (6,7,8,11,14,15,19,21,23,27)), (150, (16,20,22,24,26))):
        expected.update({f"ME-{n:03}": score for n in members})
    require(reward["threatUnits"] == expected and all(type(v) is int for v in reward["threatUnits"].values()), "threat schedule")
    require(reward["threatScale"] == 100 and reward["baselineThreatUnits"] == 100, "threat baseline")
    require(reward["xp"] == dict(bonusNumerator=15, bonusDenominator=100, maximumBonusThreatUnits=900), "XP contract")
    require(reward["equipment"] == dict(bonusNumerator=15, bonusDenominator=100,
            maximumThreatFactorNumerator=205, maximumThreatFactorDenominator=100, maximumExpectedCount=16,
            baseCount="SUCCESSFUL_ELIGIBLE_ORIGINAL_BASE_SLOTS_BEFORE_GENERATION",
            rounding="ONE_DETERMINISTIC_FRACTIONAL_ROUND_AFTER_EXPECTATION_CAP", quality="UNCHANGED"), "equipment contract")
    require(reward["learning"] == dict(bonusNumerator=1, bonusDenominator=10,
            maximumBonusNumerator=3, maximumBonusDenominator=100, ordinaryCapNumerator=95,
            ordinaryCapDenominator=100, source="EXISTING_ELIGIBILITY_WISDOM_RARITY_THEN_AFFIX_BONUS",
            pity="EXISTING_GUARANTEE_OUTSIDE_ORDINARY_CAP", publicSkill="DEPENDENCY_PENDING",
            firstCopyPassive="DEPENDENCY_PENDING"), "learning contract")
    return warnings


def witness(prefix, suffix, era, eligible, forced=None):
    """One existence witness, no complete-set enumeration, masses, random draw or birth plan."""
    signatures = (prefix["signature"], suffix["signature"])
    if not set(signatures) <= set(eligible) or not legal(signatures):
        return None
    slots = []
    for entry in (prefix, suffix):
        candidates = tuple(c for c in eligible if c.split("/")[0] in entry["fillers"])
        slots.extend([candidates] * (entry["tier"] - 1))
    slots.extend([tuple(eligible)] * era)
    slots.sort(key=lambda s: (len(s), s))
    visited = 0

    @lru_cache(maxsize=None)
    def visit(index, chosen):
        nonlocal visited
        visited += 1
        require(visited <= 100000, "AUDIT_BUDGET_EXHAUSTED: unknown feasibility, never zero mass")
        if index == len(slots):
            return chosen if forced is None or forced in [c.split("/")[0] for c in chosen] else None
        if forced and forced not in [c.split("/")[0] for c in chosen]:
            if not any(any(c.split("/")[0] == forced for c in slot) for slot in slots[index:]):
                return None
        for candidate in slots[index]:
            next_chosen = tuple(sorted((*chosen, candidate)))
            if legal(next_chosen):
                result = visit(index + 1, next_chosen)
                if result is not None:
                    return result
        return None
    return visit(0, tuple(sorted(signatures)))


def load_policies():
    return tuple(read(POLICIES / name) for name in ("name-pools-v2.json", "selection-policy-v2.json", "reward-policy-v2.json"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/me12/content-audit.json")
    args = parser.parse_args()
    names, selection, reward = load_policies()
    warnings = validate(names, selection, reward)
    report = dict(status="INACTIVE_CONTENT_VALIDATED", warnings=warnings, runtimeLoaded=False,
                  policySha256={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                for p in sorted(POLICIES.glob("*-v2.json"))})
    if args.inputs:
        inputs = read(args.inputs)
        require(all(set(groups(k)) == set(v) for k, v in inputs["semanticGroups"].items()),
                "audit semantic groups drifted from production Java")
        contexts = {}
        rows = []
        for source in inputs["rows"]:
            row = dict(roleId=source["roleId"], canonicalRoleId=source["canonicalRoleId"],
                       productionRejection=source["productionRejection"], eras={})
            for era_no, era in enumerate(ERAS):
                if not source["hasBinding"]:
                    row["eras"][era] = dict(status="BLOCKED_NO_BINDING")
                    continue
                complete = source["completeConstraints"][era]
                if not complete["leaderAccepted"]:
                    row["eras"][era] = dict(status="POLICY_EXCLUDED_NATIVE_IMMUNITY_CAP")
                    continue
                require(complete["minionMinimum"] == 3 and complete["minionMaximum"] == 5
                        and complete["maximumMembers"] == 8, "review changed native roster contract")
                eligible = tuple(k for k, v in sorted(complete["choices"].items()) if v == "ELIGIBLE")
                key = f"{era}:" + ",".join(eligible)
                row["eras"][era] = dict(status="OFFLINE_COMPLETE_CONDITIONAL_PLAN", structuralContext=key,
                    nativeImmunities=complete["nativeImmunities"], minionAccepted=complete["minionAccepted"])
                if key in contexts:
                    continue
                pairs = {}
                reachable = set()
                for p, s in itertools.product(names["prefixes"], names["suffixes"]):
                    found = witness(p, s, era_no, eligible)
                    if found is None:
                        continue
                    pairs[p["id"] + "/" + s["id"]] = dict(tiers=[p["tier"], s["tier"]], witness=found)
                    reachable.update(c.split("/")[0] for c in found)
                for affix in set(c.split("/")[0] for c in eligible) - reachable:
                    if any(witness(p, s, era_no, eligible, affix) is not None
                           for p, s in itertools.product(names["prefixes"], names["suffixes"])
                           if p["id"] + "/" + s["id"] in pairs):
                        reachable.add(affix)
                tier_pairs = sorted(set(tuple(v["tiers"]) for v in pairs.values()))
                masses = {f"{a}/{b}": selection["tierWeights"][a-1] * selection["tierWeights"][b-1]
                          for a, b in tier_pairs}
                contexts[key] = dict(feasibleNamePairs=pairs, tierMassNumerators=masses,
                    normalization=sum(masses.values()), reachableOperators=sorted(reachable),
                    rosterProof={str(n): {pair: dict(totalMembers=1+n+(2 if "ME-022" in v["witness"] else 0),
                        guards=n if "ME-024" in v["witness"] else 0) for pair, v in pairs.items()} for n in range(3,6)},
                    minimumSupported=bool(pairs), maximumOwnCount=max((len(v["witness"]) for v in pairs.values()), default=0))
                if not pairs:
                    row["eras"][era]["status"] = "BLOCKED_NO_COMPLETE_NAME_PLAN"
            # Cached empty contexts must retain the same failure status for all aliases.
            for era in row["eras"].values():
                if era.get("structuralContext") and not contexts[era["structuralContext"]]["minimumSupported"]:
                    era["status"] = "BLOCKED_NO_COMPLETE_NAME_PLAN"
            rows.append(row)
        blockers = [r["roleId"] + "/" + era for r in rows if not r["productionRejection"]
                    for era, state in r["eras"].items() if state["status"] == "BLOCKED_NO_COMPLETE_NAME_PLAN"]
        require(not blockers, "ENABLED_ROLE_MINIMUM_UNSATISFIED:" + ",".join(blockers))
        report.update(coverageKind="COMPLETE_STATIC_NAME_CONDITIONED_FEASIBILITY",
                      assumptions=["homogeneous admitted native roster, each base minion count 3/4/5; Horde adds 2; maximum 8 members",
                                   "actual Java immunity and inheritance predicates applied; guard, survival and native caps retained",
                                   "live spawn capacity, sealed/reachable roster and actual native connected execution remain DEFERRED_TO_STAGE4",
                                   "no operator overrides; current registry weights; aliases evaluated independently"],
                      contexts=contexts, rows=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"ME12 content valid; warnings={len(warnings)}; runtimeLoaded=false; output={args.output}")


if __name__ == "__main__":
    main()
