"""Build the 160-affix evidence matrix from catalog, source, registered proofs and JUnit XML.

Unregistered or unpassed proof is GATED. A source reference is only a candidate,
never a gameplay assertion. No build or game is launched by this tool.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GEAR = ROOT / "src/main/resources/rpg/gear"
JAVA = ROOT / "src/main/java"
TESTS = ROOT / "src/test/java"
RESULTS = ROOT / "build/test-results/test"
NATIVE_RESULTS = ROOT / "build/test-results/nativeControlTest"
PROOFS = ROOT / "tools/affix-proof-map-v1.json"
ID = re.compile(r"(?:WA|GA)-\d{3}")
# These IDs have a row in AdvancedStatsViewModel. Skill ranks belong to the
# Skill Tree and source-local/conditional values have no Advanced Stats row.
ADVANCED_IDS = ({"WA-004", "WA-006", "WA-007", "WA-009", "WA-010", "WA-011", "WA-012",
                 "WA-013", "WA-064", "WA-068", "WA-078", "WA-079", "WA-099", "WA-100",
                 "WA-101", "WA-102", "WA-103", "WA-104", "WA-105", "WA-106", "WA-107",
                 "WA-108", "WA-110", "WA-111", "WA-120", "WA-151", "WA-152", "WA-156",
                 "GA-159", "GA-160"}
                | {f"WA-{n:03d}" for n in range(23, 35)}
                | {f"WA-{n:03d}" for n in range(72, 78)}
                | {f"WA-{n:03d}" for n in range(85, 94)}
                | {f"WA-{n:03d}" for n in range(113, 120)})


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def require_catalog(affixes):
    expected = {f"WA-{n:03d}" for n in range(1, 159)} | {"GA-159", "GA-160"}
    actual = [a["id"] for a in affixes]
    if len(actual) != 160 or set(actual) != expected or len(set(actual)) != 160:
        raise ValueError("Catalog must contain exactly WA-001..158 and GA-159/160 once")


def enabled_ids():
    source = (JAVA / "com/inigmasgames/hytalerpg/gear/GearAffixRuntime.java").read_text(encoding="utf-8")
    match = re.search(r"\bENABLED\s*=\s*Set\.of\((.*?)\);", source, re.S)
    if not match:
        raise ValueError("Cannot locate GearAffixRuntime.ENABLED; update parser before release")
    values = ID.findall(match.group(1))
    if len(values) != len(set(values)):
        raise ValueError("Duplicate ENABLED ID")
    return set(values)


def source_tests():
    """Discover test bodies, including methods with parameter-provider annotations."""
    index = {}
    source_by_test = {}
    expression = re.compile(r"@(?:Test|ParameterizedTest)\b(?:\([^\n]*\))?"
                            r"(?:(?:\s|@[\w.]+(?:\([^\n]*\))?)+)"
                            r"(?:public\s+|private\s+)?void\s+(\w+)\s*\(", re.S)
    for path in TESTS.rglob("*.java"):
        source = path.read_text(encoding="utf-8")
        package = re.search(r"\bpackage\s+([\w.]+)\s*;", source)
        if not package:
            continue
        methods = list(expression.finditer(source))
        for match in methods:
            opening = source.find("{", match.end())
            depth, end = 1, opening + 1
            while depth and end < len(source):
                depth += (source[end] == "{") - (source[end] == "}")
                end += 1
            if opening < 0 or depth:
                continue
            body = source[match.start():end]
            test = package.group(1) + "." + path.stem + "#" + match.group(1)
            source_by_test[test] = (path, body)
            for affix_id in set(ID.findall(body)):
                index.setdefault(affix_id, []).append(test)
    return index, source_by_test


ASSERTION = re.compile(r"\bassert(?:Equals|True|False|NotEquals|Throws|All|Null|NotNull|Same|NotSame|IterableEquals)\s*\(")


def reviewed_source(affix_id, proof, field, test, source_by_test):
    """Allow only a per-field reviewed anchor tied to this ID and this test.

    Literal IDs still work. Computed IDs require an exact, bounded loop or a
    ValueSource row. A named same-file helper must be called by the test.
    Descriptive review prose never acts as source evidence.
    """
    if test not in source_by_test:
        return None
    path, body = source_by_test[test]
    anchor = proof.get("sourceAnchors", {}).get(field, {})
    if not anchor:
        return body if affix_id in body else None
    if anchor.get("test") != test or (anchor.get("parameterId") and anchor["parameterId"] != affix_id):
        return None
    whole = path.read_text(encoding="utf-8")
    chunks = [body]
    for helper in anchor.get("helpers", []):
        if len(chunks) > 3 or not re.search(r"\b" + re.escape(helper) + r"\s*\(", chunks[-1]):
            return None
        match = re.search(r"\b" + re.escape(helper) +
                          r"\s*\([^;{}]*\)\s*(?:throws\s+[\w.,\s]+)?\{", whole)
        if not match:
            return None
        depth, end = 1, match.end()
        while depth and end < len(whole):
            depth += (whole[end] == "{") - (whole[end] == "}")
            end += 1
        if depth:
            return None
        chunks.append(whole[match.start():end])
    source = "\n".join(chunks)
    if any(value not in source for value in anchor.get("required", [])):
        return None
    if anchor.get("provider"):
        provider = anchor["provider"]
        if not re.search(r'@MethodSource\("' + re.escape(provider) + r'"\)', body):
            return None
        match = re.search(r'\b' + re.escape(provider) + r'\s*\(\s*\)\s*\{([^}]*)\}', whole)
        if not match:
            return None
        provider_body = match.group(1)
        ids = set(ID.findall(provider_body))
        for first, last in re.findall(r'IntStream\.rangeClosed\((\d+),(\d+)\)', provider_body):
            ids.update(f"WA-{n:03d}" for n in range(int(first), int(last) + 1))
        if "OWNER_IDS" in provider_body:
            numbers = re.search(r'OWNER_IDS\s*=\s*\{([^}]*)\}', whole)
            if not numbers:
                return None
            ids.update(f"WA-{int(n):03d}" for n in re.findall(r'\d+', numbers.group(1)))
        if provider == "statusCases":
            if (affix_id not in ids or anchor.get("parameterId") != affix_id
                    or not re.search(r"\bvoid\s+\w+\s*\(\s*String\s+id\b", body)
                    or not re.search(r"\bpair\s*\(\s*id\s*\)", body)):
                return None
            return source
        if (affix_id not in ids or anchor.get("parameterId") != affix_id
                or not re.search(r"\bvoid\s+\w+\s*\(\s*String\s+id\b", body)
                or not re.search(r"\bitem\s*\([^;]*\bid\b", body)):
            return None
        return source
    if anchor.get("uiMapEntry"):
        row = anchor["uiMapEntry"]
        if (f'Map.entry("{row}","{affix_id}")' not in body
                or "cases.entrySet()" not in body or "entry.getValue()" not in body):
            return None
        return source
    if anchor.get("uiChannel"):
        kind = anchor["uiChannel"]
        number = int(affix_id[3:]) if affix_id.startswith("WA-") else -1
        if kind == "damage" and (not 23 <= number <= 28 or
                                 'String specific="WA-"+String.format(Locale.ROOT,"%03d",22+channel.ordinal())' not in body
                                 or 'item(specific,15)' not in body):
            return None
        if kind == "penetration" and (not 29 <= number <= 34 or
                                      'String penetration="WA-"+String.format(Locale.ROOT,"%03d",28+channel.ordinal())' not in body
                                      or 'item(penetration,20)' not in body):
            return None
        if kind == "resistance" and (not 72 <= number <= 77 or
                                     'String affix="WA-"+String.format(Locale.ROOT,"%03d",71+channel.ordinal())' not in body
                                     or 'item(affix,15)' not in body):
            return None
        if kind == "allResistance" and (affix_id != "WA-078" or 'item("WA-078",10)' not in body):
            return None
        if kind not in {"damage", "penetration", "resistance", "allResistance"}:
            return None
        return source
    if anchor.get("channelLoop"):
        offset = anchor["channelLoop"]
        number = int(affix_id[3:]) if affix_id.startswith("WA-") else -1
        expression = ('String flatId="WA-"+String.format("%03d",17+i)' if offset == 17
                      else 'String penetrationId="WA-"+String.format("%03d",29+i)')
        final_owner = (f'String id="WA-"+String.format("%03d",{offset}+i)' in body
                       and 'rolled(id)' in body)
        if (offset not in (17, 29) or not offset <= number < offset + 6
                or not (expression in body or final_owner) or 'for(int i=0;i<channels.size();i++)' not in body
                or 'var channels=List.of(GearCombatEffects.Channel.WIND' not in body
                or 'GearCombatEffects.Channel.VOID)' not in body
                or (not final_owner and ('var flat=item(flatId' if offset == 17 else 'var pen=mapped(penetrationId)') not in body)):
            return None
        return source
    if anchor.get("mapEntry"):
        entry = anchor["mapEntry"]
        if (f'Map.entry("{affix_id}","{entry}")' not in whole
                or "CASES.entrySet()" not in body
                or "entry.getKey()" not in body):
            return None
        return source
    if anchor.get("methodSource") == "authoredAffixes":
        if (not re.search(r'@MethodSource\("authoredAffixes"\)', body)
                or "QA.coverage().stream().map(row->Arguments.of(row.affixId(),row.fixtureId(),row.itemBaseId()))" not in whole
                or '"ab-"+id.toLowerCase(Locale.ROOT)+"-affixed"' not in whole
                or "QA.fixtures().stream().filter" not in body
                or "GearDropGenerator.eligible(CATALOG.affix(id),base)" not in body
                or "assertEquals(expectedBase,base.id())" not in body
                or "assertTrue(new GearBindings().require(base.id()).mapped()" not in body
                or "assertTrue(base.worldDropCandidate()" not in body):
            return None
        return source
    if affix_id not in body and not (anchor.get("helpers") and affix_id in source):
        parameter = anchor.get("parameterId")
        if parameter != affix_id:
            return None
        if re.search(r"(?:@|\.)ValueSource\s*\(\s*strings\s*=\s*\{[^}]*\b" + re.escape(affix_id) + r"\b", body):
            pass
        else:
            rule = anchor.get("loop", {})
            first, last = rule.get("first"), rule.get("last")
            expression = rule.get("expression", "")
            if not (affix_id.startswith("WA-") and isinstance(first, int) and isinstance(last, int)
                    and first <= int(affix_id[3:]) <= last and 0 < last - first < 160
                    and expression in body and (re.search(r"for\s*\(\s*int\s+(id|number)\s*=\s*" + str(first) +
                                  r"\s*;\s*\1\s*<=\s*" + str(last) + r"\s*;\s*\1\+\+", body)
                    or re.search(r"for\s*\(\s*int\s+i\s*=\s*0\s*;\s*i\s*<\s*" +
                                  str(last - first + 1) + r"\s*;\s*i\+\+", body))):
                return None
            if expression not in (f'"WA-"+({first}+i)', '"WA-"+id',
                                  'String.format(Locale.ROOT,"WA-%03d",id)',
                                  'String.format(Locale.ROOT,"WA-%03d",number)'):
                return None
    return source


def invokes_consumer(source, name):
    if re.search(r"\b" + re.escape(name) + r"\s*\.\s*\w+\s*\(", source):
        return True
    if re.search(r"\bnew\s+" + re.escape(name) + r"\s*\([^;{}]*\)\s*\.\s*\w+\s*\(", source):
        return True
    constructed = re.search(r"\b(?:var|[\w.]+)\s+(\w+)\s*=\s*new\s+(?:[\w.]+\.)?" +
                            re.escape(name) + r"\s*\(", source)
    return bool(constructed and re.search(r"\b" + re.escape(constructed.group(1)) +
                                          r"\s*\.\s*\w+\s*\(", source))


def reviewed_runtime_chain(proof, field, test_source, test_path, consumer):
    """Check an explicit short source path from an inherited test harness to owner."""
    anchor = proof.get("sourceAnchors", {}).get(field, {})
    chain = anchor.get("runtimeChain", [])
    if (not 2 <= len(chain) <= 4 or not anchor.get("entryCall") or anchor["entryCall"] not in test_source
            or not anchor.get("testClassRequired")
            or anchor["testClassRequired"] not in test_path.read_text(encoding="utf-8")):
        return False
    if chain[-1].get("path") != consumer:
        return False
    for step in chain:
        path = step.get("path", "")
        snippets = step.get("required", [])
        if (not path.startswith(("src/test/java/", "src/main/java/")) or ".." in Path(path).parts
                or not snippets or not (ROOT / path).is_file()):
            return False
        content = (ROOT / path).read_text(encoding="utf-8")
        if any(snippet not in content for snippet in snippets):
            return False
    return True


def source_consumers():
    index = {}
    for path in JAVA.rglob("*.java"):
        if path.name in {"GearAffixQaSuite.java", "GearCatalog.java", "GearAffixRuntime.java"}:
            continue
        source = path.read_text(encoding="utf-8")
        for ident in set(ID.findall(source)):
            index.setdefault(ident, []).append(path.relative_to(ROOT).as_posix())
    return index


def sentinel_policies():
    source = (JAVA / "com/inigmasgames/hytalerpg/execution/summon/IronSentinelAffixes.java").read_text(encoding="utf-8")
    policies = {f"WA-{n:03d}": "SELF_STAT" for n in range(1, 159)}
    policies.update({"GA-159": "SELF_STAT", "GA-160": "SELF_STAT"})
    for first, last, disposition in re.findall(r"for\(int n=(\d+);n<=(\d+);n\+\+\)\s*result\.put\([^;]+Disposition\.(\w+)\)", source):
        for number in range(int(first), int(last) + 1):
            policies[f"WA-{number:03d}"] = disposition
    for numbers, disposition in re.findall(r"for\(int n:new int\[\]\{([^}]+)\}\)\s*result\.put\([^;]+Disposition\.(\w+)\)", source):
        for number in re.findall(r"\d+", numbers):
            policies[f"WA-{int(number):03d}"] = disposition
    match = re.search(r"\bADAPTED\s*=\s*Set\.of\((.*?)\);", source, re.S)
    if match:
        adapted = set(ID.findall(match.group(1)))
    else:
        helper = re.search(r"\bADAPTED\s*=\s*directAdapters\(\);(.*?)private static final Map", source, re.S)
        if not helper:
            raise ValueError("Cannot parse current Sentinel adapter set")
        adapted = set(ID.findall(helper.group(1)))
        for first, last in re.findall(r"for\(int n=(\d+);n<=(\d+);n\+\+\)ids\.add", helper.group(1)):
            adapted.update(f"WA-{number:03d}" for number in range(int(first), int(last) + 1))
    if len(policies) != 160:
        raise ValueError("Cannot parse current Sentinel policy")
    return policies, adapted


def passed_tests():
    passed = {}
    # Native codec/control tests run in a JVM with Hytale's logging manager.
    # Read both real task outputs; a later failed result invalidates an older pass.
    paths = sorted([*RESULTS.glob("TEST-*.xml"), *NATIVE_RESULTS.glob("TEST-*.xml")],
                   key=lambda path: path.stat().st_mtime)
    for path in paths:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as error:
            raise ValueError(f"Invalid JUnit XML {path}: {error}") from error
        for case in suite.findall("testcase"):
            key = case.get("classname", "") + "#" + case.get("name", "").split("(", 1)[0].split("[", 1)[0]
            if not any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                passed[key] = path.stat().st_mtime
            else:
                passed.pop(key, None)
    return passed


def passed_parameter_ids():
    """A parameterized row needs its own real passing XML invocation."""
    result = {}
    paths = sorted([*RESULTS.glob("TEST-*.xml"), *NATIVE_RESULTS.glob("TEST-*.xml")],
                   key=lambda path: path.stat().st_mtime)
    for path in paths:
        suite = ET.parse(path).getroot()
        for case in suite.findall("testcase"):
            name = case.get("name", "")
            key = case.get("classname", "") + "#" + name.split("(", 1)[0].split("[", 1)[0]
            for affix_id in set(ID.findall(name)):
                pair = (key, affix_id)
                if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                    result.pop(pair, None)
                else:
                    result[pair] = path.stat().st_mtime
    return result


def observed_legal_carriers():
    """Only successful actual Java admission rows supply concrete catalog mappings."""
    result = {}
    for path in sorted([*RESULTS.glob("TEST-*.xml"), *NATIVE_RESULTS.glob("TEST-*.xml")], key=lambda p: p.stat().st_mtime):
        for case in ET.parse(path).getroot().findall("testcase"):
            if case.get("classname") != "com.inigmasgames.hytalerpg.gear.MasterAffixEquipmentSimulatorTest":
                continue
            match = re.fullmatch(r"authoredCarrierIsProductionEligible\(((?:WA|GA)-\d{3}), (ab-[a-z0-9-]+), (gm\.[a-z0-9_.]+)\)", case.get("name", ""))
            if not match:
                continue
            ident, fixture, base = match.groups()
            if fixture != "ab-" + ident.lower() + "-affixed":
                raise ValueError("Mismatched legal carrier fixture")
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                result.pop(ident, None)
            else:
                result[ident] = (base, path.stat().st_mtime)
    return result


def sentinel_inapplicable():
    source = (JAVA / "com/inigmasgames/hytalerpg/execution/summon/IronSentinelAffixes.java").read_text(encoding="utf-8")
    if "if(conditional!=null)return new Decision(kind,true,conditional);" not in source:
        return set()
    block = re.search(r"String conditional=switch\(id\)\{(.*?)\};", source, re.S)
    if not block:
        return set()
    return {ident for cases, reason in re.findall(r'case (.*?) ->\s*"(INAPPLICABLE[^"]*)"', block.group(1), re.S)
            for ident in ID.findall(cases)}


def registered_proofs():
    data = read_json(PROOFS)
    if data.get("schemaVersion") != 1 or not isinstance(data.get("proofs"), dict):
        raise ValueError("Invalid affix proof registry")
    return data["proofs"]


def check_proof(affix_id, proof, source_by_test, passed, parameter_passed=None):
    holes = []
    consumer = proof.get("realRuntimeConsumer")
    if not consumer or not (ROOT / consumer).is_file() or not consumer.startswith("src/main/java/"):
        holes.append("real runtime consumer missing")
        consumer_name = None
    else:
        consumer_name = Path(consumer).stem
    def fresh(test, field):
        if test not in source_by_test or test not in passed:
            return False
        timestamp = passed[test]
        anchor = proof.get("sourceAnchors", {}).get(field, {})
        if anchor.get("parameterId") and re.search(r"(?:@|\.)(?:ValueSource|MethodSource)", source_by_test[test][1]):
            timestamp = (parameter_passed or {}).get((test, affix_id), -1)
        dependencies = [source_by_test[test][0]]
        dependencies.extend(ROOT / step.get("path", "") for step in anchor.get("runtimeChain", []))
        return all(path.is_file() and timestamp >= path.stat().st_mtime for path in dependencies)
    # Supplemental owner measurements must independently retain ID-bound source
    # assertions and fresh XML. They never replace the primary consumer checks.
    fields = ("positiveTest", "controlTest", "negativeTest", "equippedAuthorityTest")
    for field in (*fields, *proof.get("additionalOwnerTests", [])):
        test = proof.get(field)
        if not test or test not in source_by_test:
            holes.append(field + " source missing")
        elif (source := reviewed_source(affix_id, proof, field, test, source_by_test)) is None:
            holes.append(field + " does not identify affix")
        elif not ASSERTION.search(source) or (proof.get("sourceAnchors", {}).get(field)
                                              and not any(ASSERTION.search(value) for value in proof["sourceAnchors"][field].get("required", []))):
            holes.append(field + " lacks an assertion")
        elif not fresh(test, field) or (consumer_name and passed[test] < (ROOT / consumer).stat().st_mtime):
            holes.append(field + " has no passing JUnit XML")
        elif (consumer_name and field in ("positiveTest", "controlTest", "negativeTest")
              and not (invokes_consumer(source, consumer_name)
                       or reviewed_runtime_chain(proof, field, source, source_by_test[test][0], consumer))):
            holes.append(field + " does not invoke registered consumer")
    return holes


def build():
    affixes = read_json(GEAR / "affixes-v1.json")["affixes"]
    require_catalog(affixes)
    fixtures = read_json(GEAR / "affix-qa-fixtures-v1.json")["fixtures"]
    bases = {b["id"]: b for b in read_json(GEAR / "bases-v1.json")["bases"]}
    bindings = {b["baseId"]: b for b in read_json(GEAR / "native-bindings-v1.json")["bindings"]}
    enabled = enabled_ids()
    authored = {a["id"] for a in affixes}
    if enabled - authored:
        raise ValueError("Production gate contains unknown affix IDs")
    references, source_by_test = source_tests()
    candidate_consumers = source_consumers()
    sentinel_policy, sentinel_adapted = sentinel_policies()
    passed = passed_tests()
    parameter_passed = passed_parameter_ids()
    observed_carriers = observed_legal_carriers()
    inapplicable = sentinel_inapplicable()
    proofs = registered_proofs()
    if set(proofs) - {a["id"] for a in affixes}:
        raise ValueError("Proof registry contains unknown IDs")
    fixture_ids = {}
    fixture_carriers = {}
    seen_fixtures = set()
    for fixture in fixtures:
        if fixture["fixtureId"] in seen_fixtures:
            raise ValueError("Duplicate QA fixture ID")
        seen_fixtures.add(fixture["fixtureId"])
        for affix_id in fixture["affixIds"]:
            if affix_id not in authored:
                raise ValueError("QA fixture contains unknown affix ID")
            fixture_ids.setdefault(affix_id, []).append(fixture["fixtureId"])
            fixture_carriers.setdefault(affix_id, []).append(fixture["itemBaseId"])
    rows = []
    for affix in affixes:
        ident = affix["id"]
        proof = proofs.get(ident, {})
        holes = check_proof(ident, proof, source_by_test, passed, parameter_passed)
        holes.extend(proof.get("qualificationHoles", []))
        if ident not in enabled:
            holes.append("production capability gate closed")
        mapped = [b["id"] for b in bases.values() if b.get("worldDropCandidate")
                  and bindings.get(b["id"], {}).get("disposition") == "MAPPED"]
        # Catalog legality is Java-owned. These are authored scopes, not a guessed
        # Python copy of GearDropGenerator. Registered legal-carrier proof is needed.
        observed = observed_carriers.get(ident)
        carrier = observed[0] if observed else proof.get("legalCarrier")
        if not carrier or carrier not in mapped:
            holes.append("mapped legal carrier proof missing")
        legal_test = proof.get("legalCarrierTest")
        legal_anchor = proof.get("sourceAnchors", {}).get("legalCarrierTest", {})
        legal_parameter = legal_anchor.get("methodSource") == "authoredAffixes"
        legal_pass = (parameter_passed.get((legal_test, ident), -1) if legal_parameter
                      else passed.get(legal_test, -1))
        if not legal_test or legal_test not in source_by_test or legal_pass < source_by_test[legal_test][0].stat().st_mtime:
            holes.append("legal carrier test missing or unpassed")
        elif reviewed_source(ident, proof, "legalCarrierTest", legal_test, source_by_test) is None:
            holes.append("legal carrier test does not identify affix")
        elif not legal_parameter and (carrier not in source_by_test[legal_test][1]
                                       or "eligible(" not in source_by_test[legal_test][1]):
            holes.append("legal carrier test does not check this base with production eligibility")
        if ident in ADVANCED_IDS:
            view_test = proof.get("advancedStatsTest")
            if (not view_test or view_test not in source_by_test or view_test not in passed
                    or passed[view_test] < source_by_test[view_test][0].stat().st_mtime
                    or reviewed_source(ident, proof, "advancedStatsTest", view_test, source_by_test) is None
                    or (proof.get("sourceAnchors", {}).get("advancedStatsTest", {}).get("parameterId")
                        and re.search(r"(?:@|\.)(?:ValueSource|MethodSource)", source_by_test[view_test][1])
                        and parameter_passed.get((view_test,ident),-1) < source_by_test[view_test][0].stat().st_mtime)):
                holes.append("Advanced Stats comparison missing or unpassed")
        sentinel = proof.get("sentinelBehavior")
        if sentinel and sentinel != ("INAPPLICABLE" if ident in inapplicable else "OWNER_ONLY" if sentinel_policy[ident] == "OWNER_ONLY" else "INHERITED"):
            holes.append("Sentinel proof contradicts current D12 policy")
        if sentinel == "INHERITED" and ident not in sentinel_adapted:
            holes.append("Sentinel native adapter not registered")
        if sentinel not in ("OWNER_ONLY", "INHERITED", "INAPPLICABLE"):
            holes.append("Sentinel disposition proof missing")
        elif (not proof.get("sentinelTest") or proof["sentinelTest"] not in source_by_test
                or proof["sentinelTest"] not in passed
                or passed[proof["sentinelTest"]] < source_by_test[proof["sentinelTest"]][0].stat().st_mtime
                or (proof.get("sourceAnchors", {}).get("sentinelTest", {}).get("parameterId")
                    and re.search(r"(?:@|\.)(?:ValueSource|MethodSource)", source_by_test[proof["sentinelTest"]][1])
                    and parameter_passed.get((proof["sentinelTest"], ident), -1)
                    < source_by_test[proof["sentinelTest"]][0].stat().st_mtime)
                or reviewed_source(ident, proof, "sentinelTest", proof["sentinelTest"], source_by_test) is None):
            holes.append("Sentinel behavior test missing or unpassed")
        rows.append({
            "affixId": ident, "name": affix["name"], "prefixOrSuffix": affix["side"],
            "operator": affix["operator"], "eligibleFamilies": affix["eligibility"],
            "intendedEffect": affix.get("effectContract", affix.get("scopeContract", "")),
            "armorExtension": affix["armorExtension"], "legalCarrier": carrier,
            "legalCarrierTest": proof.get("legalCarrierTest"),
            "legalCarrierObservedTest": (f"{legal_test}({ident}, ab-{ident.lower()}-affixed, {observed[0]})"
                                         if observed and legal_test else None),
            "seedCarrierCandidates": sorted(set(fixture_carriers.get(ident, []))),
            "simulatorFixture": "ab-" + ident.lower() + "-affixed",
            "controlFixture": "ab-" + ident.lower() + "-control",
            "combinedFixtures": fixture_ids.get(ident, []),
            "candidateTests": sorted(references.get(ident, [])),
            "candidateConsumers": sorted(candidate_consumers.get(ident, [])),
            "qualificationHoles": proof.get("qualificationHoles", []),
            "realRuntimeConsumer": proof.get("realRuntimeConsumer"),
            "positiveTest": proof.get("positiveTest"), "controlTest": proof.get("controlTest"),
            "negativeTest": proof.get("negativeTest"),
            "additionalOwnerTests": [proof[field] for field in proof.get("additionalOwnerTests", [])],
            "qualificationResolution": proof.get("qualificationResolution", ""),
            "equippedAuthority": "PROVEN" if proof.get("equippedAuthorityTest") and not any(h.startswith("equippedAuthorityTest") for h in holes) else "UNPROVEN",
            "equippedAuthorityTest": proof.get("equippedAuthorityTest"),
            "advancedStatsField": proof.get("advancedStatsField"),
            "advancedStatsRequired": ident in ADVANCED_IDS,
            "advancedStatsTest": proof.get("advancedStatsTest"),
            "sentinelPolicy": sentinel_policy[ident],
            "sentinelAdapterRegistered": ident in sentinel_adapted or sentinel_policy[ident] == "OWNER_ONLY",
            "sentinelBehavior": sentinel, "sentinelTest": proof.get("sentinelTest"),
            "productionEnabled": ident in enabled,
            "result": "FUNCTIONAL" if not holes else "GATED", "proofHoles": holes,
        })
    if len(rows) != 160 or len({r["affixId"] for r in rows}) != 160:
        raise ValueError("Coverage omitted or duplicated an ID")
    return rows


def write(rows, json_path, csv_path, md_path):
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    fields = list(rows[0])
    with csv_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: json.dumps(value, ensure_ascii=False) if isinstance(value, list) else value
                             for key, value in row.items()})
    counts = Counter(row["result"] for row in rows)
    lines = ["# Gear affix runtime evidence audit", "", "Generated from current catalog, proof registry and JUnit XML. A candidate test reference is not a passing gameplay proof.",
             "", f"Rows: {len(rows)}; FUNCTIONAL: {counts['FUNCTIONAL']}; GATED: {counts['GATED']}; UNSUPPORTED: 0.", "",
             "FUNCTIONAL means the offline admitted-equipment and effect-bearing runtime/adapter checks passed; connected gameplay acceptance remains the owner's QA. WA-155 stays gated until measured renderer calibration, per the owner's explicit exception. Base skills remain authoritative.", "",
             "| Affix ID | Name | Intended Effect | Eligible Gear | Status | Existing Runtime Owner | Exact Missing Boundary | Notes |",
             "| --- | --- | --- | --- | --- | --- | --- | --- |"]
    cell = lambda value: str(value or "").replace("|", "\\|").replace("\n", " ")
    for row in rows:
        notes = (f"Carrier: {row['legalCarrier']}; Sentinel: {row['sentinelBehavior']}. "
                 + row["qualificationResolution"])
        lines.append("| " + " | ".join(cell(value) for value in (row["affixId"], row["name"], row["intendedEffect"],
                     row["eligibleFamilies"] + "; Armor: " + row["armorExtension"], row["result"],
                     row["realRuntimeConsumer"], "; ".join(row["proofHoles"]) or "None in offline qualification", notes)) + " |")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-complete", action="store_true", help="fail if any row remains GATED")
    parser.add_argument("--json", type=Path, default=GEAR / "affix-qa-coverage-v1.json")
    parser.add_argument("--csv", type=Path, default=ROOT / "docs/GEAR_AFFIX_RUNTIME_AUDIT.csv")
    parser.add_argument("--md", type=Path, default=ROOT / "docs/GEAR_AFFIX_RUNTIME_AUDIT.md")
    args = parser.parse_args(argv)
    rows = build()
    write(rows, args.json, args.csv, args.md)
    counts = Counter(row["result"] for row in rows)
    print(f"Affix coverage: {counts['FUNCTIONAL']} FUNCTIONAL, {counts['GATED']} GATED, 0 UNSUPPORTED")
    if args.require_complete and counts["GATED"]:
        print("Release proof incomplete; see proofHoles in JSON/CSV/MD", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
