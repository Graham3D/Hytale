package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage12SupportCreditTest {
    @TempDir Path directory;
    private final UUID world=UUID.randomUUID(),enemy=UUID.randomUUID(),actor=UUID.randomUUID(),healer=UUID.randomUUID();
    private FileEncounterStore current;
    @AfterEach void closeStore(){if(current!=null)current.close();}
    private FileEncounterStore store(){if(current==null)current=new FileEncounterStore(directory.resolve("encounters"));return current;}
    private EnemyRewardRegistry.Spawn spawn(UUID id){return EnemyRewardRegistry.load().classify(world,id,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,0).orElseThrow();}
    private PersistentEncounterRuntime runtime(){closeStore();current=null;return new PersistentEncounterRuntime(store(),(id,reward)->{});}
    private PersistentEncounterRuntime combat(){var r=runtime();r.attach(world,enemy,"Wolf_Black",Optional.of(spawn(enemy)));assertTrue(r.damage(world,enemy,actor,100,80,100,true,0));return r;}
    private EncounterContributions.Participant member(UUID id){return new EncounterContributions.Participant(id,world,Vec3.ZERO,5,true,null);}
    private List<EncounterContributions.Participant> members(){return List.of(member(actor),member(healer));}
    private PartyMembershipProvider party(){return (world,ids)->Map.of(actor,"server-party",healer,"server-party");}

    @Test void nonOverhealSupportPersistsBeforeDeathAndNeedsNoLastHit(){var r=combat();assertEquals(1,r.heal(world,healer,actor,10,true,1));assertEquals(2,store().load(world,enemy).orElseThrow().credits().size());var restored=runtime();restored.attach(world,enemy,"Wolf_Black",Optional.empty());var shares=restored.death(world,enemy,Vec3.ZERO,2,PartyMembershipProvider.apply(world,members(),party())).orElseThrow().shares();assertEquals(2,shares.size());assertTrue(shares.stream().allMatch(s->s.xp()==65));}
    @Test void overhealDoesNotAddContributor(){var r=combat();assertEquals(0,r.heal(world,healer,actor,0,true,1));assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void disallowedAllyDoesNotAddContributor(){var r=combat();assertEquals(0,r.heal(world,healer,actor,5,false,1));assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void unknownOrStaleBeneficiaryCannotCreateEncounterCredit(){var r=combat();assertEquals(0,r.heal(world,healer,UUID.randomUUID(),5,true,1));assertEquals(0,r.heal(world,healer,actor,5,true,20001));assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void negativeAndNonfiniteHealingCannotCredit(){var r=combat();assertEquals(0,r.heal(world,healer,actor,-1,true,1));assertEquals(0,r.heal(world,healer,actor,Double.POSITIVE_INFINITY,true,1));assertEquals(0,r.heal(world,healer,actor,Double.NaN,true,1));}
    @Test void wrongWorldCannotCreditHealing(){var r=combat();assertEquals(0,r.heal(UUID.randomUUID(),healer,actor,5,true,1));assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void deadOrConvertedEncounterCannotBeRefreshedByHeal(){var r=combat();r.disqualify(world,enemy);assertEquals(0,r.heal(world,healer,actor,5,true,1));}
    @Test void frozenDeathCannotGainLaterHealer(){var r=combat();var first=r.death(world,enemy,Vec3.ZERO,1,members()).orElseThrow();assertEquals(0,r.heal(world,healer,actor,5,true,2));assertEquals(1,first.shares().size());}
    @Test void healCanSupportMultipleRecentEncountersButNotAnUnrelatedOne(){var r=combat();var other=UUID.randomUUID();var unrelated=UUID.randomUUID();for(var id:List.of(other,unrelated))r.attach(world,id,"Wolf_Black",Optional.of(spawn(id)));r.damage(world,other,actor,100,90,100,true,1);assertEquals(2,r.heal(world,healer,actor,5,true,2));assertEquals(2,store().load(world,other).orElseThrow().credits().size());assertTrue(store().load(world,unrelated).orElseThrow().credits().isEmpty());}
    @Test void shieldProviderGetsActualConsumedHostileCredit(){var r=combat();assertTrue(r.absorb(world,enemy,healer,8,true,1));var plan=r.death(world,enemy,Vec3.ZERO,2,PartyMembershipProvider.apply(world,members(),party())).orElseThrow();assertEquals(2,plan.shares().size());assertEquals(EncounterContributions.Kind.ABSORB,store().load(world,enemy).orElseThrow().credits().stream().filter(c->c.player().equals(healer)).findFirst().orElseThrow().kind());}
    @Test void emptyOrFriendlyShieldDoesNotGetSupportCredit(){var r=combat();assertFalse(r.absorb(world,enemy,healer,0,true,1));assertFalse(r.absorb(world,enemy,healer,8,false,1));assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void supportOnlyCannotKeepFarmMasteryActiveForever(){var r=combat();assertTrue(r.absorb(world,enemy,healer,5,true,61000));assertFalse(r.masteryEligible(world,enemy,healer,5,61000));}
    @Test void unavailablePartyProviderExplicitlyKeepsSolo(){var result=PartyMembershipProvider.apply(world,members(),PartyMembershipProvider.UNAVAILABLE);assertTrue(result.stream().allMatch(p->p.partyId()==null));assertEquals("NATIVE_PARTY_PROVIDER_UNAVAILABLE_SOLO_ONLY",PartyMembershipProvider.UNAVAILABLE.availability());}
    @Test void coherentTrustedMembershipProducesOneCommonPot(){var r=combat();r.heal(world,healer,actor,5,true,1);var result=PartyMembershipProvider.apply(world,members(),party());var shares=r.death(world,enemy,Vec3.ZERO,2,result).orElseThrow().shares();assertEquals(130,shares.stream().mapToLong(EncounterContributions.Share::xp).sum());assertTrue(shares.stream().allMatch(s->s.eligiblePartyMembers()==2&&s.insight()==1));}
    @Test void providerCannotSmuggleUnqueriedPlayer(){assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->Map.of(UUID.randomUUID(),"fake")));}
    @Test void providerCannotUseBlankControlOrOversizedPartyIds(){for(String id:List.of(" ","party\nforged","p".repeat(129)))assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->Map.of(actor,id)));}
    @Test void providerCannotReturnNullSnapshotOrNullParty(){assertThrows(NullPointerException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->null));assertThrows(NullPointerException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->{Map<UUID,String> result=new HashMap<>();result.put(actor,null);return result;}));}
    @Test void providerFailureDoesNotSilentlyRepriceAsSolo(){assertThrows(IllegalStateException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->{throw new IllegalStateException("MEMBERSHIP_UNAVAILABLE");}));}
    @Test void providerReceivesImmutableCandidateSet(){assertThrows(UnsupportedOperationException.class,()->PartyMembershipProvider.apply(world,members(),(w,ids)->{ids.clear();return Map.of();}));}
    @Test void partySnapshotCannotChangeAfterReturn(){Map<UUID,String> live=new HashMap<>();live.put(actor,"one");var result=PartyMembershipProvider.apply(world,members(),(w,ids)->live);live.put(actor,"two");assertEquals("one",result.getFirst().partyId());assertThrows(UnsupportedOperationException.class,()->result.clear());}
    @Test void missingMembershipMeansSoloNotInferredAlliance(){var result=PartyMembershipProvider.apply(world,members(),(w,ids)->Map.of(actor,"one"));assertEquals("one",result.getFirst().partyId());assertNull(result.getLast().partyId());}
    @Test void candidateWorldLoadedAndUniquenessAreValidated(){assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(world,List.of(member(actor),member(actor)),party()));assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(UUID.randomUUID(),members(),party()));assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(world,List.of(new EncounterContributions.Participant(actor,world,Vec3.ZERO,5,false,null)),party()));}
    @Test void candidateBudgetIsExplicitBeforeCallingProvider(){var candidates=new ArrayList<EncounterContributions.Participant>();for(int i=0;i<257;i++)candidates.add(member(UUID.randomUUID()));assertThrows(IllegalArgumentException.class,()->PartyMembershipProvider.apply(world,candidates,(w,ids)->{fail();return Map.of();}));}
    @Test void persistedPartyDeathNeverRerollsAfterPartyChange(){var r=combat();r.heal(world,healer,actor,5,true,1);var first=r.death(world,enemy,Vec3.ZERO,2,PartyMembershipProvider.apply(world,members(),party())).orElseThrow();var replay=runtime().death(world,enemy,new Vec3(100,0,0),5000,PartyMembershipProvider.apply(world,members(),PartyMembershipProvider.UNAVAILABLE)).orElseThrow();assertEquals(first,replay);assertEquals(65,replay.shares().getFirst().xp());}
}
