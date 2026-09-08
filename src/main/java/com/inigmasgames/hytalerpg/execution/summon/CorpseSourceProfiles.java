package com.inigmasgames.hytalerpg.execution.summon;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited role allowlist. Unknown roles are NOT inferred to be common or safe to clone. */
public final class CorpseSourceProfiles {
    public record Profile(String role,String projectionRole,CorpseLedger.Rank rank,double basePower,double attackInterval,String evidence){}
    private final Map<String,Profile> profiles;
    private CorpseSourceProfiles(List<Profile> rows){
        Map<String,Profile> result=new LinkedHashMap<>();
        for(var row:rows){
            if(row.role()==null||row.projectionRole()==null||!row.projectionRole().startsWith("RPG_Summon_")||row.evidence()==null||row.evidence().isBlank()
                    ||!Double.isFinite(row.basePower())||row.basePower()<=0||!Double.isFinite(row.attackInterval())||row.attackInterval()<1
                    ||!Set.of(CorpseLedger.Rank.COMMON,CorpseLedger.Rank.SPECIALIST,CorpseLedger.Rank.ELITE).contains(row.rank())
                    ||result.put(row.role(),row)!=null)throw new IllegalArgumentException("Invalid corpse role registry");
        }profiles=Map.copyOf(result);
    }
    public static CorpseSourceProfiles load(){
        try(var stream=CorpseSourceProfiles.class.getResourceAsStream("/rpg/runtime/corpse-source-profiles.json")){
            if(stream==null)throw new IllegalStateException("Missing corpse profiles");
            return new CorpseSourceProfiles(List.of(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Profile[].class)));
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
    public Optional<Profile> find(String role){return Optional.ofNullable(profiles.get(role));}
    public Collection<Profile> all(){return profiles.values();}
}
