package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Server-plugin-only membership seam. No client party IDs, inferred neutral alliances or synthetic native API. */
public interface PartyMembershipProvider {
    /** Return a coherent snapshot for exactly these previously credited, loaded candidates. Missing keys mean solo. */
    Map<UUID,String> snapshot(UUID world,Set<UUID> candidates);
    default String availability(){return "TRUSTED_SERVER_PROVIDER_CONNECTED_UNVERIFIED";}
    PartyMembershipProvider UNAVAILABLE=new PartyMembershipProvider(){
        public Map<UUID,String> snapshot(UUID world,Set<UUID> candidates){return Map.of();}
        public String availability(){return "NATIVE_PARTY_PROVIDER_UNAVAILABLE_SOLO_ONLY";}
    };
    static List<EncounterContributions.Participant> apply(UUID world,List<EncounterContributions.Participant> candidates,PartyMembershipProvider provider){
        Objects.requireNonNull(world);Objects.requireNonNull(provider);
        if(candidates.size()>EncounterContributions.MAX_CONTRIBUTORS)throw new IllegalArgumentException("PARTY_CANDIDATE_BUDGET");
        Set<UUID> ids=new HashSet<>();
        for(var member:candidates)if(!member.world().equals(world)||!member.loaded()||!ids.add(member.player()))throw new IllegalArgumentException("INVALID_PARTY_CANDIDATE");
        Map<UUID,String> membership=Map.copyOf(Objects.requireNonNull(provider.snapshot(world,Set.copyOf(ids))));
        if(membership.size()>ids.size()||!ids.containsAll(membership.keySet()))throw new IllegalArgumentException("PARTY_PROVIDER_UNKNOWN_MEMBER");
        for(String id:membership.values())if(id.isBlank()||id.length()>128||id.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("INVALID_PARTY_ID");
        return candidates.stream().map(p->new EncounterContributions.Participant(p.player(),p.world(),p.position(),p.level(),p.loaded(),membership.get(p.player()),p.learning())).toList();
    }
}
