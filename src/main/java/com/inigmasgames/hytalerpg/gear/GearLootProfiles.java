package com.inigmasgames.hytalerpg.gear;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Frozen quantity policy. Quality and Magic Find are deliberately absent from this owner. */
public final class GearLootProfiles {
    public record Profile(String id,int guaranteedPicks,List<Double> optionalPickChances,int maxEquipment) {
        public Profile {
            if(optionalPickChances==null)throw new IllegalArgumentException("Missing optional equipment picks");
            optionalPickChances=List.copyOf(optionalPickChances);
            int picks=guaranteedPicks+optionalPickChances.size();
            if(id==null||id.isBlank()||guaranteedPicks<0||picks<1||picks>8
                    ||maxEquipment<1||maxEquipment<guaranteedPicks||maxEquipment>picks
                    ||optionalPickChances.stream().anyMatch(chance->!Double.isFinite(chance)||chance<0||chance>1))
                throw new IllegalArgumentException("Invalid equipment loot profile");
        }
        public int picks(){return guaranteedPicks+optionalPickChances.size();}
    }
    private record Data(String revision,Map<String,Profile> rankDefaults,Map<String,Profile> roleOverrides) {}
    public record Pick(int index,boolean guaranteed,Double optionalChance,Double optionalRoll,
                       boolean opportunity,String childEventId,String seed) {}
    public record Decision(String profileId,String revision,String canonicalRole,ProgressionMath.Rank rank,
                           int guaranteedPicks,List<Double> optionalPickChances,int maxEquipment,List<Pick> rolls) {
        public Decision {optionalPickChances=List.copyOf(optionalPickChances);rolls=List.copyOf(rolls);}
        public int picks(){return rolls.size();}
        public long succeeded(){return rolls.stream().filter(Pick::opportunity).count();}
    }
    public static final GearLootProfiles CURRENT=load();
    private final Data data;
    private final Map<String,String> canonicalByRuntime;
    private GearLootProfiles(Data data,Map<String,String> canonicalByRuntime){
        this.data=data;this.canonicalByRuntime=Map.copyOf(canonicalByRuntime);
        if(data==null||data.revision()==null||data.revision().isBlank()||data.rankDefaults()==null||data.roleOverrides()==null)
            throw new IllegalArgumentException("Invalid loot profile catalog");
        for(var rank:ProgressionMath.Rank.values())Objects.requireNonNull(data.rankDefaults().get(rank.name()),"Missing "+rank);
        for(var entry:data.roleOverrides().entrySet()){
            String key=entry.getKey();int separator=key.indexOf('@');String role=separator<0?key:key.substring(0,separator);
            if(!canonicalByRuntime.containsValue(role)||separator>=0&&!Arrays.stream(ProgressionMath.Rank.values())
                    .anyMatch(rank->rank.name().equals(key.substring(separator+1))))
                throw new IllegalArgumentException("Unknown canonical enemy role/rank "+key);
        }
    }
    private static GearLootProfiles load(){
        try(var input=GearLootProfiles.class.getResourceAsStream("/rpg/gear/loot-profiles-v1.json")){
            if(input==null)throw new IllegalStateException("Missing equipment loot profiles");
            var data=new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),Data.class);
            var registry=EnemyRewardRegistry.load();var canonical=new HashMap<String,String>();
            for(var role:registry.roles())canonical.put(role.roleId(),role.combatIdentity());
            for(var alias:registry.aliases())registry.resolveRole(alias.roleId()).ifPresent(resolved->
                    canonical.put(alias.roleId(),resolved.canonical().combatIdentity()));
            return new GearLootProfiles(data,canonical);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    public String revision(){return data.revision();}
    public Profile rank(ProgressionMath.Rank rank){return data.rankDefaults().get(rank.name());}
    public String canonicalRole(String role){return canonicalByRuntime.getOrDefault(role,role);}
    public Profile resolve(String role,ProgressionMath.Rank rank){
        String canonical=canonicalRole(role);
        return data.roleOverrides().getOrDefault(canonical+"@"+rank.name(),
                data.roleOverrides().getOrDefault(canonical,rank(rank)));
    }
    public Decision decide(String role,ProgressionMath.Rank rank,String eventId){
        var profile=resolve(role,rank);var random=new GearRandom(eventId);
        var picks=new ArrayList<Pick>();int successes=0;
        for(int i=1;i<=profile.picks();i++){
            boolean guaranteed=i<=profile.guaranteedPicks();
            Double chance=guaranteed?null:profile.optionalPickChances().get(i-profile.guaranteedPicks()-1);
            Double roll=guaranteed?null:random.stream("loot-optional-pick/"+i).nextDouble();
            boolean opportunity=guaranteed||successes<profile.maxEquipment()&&roll<chance;
            if(opportunity)successes++;
            picks.add(new Pick(i,guaranteed,chance,roll,opportunity,eventId+"/gear-pick/"+i,
                    "loot-profile/"+data.revision()+"/"+eventId+"/pick/"+i));
        }
        return new Decision(profile.id(),data.revision(),canonicalRole(role),rank,profile.guaranteedPicks(),
                profile.optionalPickChances(),profile.maxEquipment(),picks);
    }
}
