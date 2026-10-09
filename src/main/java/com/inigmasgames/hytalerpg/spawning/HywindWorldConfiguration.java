package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.enemies.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Validated, save-root operator policy. One immutable snapshot is published after full validation. */
public final class HywindWorldConfiguration implements SpawnDensitySetting {
    /** Hytale supplies plugin data paths relative to the active save's process directory. */
    public static Path resolveSaveRoot(Path pluginDataDirectory){
        return resolveSaveRoot(pluginDataDirectory,Path.of("").toAbsolutePath());
    }
    static Path resolveSaveRoot(Path pluginDataDirectory,Path processDirectory){
        Objects.requireNonNull(pluginDataDirectory);Objects.requireNonNull(processDirectory);
        var absolute=(pluginDataDirectory.isAbsolute()?pluginDataDirectory:processDirectory.resolve(pluginDataDirectory))
                .toAbsolutePath().normalize();
        var mods=absolute.getParent();
        if(mods==null||mods.getFileName()==null||!mods.getFileName().toString().equalsIgnoreCase("mods")
                ||!Files.isDirectory(mods))throw new IllegalStateException("RPG_SAVE_ROOT_UNAVAILABLE:"+absolute);
        var save=mods.getParent();
        if(save==null)throw new IllegalStateException("RPG_SAVE_ROOT_UNAVAILABLE:"+absolute);
        return save;
    }
    public record Snapshot(JsonObject source,String configRevision,double density,
                           NativePopulationBalance.Policy population,EnemyBalance enemyBalance,
                           Map<String,Double> affixWeightMultipliers,Set<String> randomAffixesDisabled) {
        public Snapshot{
            source=source.deepCopy();affixWeightMultipliers=Map.copyOf(affixWeightMultipliers);
            randomAffixesDisabled=Set.copyOf(randomAffixesDisabled);
        }
        public JsonObject export(){return source.deepCopy();}
    }
    private final Path path,archiveDirectory;
    private volatile Snapshot current;
    public HywindWorldConfiguration(Path saveRoot,Path legacyDensityFile){
        Objects.requireNonNull(saveRoot);Objects.requireNonNull(legacyDensityFile);
        path=saveRoot.resolve("world-config.json");
        archiveDirectory=legacyDensityFile.getParent().resolve("world-config-balance-history");
        EnemyBalance.loadArchives(archiveDirectory);
        if(!Files.exists(path)){
            var defaults=defaultJson();
            defaults.getAsJsonObject("world").addProperty("nativeEnvironmentSpawnMultiplier",
                    new WorldSpawnDensitySettings(legacyDensityFile).multiplier());
            write(defaults,false);
        }
        var initial=readCandidate();
        EnemyBalance.activate(initial.enemyBalance(),archiveDirectory);
        current=initial;
    }
    public Path path(){return path;}
    public Snapshot snapshot(){return current;}
    @Override public double multiplier(){return current.density();}
    /** /rpg spawns updates exactly the same save-root field as a manual operator edit. */
    @Override public synchronized void set(double density){
        WorldSpawnDensitySettings.validate(density);
        var onDisk=readJson();
        var old=current.export();
        onDisk.getAsJsonObject("world").addProperty("nativeEnvironmentSpawnMultiplier",old.getAsJsonObject("world")
                .get("nativeEnvironmentSpawnMultiplier").getAsDouble());
        if(!onDisk.equals(old))throw new IllegalStateException("WORLD_CONFIG_PENDING_EDIT_RELOAD_FIRST");
        var next=old.deepCopy();
        next.getAsJsonObject("world").addProperty("nativeEnvironmentSpawnMultiplier",density);
        var candidate=parse(next);
        if(candidate.density()==current.density())return;
        write(next,true);
        current=candidate;
    }
    /** On failure neither the active policy nor the density setting changes. */
    public synchronized Snapshot reload(){
        var candidate=readCandidate();
        EnemyBalance.activate(candidate.enemyBalance(),archiveDirectory);
        current=candidate;
        return candidate;
    }
    private Snapshot readCandidate(){return parse(readJson());}
    private JsonObject readJson(){
        try{return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(Exception error){throw new IllegalStateException("WORLD_CONFIG_READ_FAILED:"+path,error);}
    }
    private void write(JsonObject json,boolean backup){
        var temp=path.resolveSibling(path.getFileName()+".tmp");
        try{
            Files.createDirectories(path.getParent());
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(json)+"\n",StandardCharsets.UTF_8);
            if(backup&&Files.exists(path))Files.copy(path,path.resolveSibling("world-config.json.bak"),StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception error){throw new IllegalStateException("WORLD_CONFIG_WRITE_FAILED:"+path,error);}
    }
    private static JsonObject defaultJson(){
        try(var stream=HywindWorldConfiguration.class.getResourceAsStream("/rpg/world-config-default.json")){
            if(stream==null)throw new IllegalStateException("WORLD_CONFIG_DEFAULT_MISSING");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException error){throw new IllegalStateException("WORLD_CONFIG_DEFAULT_READ",error);}
    }
    public static Snapshot parse(JsonObject source){
        var json=source.deepCopy();
        exact(json,"", "schemaVersion","configRevision","world","spawnPopulationBalance","monsterRarity",
                "packs","combat","monsterAffixes","newImmunityGrants","rewards");
        if(integer(json,"schemaVersion",1,1)!=1)throw new IllegalArgumentException("WORLD_CONFIG_SCHEMA");
        String revision=text(json,"configRevision");
        if(!revision.matches("[A-Za-z0-9._-]{1,80}"))throw new IllegalArgumentException("WORLD_CONFIG_REVISION");
        var world=obj(json,"world");exact(world,"world","nativeEnvironmentSpawnMultiplier");
        double density=number(world,"nativeEnvironmentSpawnMultiplier",.25,8);
        var population=obj(json,"spawnPopulationBalance");
        exact(population,"spawnPopulationBalance","enabled","mode","scope","hostileTargetShare","wildlifeTargetShare",
                "excludePassiveAvians","excludeAquaticWildlife","preserveNativePopulationCaps",
                "preserveNativeSpeciesWeights","countExpectedFlockMembers","forceDespawnToTarget");
        if(!text(population,"mode").equals("NATIVE_WEIGHTED_SELECTION")
                ||!text(population,"scope").equals("PER_ENVIRONMENT"))
            throw new IllegalArgumentException("UNSUPPORTED_POPULATION_BALANCE_MODE");
        for(String key:List.of("excludePassiveAvians","excludeAquaticWildlife","preserveNativePopulationCaps",
                "preserveNativeSpeciesWeights","countExpectedFlockMembers"))requiredFlag(population,key,true);
        requiredFlag(population,"forceDespawnToTarget",false);
        var policy=new NativePopulationBalance.Policy(flag(population,"enabled"),
                number(population,"hostileTargetShare",.01,.99),number(population,"wildlifeTargetShare",.01,.99));
        var rarity=obj(json,"monsterRarity");exact(rarity,"monsterRarity","enabledForNewSpawns","perEra");
        boolean enabled=flag(rarity,"enabledForNewSpawns");
        var eras=obj(rarity,"perEra");exact(eras,"monsterRarity.perEra","NORMAL","NIGHTMARE","HELL");
        var pack=obj(json,"packs");
        exact(pack,"packs","championMembersMin","championMembersMax","uniqueMinionsMin","uniqueMinionsMax",
                "maxMembersPerPack","maxActiveSpecialPacksPerLoadedWorld","maxSpecialPacksPer64mCell");
        var combat=obj(json,"combat");exact(combat,"combat","rarityStatFactors");
        var stats=obj(combat,"rarityStatFactors");exact(stats,"combat.rarityStatFactors","CHAMPION","UNIQUE","SUPER_UNIQUE");
        var affixes=obj(json,"monsterAffixes");
        exact(affixes,"monsterAffixes","randomWeightMultipliers","randomlyEligibleAffixesDisabled");
        var multipliers=new TreeMap<String,Double>();
        obj(affixes,"randomWeightMultipliers").entrySet().forEach(entry->{
            validAffix(entry.getKey());
            double value=scalar(entry.getValue(),0,10,"monsterAffixes.randomWeightMultipliers."+entry.getKey());
            multipliers.put(entry.getKey(),value);
        });
        var disabled=new TreeSet<String>();
        for(var entry:arr(affixes,"randomlyEligibleAffixesDisabled")){
            String id=entry.getAsString();validAffix(id);
            if(!disabled.add(id))throw new IllegalArgumentException("DUPLICATE_DISABLED_AFFIX:"+id);
        }
        var immunities=obj(json,"newImmunityGrants");exact(immunities,"newImmunityGrants","perEra");
        var immunityEras=obj(immunities,"perEra");exact(immunityEras,"newImmunityGrants.perEra","NORMAL","NIGHTMARE","HELL");
        var rewards=obj(json,"rewards");
        exact(rewards,"rewards","xpBonusPerAffixBeyondFirst","equipmentQuantityBonusPerAffixBeyondFirst");
        var balanceJson=EnemyBalance.base().export();
        var promotion=balanceJson.getAsJsonObject("promotion");
        var weights=promotion.getAsJsonObject("weightsByDifficulty");
        var rarityStats=balanceJson.getAsJsonObject("rarities");
        var immunity=balanceJson.getAsJsonObject("immunity");
        var affinity=immunity.getAsJsonArray("nativeAffinityGrantChance");
        var affixImmunity=immunity.getAsJsonArray("affixGrantChance");
        for(var era:DifficultyId.values()){
            var eraJson=obj(eras,era.name());
            exact(eraJson,"monsterRarity.perEra."+era,"championChance","uniqueChance","uniqueAffixCount","superUniqueAdditionalAffixes");
            int champion=thousandths(number(eraJson,"championChance",0,1));
            int unique=thousandths(number(eraJson,"uniqueChance",0,1));
            if(champion+unique>1000)throw new IllegalArgumentException("RARITY_CHANCES_EXCEED_ONE:"+era);
            var row=obj(weights,era.name());
            row.addProperty("NORMAL",enabled?1000-champion-unique:1000);
            row.addProperty("CHAMPION",enabled?champion:0);
            row.addProperty("UNIQUE",enabled?unique:0);
            rarityStats.getAsJsonObject("UNIQUE").getAsJsonArray("affixes").set(era.ordinal(),
                    new JsonPrimitive(integer(eraJson,"uniqueAffixCount",1,4)));
            rarityStats.getAsJsonObject("SUPER_UNIQUE").getAsJsonArray("additionalAffixes").set(era.ordinal(),
                    new JsonPrimitive(integer(eraJson,"superUniqueAdditionalAffixes",0,4)));
            var grant=obj(immunityEras,era.name());
            exact(grant,"newImmunityGrants.perEra."+era,"nativeAffinityChance","elementalAffixChance");
            affinity.set(era.ordinal(),new JsonPrimitive(number(grant,"nativeAffinityChance",0,1)));
            affixImmunity.set(era.ordinal(),new JsonPrimitive(number(grant,"elementalAffixChance",0,1)));
        }
        promotion.add("championMembers",pair(integer(pack,"championMembersMin",2,4),integer(pack,"championMembersMax",2,4)));
        promotion.add("uniqueMinions",pair(integer(pack,"uniqueMinionsMin",3,5),integer(pack,"uniqueMinionsMax",3,5)));
        promotion.addProperty("maximumPackMembers",integer(pack,"maxMembersPerPack",3,8));
        promotion.addProperty("specialPackLimitPerLoadedWorld",integer(pack,"maxActiveSpecialPacksPerLoadedWorld",1,12));
        promotion.addProperty("specialPackLimitPerHorizontalCell",integer(pack,"maxSpecialPacksPer64mCell",1,3));
        for(String name:List.of("CHAMPION","UNIQUE","SUPER_UNIQUE")){
            var factor=obj(stats,name);exact(factor,"combat.rarityStatFactors."+name,"health","directDamage");
            var target=rarityStats.getAsJsonObject(name);
            target.addProperty("maxHealth",number(factor,"health",.25,10));
            target.addProperty("directDamage",number(factor,"directDamage",.5,2));
        }
        var extras=balanceJson.getAsJsonObject("affixRewards");
        extras.addProperty("xpPerAdditionalAffix",number(rewards,"xpBonusPerAffixBeyondFirst",0,1));
        extras.addProperty("quantityPerAdditionalAffix",number(rewards,"equipmentQuantityBonusPerAffixBeyondFirst",0,1));
        boolean modified=!balanceJson.equals(EnemyBalance.base().export())||!multipliers.isEmpty()||!disabled.isEmpty();
        if(modified){
            balanceJson.addProperty("revision","me-world-"+sha256(balanceJson.toString()+multipliers+disabled).substring(0,24));
        }
        var balance=modified?new EnemyBalance(balanceJson):EnemyBalance.base();
        return new Snapshot(json,revision,density,policy,balance,multipliers,disabled);
    }
    private static JsonArray pair(int first,int last){var value=new JsonArray();value.add(first);value.add(last);return value;}
    private static int thousandths(double value){
        double scaled=value*1000;
        if(Math.abs(scaled-Math.rint(scaled))>1e-8)throw new IllegalArgumentException("RARITY_CHANCE_RESOLUTION_0.001");
        return (int)Math.rint(scaled);
    }
    private static String sha256(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        }catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    private static void validAffix(String id){
        if(!id.matches("ME-0(0[1-9]|1[0-9]|2[0-7])"))throw new IllegalArgumentException("UNKNOWN_RANDOM_AFFIX:"+id);
    }
    private static void exact(JsonObject object,String path,String... keys){
        var allowed=Set.of(keys);
        for(var entry:object.entrySet())if(!allowed.contains(entry.getKey()))
            throw new IllegalArgumentException("UNSUPPORTED_CONFIG_CONTROL:"+(path.isEmpty()?"":path+".")+entry.getKey());
        for(String key:keys)if(!object.has(key))throw new IllegalArgumentException("MISSING_CONFIG_CONTROL:"+(path.isEmpty()?"":path+".")+key);
    }
    private static JsonObject obj(JsonObject parent,String key){
        try{return parent.getAsJsonObject(key);}catch(RuntimeException error){throw new IllegalArgumentException("CONFIG_OBJECT_REQUIRED:"+key,error);}
    }
    private static JsonArray arr(JsonObject parent,String key){
        try{return parent.getAsJsonArray(key);}catch(RuntimeException error){throw new IllegalArgumentException("CONFIG_ARRAY_REQUIRED:"+key,error);}
    }
    private static String text(JsonObject parent,String key){
        try{var value=parent.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException();return value.getAsString();}
        catch(RuntimeException error){throw new IllegalArgumentException("CONFIG_TEXT_REQUIRED:"+key,error);}
    }
    private static boolean flag(JsonObject parent,String key){
        try{var value=parent.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();return value.getAsBoolean();}
        catch(RuntimeException error){throw new IllegalArgumentException("CONFIG_BOOLEAN_REQUIRED:"+key,error);}
    }
    private static void requiredFlag(JsonObject parent,String key,boolean expected){
        if(flag(parent,key)!=expected)throw new IllegalArgumentException("UNSUPPORTED_CONFIG_CONTROL:spawnPopulationBalance."+key);
    }
    private static double number(JsonObject parent,String key,double min,double max){return scalar(parent.get(key),min,max,key);}
    private static double scalar(JsonElement value,double min,double max,String key){
        try{
            if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();
            double number=value.getAsDouble();
            if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException();
            return number;
        }catch(RuntimeException error){throw new IllegalArgumentException("CONFIG_NUMBER_OUT_OF_RANGE:"+key+" expected="+min+".."+max,error);}
    }
    private static int integer(JsonObject parent,String key,int min,int max){
        double value=number(parent,key,min,max);
        if(value!=Math.rint(value))throw new IllegalArgumentException("CONFIG_INTEGER_REQUIRED:"+key);
        return (int)value;
    }
}
