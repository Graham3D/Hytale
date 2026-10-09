package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Typed authoring backend. Validation cannot grant a source role, reward override or new attack. */
public final class SuperUniqueTemplates {
    public enum PlacementMode { ADMIN_PLACED_ANCHOR, EXISTING_ENCOUNTER_BINDING }
    public enum RespawnMode { REUSABLE, ONE_SHOT }
    public record Minions(String canonicalRoleId,int count,boolean countsForPackbound){
        public Minions{identifier(canonicalRoleId);require(count>=1&&count<=7,"TEMPLATE_MINION_COUNT");}
    }
    public record Placement(PlacementMode mode,String encounterBindingId,int maximumLivePerAnchor,double leashMeters){
        public Placement{Objects.requireNonNull(mode);require(maximumLivePerAnchor==1&&Double.isFinite(leashMeters)&&leashMeters>=.1&&leashMeters<=32,"TEMPLATE_PLACEMENT");
            require((mode==PlacementMode.EXISTING_ENCOUNTER_BINDING)==(encounterBindingId!=null),"TEMPLATE_BINDING_REQUIRED");if(encounterBindingId!=null)identifier(encounterBindingId);}
    }
    public record Respawn(RespawnMode mode,Integer delaySeconds){
        public Respawn{Objects.requireNonNull(mode);require(mode==RespawnMode.ONE_SHOT?delaySeconds==null:delaySeconds!=null&&delaySeconds>=300&&delaySeconds<=86400,"TEMPLATE_RESPAWN");}
    }
    public record Template(String id,int revision,String name,String canonicalRoleId,Set<DifficultyId> difficultyModes,
            double maxHealthFactor,double directDamageFactor,List<EnemyAffixSelection.Choice> fixedAffixes,
            List<Integer> randomAdditionalAffixes,List<Minions> minions,Map<DifficultyId,Set<String>> explicitImmuneChannelsByMode,
            String visualVariantId,String lootProfileOverrideId,Placement placement,Respawn respawn){
        public Template{
            require(id!=null&&id.matches("[a-z][a-z0-9_]{1,63}")&&revision>=1,"TEMPLATE_ID_OR_REVISION");
            identifier(canonicalRoleId);require(name!=null&&!name.isBlank()&&name.codePointCount(0,name.length())<=40
                    &&name.chars().noneMatch(Character::isISOControl),"TEMPLATE_NAME");
            var modes=EnumSet.noneOf(DifficultyId.class);modes.addAll(difficultyModes);require(!modes.isEmpty(),"TEMPLATE_ENABLED_MODES");difficultyModes=Collections.unmodifiableSet(modes);
            require(Double.isFinite(maxHealthFactor)&&maxHealthFactor>=.25&&maxHealthFactor<=10&&Double.isFinite(directDamageFactor)
                    &&directDamageFactor>=.5&&directDamageFactor<=2,"TEMPLATE_STAT_BOUNDS");
            fixedAffixes=List.copyOf(fixedAffixes);randomAdditionalAffixes=List.copyOf(randomAdditionalAffixes);minions=List.copyOf(minions);
            require(fixedAffixes.stream().map(EnemyAffixSelection.Choice::affixId).distinct().count()==fixedAffixes.size(),"TEMPLATE_DUPLICATE_AFFIX");
            require(randomAdditionalAffixes.size()==3,"TEMPLATE_DIFFICULTY_ARRAY");
            for(int n:randomAdditionalAffixes)require(n>=0&&n+fixedAffixes.size()<=4,"TEMPLATE_AFFIX_CAP");
            int total=minions.stream().mapToInt(Minions::count).sum();require(total<=7,"TEMPLATE_PACK_CAP");
            require(minions.stream().map(Minions::canonicalRoleId).distinct().count()==minions.size(),"TEMPLATE_DUPLICATE_MINION_ROLE");
            boolean packbound=fixedAffixes.stream().anyMatch(a->a.affixId().equals("ME-024"));
            int guards=minions.stream().filter(Minions::countsForPackbound).mapToInt(Minions::count).sum();
            require(!packbound||guards>=2&&guards<=5,"TEMPLATE_PACKBOUND_GUARD_COUNT");
            var immune=new EnumMap<DifficultyId,Set<String>>(DifficultyId.class);
            require(explicitImmuneChannelsByMode.keySet().equals(EnumSet.allOf(DifficultyId.class)),"TEMPLATE_IMMUNITY_MODES");
            explicitImmuneChannelsByMode.forEach((mode,channels)->{
                require(CHANNELS.containsAll(channels),"TEMPLATE_IMMUNITY_CHANNEL");
                require(!packbound||channels.stream().filter(c->!c.equals("PHYSICAL")).count()<=1,"PACKBOUND_TWO_ELEMENT_IMMUNITY");
                immune.put(mode,Collections.unmodifiableSet(new TreeSet<>(channels)));
            });explicitImmuneChannelsByMode=Collections.unmodifiableMap(immune);
            if(visualVariantId!=null)identifier(visualVariantId);if(lootProfileOverrideId!=null)identifier(lootProfileOverrideId);
            Objects.requireNonNull(placement);Objects.requireNonNull(respawn);
        }
        public int minionCount(){return minions.stream().mapToInt(Minions::count).sum();}
        public int affixCount(DifficultyId mode){return fixedAffixes.size()+randomAdditionalAffixes.get(mode.ordinal());}
        /** Concrete binding/accessibility proof is required by the publication caller, never inferred from the name. */
        public void validateBindings(EnemyAffixSelection selection,java.util.function.Function<String,EnemyAffixSelection.Binding> binding,
                java.util.function.BiPredicate<Template,DifficultyId> accessible,
                java.util.function.Predicate<String> existingVisual,java.util.function.Predicate<String> compatibleLoot){
            if(visualVariantId!=null)require(existingVisual.test(visualVariantId),"TEMPLATE_VISUAL_MISSING");
            if(lootProfileOverrideId!=null)require(compatibleLoot.test(lootProfileOverrideId),"TEMPLATE_LOOT_OVERRIDE_INCOMPATIBLE");
            var leader=Objects.requireNonNull(binding.apply(canonicalRoleId),"TEMPLATE_LEADER_BINDING_MISSING");
            require(leader.initialMinions()==minionCount(),"TEMPLATE_BINDING_ROSTER_MISMATCH");
            for(var minion:minions)require(binding.apply(minion.canonicalRoleId())!=null,"TEMPLATE_MINION_BINDING_MISSING");
            for(var mode:difficultyModes){
                require(accessible.test(this,mode),"TEMPLATE_ACCESSIBILITY_NOT_PROVEN:"+mode);
                for(var fixed:fixedAffixes){
                    var rejected=selection.rejectionReason(leader,EnemyRarity.SUPER_UNIQUE,fixed);
                    if(rejected.isPresent())throw new IllegalArgumentException(
                            "TEMPLATE_AFFIX_UNSUPPORTED:"+fixed.affixId()+":"+rejected.get());
                }
                var request=new EnemyAffixSelection.Request(leader,mode,EnemyRarity.SUPER_UNIQUE,affixCount(mode),fixedAffixes,false);
                require(selection.select(request,"template-validation/"+id+"/"+revision+"/"+mode).isPresent(),"TEMPLATE_NO_COMPLETE_AFFIX_SET");
            }
        }
    }
    private final String revision;private final Map<String,Template> templates;
    public SuperUniqueTemplates(JsonObject json){
        exact(json,Set.of("schemaVersion","revision","templates"));require(number(json.get("schemaVersion"),true,1)==1,"TEMPLATE_SCHEMA");revision=string(json,"revision");
        var result=new TreeMap<String,Template>();for(var value:json.getAsJsonArray("templates")){
            var template=parse(value.getAsJsonObject());require(result.putIfAbsent(template.id(),template)==null,"DUPLICATE_TEMPLATE");
        }require(!result.isEmpty()&&result.size()<=1024,"TEMPLATE_CATALOG_BOUNDS");templates=Collections.unmodifiableMap(result);
    }
    public String revision(){return revision;}
    public Collection<Template> all(){return templates.values();}
    public Template requireTemplate(String id){var template=templates.get(id);if(template==null)throw new IllegalArgumentException("UNKNOWN_SUPER_UNIQUE:"+id);return template;}
    public static SuperUniqueTemplates canonical(){return new SuperUniqueTemplates(resource("super-uniques-v1.json"));}
    private static Template parse(JsonObject json){
        var required=Set.of("id","revision","name","canonicalRoleId","difficultyModes","baseProfileMode","rarity","fixedAffixes","randomAdditionalAffixes",
                "minions","explicitImmuneChannelsByMode","visualVariantId","lootProfileOverrideId","placement","respawn","learningSourceMode");
        var allowed=new HashSet<>(required);allowed.addAll(Set.of("maxHealthFactor","directDamageFactor"));
        require(json.keySet().containsAll(required)&&allowed.containsAll(json.keySet()),"UNKNOWN_OR_MISSING_TEMPLATE_KEYS");
        require(string(json,"baseProfileMode").equals("INHERIT_ROLE")&&string(json,"rarity").equals("SUPER_UNIQUE")
                &&string(json,"learningSourceMode").equals("CANONICAL_ROLE"),"TEMPLATE_CANNOT_OVERRIDE_SOURCE_OWNERS");
        var fixed=new ArrayList<EnemyAffixSelection.Choice>();for(var value:json.getAsJsonArray("fixedAffixes")){
            var entry=value.getAsJsonObject();exact(entry,entry.has("selector")?Set.of("affixId","selector"):Set.of("affixId"));
            fixed.add(new EnemyAffixSelection.Choice(string(entry,"affixId"),entry.has("selector")?Selector.valueOf(string(entry,"selector")):null));
        }
        var minions=new ArrayList<Minions>();for(var value:json.getAsJsonArray("minions")){
            var entry=value.getAsJsonObject();exact(entry,Set.of("canonicalRoleId","count","countsForPackbound"));
            var guard=entry.get("countsForPackbound");require(guard.isJsonPrimitive()&&guard.getAsJsonPrimitive().isBoolean(),"TEMPLATE_GUARD_BOOLEAN");
            minions.add(new Minions(string(entry,"canonicalRoleId"),(int)number(entry.get("count"),true,7),guard.getAsBoolean()));
        }
        var channels=json.getAsJsonObject("explicitImmuneChannelsByMode");exact(channels,Set.of("NORMAL","NIGHTMARE","HELL"));
        var immune=new EnumMap<DifficultyId,Set<String>>(DifficultyId.class);for(var mode:DifficultyId.values()){
            var names=strings(channels.get(mode.name()));require(names.size()==new HashSet<>(names).size(),"TEMPLATE_DUPLICATE_IMMUNITY");immune.put(mode,new TreeSet<>(names));
        }
        var p=json.getAsJsonObject("placement");var pMode=PlacementMode.valueOf(string(p,"mode"));
        exact(p,pMode==PlacementMode.ADMIN_PLACED_ANCHOR?Set.of("mode","maximumLivePerAnchor","leashMeters"):Set.of("mode","maximumLivePerAnchor","leashMeters","encounterBindingId"));
        var placement=new Placement(pMode,pMode==PlacementMode.ADMIN_PLACED_ANCHOR?null:string(p,"encounterBindingId"),(int)number(p.get("maximumLivePerAnchor"),true,1),number(p.get("leashMeters"),false,32));
        var r=json.getAsJsonObject("respawn");var rMode=RespawnMode.valueOf(string(r,"mode"));
        exact(r,rMode==RespawnMode.ONE_SHOT?Set.of("mode"):Set.of("mode","delaySeconds"));
        var respawn=new Respawn(rMode,rMode==RespawnMode.ONE_SHOT?null:(int)number(r.get("delaySeconds"),true,86400));
        return new Template(string(json,"id"),(int)number(json.get("revision"),true,Integer.MAX_VALUE),string(json,"name"),string(json,"canonicalRoleId"),enums(json.get("difficultyModes"),DifficultyId.class),
                json.has("maxHealthFactor")?number(json.get("maxHealthFactor"),false,10):3,
                json.has("directDamageFactor")?number(json.get("directDamageFactor"),false,2):1.15,fixed,
                numbers(json.get("randomAdditionalAffixes"),true,3,4).stream().map(Double::intValue).toList(),minions,immune,
                optionalId(json,"visualVariantId"),optionalId(json,"lootProfileOverrideId"),placement,respawn);
    }
    private static String optionalId(JsonObject json,String key){return json.get(key).isJsonNull()?null:string(json,key);}
    private static void identifier(String value){require(value!=null&&!value.isBlank()&&value.length()<=256&&value.chars().noneMatch(Character::isISOControl),"INVALID_TEMPLATE_REFERENCE");}
}
