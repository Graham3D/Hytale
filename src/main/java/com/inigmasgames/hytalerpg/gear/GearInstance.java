package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Immutable item payload carried by the existing native ItemStack persistence owner. */
public record GearInstance(int schemaVersion,UUID identity,String definitionRevision,String baseId,
                           String baseName,GearCatalog.Category category,DifficultyId sourceEra,
                           int itemLevel,GearRarity rarity,int intrinsicThousandths,Map<String,Double> intrinsicStats,
                           GearRequirements.Gate requirements,List<AffixRoll> affixes,String rngVersion,boolean qaOnly) {
    private static final Gson JSON=new Gson();
    public static final String LEGACY_LEGALITY_REVISION="gear-legacy-legality-v1";
    public record AffixRoll(String familyId,GearCatalog.Side side,String exclusionGroup,int tier,
                            double value,GearRequirements.Gate requirements,String description,String name,String selector) {
        /** Historical payloads have no selector; they remain readable without inventing one. */
        public AffixRoll(String familyId,GearCatalog.Side side,String exclusionGroup,int tier,
                         double value,GearRequirements.Gate requirements,String description,String name) {
            this(familyId,side,exclusionGroup,tier,value,requirements,description,name,null);
        }
        public AffixRoll {
            if(familyId==null || !familyId.matches("(?:WA|GA)-[0-9]{3}") || side==null
                    || exclusionGroup==null || exclusionGroup.isBlank() || tier<1 || tier>5
                    || !Double.isFinite(value) || requirements==null || description==null || description.isBlank() || name==null || name.isBlank())
                throw new IllegalArgumentException("Invalid frozen affix");
            if(selector!=null && !selector.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid frozen selector");
        }
    }
    public GearInstance {
        if(schemaVersion!=1 || identity==null || definitionRevision==null || definitionRevision.isBlank()
                || baseId==null || !baseId.matches("gm\\.[a-z0-9_.]+") || baseName==null || baseName.isBlank()
                || category==null || sourceEra==null || rarity==null || !rarity.eligible(itemLevel,sourceEra)
                || intrinsicThousandths<900 || intrinsicThousandths>1000 || requirements==null
                || rngVersion==null || rngVersion.isBlank()) throw new IllegalArgumentException("Invalid persisted gear instance");
        intrinsicStats=Map.copyOf(intrinsicStats); affixes=List.copyOf(affixes);
        if(intrinsicStats.values().stream().anyMatch(v->!Double.isFinite(v) || v<0)) throw new IllegalArgumentException("Invalid frozen intrinsic stats");
        int prefixes=(int)affixes.stream().filter(a->a.side()==GearCatalog.Side.PREFIX).count();
        if(qaOnly ? affixes.size()>10 : !rarity.legalBudget(prefixes,affixes.size()-prefixes))
            throw new IllegalArgumentException("Illegal rarity affix budget");
        if(affixes.stream().map(AffixRoll::exclusionGroup).distinct().count()!=affixes.size()
                || affixes.stream().map(AffixRoll::familyId).distinct().count()!=affixes.size())
            throw new IllegalArgumentException("Duplicate affix family/group");
        if(category==GearCatalog.Category.TOOL && rarity.quality()!=GearQuality.NORMAL) throw new IllegalArgumentException("Tools cannot roll affixes");
    }
    /** Explicit protected QA construction; encounter generation is not connected in stage one. */
    public static GearInstance authoredQa(GearCatalog.Base base,UUID identity,int itemLevel,int roll,
                                          GearRarity rarity,List<AffixRoll> affixes,BigDecimal reduction) {
        if(roll<900 || roll>1000) throw new IllegalArgumentException("Intrinsic roll outside 900..1000");
        if(base.category()!=GearCatalog.Category.TOOL && !base.eligible(base.era(),itemLevel))
            throw new IllegalArgumentException("Source level outside authored base window");
        var stats=new LinkedHashMap<String,Double>();
        base.perfectStats().forEach((key,value)->stats.put(key,BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(roll,3)).setScale(1,RoundingMode.HALF_UP).doubleValue()));
        var gate=GearRequirements.combine(new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),
                affixes.stream().map(AffixRoll::requirements).toList(),reduction);
        return new GearInstance(1,identity,GearCatalog.REVISION,base.id(),base.name(),base.category(),base.era(),
                itemLevel,rarity,roll,stats,gate,affixes,"qa-authored-v1",true);
    }
    public String toJson() { return JSON.toJson(this); }
    /** Record constructors validate reload; current definitions are intentionally not consulted. */
    public static GearInstance fromJson(String json) {
        if(json==null || json.length()>65536) throw new IllegalArgumentException("Invalid gear payload size");
        var value=JSON.fromJson(json,GearInstance.class);
        if(value==null) throw new IllegalArgumentException("Missing gear payload");
        Qa159Pack.validatePersisted(value); return value;
    }
    public GearQuality quality(){return rarity.quality();}
    /** Diagnostics only: a historical payload is never rerolled or rewritten here. */
    public Set<String> legacyIssues() {
        var issues=new LinkedHashSet<String>();
        if(!qaOnly) {
            int prefixes=(int)affixes.stream().filter(a->a.side()==GearCatalog.Side.PREFIX).count();
            if(!rarity.legalNewBudget(prefixes,affixes.size()-prefixes))issues.add("HISTORICAL_AFFIX_BUDGET");
        }
        if(rarity==GearRarity.RARE&&affixes.stream().anyMatch(a->a.familyId().equals("WA-121")))
            issues.add("WA121_RARE_LEGACY");
        if(affixes.stream().anyMatch(a->(a.familyId().equals("WA-122")||a.familyId().equals("WA-144"))&&a.selector()==null))
            issues.add("MISSING_FROZEN_SELECTOR");
        return Collections.unmodifiableSet(issues);
    }
    public boolean perfectCommon() { return quality()==GearQuality.NORMAL && intrinsicThousandths==1000; }
    public String displayName() {
        if(affixes.isEmpty()) return baseName;
        String prefix=affixes.stream().filter(a->a.side()==GearCatalog.Side.PREFIX).map(AffixRoll::name).findFirst().orElse("");
        String suffix=affixes.stream().filter(a->a.side()==GearCatalog.Side.SUFFIX).map(AffixRoll::name).findFirst().orElse("");
        return (prefix+" "+baseName+" "+suffix).trim();
    }
}
