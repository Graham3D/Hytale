package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyBirthPlannerTest {
    final EnemyBalance balance=EnemyBalance.canonical();
    final EnemyBirthPlanner planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
    static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    EnemyBirthPlanner.Candidate candidate(int index,DifficultyId mode,Set<Capability> capabilities,int maxLoot){
        var descriptor=new EnemyDescriptor(1,1,balance.revision(),"certified-fixture",id("world"),id("encounter"),1,id("logical/"+index),id("native/"+index),
                "cycle",EnemyRewardContext.Origin.NATURAL,"Trork_Warrior","Trork_Warrior",50,mode,ProgressionMath.Rank.COMMON,EnemyRarity.NORMAL,
                EnemyDescriptor.PackRole.NONE,null,null,"source-proof","native-loot","canonical-learning","Trork Warrior","baseline-name",null,
                List.of(),List.of(),Set.of(),List.of(),"native-control",balance.rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.NATURAL,false,0),"a".repeat(64),null);
        return new EnemyBirthPlanner.Candidate(descriptor,new EnemyAffixSelection.Binding("certified-fixture",capabilities,0,true,false,false,0,true),maxLoot);
    }
    EnemyBirthPlanner.Request request(String seed,DifficultyId mode,int originals,int capacity,boolean packCapacity,Set<Capability> capabilities,int loot){
        var all=new ArrayList<EnemyBirthPlanner.Candidate>();for(int i=0;i<8;i++)all.add(candidate(i,mode,capabilities,loot));
        return new EnemyBirthPlanner.Request(seed,id("pack"),Vec3.ZERO,"native-job/birth",all.subList(0,originals),all.subList(originals,8),true,capacity,packCapacity);
    }
    EnemyBirthPlanner.Request request(String seed,DifficultyId mode){return request(seed,mode,2,6,true,EnumSet.allOf(Capability.class),2);}
    EnemyBirthPlanner.Request withNativePassiveDefenses(EnemyBirthPlanner.Request request){
        java.util.function.Function<EnemyBirthPlanner.Candidate,EnemyBirthPlanner.Candidate> update=c->new EnemyBirthPlanner.Candidate(c.nativeBaseline(),
                new EnemyAffixSelection.Binding(c.binding().revision(),c.binding().capabilities(),0,false,true,true,0,c.binding().distanceDisplacementDelivery()),c.maximumEquipment());
        return new EnemyBirthPlanner.Request(request.seed(),request.packId(),request.anchor(),request.nativePlanReceipt(),
                request.originals().stream().map(update).toList(),request.additionalSlots().stream().map(update).toList(),true,request.additionalNativeCapacity(),request.specialPackCapacity());
    }
    String seedFor(EnemyRarity rarity){return seedFor(rarity,DifficultyId.NORMAL);}
    String seedFor(EnemyRarity rarity,DifficultyId mode){for(int i=0;i<10000;i++){String seed="sample/"+i;if(planner.rarity(seed,mode)==rarity)return seed;}throw new AssertionError();}
    @Test void difficultyPromotionThresholdsUseOneDeterministicRarityDraw(){
        var expected=Map.of(
                DifficultyId.NORMAL,Map.of(EnemyRarity.NORMAL,920,EnemyRarity.CHAMPION,16,EnemyRarity.UNIQUE,64),
                DifficultyId.NIGHTMARE,Map.of(EnemyRarity.NORMAL,860,EnemyRarity.CHAMPION,28,EnemyRarity.UNIQUE,112),
                DifficultyId.HELL,Map.of(EnemyRarity.NORMAL,800,EnemyRarity.CHAMPION,40,EnemyRarity.UNIQUE,160));
        assertEquals(expected,balance.promotion().weightsByDifficulty());
        var witnesses=new HashMap<Long,String>();
        for(int i=0;i<20000&&witnesses.size()<1000;i++){
            String seed="rarity-threshold/"+i;
            long draw=new com.inigmasgames.hytalerpg.gear.GearRandom("master-enemies-v1.1/"+balance.revision()+"/"+seed)
                    .stream("me.rarity").nextLong(1000);
            witnesses.putIfAbsent(draw,seed);
        }
        for(var era:DifficultyId.values()){
            var weights=expected.get(era);int normal=weights.get(EnemyRarity.NORMAL);
            int champion=weights.get(EnemyRarity.CHAMPION);
            assertEquals(1000,weights.values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(4*champion,weights.get(EnemyRarity.UNIQUE));
            for(long draw:new long[]{0,normal-1,normal,normal+champion-1,normal+champion,999}){
                String seed=witnesses.get(draw);assertNotNull(seed,"Missing deterministic draw "+draw);
                var rarity=draw<normal?EnemyRarity.NORMAL:draw<normal+champion?EnemyRarity.CHAMPION:EnemyRarity.UNIQUE;
                assertEquals(rarity,planner.rarity(seed,era),era+" draw="+draw);
                assertEquals(rarity,planner.rarity(seed,era),"Replay changed "+era+" draw="+draw);
            }
        }
        assertEquals(EnemyRarity.NORMAL,planner.rarity(witnesses.get(850L),DifficultyId.NORMAL));
        assertEquals(EnemyRarity.UNIQUE,planner.rarity(witnesses.get(850L),DifficultyId.HELL));
    }
    @Test void reloadRosterUsesDurableDeathsAndRetainsLivingBirthOrder(){
        var result=planner.plan(request(seedFor(EnemyRarity.CHAMPION),DifficultyId.NORMAL));
        assertTrue(result.promoted());var birth=result.plan();
        var published=birth.pack().staged().publish();
        assertEquals(birth.actors(),birth.activeActors(published));
        var defeated=birth.actors().getFirst();
        var current=published.terminalDefeat(defeated.logicalActorId(),
                "enemy-death/"+birth.world()+"/"+defeated.entityId());
        assertEquals(birth.actors().subList(1,birth.actors().size()),birth.activeActors(current));
        var single=List.of(birth.actors().get(1));
        assertEquals(single,birth.activeSubset(current,single));
        assertThrows(IllegalArgumentException.class,()->birth.activeSubset(current,List.of(defeated)));
        assertThrows(IllegalArgumentException.class,()->birth.activeSubset(current,List.of(single.getFirst(),single.getFirst())));
        var foreign=new EnemyPackRecord(current.schemaVersion(),UUID.randomUUID(),current.worldId(),
                current.encounterId(),current.generation(),current.state(),current.suspendedFrom(),
                current.anchor(),current.birthRoster(),current.leaderId(),current.guardIds(),
                current.deadMemberReceipts(),current.sealedRoster(),current.packboundReleased(),
                current.spawnPlanReceipt(),current.abortReason());
        assertThrows(IllegalArgumentException.class,()->birth.activeActors(foreign));
    }
    @Test void championCountPreviewUsesTheFinalBirthDraw(){
        int checked=0;
        for(int i=0;i<10000&&checked<50;i++){
            var request=request("champion-preview/"+i,DifficultyId.HELL);
            if(planner.rarity(request.seed(),DifficultyId.HELL)!=EnemyRarity.CHAMPION)continue;
            var preview=planner.additionalChampionMembers(request);
            assertTrue(preview.isPresent());
            var result=planner.plan(request);
            if(result.promoted())assertEquals(request.originals().size()+preview.getAsInt(),result.plan().actors().size());
            checked++;
        }
        assertEquals(50,checked);
    }
    @Test void hundredThousandGroupPlansAreDeterministicBoundedAndKeepEveryOriginal(){
        var counts=new EnumMap<EnemyRarity,Integer>(EnemyRarity.class);var affixes=new HashSet<String>();
        for(int i=0;i<100000;i++){
            var mode=DifficultyId.values()[i%3];var request=request("birth/"+i,mode);var result=planner.plan(request);var plan=result.plan();
            counts.merge(result.rolledRarity(),1,Integer::sum);
            assertNull(result.fallbackReason());assertTrue(plan.actors().size()<=8);
            assertTrue(plan.actors().stream().map(EnemyDescriptor::entityId).toList().containsAll(plan.originalNativeEntities()));
            if(i%256==0)assertEquals(result,planner.plan(request));
            if(plan.pack()!=null){
                assertEquals(EnemyPackRecord.State.RESERVED,plan.pack().state());
                assertEquals(plan.actors().size(),plan.pack().birthRoster().size());
                for(var actor:plan.actors()){
                    actor.ownAffixes().forEach(a->affixes.add(a.affixId()));
                    if(actor.packRole()==EnemyDescriptor.PackRole.MINION){
                        assertTrue(actor.ownAffixes().isEmpty());assertEquals(EnemyRarity.NORMAL,actor.enemyRarity());
                        assertEquals(0,actor.immutableRewardContext().ownAffixCount());
                        assertEquals(EnemyRewardContext.Origin.INITIAL_PACK_MINION,actor.spawnOrigin());
                    }else assertEquals(actor.enemyRarity()==EnemyRarity.CHAMPION?1:mode.ordinal()+1,actor.ownAffixes().size());
                    assertEquals("source-proof",actor.sourceValidationId());assertEquals("native-loot",actor.lootSourceId());
                    assertEquals("canonical-learning",actor.acquisitionSourceId());assertEquals(50,actor.combatLevel());
                }
                if(!plan.pack().guardIds().isEmpty())assertTrue(plan.pack().guardIds().size()>=3&&plan.pack().guardIds().size()<=5);
            }else assertEquals(2,plan.actors().size());
        }
        assertEquals(27,affixes.size());
        assertTrue(Math.abs(counts.get(EnemyRarity.NORMAL)-86000)<700,counts.toString());
        assertTrue(Math.abs(counts.get(EnemyRarity.CHAMPION)-2800)<250,counts.toString());
        assertTrue(Math.abs(counts.get(EnemyRarity.UNIQUE)-11200)<500,counts.toString());
    }
    @Test void exhaustedCapacityAndMissingCapabilitiesProduceTheOriginalPlanWithoutRerolling(){
        for(var rarity:List.of(EnemyRarity.CHAMPION,EnemyRarity.UNIQUE)){
            var seed=seedFor(rarity,DifficultyId.HELL);
            for(var request:List.of(request(seed,DifficultyId.HELL,1,0,true,EnumSet.allOf(Capability.class),2),
                    request(seed,DifficultyId.HELL,1,7,false,EnumSet.allOf(Capability.class),2),
                    withNativePassiveDefenses(request(seed,DifficultyId.HELL,1,7,true,Set.of(),2)),request(seed,DifficultyId.HELL,1,7,true,EnumSet.allOf(Capability.class),16))){
                var result=planner.plan(request);assertEquals(rarity,result.rolledRarity());assertFalse(result.promoted());assertNotNull(result.fallbackReason());
                assertEquals(1,result.plan().actors().size());assertTrue(result.plan().actors().getFirst().ownAffixes().isEmpty());
                assertEquals(result,planner.plan(request));
            }
        }
    }
    @Test void aLargeIncomingGroupIsNeverTrimmedToFitAChampionRoll(){
        var result=planner.plan(request(seedFor(EnemyRarity.CHAMPION),DifficultyId.NORMAL,5,3,true,EnumSet.allOf(Capability.class),2));
        assertEquals("INCOMING_ROSTER_EXCEEDS_SELECTED_PACK",result.fallbackReason());assertEquals(5,result.plan().actors().size());
    }
    @Test void sharedChampionAffixMustExecuteForEverySelectedMember(){
        var base=withNativePassiveDefenses(request(seedFor(EnemyRarity.CHAMPION),DifficultyId.NORMAL,2,6,true,Set.of(Capability.DEFENSE_STAT),2));
        var result=planner.plan(base);assertTrue(result.promoted());
        assertNull(result.plan().pack().leaderId());
        assertTrue(result.plan().pack().guardIds().isEmpty());
        for(var actor:result.plan().actors()){
            assertEquals(EnemyRarity.CHAMPION,actor.enemyRarity());
            assertEquals(EnemyDescriptor.PackRole.MEMBER,actor.packRole());
            assertTrue(actor.inheritedAffixes().isEmpty());
            assertEquals("ME-004",actor.ownAffixes().getFirst().affixId());
            assertEquals(150,EnemyAffixSnapshot.resolve(actor,balance,result.plan().pack(),0,false,false).stoneSkinDefenseRating(),1e-10);
        }
    }
    @Test void sharedChampionSelectionIntersectsEveryMembersActionAffixes(){
        var base=request(seedFor(EnemyRarity.CHAMPION),DifficultyId.NORMAL,2,6,true,EnumSet.allOf(Capability.class),2);
        var first=base.originals().getFirst();var second=base.originals().get(1);
        java.util.function.BiFunction<EnemyBirthPlanner.Candidate,Set<String>,EnemyBirthPlanner.Candidate> restrict=(candidate,ids)->{
            var b=candidate.binding();
            return new EnemyBirthPlanner.Candidate(candidate.nativeBaseline(),
                    new EnemyAffixSelection.Binding(b.revision(),b.capabilities(),b.usableDefense(),b.improvesElementalResistance(),
                            b.nativeStunStaggerImmune(),b.nativeSlowImmune(),b.initialMinions(),b.distanceDisplacementDelivery(),ids),
                    candidate.maximumEquipment());
        };
        var originals=List.of(restrict.apply(first,Set.of("ME-004","ME-015")),
                restrict.apply(second,Set.of("ME-004","ME-002")));
        var additional=base.additionalSlots().stream().map(candidate->restrict.apply(candidate,Set.of("ME-004"))).toList();
        var restricted=new EnemyBirthPlanner.Request(base.seed(),base.packId(),base.anchor(),base.nativePlanReceipt(),
                originals,additional,base.compatibleNativeGroup(),base.additionalNativeCapacity(),base.specialPackCapacity());
        var result=planner.plan(restricted);
        assertTrue(result.promoted());
        assertTrue(result.plan().actors().stream().allMatch(actor->actor.ownAffixes().size()==1
                &&actor.ownAffixes().getFirst().affixId().equals("ME-004")));
    }
    @Test void inheritedAffixCannotEnterAMinionWhoseActionExcludesIt(){
        var base=request(seedFor(EnemyRarity.UNIQUE),DifficultyId.NORMAL);
        var leader=base.originals().getFirst();var follower=base.originals().get(1);
        java.util.function.BiFunction<EnemyBirthPlanner.Candidate,Set<String>,EnemyBirthPlanner.Candidate> restrict=(candidate,ids)->{
            var b=candidate.binding();
            return new EnemyBirthPlanner.Candidate(candidate.nativeBaseline(),
                    new EnemyAffixSelection.Binding(b.revision(),b.capabilities(),b.usableDefense(),b.improvesElementalResistance(),
                            b.nativeStunStaggerImmune(),b.nativeSlowImmune(),b.initialMinions(),b.distanceDisplacementDelivery(),ids),
                    candidate.maximumEquipment());
        };
        var originals=List.of(restrict.apply(leader,Set.of("ME-002")),restrict.apply(follower,Set.of("ME-004")));
        var restricted=new EnemyBirthPlanner.Request(base.seed(),base.packId(),base.anchor(),base.nativePlanReceipt(),
                originals,base.additionalSlots(),base.compatibleNativeGroup(),base.additionalNativeCapacity(),base.specialPackCapacity());
        var result=planner.plan(restricted);
        assertFalse(result.promoted());
        assertEquals("NO_COMPLETE_LEGAL_AFFIX_SET",result.fallbackReason());
        assertTrue(result.plan().actors().stream().allMatch(actor->actor.ownAffixes().isEmpty()
                &&actor.inheritedAffixes().isEmpty()));
    }
    @Test void movementOnlyExtraFastFreezesNoRecoveryBonusInTheSealedDescriptor(){
        var capabilities=Set.of(Capability.MOBILE);
        boolean found=false;
        for(int i=0;i<10000&&!found;i++){
            var result=planner.plan(request("movement-only/"+i,DifficultyId.HELL,4,0,true,capabilities,0));
            if(!result.promoted()||result.rolledRarity()!=EnemyRarity.CHAMPION)continue;
            var first=result.plan().actors().getFirst();
            if(first.own(Operator.EXTRA_FAST).isEmpty())continue;
            assertEquals(0,first.own(Operator.EXTRA_FAST).orElseThrow().value("recoveryRateIncrease"));
            var stats=EnemyAffixSnapshot.resolve(first,balance,result.plan().pack(),0,true,false);
            assertTrue(stats.movementMultiplier()>1);
            assertEquals(1,stats.recoveryRateMultiplier());
            found=true;
        }
        assertTrue(found,"A movement-only Champion must have a positive Extra Fast route");
    }
    @Test void nonMobileAvengerFreezesZeroMovementButKeepsItsDirectStackRoute(){
        var capabilities=EnumSet.allOf(Capability.class);capabilities.remove(Capability.MOBILE);
        boolean found=false;
        for(int i=0;i<20000&&!found;i++){
            var result=planner.plan(request("nonmobile-avenger/"+i,DifficultyId.HELL,2,6,true,capabilities,2));
            if(!result.promoted()||result.rolledRarity()!=EnemyRarity.UNIQUE)continue;
            var leader=result.plan().actors().getFirst();
            if(leader.own(Operator.AVENGER).isEmpty())continue;
            assertEquals(0,leader.own(Operator.AVENGER).orElseThrow().value("perDefeatedMinionMovementIncrease"));
            var first=result.plan().pack().birthRoster().stream().filter(m->m.role()==EnemyPackRecord.Role.MINION).findFirst().orElseThrow();
            var published=result.plan().pack().staged().publish();
            var dead=published.terminalDefeat(first.logicalActorId(),"enemy-death/"+leader.worldId()+"/"+first.nativeEntityId());
            var before=EnemyAffixSnapshot.resolve(leader,balance,published,0,false,false);
            var after=before.withPackProtection(leader,dead,balance);
            assertEquals(1,after.avengerStacks());assertTrue(after.allDirectIncrease()>before.allDirectIncrease());
            assertEquals(1,after.movementMultiplier());
            found=true;
        }
        assertTrue(found,"A non-mobile Unique must retain Avenger's direct positive route");
    }
    @Test void uniqueAdditionalDemandMatchesTheFinalSealedRosterWithoutASecondDraw(){
        int checked=0,horde=0;
        for(int i=0;i<10000&&checked<50;i++){
            var full=request("preview-unique/"+i,DifficultyId.HELL,2,6,true,EnumSet.allOf(Capability.class),2);
            if(planner.rarity(full.seed(),DifficultyId.HELL)!=EnemyRarity.UNIQUE)continue;
            var preview=new EnemyBirthPlanner.Request(full.seed(),full.packId(),full.anchor(),full.nativePlanReceipt(),
                    full.originals(),List.of(),true,0,true);
            var needed=planner.additionalUniqueMembers(preview);
            var finalPlan=planner.plan(full);
            if(!finalPlan.promoted()){assertTrue(needed.isEmpty());continue;}
            assertTrue(needed.isPresent());
            assertEquals(finalPlan.plan().actors().size()-preview.originals().size(),needed.getAsInt());
            assertEquals(finalPlan,planner.plan(full));
            if(finalPlan.plan().actors().getFirst().own(Operator.HORDE).isPresent())horde++;
            checked++;
        }
        assertEquals(50,checked);assertTrue(horde>0,"Preview must also cover native demand caused by Horde");
    }
}
