package com.inigmasgames.hytalerpg.execution.summon;

import java.util.Set;

/** Explicit encounter opt-in; NOT a default rank/ownership inference for arbitrary native NPCs. */
public final class DecoyAttractionPolicy {
    private static final Set<String> ROLES=Set.of("Wolf_Black");
    private DecoyAttractionPolicy(){}
    public static boolean accepts(String role,boolean targetingCaster,boolean protectedOrOwned,boolean hostile,boolean lineOfSight){
        return ROLES.contains(role)&&targetingCaster&&!protectedOrOwned&&hostile&&lineOfSight;
    }
}
