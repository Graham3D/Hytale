package com.inigmasgames.hytalerpg.gear;

import java.util.List;
import java.util.Objects;

/** Reserved schema boundary. The authored registry is empty until content is explicitly added. */
public record AuthoredGearDefinition(String authoredId,GearQuality quality,String baseId,String setId,
                                     String uniqueAffixId,List<String> sourceIds) {
    public AuthoredGearDefinition {
        if(authoredId==null||authoredId.isBlank()||baseId==null||!baseId.startsWith("gm.")
                ||quality!=GearQuality.SET&&quality!=GearQuality.UNIQUE)throw new IllegalArgumentException("Invalid authored special gear");
        if(quality==GearQuality.SET&&(setId==null||setId.isBlank()||uniqueAffixId!=null)
                ||quality==GearQuality.UNIQUE&&(uniqueAffixId==null||uniqueAffixId.isBlank()||setId!=null))
            throw new IllegalArgumentException("Missing authored special identity");
        sourceIds=List.copyOf(Objects.requireNonNull(sourceIds));
        if(sourceIds.isEmpty()||sourceIds.stream().anyMatch(s->s==null||s.isBlank()))throw new IllegalArgumentException("Authored source required");
    }
    public static List<AuthoredGearDefinition> registered(){return List.of();}
}
