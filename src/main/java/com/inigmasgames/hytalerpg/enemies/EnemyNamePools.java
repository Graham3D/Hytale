package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Birth-only names. This purpose-separated stream cannot change affix or reward draws. */
public final class EnemyNamePools {
    private final String revision,format;private final List<String> stems,epithets;private final int maximum;
    public EnemyNamePools(JsonObject json){
        exact(json,Set.of("schemaVersion","revision","format","stems","epithets","maxGeneratedCodepoints","championNameMode","superUniqueNameMode"));
        require(number(json.get("schemaVersion"),true,1)==1,"NAME_POOL_SCHEMA");revision=string(json,"revision");format=string(json,"format");
        require(format.equals("{stem} the {epithet}")&&string(json,"championNameMode").equals("CANONICAL_ROLE_DISPLAY")
                &&string(json,"superUniqueNameMode").equals("AUTHORED"),"NAME_POOL_FORMAT");
        maximum=(int)number(json.get("maxGeneratedCodepoints"),true,32);require(maximum>0,"NAME_POOL_MAXIMUM");
        stems=pool(json,"stems");epithets=pool(json,"epithets");
        for(String stem:stems)for(String epithet:epithets){String name=format(stem,epithet);require(name.codePointCount(0,name.length())<=maximum,"GENERATED_NAME_TOO_LONG");}
    }
    public String revision(){return revision;}
    public String uniqueName(String frozenBirthSeed){
        var rng=new GearRandom(revision+"/"+frozenBirthSeed).stream("me.name");
        return format(stems.get(rng.nextInt(stems.size())),epithets.get(rng.nextInt(epithets.size())));
    }
    private String format(String stem,String epithet){return format.replace("{stem}",stem).replace("{epithet}",epithet);}
    private static List<String> pool(JsonObject json,String key){
        var values=strings(json.get(key));require(!values.isEmpty()&&values.size()<=256&&new HashSet<>(values).size()==values.size(),"NAME_POOL_BOUNDS_OR_DUPLICATE");
        for(String value:values)require(!value.isBlank()&&value.codePointCount(0,value.length())<=32&&value.chars().noneMatch(c->Character.isISOControl(c)||c=='{'||c=='}'),"NAME_POOL_TEXT");
        return List.copyOf(values);
    }
    public static EnemyNamePools canonical(){return new EnemyNamePools(resource("name-pools-v1.json"));}
}
