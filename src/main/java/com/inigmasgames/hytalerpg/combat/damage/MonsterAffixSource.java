package com.inigmasgames.hytalerpg.combat.damage;

import java.util.*;

/** Immutable monster provenance shared by damage/status owners; no Skill or equipment identity. */
public record MonsterAffixSource(UUID worldId,UUID logicalActorId,UUID nativeActorId,long encounterGeneration,
        String affixId,String balanceRevision,String rootId,String strikeId) {
    public MonsterAffixSource {
        Objects.requireNonNull(worldId);Objects.requireNonNull(logicalActorId);Objects.requireNonNull(nativeActorId);
        if(encounterGeneration<0||affixId==null||!affixId.matches("ME-0(0[1-9]|1[0-9]|2[0-7])"))throw new IllegalArgumentException("INVALID_MONSTER_AFFIX_SOURCE");
        for(String id:Arrays.asList(balanceRevision,rootId,strikeId))
            if(id==null||id.isBlank()||id.length()>512||id.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("INVALID_MONSTER_SOURCE_ID");
    }
}
