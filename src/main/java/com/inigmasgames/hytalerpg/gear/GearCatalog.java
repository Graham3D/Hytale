package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored definition compiler. Definition presence never grants a native capability. */
public final class GearCatalog {
    public static final String REVISION="gm.1.1.stage1.v1";
    public enum Category { HELD, ARMOR, TOOL }
    public enum Slot { HELD, HEAD, CHEST, HANDS, LEGS }
    public enum Side { PREFIX, SUFFIX }
    public record Base(String id, String name, String family, DifficultyId era, String grade,
                       Category category, Slot slot, List<Integer> sourceWindow, int requiredLevel,
                       Map<RpgAttribute,Integer> requiredAttributes, RpgAttribute primaryAttribute,
                       Map<String,Double> perfectStats, int durability, boolean worldDropCandidate,
                       String nativeBehavior, List<String> authoredCells) {
        public Base {
            Objects.requireNonNull(era); Objects.requireNonNull(category); Objects.requireNonNull(slot);
            if(id==null || !id.matches("gm\\.[a-z0-9_.]+") || name==null || name.isBlank()
                    || family==null || !id.startsWith(family+".") || requiredLevel<1 || requiredLevel>99
                    || durability<=0 || primaryAttribute==null) throw new IllegalArgumentException("Invalid gear base: "+id);
            requiredAttributes=Map.copyOf(requiredAttributes); perfectStats=Map.copyOf(perfectStats);
            authoredCells=List.copyOf(authoredCells);
            if(requiredAttributes.isEmpty() || requiredAttributes.getOrDefault(primaryAttribute,0)<10
                    || requiredAttributes.values().stream().anyMatch(v->v<10)) throw new IllegalArgumentException("Invalid base requirements: "+id);
            if(category==Category.TOOL) {
                if(sourceWindow!=null || worldDropCandidate) throw new IllegalArgumentException("Tools have no combat drop window");
            } else {
                sourceWindow=List.copyOf(sourceWindow);
                if(sourceWindow.size()!=2 || sourceWindow.get(0)<1 || sourceWindow.get(1)>99
                        || sourceWindow.get(0)>sourceWindow.get(1)) throw new IllegalArgumentException("Invalid source window");
            }
            var fields=Set.of("physicalMin","physicalMax","magicPower","healingPower","shieldDefense","protectionPoints","health","mana","stamina");
            for(var field:perfectStats.entrySet()) if(!fields.contains(field.getKey()) || !Double.isFinite(field.getValue()) || field.getValue()<=0)
                throw new IllegalArgumentException("Invalid intrinsic field: "+field);
        }
        public boolean eligible(DifficultyId sourceEra,int sourceCombatLevel) {
            return worldDropCandidate && era==sourceEra && sourceCombatLevel>=sourceWindow.get(0) && sourceCombatLevel<=sourceWindow.get(1);
        }
    }
    public record Affix(String id,String name,Side side,int firstItemLevel,String tierModel,
                        String featureDisposition,String eligibility,String topAndGroup,String weightCode,
                        int weight,String requirementPolicy,String armorExtension,String effectContract,List<String> authoredCells,
                        String exclusionGroup,String operator,String scopeContract,String featureRequirement,List<Double> topRange,
                        String playerTooltipTemplate) {
        public Affix {
            if(id==null || !id.matches("(?:WA|GA)-[0-9]{3}") || name==null || name.isBlank()
                    || side==null || firstItemLevel<1 || firstItemLevel>99 || tierModel==null || tierModel.isBlank()
                    || !Set.of("TEST","GATED").contains(featureDisposition) || eligibility==null || eligibility.isBlank()
                    || weight<=0 || !requirementPolicy.matches("(?:BASE|STR|DEX|INT|WIS|LUCK)-(?:SOFT|CORE|RANK|FIXED)")
                    || armorExtension==null || effectContract==null || effectContract.isBlank())
                throw new IllegalArgumentException("Invalid affix definition: "+id);
            authoredCells=List.copyOf(authoredCells);
            if(exclusionGroup==null || exclusionGroup.isBlank() || operator==null || operator.isBlank()
                    || scopeContract==null || scopeContract.isBlank() || featureRequirement==null || featureRequirement.isBlank())
                throw new IllegalArgumentException("Incomplete affix operator contract: "+id);
            if(topRange!=null) {
                topRange=List.copyOf(topRange);
                if(topRange.size()!=2 || topRange.getFirst()<0 || topRange.getFirst()>topRange.getLast())
                    throw new IllegalArgumentException("Invalid top affix interval");
            }
        }
        public int ordinaryTierMinimum(int tierIndex) {
            if(!tierModel.equals("Q") || tierIndex<0 || tierIndex>4) throw new IllegalArgumentException("Not an ordinary Q tier");
            return (int)Math.ceil(firstItemLevel+(90-firstItemLevel)*tierIndex/4.0);
        }
        public int attributeFloor(int tierIndex,int rankTierMinimum) {
            String policy=requirementPolicy.split("-")[1];
            return switch(policy) {
                case "SOFT" -> new int[]{10,15,25,35,45}[checkedTier(tierIndex)];
                case "CORE" -> new int[]{10,25,45,65,85}[checkedTier(tierIndex)];
                case "RANK" -> { if(rankTierMinimum<1 || rankTierMinimum>99) throw new IllegalArgumentException("Invalid rank tier"); yield Math.max(10,(int)Math.ceil(.8*rankTierMinimum)); }
                case "FIXED" -> Math.min(80,Math.max(10,(int)Math.ceil(.75*firstItemLevel)));
                default -> throw new IllegalStateException("Unknown requirement policy");
            };
        }
        public RpgAttribute requirementAttribute(Base base) {
            String attribute=requirementPolicy.split("-")[0];
            return attribute.equals("BASE")?base.primaryAttribute():RpgAttribute.valueOf(attribute);
        }
        private static int checkedTier(int index) {
            if(index<0 || index>4) throw new IllegalArgumentException("Invalid Q tier"); return index;
        }
        private Affix withTooltip(String template){return new Affix(id,name,side,firstItemLevel,tierModel,featureDisposition,
                eligibility,topAndGroup,weightCode,weight,requirementPolicy,armorExtension,effectContract,authoredCells,
                exclusionGroup,operator,scopeContract,featureRequirement,topRange,template);}
    }
    private record Bases(int schemaVersion,String documentSha256,List<Base> bases) {}
    private record Affixes(int schemaVersion,String documentSha256,List<Affix> affixes) {}
    private record TooltipRow(String id,String playerTooltipTemplate) {}
    private record Tooltips(int schemaVersion,String specification,String sourceSha256,List<TooltipRow> templates) {}
    private final Map<String,Base> bases;
    private final Map<String,Affix> affixes;
    private GearCatalog(Bases baseData,Affixes affixData,Tooltips tooltipData) {
        if(baseData.schemaVersion()!=1 || affixData.schemaVersion()!=1 || !baseData.documentSha256().equals(affixData.documentSha256()))
            throw new IllegalArgumentException("Gear source revision mismatch");
        if(tooltipData.schemaVersion()!=1 || !"Gear Master Document v1.2 §17.1".equals(tooltipData.specification())
                || tooltipData.sourceSha256()==null || !tooltipData.sourceSha256().matches("[A-F0-9]{64}"))
            throw new IllegalArgumentException("Invalid Gear Master tooltip source");
        var templates=new LinkedHashMap<String,String>();
        for(var row:tooltipData.templates()) {
            GearAffixDisplay.validateTemplate(row.playerTooltipTemplate());
            if(templates.putIfAbsent(row.id(),row.playerTooltipTemplate())!=null)
                throw new IllegalArgumentException("Duplicate player tooltip template "+row.id());
        }
        var b=new LinkedHashMap<String,Base>();
        for(var row:baseData.bases()) if(b.put(row.id(),row)!=null) throw new IllegalArgumentException("Duplicate base "+row.id());
        var a=new LinkedHashMap<String,Affix>();
        for(var row:affixData.affixes()) {
            var template=templates.remove(row.id());if(template==null)throw new IllegalArgumentException("Missing player tooltip template "+row.id());
            if(a.put(row.id(),row.withTooltip(template))!=null) throw new IllegalArgumentException("Duplicate affix "+row.id());
        }
        if(b.size()!=447 || a.size()!=160 || !templates.isEmpty()) throw new IllegalArgumentException("Incomplete Gear Master import or unexpected tooltip ID");
        for(int i=1;i<=160;i++) if(!a.containsKey((i<=158?"WA-":"GA-")+String.format(Locale.ROOT,"%03d",i)))
            throw new IllegalArgumentException("Missing authored affix "+i);
        bases=Collections.unmodifiableMap(b); affixes=Collections.unmodifiableMap(a);
    }
    public static GearCatalog load() { return new GearCatalog(read("bases-v1.json",Bases.class),read("affixes-v1.json",Affixes.class),
            read("affix-tooltips-v1.json",Tooltips.class)); }
    private static <T>T read(String file,Class<T> type) {
        try(var stream=GearCatalog.class.getResourceAsStream("/rpg/gear/"+file)) {
            if(stream==null) throw new IllegalStateException("Missing gear catalog "+file);
            return new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),type);
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public Base base(String id) { var value=bases.get(id); if(value==null) throw new IllegalArgumentException("Unknown base "+id); return value; }
    public Affix affix(String id) { var value=affixes.get(id); if(value==null) throw new IllegalArgumentException("Unknown affix "+id); return value; }
    public Collection<Base> bases() { return bases.values(); }
    public Collection<Affix> affixes() { return affixes.values(); }
    public List<Base> eligible(DifficultyId era,int combatLevel) { return bases.values().stream().filter(b->b.eligible(era,combatLevel)).toList(); }
}
