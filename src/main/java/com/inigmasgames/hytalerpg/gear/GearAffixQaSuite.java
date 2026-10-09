package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Protected, deterministic fixtures. The catalog and production generator remain the legality authority. */
public final class GearAffixQaSuite {
    public record Fixture(String fixtureId,String itemBaseId,int itemLevel,String rarity,String group,List<String> affixIds) {}
    private record File(int schemaVersion,String note,List<Fixture> fixtures) {}
    public record Coverage(String affixId,String name,String fixtureId,String itemBaseId,String slotOrFamily,
                           String rarity,Double value,String eligibility,String scope,String inheritancePolicy,
                           boolean combatTestable,boolean advancedStatsTestable,boolean sentinelInheritable,String status) {}
    private final GearCatalog catalog;
    private final GearBindings bindings=new GearBindings();
    private final List<Fixture> fixtures;
    private final Map<String,Fixture> byId;
    private final List<Coverage> coverage;

    public GearAffixQaSuite(GearCatalog catalog) {
        this.catalog=Objects.requireNonNull(catalog);
        File file;
        try(var stream=GearAffixQaSuite.class.getResourceAsStream("/rpg/gear/affix-qa-fixtures-v1.json")){
            if(stream==null)throw new IllegalStateException("Missing affix QA fixtures");
            file=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),File.class);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        if(file==null||file.schemaVersion()!=1||file.fixtures()==null)throw new IllegalStateException("Invalid affix QA fixture file");
        var ids=new LinkedHashMap<String,Fixture>();var assigned=new HashMap<String,Fixture>();
        for(var fixture:file.fixtures()){
            if(fixture.fixtureId()==null||!fixture.fixtureId().matches("[a-z0-9-]+")
                    ||ids.putIfAbsent(fixture.fixtureId(),fixture)!=null)throw new IllegalStateException("Duplicate/invalid fixture ID");
            if(!Set.of("armor","weapons","support","summons").contains(fixture.group()))throw new IllegalStateException("Invalid fixture group");
            var base=catalog.base(fixture.itemBaseId());
            if(!bindings.require(base.id()).mapped())throw new IllegalStateException("Unmapped QA base "+base.id());
            if(fixture.group().equals("armor")&&base.category()!=GearCatalog.Category.ARMOR)throw new IllegalStateException("Wrong fixture group");
            GearRarity rarity=GearRarity.valueOf(fixture.rarity());
            if(!rarity.eligible(fixture.itemLevel(),base.era())||!base.eligible(base.era(),fixture.itemLevel()))
                throw new IllegalStateException("QA source window invalid "+fixture.fixtureId());
            for(String id:fixture.affixIds()){
                if(!GearAffixRuntime.ENABLED.contains(id))throw new IllegalStateException("Unadapted QA affix "+id);
                var affix=catalog.affix(id);
                if(!GearDropGenerator.eligible(affix,base))
                    throw new IllegalStateException("Illegal affix "+id+" on "+base.id());
                if(assigned.putIfAbsent(id,fixture)!=null)throw new IllegalStateException("Duplicated QA affix "+id);
            }
            // Materialize every fixture at load time: gates, exclusion groups, tiers and rarity budgets must all pass.
            createUnchecked(fixture,UUID.nameUUIDFromBytes(("affixqa/validation/"+fixture.fixtureId()).getBytes(StandardCharsets.UTF_8)));
        }
        // Single-affix A/B rows are deliberately enumerated for every authored ID.
        // Unsupported rows remain inspectable, but the command refuses to issue them.
        var allFixtures=new ArrayList<>(file.fixtures());
        for(var affix:catalog.affixes()) {
            var rarity=switch(affix.tierModel()) {
                case "ALL" -> GearRarity.LEGENDARY;
                case "NAMED","FAMILY","FIXED" -> GearRarity.RARE;
                default -> GearRarity.MAGIC;
            };
            int minimum=GearAffixTiers.compile(affix).getFirst().minimumItemLevel();
            var base=catalog.bases().stream().filter(b->b.category()!=GearCatalog.Category.TOOL
                    &&b.era()==com.inigmasgames.hytalerpg.difficulty.DifficultyId.HELL
                    &&b.sourceWindow().getLast()>=Math.max(minimum,rarity.minimumLevel)
                    &&GearDropGenerator.eligible(affix,b))
                    .sorted(Comparator.comparing((GearCatalog.Base b)->!bindings.require(b.id()).mapped())
                            .thenComparing(GearCatalog.Base::id)).findFirst().orElse(null);
            if(base==null)throw new IllegalStateException("No authored A/B carrier for "+affix.id());
            String group=affix.id().matches("WA-11[3-9]|WA-120|WA-131")?"summons"
                    :affix.id().matches("WA-10[1-9]|WA-11[0-2]|WA-130|WA-14[4-9]|WA-150")?"support"
                    :base.category()==GearCatalog.Category.ARMOR?"armor":"weapons";
            int level=base.sourceWindow().getLast();
            String stem="ab-"+affix.id().toLowerCase(Locale.ROOT);
            for(boolean control:new boolean[]{true,false}){
                var row=new Fixture(stem+(control?"-control":"-affixed"),base.id(),level,rarity.name(),group,
                        control?List.of():List.of(affix.id()));
                if(ids.putIfAbsent(row.fixtureId(),row)!=null)throw new IllegalStateException("Duplicate A/B fixture");
                allFixtures.add(row);
            }
        }
        var combined=new LinkedHashMap<String,List<List<String>>>();
        for(var affix:catalog.affixes()) {
            var single=ids.get("ab-"+affix.id().toLowerCase(Locale.ROOT)+"-affixed");
            if(!GearAffixRuntime.ENABLED.contains(affix.id())
                    ||!bindings.require(single.itemBaseId()).mapped()
                    ||!GearDropGenerator.eligible(affix,catalog.base(single.itemBaseId())))continue;
            var baskets=combined.computeIfAbsent(single.itemBaseId(),ignored->new ArrayList<>());
            boolean placed=false;
            for(var basket:baskets) {
                if(basket.size()>=10||basket.stream().anyMatch(id->catalog.affix(id).exclusionGroup().equals(affix.exclusionGroup())))continue;
                var candidate=new ArrayList<>(basket);candidate.add(affix.id());
                var row=new Fixture("combo-probe",single.itemBaseId(),single.itemLevel(),"LEGENDARY",single.group(),candidate);
                try {createUnchecked(row,UUID.nameUUIDFromBytes("combo/probe".getBytes(StandardCharsets.UTF_8)));}
                catch(IllegalArgumentException|IllegalStateException invalid){continue;}
                basket.add(affix.id());placed=true;break;
            }
            if(!placed)baskets.add(new ArrayList<>(List.of(affix.id())));
        }
        for(var entry:combined.entrySet())for(int i=0;i<entry.getValue().size();i++) {
            var base=catalog.base(entry.getKey());
            var row=new Fixture("combo-"+base.id().substring(3).replace('_','-').replace('.','-')+"-"+(i+1),
                    base.id(),base.sourceWindow().getLast(),"LEGENDARY",
                    base.category()==GearCatalog.Category.ARMOR?"armor":"weapons",List.copyOf(entry.getValue().get(i)));
            createUnchecked(row,UUID.nameUUIDFromBytes(("combo/validation/"+row.fixtureId()).getBytes(StandardCharsets.UTF_8)));
            if(ids.putIfAbsent(row.fixtureId(),row)!=null)throw new IllegalStateException("Duplicate combined fixture");
            allFixtures.add(row);
        }
        // Presentation comparison: same authored Normal-era Adamantite base,
        // fixed intrinsic roll and ordinary legal affixes. No production rolls change.
        for(var row:List.of(
                new Fixture("tooltip-common-battleaxe","gm.battleaxe_adamantite.n",40,"COMMON","weapons",List.of()),
                new Fixture("tooltip-magic-battleaxe","gm.battleaxe_adamantite.n",40,"MAGIC","weapons",List.of("WA-001")),
                new Fixture("tooltip-rare-battleaxe","gm.battleaxe_adamantite.n",40,"RARE","weapons",List.of("WA-002","WA-018","WA-008")))) {
            createUnchecked(row,UUID.nameUUIDFromBytes(("tooltip/validation/"+row.fixtureId()).getBytes(StandardCharsets.UTF_8)));
            if(ids.putIfAbsent(row.fixtureId(),row)!=null)throw new IllegalStateException("Duplicate tooltip fixture");
            allFixtures.add(row);
        }
        for(var row:Qa159Pack.load(catalog)) {
            var item=createUnchecked(row,UUID.nameUUIDFromBytes(("qa159/validation/"+row.fixtureId()).getBytes(StandardCharsets.UTF_8)));
            Qa159Pack.validateItem(catalog,item);
            IronSentinelAffixes.requireAdapted(item);
            if(ids.putIfAbsent(row.fixtureId(),row)!=null)throw new IllegalStateException("Duplicate QA159 fixture");
            allFixtures.add(row);
        }
        fixtures=List.copyOf(allFixtures);byId=Map.copyOf(ids);
        var evidenceStatus=loadEvidenceStatus();
        var rows=new ArrayList<Coverage>();
        for(var affix:catalog.affixes()){
            Fixture fixture=ids.get("ab-"+affix.id().toLowerCase(Locale.ROOT)+"-affixed");
            var decision=IronSentinelAffixes.classify(affix.id());
            var item=createUnchecked(fixture,UUID.nameUUIDFromBytes(("affixqa/coverage/"+fixture.fixtureId()).getBytes(StandardCharsets.UTF_8)));
            var roll=item==null?null:item.affixes().stream().filter(a->a.familyId().equals(affix.id())).findFirst().orElseThrow();
            var base=catalog.base(fixture.itemBaseId());
            rows.add(new Coverage(affix.id(),affix.name(),fixture.fixtureId(),fixture.itemBaseId(),
                    base.category()==GearCatalog.Category.ARMOR?base.slot().name():base.family(),
                    fixture.rarity(),roll.value(),affix.eligibility(),affix.scopeContract(),
                    decision.disposition().name(),isCombat(affix),isAdvanced(affix),
                    decision.adapted()&&decision.disposition()!=IronSentinelAffixes.Disposition.OWNER_ONLY,
                    Objects.requireNonNull(evidenceStatus.get(affix.id()),"Missing evidence row "+affix.id())));
        }
        if(rows.size()!=160||rows.stream().map(Coverage::affixId).distinct().count()!=160)
            throw new IllegalStateException("Affix QA coverage is incomplete or duplicated");
        coverage=List.copyOf(rows);
    }
    private static Map<String,String> loadEvidenceStatus(){
        try(var stream=GearAffixQaSuite.class.getResourceAsStream("/rpg/gear/affix-qa-coverage-v1.json")){
            if(stream==null)throw new IllegalStateException("Missing generated affix evidence manifest");
            var parsed=com.google.gson.JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
            if(!parsed.isJsonArray())throw new IllegalStateException("Invalid affix evidence manifest");
            var statuses=new HashMap<String,String>();
            for(var row:parsed.getAsJsonArray()){
                var value=row.getAsJsonObject();
                String id=value.get("affixId").getAsString(),status=value.get("result").getAsString();
                if(!Set.of("FUNCTIONAL","GATED").contains(status)||statuses.putIfAbsent(id,status)!=null)
                    throw new IllegalStateException("Duplicate or invalid affix evidence row "+id);
            }
            if(statuses.size()!=160)throw new IllegalStateException("Incomplete affix evidence manifest");
            return Map.copyOf(statuses);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static boolean isCombat(GearCatalog.Affix a){return Set.of("LOCAL_PHYS_FLAT","LOCAL_PHYS_INC","LOCAL_PHYS_MIN","LOCAL_PHYS_MAX").contains(a.operator());}
    private static boolean isAdvanced(GearCatalog.Affix a){return Set.of("ATTRIBUTE","MAX_HEALTH","MAX_MANA","MAX_STAMINA","CAST_SPEED","COOLDOWN_RECOVERY","SKILLER","MAGIC_FIND","LOCAL_ARMOR_FLAT","LOCAL_ARMOR_INCREASED").contains(a.operator());}
    public List<Fixture> fixtures(){return fixtures;}
    public List<Coverage> coverage(){return coverage;}
    public boolean spawnable(Fixture fixture) {
        if(fixture.fixtureId().endsWith("-control")) {
            var paired=byId.get(fixture.fixtureId().substring(0,fixture.fixtureId().length()-8)+"-affixed");
            return paired!=null&&spawnable(paired);
        }
        return bindings.require(fixture.itemBaseId()).mapped()
                &&fixture.affixIds().stream().allMatch(GearAffixRuntime.ENABLED::contains)
                &&fixture.affixIds().stream().allMatch(id->GearDropGenerator.eligible(catalog.affix(id),catalog.base(fixture.itemBaseId())));
    }
    public GearInstance create(String fixtureId,UUID player){
        var fixture=byId.get(fixtureId);if(fixture==null)throw new IllegalArgumentException("Unknown affix QA fixture: "+fixtureId);
        if(!spawnable(fixture))throw new IllegalArgumentException("Fixture pending native carrier or verified affix adapter: "+fixtureId);
        UUID identity=UUID.nameUUIDFromBytes(("affixqa/v1/"+player+"/"+fixtureId).getBytes(StandardCharsets.UTF_8));
        var result=createUnchecked(fixture,identity);
        if(fixtureId.endsWith("-control")) {
            var sibling=byId.get(fixtureId.substring(0,fixtureId.length()-8)+"-affixed");
            if(sibling!=null) {
                var paired=createUnchecked(sibling,identity);
                result=new GearInstance(result.schemaVersion(),result.identity(),result.definitionRevision(),result.baseId(),
                        result.baseName(),result.category(),result.sourceEra(),result.itemLevel(),result.rarity(),
                        result.intrinsicThousandths(),result.intrinsicStats(),paired.requirements(),result.affixes(),
                        result.rngVersion(),true);
            }
        }
        return result;
    }
    /** Catalog preview is available for pending fixtures; it grants no gameplay capability. */
    public GearInstance preview(Fixture fixture) {
        return createUnchecked(fixture,UUID.nameUUIDFromBytes(("affixqa/preview/"+fixture.fixtureId()).getBytes(StandardCharsets.UTF_8)));
    }
    private GearInstance createUnchecked(Fixture fixture,UUID identity){
        var base=catalog.base(fixture.itemBaseId());var rarity=GearRarity.valueOf(fixture.rarity());
        var rolls=new ArrayList<GearInstance.AffixRoll>();BigDecimal reduction=BigDecimal.ZERO;
        for(String id:fixture.affixIds()){
            var a=catalog.affix(id);
            var tier=GearAffixTiers.compile(a).stream().filter(t->t.minimumItemLevel()<=fixture.itemLevel()
                    &&GearAffixTiers.rarityAllows(a,t,rarity)&&GearDropGenerator.rollable(a,base,t))
                    .findFirst().orElseThrow(()->new IllegalStateException("No legal tier for "+id));
            boolean pack=fixture.group().equals("qa159");
            double value=pack?tier.high():tier.low();
            if(a.tierModel().equals("Q"))value=BigDecimal.valueOf(GearDropGenerator.scaledInterval(a,base,tier)[pack?1:0])
                    .multiply(BigDecimal.valueOf(tier.grid())).doubleValue();
            var gate=new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                    a.attributeFloor(a.tierModel().equals("Q")?5-tier.tier():0,tier.minimumItemLevel())));
            String description=a.effectContract().split(" Require:")[0].replaceAll("\\bV\\b",Double.toString(value));
            String selector=a.eligibility().equals("MATCH")?GearDropGenerator.matchingSkillIds(base).stream().findFirst()
                    .orElseThrow(()->new IllegalStateException("No legal selector for "+id+" on "+base.id())):null;
            rolls.add(new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),tier.tier(),value,gate,description,a.name(),selector));
            if(id.equals("WA-154"))reduction=BigDecimal.valueOf(value).movePointLeft(2);
        }
        var item=GearInstance.authoredQa(base,identity,fixture.itemLevel(),950,rarity,rolls,reduction);
        return fixture.group().equals("qa159")?Qa159Pack.mark(item,fixture.fixtureId()):item;
    }
}
