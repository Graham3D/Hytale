package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import com.inigmasgames.hytalerpg.ui.inventory.FootprintCatalog;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** QA allocation only. Production rolls, capability gates and runtime mechanics are unchanged. */
public final class Qa159Pack {
    public static final String PROVENANCE="qa159-v1/";
    private record File(int schemaVersion,List<GearAffixQaSuite.Fixture> fixtures) {}
    private Qa159Pack() {}
    private static final class Data {
        static final GearCatalog CATALOG=GearCatalog.load();
        static final FootprintCatalog FOOTPRINTS=FootprintCatalog.loadDefault();
        static final File FILE=readFile();
    }
    private static File readFile() {
        try(var input=Qa159Pack.class.getResourceAsStream("/rpg/gear/qa159-v1.json")) {
            if(input==null)throw new IllegalStateException("Missing QA159 pack");
            return new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),File.class);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    public static void validatePersisted(GearInstance item) {
        if(item.rngVersion().startsWith(PROVENANCE))validateItem(Data.CATALOG,item);
    }
    public static List<GearAffixQaSuite.Fixture> load(GearCatalog catalog) {
        try(var input=Qa159Pack.class.getResourceAsStream("/rpg/gear/qa159-v1.json")) {
            if(input==null)throw new IllegalStateException("Missing QA159 pack");
            var file=new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),File.class);
            if(file.schemaVersion()!=1||file.fixtures().size()!=17)throw new IllegalStateException("QA159 requires 17 fixtures");
            var bases=new HashSet<String>();var ids=new HashSet<String>();var affixes=new HashSet<String>();
            var armor=EnumSet.noneOf(GearCatalog.Slot.class);var bindings=new GearBindings();
            for(int i=0;i<17;i++) {
                var row=file.fixtures().get(i);var base=catalog.base(row.itemBaseId());
                if(!row.fixtureId().equals("qa159-%02d".formatted(i+1))||!ids.add(row.fixtureId())||!bases.add(base.id())
                        ||!row.group().equals("qa159")||row.affixIds().size()!=(i<6?10:9))
                    throw new IllegalStateException("Invalid QA159 fixture "+row.fixtureId());
                if(i<4){if(base.category()!=GearCatalog.Category.ARMOR||!armor.add(base.slot()))throw new IllegalStateException("QA159 armor slots");}
                else if(base.category()!=GearCatalog.Category.HELD)throw new IllegalStateException("QA159 held carrier required");
                var binding=bindings.require(base.id());
                if(!binding.mapped()||Data.FOOTPRINTS.size(binding.nativeItemId())==null)
                    throw new IllegalStateException("QA159 native carrier/footprint missing: "+base.id());
                for(String id:row.affixIds())if(!affixes.add(id)||!GearAffixRuntime.ENABLED.contains(id)
                        ||!GearDropGenerator.eligible(catalog.affix(id),base)||!evidence(id).get("result").getAsString().equals("FUNCTIONAL"))
                    throw new IllegalStateException("QA159 duplicate, gated or illegal affix "+id+" on "+base.id());
            }
            if(affixes.size()!=159||affixes.contains("WA-155")||!affixes.equals(GearAffixRuntime.ENABLED))
                throw new IllegalStateException("QA159 coverage mismatch");
            return List.copyOf(file.fixtures());
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    public static String fixtureId(GearInstance item) {
        return item.rngVersion().startsWith(PROVENANCE)?item.rngVersion().substring(PROVENANCE.length()):"NON_QA159";
    }
    public static GearInstance mark(GearInstance item,String fixture) {
        return new GearInstance(item.schemaVersion(),item.identity(),item.definitionRevision(),item.baseId(),item.baseName(),
                item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),item.intrinsicThousandths(),item.intrinsicStats(),
                item.requirements(),item.affixes(),PROVENANCE+fixture,true);
    }
    /** New pack payloads retain normal magnitude/tier/selector checks on reload as well as creation. */
    public static void validateItem(GearCatalog catalog,GearInstance item) {
        if(!item.rngVersion().startsWith(PROVENANCE))return;
        var fixture=Data.FILE.fixtures().stream().filter(f->f.fixtureId().equals(fixtureId(item))).findFirst().orElseThrow();
        if(!item.qaOnly()||!fixture.itemBaseId().equals(item.baseId())||item.itemLevel()!=fixture.itemLevel()
                ||!item.rarity().name().equals(fixture.rarity())
                ||!new HashSet<>(fixture.affixIds()).equals(item.affixes().stream().map(GearInstance.AffixRoll::familyId).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("QA159 persisted identity/assignment mismatch");
        var base=catalog.base(item.baseId());
        if(item.category()!=base.category()||item.sourceEra()!=base.era())throw new IllegalArgumentException("QA159 carrier metadata mismatch");
        for(var roll:item.affixes()) {
            var a=catalog.affix(roll.familyId());
            if(!GearAffixRuntime.ENABLED.contains(a.id())||!GearDropGenerator.eligible(a,base))
                throw new IllegalArgumentException("QA159 gated or illegal carrier");
            var tier=GearAffixTiers.compile(a).stream().filter(t->t.tier()==roll.tier()
                    &&t.minimumItemLevel()<=item.itemLevel()&&GearAffixTiers.rarityAllows(a,t,item.rarity())
                    &&GearDropGenerator.rollable(a,base,t)).findFirst().orElseThrow();
            double low=tier.low(),high=tier.high();
            if(a.tierModel().equals("Q")){var range=GearDropGenerator.scaledInterval(a,base,tier);low=range[0]*tier.grid();high=range[1]*tier.grid();}
            if(roll.value()<low-1e-8||roll.value()>high+1e-8||Math.abs(roll.value()/tier.grid()-Math.rint(roll.value()/tier.grid()))>1e-6
                    ||!roll.exclusionGroup().equals(a.exclusionGroup())||roll.side()!=a.side()
                    ||a.eligibility().equals("MATCH")&&!GearDropGenerator.matchingSkillIds(base).contains(roll.selector()))
                throw new IllegalArgumentException("QA159 illegal persisted roll: "+a.id());
            var gate=new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                    a.attributeFloor(a.tierModel().equals("Q")?5-tier.tier():0,tier.minimumItemLevel())));
            if(!gate.equals(roll.requirements()))throw new IllegalArgumentException("QA159 altered affix requirement");
        }
        var expected=GearInstance.authoredQa(base,item.identity(),item.itemLevel(),item.intrinsicThousandths(),item.rarity(),item.affixes(),
                java.math.BigDecimal.valueOf(GearAffixRuntime.value(item,"WA-154")).movePointLeft(2));
        if(!expected.requirements().equals(item.requirements())||!expected.intrinsicStats().equals(item.intrinsicStats()))
            throw new IllegalArgumentException("QA159 altered carrier requirements/intrinsics");
    }
    private static final Map<String,JsonObject> EVIDENCE=loadEvidence();
    private static Map<String,JsonObject> loadEvidence() {
        try(var input=Qa159Pack.class.getResourceAsStream("/rpg/gear/affix-qa-coverage-v1.json")){
            var result=new HashMap<String,JsonObject>();
            for(var row:JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonArray()){
                var value=row.getAsJsonObject();result.put(value.get("affixId").getAsString(),value);
            }return Map.copyOf(result);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static JsonObject evidence(String id){return Objects.requireNonNull(EVIDENCE.get(id));}
    public static Map<String,Object> checks(String id) {
        var proof=evidence(id);var decision=IronSentinelAffixes.classify(id);
        boolean local=Set.of("WA-001","WA-002","WA-003","WA-004","WA-010","WA-011").contains(id);
        boolean ui=local||proof.get("advancedStatsRequired").getAsBoolean();
        return Map.of("inventoryStats",ui?"EXPECTED":"NOT_APPLICABLE","gameplay","EXPECTED",
                "sentinel",disposition(id).equals("INHERITED")?"EXPECTED":"NOT_APPLICABLE","sentinelDisposition",disposition(id),"sentinelReason",decision.reason(),
                "runtimeOwner",proof.get("realRuntimeConsumer").getAsString(),
                "statsField",proof.get("advancedStatsField").isJsonNull()?(local?"weapon/critical presentation":"NOT_APPLICABLE"):proof.get("advancedStatsField").getAsString());
    }
    public static String disposition(String id){var d=IronSentinelAffixes.classify(id);
        return d.disposition()==IronSentinelAffixes.Disposition.OWNER_ONLY?"OWNER_ONLY":
                d.reason().startsWith("INAPPLICABLE")||!d.adapted()?"INAPPLICABLE":"INHERITED";}
    public static Map<String,Object> source(GearInstance item) {
        var rows=item.affixes().stream().map(a->Map.of("affixId",a.familyId(),"value",a.value(),
                "selector",a.selector()==null?"":a.selector(),"checks",checks(a.familyId()))).toList();
        return Map.of("itemId",item.identity(),"fixtureId",fixtureId(item),"baseId",item.baseId(),"qaOnly",item.qaOnly(),"affixes",rows);
    }
    public static String listing(GearAffixQaSuite.Fixture row,GearCatalog catalog) {
        var base=catalog.base(row.itemBaseId());var binding=new GearBindings().require(base.id());
        var size=Data.FOOTPRINTS.size(binding.nativeItemId());
        return row.fixtureId()+" "+base.id()+" native="+binding.nativeItemId()+" "+base.family()+"/"+base.slot()
                +" "+size.width()+"x"+size.height()+" "+row.affixIds()+" Sentinel=ELIGIBLE (per-affix disposition in trace)";
    }
}
