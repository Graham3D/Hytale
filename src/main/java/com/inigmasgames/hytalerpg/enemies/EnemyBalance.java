package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Immutable, strictly validated common ME policy; consumers use one frozen revision. */
public final class EnemyBalance {
    public record Rarity(double maxHealth,double directDamage,double xp,double quantity,double learn,double learnCap,List<Integer> counts){
        public Rarity{counts=List.copyOf(counts);}
    }
    public record Promotion(Map<DifficultyId,Map<EnemyRarity,Integer>> weightsByDifficulty,int championMinimum,int championMaximum,int minionMinimum,int minionMaximum,
            int maximumMembers,int worldLimit,int cellLimit,double cellMeters,double leashMeters,double minimumDistance,double maximumDistance){
        public Promotion{
            var frozen=new EnumMap<DifficultyId,Map<EnemyRarity,Integer>>(DifficultyId.class);
            weightsByDifficulty.forEach((difficulty,weights)->frozen.put(difficulty,Collections.unmodifiableMap(new EnumMap<>(weights))));
            weightsByDifficulty=Collections.unmodifiableMap(frozen);
        }
        public Map<EnemyRarity,Integer> weights(DifficultyId difficulty){return Objects.requireNonNull(weightsByDifficulty.get(difficulty));}
    }
    public record Caps(double movementMultiplier,double recoveryMultiplier,double allDirectIncrease,double physicalIncrease,
            double extraElementalFraction,double defenseIncrease,double maxHealthIncrease){}
    private final String revision;
    private final Map<EnemyRarity,Rarity> rarities;
    private final Rarity minion;
    private final Promotion promotion;
    private final Caps caps;
    private final double extraXp,extraQuantity;
    private final List<Double> affinityChance,affixChance;
    private final Map<EnemyRarity,List<Integer>> immunityCaps;
    private final JsonObject frozen;
    public EnemyBalance(JsonObject input){
        var json=input.deepCopy();
        exact(json,Set.of("schemaVersion","revision","difficultyOrder","promotion","rarities","relationships","affixRewards","loot","learning","immunity",
                "aggregateCaps","packRecovery","presentation","trace","qa","permissions"));
        require(number(json.get("schemaVersion"),true,1)==1,"ME_BALANCE_SCHEMA");revision=string(json,"revision");
        require(strings(json.get("difficultyOrder")).equals(List.of("NORMAL","NIGHTMARE","HELL")),"DIFFICULTY_ORDER");
        var p=json.getAsJsonObject("promotion");
        exact(p,Set.of("weightsByDifficulty","championMembers","uniqueMinions","maximumPackMembers","specialPackLimitPerLoadedWorld","specialPackLimitPerHorizontalCell",
                "horizontalCellMeters","ordinaryLeashMeters","minimumMinionSpawnDistanceMeters","maximumMinionSpawnDistanceMeters","promoteNativeBosses","promoteNeutralWithoutExplicitBinding"));
        flag(p,"promoteNativeBosses",false);flag(p,"promoteNeutralWithoutExplicitBinding",false);
        var rows=p.getAsJsonObject("weightsByDifficulty");exact(rows,Set.of("NORMAL","NIGHTMARE","HELL"));
        var chances=new EnumMap<DifficultyId,Map<EnemyRarity,Integer>>(DifficultyId.class);
        for(var difficulty:DifficultyId.values()){
            var row=rows.getAsJsonObject(difficulty.name());exact(row,Set.of("NORMAL","CHAMPION","UNIQUE"));
            var weights=new EnumMap<EnemyRarity,Integer>(EnemyRarity.class);
            for(var rarity:List.of(EnemyRarity.NORMAL,EnemyRarity.CHAMPION,EnemyRarity.UNIQUE))
                weights.put(rarity,(int)number(row.get(rarity.name()),true,1000));
            require(weights.values().stream().mapToInt(Integer::intValue).sum()==1000
                    &&weights.values().stream().allMatch(value->value>=0),
                    "PROMOTION_DIFFICULTY_WEIGHT_CONTRACT:"+difficulty);
            chances.put(difficulty,weights);
        }
        var champions=numbers(p.get("championMembers"),true,2,4);var minions=numbers(p.get("uniqueMinions"),true,2,5);
        require(champions.get(0)>=2&&champions.get(0)<=champions.get(1)&&minions.get(0)>=3&&minions.get(0)<=minions.get(1),"PROMOTION_SIZE_RANGE");
        promotion=new Promotion(chances,champions.get(0).intValue(),champions.get(1).intValue(),minions.get(0).intValue(),minions.get(1).intValue(),
                integer(p,"maximumPackMembers",1,8),integer(p,"specialPackLimitPerLoadedWorld",1,12),integer(p,"specialPackLimitPerHorizontalCell",1,3),
                bounded(p,"horizontalCellMeters",1,1024),bounded(p,"ordinaryLeashMeters",.1,32),bounded(p,"minimumMinionSpawnDistanceMeters",.1,32),bounded(p,"maximumMinionSpawnDistanceMeters",.1,32));
        require(promotion.maximumMembers()>=1+promotion.minionMaximum()&&promotion.minimumDistance()<=promotion.maximumDistance(),"PROMOTION_CAPACITY_OR_DISTANCE");
        var r=json.getAsJsonObject("rarities");exact(r,Set.of("NORMAL","CHAMPION","UNIQUE","SUPER_UNIQUE","BOSS"));
        var resolved=new EnumMap<EnemyRarity,Rarity>(EnemyRarity.class);
        for(var rarity:EnemyRarity.values()){
            String count=rarity==EnemyRarity.SUPER_UNIQUE?"additionalAffixes":rarity==EnemyRarity.BOSS?"automaticAffixes":"affixes";
            var values=r.getAsJsonObject(rarity.name());exact(values,Set.of("maxHealth","directDamage","xp","quantity","learn","learnCap",count));
            resolved.put(rarity,rarity(values,numbers(values.get(count),true,3,4).stream().map(Double::intValue).toList()));
        }
        require(resolved.get(EnemyRarity.NORMAL).counts().equals(List.of(0,0,0))&&resolved.get(EnemyRarity.CHAMPION).counts().equals(List.of(1,1,1))
                &&resolved.get(EnemyRarity.UNIQUE).counts().stream().allMatch(count->count>=1&&count<=4)
                &&resolved.get(EnemyRarity.SUPER_UNIQUE).counts().stream().allMatch(count->count>=0&&count<=4)
                &&resolved.get(EnemyRarity.BOSS).counts().equals(List.of(0,0,0)),"RARITY_AFFIX_COUNT_CONTRACT");
        require(resolved.get(EnemyRarity.BOSS).maxHealth()==1&&resolved.get(EnemyRarity.BOSS).directDamage()==1
                &&resolved.get(EnemyRarity.BOSS).xp()==1&&resolved.get(EnemyRarity.BOSS).quantity()==1,"NO_SECOND_BOSS_MULTIPLIER");
        rarities=Collections.unmodifiableMap(resolved);
        var relations=json.getAsJsonObject("relationships");exact(relations,Set.of("MINION"));var m=relations.getAsJsonObject("MINION");
        exact(m,Set.of("maxHealth","directDamage","xp","quantity","learn","learnCap","ownAffixCount"));
        require(number(m.get("ownAffixCount"),true,0)==0,"MINION_OWN_AFFIXES");minion=rarity(m,List.of(0,0,0));
        var supplement=json.getAsJsonObject("affixRewards");exact(supplement,Set.of("firstCountAtBaseline","maximumCount","xpPerAdditionalAffix","quantityPerAdditionalAffix","learnPerAdditionalAffix"));
        require(number(supplement.get("firstCountAtBaseline"),true,1)==1&&number(supplement.get("maximumCount"),true,4)==4
                &&number(supplement.get("learnPerAdditionalAffix"),false,0)==0,"AFFIX_REWARD_CONTRACT");
        extraXp=bounded(supplement,"xpPerAdditionalAffix",0,1);extraQuantity=bounded(supplement,"quantityPerAdditionalAffix",0,1);
        var loot=json.getAsJsonObject("loot");exact(loot,Set.of("quantityMode","maximumEquipmentPerDefeat","modifyQualityWeights","multiplyNativeMaterials"));
        require(string(loot,"quantityMode").equals("BONUS_FROM_FINALIZED_BASE_EQUIPMENT_SLOTS")&&integer(loot,"maximumEquipmentPerDefeat",16,16)==16,"LOOT_SLOT_CONTRACT");
        flag(loot,"modifyQualityWeights",false);flag(loot,"multiplyNativeMaterials",false);
        var learning=json.getAsJsonObject("learning");exact(learning,Set.of("retainCanonicalSourceMapping","retainWisdomFormula","retainPity","ordinaryChanceCap","bonusMode","allowAdditionalAbilityPerSource"));
        for(String key:List.of("retainCanonicalSourceMapping","retainWisdomFormula","retainPity"))flag(learning,key,true);
        flag(learning,"allowAdditionalAbilityPerSource",false);require(bounded(learning,"ordinaryChanceCap",0,1)==.95&&string(learning,"bonusMode").equals("MIN_RELATIVE_BONUS_AND_RARITY_POINT_CAP"),"LEARNING_CONTRACT");
        var immunity=json.getAsJsonObject("immunity");exact(immunity,Set.of("ordinaryElementalResistanceCap","ordinaryPenetrationBreaksImmunity","nativeAffinityGrantChance",
                "affixGrantChance","affixGrantRarities","newElementalCaps","randomPhysicalImmunity","preserveExistingAuthoredNativeImmunities"));
        require(bounded(immunity,"ordinaryElementalResistanceCap",0,1)==.75,"RESISTANCE_CAP_CONTRACT");
        flag(immunity,"ordinaryPenetrationBreaksImmunity",false);flag(immunity,"randomPhysicalImmunity",false);flag(immunity,"preserveExistingAuthoredNativeImmunities",true);
        affinityChance=numbers(immunity.get("nativeAffinityGrantChance"),false,3,1);affixChance=numbers(immunity.get("affixGrantChance"),false,3,1);
        require(affinityChance.getFirst()==0&&affixChance.getFirst()==0,"NORMAL_RANDOM_IMMUNITY_FORBIDDEN");
        require(enums(immunity.get("affixGrantRarities"),EnemyRarity.class).equals(Set.of(EnemyRarity.UNIQUE,EnemyRarity.SUPER_UNIQUE)),"AFFIX_IMMUNITY_RARITIES");
        var limits=immunity.getAsJsonObject("newElementalCaps");exact(limits,Set.of("NORMAL","CHAMPION","UNIQUE","SUPER_UNIQUE"));
        var immuneCaps=new EnumMap<EnemyRarity,List<Integer>>(EnemyRarity.class);
        for(var entry:limits.entrySet()){
            var cap=numbers(entry.getValue(),true,3,2).stream().map(Double::intValue).toList();
            if(entry.getKey().equals("NORMAL")||entry.getKey().equals("CHAMPION"))require(cap.get(2)<=1,"ORDINARY_HELL_IMMUNITY_CAP");
            require(cap.get(0)==0&&cap.get(1)<=1,"RANDOM_IMMUNITY_MODE_CAP");immuneCaps.put(EnemyRarity.valueOf(entry.getKey()),cap);
        }immunityCaps=Collections.unmodifiableMap(immuneCaps);
        var a=json.getAsJsonObject("aggregateCaps");exact(a,Set.of("movementMultiplier","recoveryRateMultiplier","allDirectIncrease","physicalDirectIncrease","extraElementalPowerFraction","defenseIncrease","nonRarityMaxHealthIncrease"));
        caps=new Caps(bounded(a,"movementMultiplier",1,1.5),bounded(a,"recoveryRateMultiplier",1,1.5),bounded(a,"allDirectIncrease",.000001,.6),
                bounded(a,"physicalDirectIncrease",.000001,.6),bounded(a,"extraElementalPowerFraction",.000001,.3),bounded(a,"defenseIncrease",.000001,1),bounded(a,"nonRarityMaxHealthIncrease",.000001,.5));
        var recovery=json.getAsJsonObject("packRecovery");exact(recovery,Set.of("rosterCheckIntervalMs","strayTimeoutMs","navigationFailureTimeoutMs","loadedRecoveryTimeoutMs","missingGuardCountsAsDefeated"));
        for(String key:List.of("rosterCheckIntervalMs","strayTimeoutMs","navigationFailureTimeoutMs","loadedRecoveryTimeoutMs"))integer(recovery,key,1,60000);
        flag(recovery,"missingGuardCountsAsDefeated",false);
        var presentation=json.getAsJsonObject("presentation");exact(presentation,Set.of("targetCardAllTagsRequired","targetCardWidth","targetCardMaxWidth","viewportMargin","padding","tagGap","titleFontSize","tagFontSize","maxTitleLines","generatedNameMaxCodepoints","authoredNameMaxCodepoints","routineStateUpdatesPerSecond","overheadMode"));
        flag(presentation,"targetCardAllTagsRequired",true);require(string(presentation,"overheadMode").equals("SUPPORTED_TEXT_IDENTIFIER"),"OVERHEAD_SURFACE");
        for(var entry:presentation.entrySet())if(!Set.of("targetCardAllTagsRequired","overheadMode").contains(entry.getKey()))number(entry.getValue(),true,1024);
        require(integer(presentation,"routineStateUpdatesPerSecond",1,4)<=4&&integer(presentation,"generatedNameMaxCodepoints",1,32)<=32
                &&integer(presentation,"authoredNameMaxCodepoints",1,40)<=40&&integer(presentation,"maxTitleLines",1,2)<=2,"PRESENTATION_BOUNDS");
        var trace=json.getAsJsonObject("trace");exact(trace,Set.of("detailedMaximumMillis","detailedMaximumEvents"));integer(trace,"detailedMaximumMillis",1,30000);integer(trace,"detailedMaximumEvents",1,10000);
        var qa=json.getAsJsonObject("qa");exact(qa,Set.of("marker","requiresCopiedSaveGuard","requiresActiveRpgSave","qaEconomicRewards"));
        require(string(qa,"marker").equals("enemy-qa-enabled"),"QA_MARKER");flag(qa,"qaEconomicRewards",false);
        // Current owner QA workflow supersedes the document's older copied-save requirement.
        flag(qa,"requiresCopiedSaveGuard",false);flag(qa,"requiresActiveRpgSave",true);
        var permissions=json.getAsJsonObject("permissions");exact(permissions,Set.of("author"));require(string(permissions,"author").equals("inigmasgames.rpg.enemies.author"),"AUTHOR_PERMISSION");
        frozen=json;
    }
    public String revision(){return revision;}
    public Promotion promotion(){return promotion;}
    public Caps caps(){return caps;}
    public Rarity rarity(EnemyRarity rarity,boolean isMinion){return isMinion?minion:rarities.get(rarity);}
    public double affinityChance(DifficultyId mode){return affinityChance.get(mode.ordinal());}
    public double affixChance(DifficultyId mode){return affixChance.get(mode.ordinal());}
    public int immunityCap(EnemyRarity rarity,DifficultyId mode){var value=immunityCaps.get(rarity);if(value==null)throw new IllegalArgumentException("AUTHORED_BOSS_HAS_NO_RANDOM_IMMUNITY_BUDGET");return value.get(mode.ordinal());}
    public JsonObject export(){return frozen.deepCopy();}
    public EnemyRewardContext rewards(EnemyRarity rarity,EnemyRewardContext.Origin origin,boolean isMinion,int count){
        var values=rarity(rarity,isMinion);return new EnemyRewardContext(revision,rarity,origin,isMinion,count,values.xp(),values.quantity(),values.learn(),values.learnCap(),extraXp,extraQuantity);
    }
    private static final class Canonical{
        private static final EnemyBalance BASE=new EnemyBalance(resource("master-enemies-v1.json"));
        private static final java.util.concurrent.ConcurrentMap<String,EnemyBalance> BY_REVISION=new java.util.concurrent.ConcurrentHashMap<>();
        private static volatile EnemyBalance active=BASE;
        static{BY_REVISION.put(BASE.revision(),BASE);}
    }
    public static EnemyBalance base(){return Canonical.BASE;}
    public static EnemyBalance canonical(){return Canonical.active;}
    public static EnemyBalance forRevision(String revision){
        var value=Canonical.BY_REVISION.get(revision);
        if(value==null)throw new IllegalStateException("UNKNOWN_ENEMY_BALANCE_REVISION:"+revision);
        return value;
    }
    /** Archive a validated policy before publication; old descriptors keep their exact birth contract. */
    public static synchronized void activate(EnemyBalance candidate,java.nio.file.Path archiveDirectory){
        Objects.requireNonNull(candidate);Objects.requireNonNull(archiveDirectory);
        var file=archiveDirectory.resolve(candidate.revision()+".json");
        if(!candidate.revision().matches("[A-Za-z0-9._-]{1,96}"))throw new IllegalArgumentException("BALANCE_REVISION_FILENAME");
        try{
            java.nio.file.Files.createDirectories(archiveDirectory);
            if(java.nio.file.Files.exists(file)){
                var old=JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
                if(!old.equals(candidate.export()))throw new IllegalStateException("BALANCE_REVISION_COLLISION");
            }else{
                var temp=file.resolveSibling(file.getFileName()+".tmp");
                java.nio.file.Files.writeString(temp,candidate.export().toString(),java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.move(temp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
        }catch(java.io.IOException error){throw new IllegalStateException("BALANCE_ARCHIVE_UNAVAILABLE",error);}
        Canonical.BY_REVISION.put(candidate.revision(),candidate);Canonical.active=candidate;
    }
    public static synchronized void loadArchives(java.nio.file.Path directory){
        if(!java.nio.file.Files.exists(directory))return;
        try(var files=java.nio.file.Files.list(directory)){
            for(var path:files.filter(p->p.getFileName().toString().endsWith(".json")).toList()){
                var balance=new EnemyBalance(JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject());
                if(!path.getFileName().toString().equals(balance.revision()+".json"))
                    throw new IllegalStateException("BALANCE_ARCHIVE_IDENTITY:"+path);
                var existing=Canonical.BY_REVISION.putIfAbsent(balance.revision(),balance);
                if(existing!=null&&!existing.export().equals(balance.export()))throw new IllegalStateException("BALANCE_ARCHIVE_COLLISION");
            }
        }catch(java.io.IOException error){throw new IllegalStateException("BALANCE_ARCHIVE_READ",error);}
    }
    private static Rarity rarity(JsonObject values,List<Integer> count){return new Rarity(bounded(values,"maxHealth",.25,10),bounded(values,"directDamage",.5,2),
            bounded(values,"xp",1,10),bounded(values,"quantity",1,10),bounded(values,"learn",1,10),bounded(values,"learnCap",0,.1),count);}
    private static double bounded(JsonObject object,String key,double min,double max){double n=number(object.get(key),false,max);require(n>=min,"BELOW_MINIMUM:"+key);return n;}
    private static int integer(JsonObject object,String key,int min,int max){double n=number(object.get(key),true,max);require(n>=min,"BELOW_MINIMUM:"+key);return (int)n;}
    private static void flag(JsonObject object,String key,boolean expected){var value=object.get(key);require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean()&&value.getAsBoolean()==expected,"INVARIANT_BOOLEAN:"+key);}
}
