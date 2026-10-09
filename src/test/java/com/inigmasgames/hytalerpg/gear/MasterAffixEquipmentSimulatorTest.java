package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Candidate equipment proof only; it does not qualify an affix's gameplay consumer. */
class MasterAffixEquipmentSimulatorTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);
    private static final Map<RpgAttribute,Integer> HIGH=Map.of(RpgAttribute.STR,500,RpgAttribute.DEX,500,
            RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);

    static Stream<Arguments> authoredAffixes() {
        assertEquals(160,QA.coverage().size());
        return QA.coverage().stream().map(row->Arguments.of(row.affixId(),row.fixtureId(),row.itemBaseId()));
    }
    private static GearEquipmentResolution.Candidate equipped(GearInstance item) {
        return new GearEquipmentResolution.Candidate(item,true,true,true);
    }
    private static GearEquipmentResolution.Result resolve(GearEquipmentResolution.Candidate... slots) {
        return GearEquipmentResolution.resolve(99,HIGH,List.of(slots),ALL_AUTHORED);
    }
    private static final Set<String> ALL_AUTHORED=authoredIds();
    private static Set<String> authoredIds() {
        var ids=new HashSet<String>();
        CATALOG.affixes().forEach(a->ids.add(a.id()));
        assertEquals(160,ids.size());
        return Set.copyOf(ids);
    }
    private static void rejected(GearInstance item,String reason,GearEquipmentResolution.Candidate candidate) {
        var result=resolve(candidate);
        assertEquals(reason,result.rejected().get(item.identity()));
        assertTrue(result.validItems().isEmpty());
        assertTrue(result.effects().snapshot().empty());
        assertEquals(GearAffixRuntime.Effects.NONE,result.effects());
    }

    @ParameterizedTest(name="authoredAffixReachesFrozenEquipmentSnapshotAndLeavesOnEveryInvalidation({0})")
    @ValueSource(strings={"WA-001","WA-002","WA-003","WA-004","WA-005","WA-006","WA-007","WA-008","WA-009","WA-010","WA-011","WA-012","WA-013","WA-014","WA-015","WA-016","WA-017","WA-018","WA-019","WA-020","WA-021","WA-022","WA-023","WA-024","WA-025","WA-026","WA-027","WA-028","WA-029","WA-030","WA-031","WA-032","WA-033","WA-034","WA-035","WA-036","WA-037","WA-038","WA-039","WA-040","WA-041","WA-042","WA-043","WA-044","WA-045","WA-046","WA-047","WA-048","WA-049","WA-050","WA-051","WA-052","WA-053","WA-054","WA-055","WA-056","WA-057","WA-058","WA-059","WA-060","WA-061","WA-062","WA-063","WA-064","WA-065","WA-066","WA-067","WA-068","WA-069","WA-070","WA-071","WA-072","WA-073","WA-074","WA-075","WA-076","WA-077","WA-078","WA-079","WA-080","WA-081","WA-082","WA-083","WA-084","WA-085","WA-086","WA-087","WA-088","WA-089","WA-090","WA-091","WA-092","WA-093","WA-094","WA-095","WA-096","WA-097","WA-098","WA-099","WA-100","WA-101","WA-102","WA-103","WA-104","WA-105","WA-106","WA-107","WA-108","WA-109","WA-110","WA-111","WA-112","WA-113","WA-114","WA-115","WA-116","WA-117","WA-118","WA-119","WA-120","WA-121","WA-122","WA-123","WA-124","WA-125","WA-126","WA-127","WA-128","WA-129","WA-130","WA-131","WA-132","WA-133","WA-134","WA-135","WA-136","WA-137","WA-138","WA-139","WA-140","WA-141","WA-142","WA-143","WA-144","WA-145","WA-146","WA-147","WA-148","WA-149","WA-150","WA-151","WA-152","WA-153","WA-154","WA-155","WA-156","WA-157","WA-158","GA-159","GA-160"})
    void authoredAffixReachesFrozenEquipmentSnapshotAndLeavesOnEveryInvalidation(String id) {
        String fixtureId="ab-"+id.toLowerCase(Locale.ROOT)+"-affixed";
        var row=QA.fixtures().stream().filter(f->f.fixtureId().equals(fixtureId)).findFirst().orElseThrow();
        var item=QA.preview(row);
        var base=CATALOG.base(item.baseId());
        var definition=CATALOG.affix(id);
        var roll=assertDoesNotThrow(()->item.affixes().getFirst());
        assertEquals(List.of(id),item.affixes().stream().map(GearInstance.AffixRoll::familyId).toList());
        assertEquals(row.itemLevel(),item.itemLevel());
        assertEquals(5,HIGH.size());
        assertTrue(base.eligible(base.era(),item.itemLevel()),id+" source window");
        assertEquals(definition.side(),roll.side());
        assertEquals(definition.exclusionGroup(),roll.exclusionGroup());
        var tier=GearAffixTiers.compile(definition).stream().filter(t->t.tier()==roll.tier()).findFirst().orElseThrow();
        assertTrue(tier.minimumItemLevel()<=item.itemLevel());
        assertTrue(GearAffixTiers.rarityAllows(definition,tier,item.rarity()));
        assertTrue(GearDropGenerator.rollable(definition,base,tier));
        if(definition.tierModel().equals("Q")) {
            var interval=GearDropGenerator.scaledInterval(definition,base,tier);
            var units=java.math.BigDecimal.valueOf(roll.value()).divide(java.math.BigDecimal.valueOf(tier.grid()));
            assertTrue(units.stripTrailingZeros().scale()<=0,id+" roll grid");
            assertTrue(units.longValueExact()>=interval[0]&&units.longValueExact()<=interval[1],id+" scaled range");
        } else assertEquals(tier.low(),roll.value());
        assertEquals(tier.requiredLevel(),roll.requirements().level());
        assertEquals(Map.of(definition.requirementAttribute(base),definition.attributeFloor(
                definition.tierModel().equals("Q")?5-tier.tier():0,tier.minimumItemLevel())),
                roll.requirements().attributes());
        assertEquals(GearRequirements.combine(new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),
                List.of(roll.requirements()),id.equals("WA-154")
                        ?java.math.BigDecimal.valueOf(roll.value()).movePointLeft(2):java.math.BigDecimal.ZERO),item.requirements());
        if(definition.eligibility().equals("MATCH"))
            assertTrue(GearDropGenerator.matchingSkillIds(base).contains(roll.selector()),id+" frozen selector");
        else assertNull(roll.selector());

        var accepted=resolve(equipped(item));
        assertEquals(List.of(item),accepted.validItems());
        assertTrue(accepted.rejected().isEmpty());
        var snapshot=accepted.effects().snapshot();
        assertEquals(List.of(item),snapshot.items());
        assertEquals(roll.value(),snapshot.value(id));
        assertEquals(List.of(roll),snapshot.sources(GearEffectSnapshot.Operator.valueOf(definition.operator()))
                .stream().map(GearEffectSnapshot.Source::roll).toList());
        assertEquals(item.identity(),snapshot.sources(GearEffectSnapshot.Operator.valueOf(definition.operator()))
                .getFirst().itemId());
        assertEquals(snapshot,snapshot.forItem(item.identity()));
        assertTrue(snapshot.forItem(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))).empty());

        var reloaded=GearInstance.fromJson(item.toJson());
        var afterReload=resolve(equipped(reloaded)).effects();
        assertEquals(accepted.effects(),afterReload);
        assertEquals(snapshot.revision(),afterReload.snapshot().revision(),id+" frozen revision survives reload");
        assertEquals(roll.value(),afterReload.snapshot().value(id));
        assertEquals(snapshot.revision(),resolve(equipped(item)).effects().snapshot().revision());
        assertEquals(roll,reloaded.affixes().getFirst());
        assertEquals(GearAffixRuntime.Effects.NONE,resolve().effects());
        assertEquals(roll.value(),snapshot.value(id),"earlier accepted snapshot remains frozen");

        rejected(item,"NOT_EQUIPPED",new GearEquipmentResolution.Candidate(item,false,true,true));
        rejected(item,"BROKEN",new GearEquipmentResolution.Candidate(item,true,false,true));
        rejected(item,"WRONG_SLOT",new GearEquipmentResolution.Candidate(item,true,true,false));
        var duplicate=resolve(equipped(item),equipped(reloaded));
        assertEquals("DUPLICATE_IDENTITY",duplicate.rejected().get(item.identity()));
        assertEquals(GearAffixRuntime.Effects.NONE,duplicate.effects());
        var unmet=GearEquipmentResolution.resolve(1,Map.of(),List.of(equipped(item)),ALL_AUTHORED);
        assertEquals("UNMET_REQUIREMENTS",unmet.rejected().get(item.identity()));
        assertEquals(GearAffixRuntime.Effects.NONE,unmet.effects());
        assertFalse(item.requirements().failures(1,Map.of(),base.category()==GearCatalog.Category.ARMOR).isEmpty());

        var controlRow=QA.fixtures().stream().filter(f->f.fixtureId().equals(fixtureId.replace("-affixed","-control")))
                .findFirst().orElseThrow();
        var control=QA.preview(controlRow);
        assertEquals(item.baseId(),control.baseId());
        assertEquals(item.intrinsicStats(),control.intrinsicStats());
        assertTrue(control.affixes().isEmpty());
        assertTrue(resolve(equipped(control)).effects().snapshot().sources(
                GearEffectSnapshot.Operator.valueOf(definition.operator())).isEmpty());
        if(!GearAffixRuntime.ENABLED.contains(id)) {
            var production=GearEquipmentResolution.resolve(99,HIGH,List.of(equipped(item)));
            assertEquals("CAPABILITY_GATED",production.rejected().get(item.identity()));
            assertEquals(GearAffixRuntime.Effects.NONE,production.effects());
            assertEquals(GearAffixRuntime.Effects.NONE,GearAffixRuntime.effects(List.of(item)));
        }
    }

    @ParameterizedTest(name="authoredCarrierIsProductionEligible({0}, {1}, {2})")
    @MethodSource("authoredAffixes")
    void authoredCarrierIsProductionEligible(String id,String fixtureId,String expectedBase) {
        var row=QA.fixtures().stream().filter(f->f.fixtureId().equals(fixtureId)).findFirst().orElseThrow();
        var base=CATALOG.base(row.itemBaseId());
        assertEquals(expectedBase,base.id());
        assertTrue(base.worldDropCandidate(),id+" production world-drop base");
        assertTrue(new GearBindings().require(base.id()).mapped(),id+" native carrier mapping");
        assertTrue(base.eligible(base.era(),row.itemLevel()),id+" authored source window");
        assertTrue(GearDropGenerator.eligible(CATALOG.affix(id),base),id+" has no legal carrier: "+base.id());
    }
}
