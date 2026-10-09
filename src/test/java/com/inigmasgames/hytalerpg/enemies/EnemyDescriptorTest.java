package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

class EnemyDescriptorTest {
    private final EnemyAffixRegistry registry=EnemyAffixRegistry.canonical();
    private final UUID world=UUID.randomUUID(),encounter=UUID.randomUUID(),actor=UUID.randomUUID();
    private EnemyDescriptor.AffixInstance own(Operator op){return EnemyDescriptor.ownInstance(registry.require(op),
            EnemyAffixSelection.Choice.of(op),DifficultyId.HELL,"birth/me.affix-set/"+op.id());}
    private EnemyDescriptor descriptor(List<EnemyDescriptor.AffixInstance> affixes){
        var rewards=EnemyRewardContext.canonical(EnemyRarity.UNIQUE,EnemyRewardContext.Origin.NATURAL,false,affixes.size());
        return new EnemyDescriptor(1,1,rewards.balanceRevision(),"native-test-binding",world,encounter,1,actor,actor,"spawn-cycle-1",
                rewards.origin(),"Trork_Warrior","Trork_Warrior",68,DifficultyId.HELL,ProgressionMath.Rank.COMMON,
                EnemyRarity.UNIQUE,EnemyDescriptor.PackRole.LEADER,encounter,actor,"source-validation","canonical-loot",null,
                "Gorefang the Cruel","frozen-name-seed",null,affixes,List.of(),Set.of(),List.of(),"normal",rewards,"a".repeat(64),null);
    }
    @Test void sealedDescriptorSurvivesStoreReloadAndRejectsRerolls(@TempDir Path folder){
        var descriptor=descriptor(List.of(own(Operator.FIRE_ENCHANTED),own(Operator.EXTRA_STRONG),own(Operator.EXTRA_FAST)));
        try(var store=new FileEncounterStore(folder)){
            assertEquals(descriptor,store.reserveEnemyDescriptor(descriptor));assertEquals(descriptor,store.reserveEnemyDescriptor(descriptor));
            assertThrows(IllegalStateException.class,()->store.reserveEnemyDescriptor(descriptor(List.of(own(Operator.COLD_ENCHANTED)))));
        }
        try(var store=new FileEncounterStore(folder)){assertEquals(descriptor,store.enemyDescriptor(world,actor).orElseThrow());}
    }
    @Test void minionInheritanceContainsOnlyApprovedFieldsAndNeverCountsForRewards(){
        for(var op:Operator.values()){
            if(op==Operator.AURA_ENCHANTED)continue;
            var own=own(op);var inherited=EnemyDescriptor.inheritedInstance(registry.require(op),own,actor,"inherit/"+op.id());
            if(registry.require(op).inheritance()==Inheritance.NONE){assertTrue(inherited.isEmpty());continue;}
            var value=inherited.orElseThrow();assertFalse(value.contributesToRewardCount());assertEquals(actor,value.sourceLeaderId());
            assertFalse(value.parameters().containsKey("resistanceAdd"));assertFalse(value.parameters().containsKey("earthResistanceAdd"));
            assertFalse(value.parameters().containsKey("defenseIncrease"));assertFalse(value.parameters().containsKey("recoveryRateIncrease"));
            if(op!=Operator.POISON_ENCHANTED)assertFalse(value.parameters().containsKey("statusChance"));
            if(registry.require(op).inheritance()==Inheritance.EXTRA_ELEMENTAL_POWER_ONLY)
                assertEquals(own.value("extraPowerFraction")*.5,value.value("extraPowerFraction"));
        }
    }
    @Test void immutableParameterSnapshotCannotChangeWithInputMapOrCatalog(){
        var values=new HashMap<>(Map.of("physicalIncrease",.3));
        var instance=new EnemyDescriptor.AffixInstance("ME-002","rev",values,null,EnemyDescriptor.AffixOrigin.OWN,null,true,"draw");
        values.put("physicalIncrease",9d);assertEquals(.3,instance.value("physicalIncrease"));
        assertThrows(UnsupportedOperationException.class,()->instance.parameters().put("other",1d));
        assertThrows(IllegalArgumentException.class,()->new EnemyDescriptor.AffixInstance("ME-028","rev",Map.of(),null,
                EnemyDescriptor.AffixOrigin.OWN,null,true,"draw"));
    }
}
