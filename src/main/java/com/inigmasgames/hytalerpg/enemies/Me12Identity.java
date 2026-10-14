package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Additive only on schema-2 descriptors. Sealed by the existing encounter root, never a name store. */
public record Me12Identity(int schemaVersion,Kind kind,String policyDigest,List<Me12Policies.Reference> policies,
        String nativeSnapshotDigest,String nativeSourceReceipt,String rosterDigest,long generation,
        Me12Planner.Plan plan,Map<String,Integer> threatUnits,UUID sourceLeader) {
    public enum Kind { PROCEDURAL_UNIQUE, INHERITED_INITIAL_MINION }
    public Me12Identity {
        require(schemaVersion==2&&generation>=0,"ME12_IDENTITY_VERSION");Objects.requireNonNull(kind);
        for(var digest:List.of(policyDigest,nativeSnapshotDigest,rosterDigest))require(digest.matches("[a-f0-9]{64}"),"ME12_IDENTITY_DIGEST");
        require(nativeSourceReceipt!=null&&!nativeSourceReceipt.isBlank(),"ME12_NATIVE_SOURCE");policies=List.copyOf(policies);
        require(policies.stream().map(Me12Policies.Reference::asset).toList().equals(Me12Policies.ASSETS),"ME12_POLICY_REFS");
        threatUnits=Collections.unmodifiableMap(new TreeMap<>(threatUnits));
        if(kind==Kind.PROCEDURAL_UNIQUE) {
            require(plan!=null&&sourceLeader==null&&plan.difficultySlots()>=0&&plan.difficultySlots()<=2,"ME12_LEADER_IDENTITY");
            require(plan.affixes().size()>=2&&plan.affixes().size()<=8,"ME12_LEADER_COUNT");
            require(threatUnits.keySet().equals(new TreeSet<>(plan.choices().stream().map(EnemyAffixSelection.Choice::affixId).toList()))
                    &&threatUnits.values().stream().allMatch(v->Set.of(100,115,130,150).contains(v)),"ME12_THREAT_SNAPSHOT");
            require(plan.affixes().equals(Me12Planner.canonicalOwnership(plan.prefix(),plan.suffix(),plan.difficultySlots(),plan.choices())),"ME12_CANONICAL_OWNERSHIP");
            require(plan.choices().stream().anyMatch(c->c.affixId().equals(plan.prefix().signature()))
                    &&plan.choices().stream().anyMatch(c->c.affixId().equals(plan.suffix().signature()))
                    &&!plan.prefix().signature().equals(plan.suffix().signature()),"ME12_SIGNATURES");
        }else require(plan==null&&sourceLeader!=null&&threatUnits.isEmpty(),"ME12_MINION_IDENTITY");
    }
    public int threatTotal(){return threatUnits.values().stream().mapToInt(Integer::intValue).sum();}
    public static Me12Identity leader(Me12Planner.Compiled compiled,Me12Planner.Plan plan,String rosterDigest) {
        require(compiled.key().equals(plan.contextKey()),"ME12_CONTEXT_PLAN_MISMATCH");var threat=new TreeMap<String,Integer>();
        plan.choices().forEach(c->threat.put(c.affixId(),compiled.policies().threat(c.affixId())));
        var c=compiled.context();var p=compiled.policies();
        return new Me12Identity(2,Kind.PROCEDURAL_UNIQUE,p.digest(),p.references(),c.nativeSnapshotDigest(),c.sourceReceipt(),rosterDigest,c.generation(),plan,threat,null);
    }
    public Me12Identity inherited(UUID leader){return new Me12Identity(2,Kind.INHERITED_INITIAL_MINION,policyDigest,policies,nativeSnapshotDigest,nativeSourceReceipt,rosterDigest,generation,null,Map.of(),leader);}
    public record RewardReference(int schemaVersion,String policyDigest,List<Me12Policies.Reference> policies,int threatHundredths,int ownAffixCount) {
        public RewardReference {require(schemaVersion==2&&policyDigest!=null&&policyDigest.matches("[a-f0-9]{64}")&&ownAffixCount>=2&&ownAffixCount<=8
                &&threatHundredths>=100*ownAffixCount&&threatHundredths<=150*ownAffixCount,"ME12_REWARD_VERSION");
            policies=List.copyOf(policies);require(policies.stream().map(Me12Policies.Reference::asset).toList().equals(Me12Policies.ASSETS),"ME12_REWARD_POLICIES");}
    }
    public RewardReference rewards(){require(kind==Kind.PROCEDURAL_UNIQUE,"ME12_MINIONS_KEEP_V1_ECONOMICS");return new RewardReference(2,policyDigest,policies,threatTotal(),plan.affixes().size());}
}
