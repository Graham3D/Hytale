package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyAffixSnapshotTest {
    final UUID world=UUID.randomUUID(),packId=UUID.randomUUID(),leader=UUID.randomUUID(),minion=UUID.randomUUID();
    final EnemyBalance balance=EnemyBalance.canonical();final EnemyAffixRegistry registry=EnemyAffixRegistry.canonical();
    EnemyPackRecord pack(){return new EnemyPackRecord(1,packId,world,packId,1,EnemyPackRecord.State.RESERVED,null,Vec3.ZERO,
            List.of(new EnemyPackRecord.Member(leader,leader,"Trork_Warrior",EnemyPackRecord.Role.LEADER),
                    new EnemyPackRecord.Member(minion,minion,"Trork_Warrior",EnemyPackRecord.Role.MINION)),leader,Set.of(),Map.of(),false,false,"birth",null).staged().publish();}
    EnemyDescriptor.AffixInstance affix(Operator operator){return EnemyDescriptor.ownInstance(registry.require(operator),EnemyAffixSelection.Choice.of(operator),DifficultyId.HELL,"draw/"+operator);}
    EnemyDescriptor actor(boolean isMinion,List<EnemyDescriptor.AffixInstance> own,List<EnemyDescriptor.AffixInstance> inherited,EnemyDescriptor.TemplateBirth template){
        return actor(isMinion,own,inherited,template,68,DifficultyId.HELL);
    }
    EnemyDescriptor actor(boolean isMinion,List<EnemyDescriptor.AffixInstance> own,List<EnemyDescriptor.AffixInstance> inherited,EnemyDescriptor.TemplateBirth template,int level,DifficultyId mode){
        UUID id=isMinion?minion:leader;var rarity=isMinion?EnemyRarity.NORMAL:template==null?EnemyRarity.UNIQUE:EnemyRarity.SUPER_UNIQUE;
        var origin=isMinion?EnemyRewardContext.Origin.INITIAL_PACK_MINION:EnemyRewardContext.Origin.NATURAL;
        return new EnemyDescriptor(1,1,balance.revision(),"native-binding",world,packId,1,id,id,"cycle",origin,"Trork_Warrior","Trork_Warrior",level,mode,
                ProgressionMath.Rank.COMMON,rarity,isMinion?EnemyDescriptor.PackRole.MINION:EnemyDescriptor.PackRole.LEADER,packId,leader,"validation","loot",null,
                "Name","name-seed",null,own,inherited,Set.of(),List.of(),"normal",balance.rewards(rarity,origin,isMinion,own.size()),"a".repeat(64),template);
    }
    @Test void frenzyUsesEngagedTimeAndRecoveryCapabilityDoesNotDisableDirectDamage(){
        var descriptor=actor(false,List.of(affix(Operator.FRENZIED),affix(Operator.EXTRA_FAST)),List.of(),null);
        var before=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),7999,true,true);assertFalse(before.enraged());
        var active=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),8000,true,true);
        assertTrue(active.enraged());assertEquals(.3,active.allDirectIncrease());assertEquals(1.45,active.movementMultiplier(),1e-10);
        assertEquals(1.35,active.recoveryRateMultiplier(),1e-10);assertEquals(240,active.projectedMaximumHealth(100));
        var unsupported=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),8000,false,false);
        assertEquals(1,unsupported.movementMultiplier());assertEquals(1,unsupported.recoveryRateMultiplier());assertEquals(.3,unsupported.allDirectIncrease());
        assertFalse(EnemyAffixSnapshot.resolve(descriptor,balance,pack(),12000,true,true).enraged());
    }
    @Test void empoweredInheritanceDoesNotCopyLeaderRarityDefenseResistanceOrRecovery(){
        var empowered=affix(Operator.EMPOWERED_MINIONS);var fast=affix(Operator.EXTRA_FAST);var fire=affix(Operator.FIRE_ENCHANTED);
        var inherited=List.of(empowered,fast,fire).stream().map(a->EnemyDescriptor.inheritedInstance(registry.require(a.affixId()),a,leader,"inherit/"+a.affixId()).orElseThrow()).toList();
        var minionView=EnemyAffixSnapshot.resolve(actor(true,List.of(),inherited,null),balance,pack(),0,true,true);
        assertEquals(143.75,minionView.projectedMaximumHealth(100),1e-10);assertEquals(.15,minionView.allDirectIncrease());
        assertEquals(1.25,minionView.movementMultiplier(),1e-10);assertEquals(1,minionView.recoveryRateMultiplier());
        assertEquals(0,minionView.stoneSkinDefenseRating());assertTrue(minionView.resistanceAdds().isEmpty());assertEquals(.125,minionView.extraPowerFractions().get("FIRE"));
        var leaderView=EnemyAffixSnapshot.resolve(actor(false,List.of(empowered),List.of(),null),balance,pack(),0,true,true);
        assertEquals(240,leaderView.projectedMaximumHealth(100));assertEquals(0,leaderView.allDirectIncrease());
    }
    @Test void avengerUsesDurableInitialMinionReceiptsAndSuspensionDoesNotInventDeaths(){
        var descriptor=actor(false,List.of(affix(Operator.AVENGER)),List.of(),null);var pack=pack();
        assertEquals(0,EnemyAffixSnapshot.resolve(descriptor,balance,pack.suspend(),0,true,true).avengerStacks());
        var defeated=pack.terminalDefeat(minion,"enemy-death/"+world+"/"+minion);
        var view=EnemyAffixSnapshot.resolve(descriptor,balance,defeated,0,true,true);
        assertEquals(1,view.avengerStacks());assertEquals(.07,view.allDirectIncrease());assertEquals(1.02,view.movementMultiplier());
        assertEquals(view,EnemyAffixSnapshot.resolve(descriptor,balance,defeated.terminalDefeat(minion,"enemy-death/"+world+"/"+minion),0,true,true));
        var published=EnemyAffixSnapshot.resolve(descriptor,balance,pack,0,true,true).withPackProtection(descriptor,defeated,balance);
        assertEquals(view,published);
        assertEquals(published,published.withPackProtection(descriptor,defeated,balance));
        assertThrows(IllegalArgumentException.class,()->published.withPackProtection(descriptor,pack,balance));
    }
    @Test void templateFactorsAreFrozenAndAuraCapsOnlyItsIncrementalProviderBucket(){
        var descriptor=actor(false,List.of(affix(Operator.EXTRA_STRONG)),List.of(),new EnemyDescriptor.TemplateBirth("qa_template",3,4.5,1.7));
        var effects=new FiniteSupportEffects();var source=new FiniteSupportEffects.StatSource(world,minion,1,FiniteSupportEffects.SourceKind.MONSTER_AFFIX,"ME-023");
        effects.replaceAuraStats(source,List.of(new FiniteSupportEffects.StatEffect(new FiniteSupportEffects.StatKey(source,leader,1,
                FiniteSupportEffects.Stat.AURA_PHYSICAL_INCREASE),.25,0,.5)),0,()->true);
        var view=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true,effects,.1);
        assertEquals(450,view.projectedMaximumHealth(100));assertEquals(1.7,view.rarityDirectFactor());assertEquals(.6,view.physicalIncrease());
        assertEquals(.45,EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true,effects,.5).physicalIncrease());
    }
    @Test void inheritedAuraTagAndProviderFollowTheActiveLeaseRatherThanBirthInheritance(){
        var descriptor=actor(true,List.of(),List.of(),null);
        var effects=new FiniteSupportEffects();
        var source=new FiniteSupportEffects.StatSource(world,leader,1,FiniteSupportEffects.SourceKind.MONSTER_AFFIX,"ME-023");
        var lease=new FiniteSupportEffects.StatEffect(new FiniteSupportEffects.StatKey(source,minion,1,
                FiniteSupportEffects.Stat.AURA_PHYSICAL_INCREASE),.25,0,.5);
        var absent=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true,effects,0);
        assertFalse(absent.auraMember());
        assertTrue(EnemyDisplayDto.project(descriptor,registry,absent,pack(),0,"Name","Trork Warrior",Set.of(),false,0)
                .inheritedEffectTags().isEmpty());
        assertTrue(effects.replaceAuraStats(source,List.of(lease),0,()->true));
        var active=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true,effects,.1);
        assertTrue(active.auraMember());assertEquals(.25,active.physicalIncrease(),1e-10);
        assertEquals(1,EnemyDisplayDto.project(descriptor,registry,active,pack(),1,"Name","Trork Warrior",Set.of(),false,0)
                .inheritedEffectTags().size());
        effects.withdrawAuraActor(world,leader,1);
        var withdrawn=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true,effects,.2);
        assertFalse(withdrawn.auraMember());assertEquals(0,withdrawn.physicalIncrease());
        assertTrue(EnemyDisplayDto.project(descriptor,registry,withdrawn,pack(),2,"Name","Trork Warrior",Set.of(),false,0)
                .inheritedEffectTags().isEmpty());
    }
    @Test void revisedStoneSkinAddsLevelScaledRatingThroughD01WithoutTheOldPercentIncrease(){
        double[] rating={150,200,600d*3/7},protection={.20,.25,.30};
        for(var mode:DifficultyId.values()){
            var instance=EnemyDescriptor.ownInstance(registry.require(Operator.STONE_SKIN),EnemyAffixSelection.Choice.of(Operator.STONE_SKIN),mode,"stone/"+mode);
            assertFalse(instance.parameters().containsKey("defenseIncrease"));
            var descriptor=actor(false,List.of(instance),List.of(),null,50,mode);
            var view=EnemyAffixSnapshot.resolve(descriptor,balance,pack(),0,true,true);
            assertEquals(rating[mode.ordinal()],view.stoneSkinDefenseRating(),1e-10);
            var zero=view.defenseContributions(com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions.NONE);
            assertEquals(0,zero.globalDefenseIncreased());
            assertEquals(protection[mode.ordinal()],com.inigmasgames.hytalerpg.combat.defense.DefenseView.managed(50,0,zero).managedProtection(),1e-10);
            var existing=new com.inigmasgames.hytalerpg.combat.defense.DefenseView.Contributions(100,50,.5,.25);
            var combined=com.inigmasgames.hytalerpg.combat.defense.DefenseView.managed(50,.20,view.defenseContributions(existing));
            assertEquals((150+100+50+rating[mode.ordinal()])*1.5*.75,combined.effectiveRating(),1e-10);
            assertEquals(.6,com.inigmasgames.hytalerpg.combat.defense.DefenseView.managed(50,.6,zero).managedProtection(),1e-10);
            assertTrue(view.resistanceAdds().isEmpty()); // No elemental resistance, including Fire.
        }
    }
    @Test void packboundGuardsUseTheDistinctConversionGateUntilExactGuardDeathsReleaseLeader(){
        var other=UUID.randomUUID();
        var guarded=new EnemyPackRecord(1,packId,world,packId,1,EnemyPackRecord.State.RESERVED,null,Vec3.ZERO,
                List.of(new EnemyPackRecord.Member(leader,leader,"Trork_Warrior",EnemyPackRecord.Role.LEADER),
                        new EnemyPackRecord.Member(minion,minion,"Trork_Warrior",EnemyPackRecord.Role.MINION),
                        new EnemyPackRecord.Member(other,other,"Trork_Warrior",EnemyPackRecord.Role.MINION)),
                leader,Set.of(minion,other),Map.of(),false,false,"birth",null).staged().publish();
        var leaderDescriptor=actor(false,List.of(affix(Operator.PACKBOUND)),List.of(),null);
        var guardDescriptor=actor(true,List.of(),List.of(),null);
        var guard=EnemyAffixSnapshot.resolve(guardDescriptor,balance,guarded,0,true,true);
        assertFalse(guard.blocksExternalMutation());assertTrue(guard.blocksConversion());
        var leaderBefore=EnemyAffixSnapshot.resolve(leaderDescriptor,balance,guarded,0,true,true);
        assertTrue(leaderBefore.blocksExternalMutation());assertTrue(leaderBefore.blocksConversion());
        var oneDead=guarded.terminalDefeat(minion,"enemy-death/"+world+"/"+minion);
        assertTrue(EnemyAffixSnapshot.resolve(leaderDescriptor,balance,oneDead,0,true,true).blocksConversion());
        var released=oneDead.terminalDefeat(other,"enemy-death/"+world+"/"+other);
        var leaderAfter=EnemyAffixSnapshot.resolve(leaderDescriptor,balance,released,0,true,true);
        assertFalse(leaderAfter.blocksExternalMutation());assertFalse(leaderAfter.blocksConversion());
        var projected=leaderBefore.withPackProtection(leaderDescriptor,released,balance);
        assertFalse(projected.blocksExternalMutation());assertFalse(projected.blocksConversion());
        assertEquals(leaderBefore.rarityHealthFactor(),projected.rarityHealthFactor());
        assertEquals(leaderBefore.movementMultiplier(),projected.movementMultiplier());
    }
}
