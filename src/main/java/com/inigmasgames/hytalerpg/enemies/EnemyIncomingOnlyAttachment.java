package com.inigmasgames.hytalerpg.enemies;

import java.util.Set;

/** Explicit Firesteel defender contract. Its original seven native attacks remain native-owned. */
public final class EnemyIncomingOnlyAttachment {
    private EnemyIncomingOnlyAttachment(){}
    public static boolean certified(EnemyNativeBindings.Role role){
        return role.canonicalRoleId().equals("Golem_Firesteel")&&role.nativeRoleIds().equals(Set.of("Golem_Firesteel"))
                &&role.sourceAssetPath().equals("Server/NPC/Roles/Elemental/Golem/Golem_Firesteel.json")
                &&role.sourceAssetSha256().equals("7117e1725a8306e235edd01802c295a3af95cbebead8eb05bd910ae3285c0d9f")
                &&role.actions().isEmpty()&&!role.capabilities().contains(EnemyAffixRegistry.Capability.DIRECT_HIT)
                &&role.nativeImmunityChannels().equals(Set.of("FIRE"));
    }
    public static boolean accepts(EnemyNativeBindings.Role role,EnemyDescriptor actor){
        if(!certified(role)||!actor.nativeRoleId().equals("Golem_Firesteel"))return false;
        // Only self defense/control and existing pack state. No offensive inherited package or action root.
        Set<String> incoming=Set.of("ME-003","ME-004","ME-017","ME-018","ME-022","ME-023","ME-024","ME-026","ME-027");
        return actor.inheritedAffixes().isEmpty()&&actor.ownAffixes().stream().allMatch(a->incoming.contains(a.affixId()));
    }
}
