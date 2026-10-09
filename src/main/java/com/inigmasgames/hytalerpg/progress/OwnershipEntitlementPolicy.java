package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillId;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

/** Production ownership gate with an explicitly labelled engineering override. */
public final class OwnershipEntitlementPolicy implements EntitlementPolicy {
    private final boolean developmentMode;
    private final Map<UUID,Map<UUID,String>> temporarySkills = new LinkedHashMap<>();
    public OwnershipEntitlementPolicy(boolean developmentMode) { this.developmentMode = developmentMode; }

    /** Called by the loadout owner after a validated equipment publication. Item IDs are source tokens. */
    public synchronized void publishTemporarySkills(UUID player,Map<UUID,String> grants) {
        if(grants.isEmpty())temporarySkills.remove(player);
        else temporarySkills.put(player,Map.copyOf(grants));
    }
    public synchronized int temporaryCopies(UUID player,String skill) {
        return (int)temporarySkills.getOrDefault(player,Map.of()).values().stream().filter(skill::equals).count();
    }
    public synchronized Map<UUID,String> temporaryGrants(UUID player){return temporarySkills.getOrDefault(player,Map.of());}
    public synchronized void forget(UUID player) { temporarySkills.remove(player); }

    @Override public EntitlementVerdict skill(RpgPlayerState state, SkillId id) {
        if (developmentMode) return EntitlementVerdict.allowed("DEVELOPMENT_ENTITLEMENT_MODE");
        return state.learnedSkills.contains(id.value())
                ? EntitlementVerdict.allowed("LEARNED_SKILL")
                : state.playerUuid!=null&&temporaryCopies(state.playerUuid(),id.value())>0
                ? EntitlementVerdict.allowed("EQUIPPED_ITEM_GRANT")
                : EntitlementVerdict.denied("Skill is not learned: " + id.value());
    }

    @Override public EntitlementVerdict passive(RpgPlayerState state, PassiveId id) {
        if (developmentMode) return EntitlementVerdict.allowed("DEVELOPMENT_ENTITLEMENT_MODE");
        return state.ownedPassives.getOrDefault(id.value(), 0) > equippedCopies(state, id)
                ? EntitlementVerdict.allowed("OWNED_PASSIVE_COPY")
                : EntitlementVerdict.denied("No unequipped owned copy of Passive: " + id.value());
    }

    private static long equippedCopies(RpgPlayerState state, PassiveId id) {
        return java.util.Arrays.stream(state.equippedPassives).filter(id.value()::equals).count();
    }

    @Override public boolean developmentMode() { return developmentMode; }
}
