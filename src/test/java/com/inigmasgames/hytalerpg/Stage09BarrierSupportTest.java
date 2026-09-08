package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage09BarrierSupportTest {
    @Test void secondaryExceptionBeforeApplyDoesNotAcceptOrRetryTransfer(){
        int[] calls={0};var result=SecondaryDamageAttempt.once(()->100,()->{calls[0]++;throw new IllegalStateException("before Apply");});
        assertEquals(1,calls[0]);assertNull(result.completed());assertFalse(result.transferAccepted());assertNotNull(result.failure());
    }
    @Test void secondaryExceptionAfterHealthLossDoesNotDoubleChargeOriginal(){
        double[] hp={100};int[] calls={0};
        var result=SecondaryDamageAttempt.once(()->hp[0],()->{calls[0]++;hp[0]-=8;throw new IllegalStateException("after Apply");});
        assertEquals(1,calls[0]);assertNull(result.completed());assertTrue(result.transferAccepted());assertEquals(92,result.after());
    }
    @Test void nativeCancellationAndIntegerRoundedZeroHaveDifferentTransferOutcomes(){
        var cancelled=SecondaryDamageAttempt.once(()->100,()->new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult(true,8,100,100));
        assertFalse(cancelled.transferAccepted());
        var rounded=SecondaryDamageAttempt.once(()->100,()->new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult(false,0,100,100));
        assertTrue(rounded.transferAccepted());assertNull(rounded.failure());
    }
    @Test void unavailableHealthAfterFailedSecondaryIsNotInvented(){
        int[] reads={0};var result=SecondaryDamageAttempt.once(()->{if(reads[0]++==0)return 100;throw new IllegalStateException("removed");},
                ()->{throw new IllegalStateException("native");});
        assertTrue(Double.isNaN(result.after()));assertFalse(result.transferAccepted());assertEquals(1,result.failure().getSuppressed().length);
    }
    @Test void fourProfilesHaveAuthoredCostsAndDuration(){
        var h=new Harness("pack_howl");
        assertEquals(Stage04SkillProfiles.EXPECTED_STAGE09_PROFILES,h.profiles.all().values().stream().filter(p->p.support()!=null).count());
        for(String id:List.of("pack_howl","reflective_hide","flame_weapon","spirit_shield"))assertEquals(0,h.profiles.require(id).damageCoefficient());
        assertEquals(8,h.profile.resourceCost());assertEquals(16,h.profile.cooldownSeconds());assertEquals(10,h.profile.support().durationSeconds());
    }
    @Test void howlSamplesRecipientsOnceWithoutAuraReservation(){
        var h=new Harness("pack_howl");assertTrue(h.cast().committed());var later=UUID.randomUUID();h.members.add(later);
        assertEquals(92,h.mana);assertEquals(0,h.runtime.auraCount());assertEquals(0,h.reserved);
        assertEquals(.12,h.runtime.finite().movementIncreased(h.world,h.actor,5),1e-9);
        assertEquals(0,h.runtime.finite().movementIncreased(h.world,later,5));assertEquals(0,h.runtime.finite().movementIncreased(h.world,h.actor,10));
    }
    @Test void howlAndBattleCryAddButSameNamedCopiesDoNot(){
        var howl=new Harness("pack_howl");howl.cast();var cry=new Harness("battle_cry");cry.cast();
        var context=world(cry.context,howl.world,howl.actor);
        howl.runtime.finite().apply(context,List.of(howl.actor),8,0);howl.runtime.finite().apply(context,List.of(howl.actor),8,1);
        assertEquals(1.18,howl.runtime.finite().nativeOutgoingFactor(howl.world,howl.actor,2),1e-9);
        assertEquals(.22,howl.runtime.finite().movementIncreased(howl.world,howl.actor,2),1e-9);
        assertEquals(1.33,howl.runtime.finite().damageModifiers(howl.world,howl.actor,UUID.randomUUID(),new ModifierBuckets(List.of(.15),List.of(),List.of(),List.of()),2).factor(),1e-9);
    }
    @Test void nativeMovementAssetSelectionIsExactAndRejectsUnmappedMagnitude(){
        assertEquals("RPG_Howl_Movement",SupportNativeEffects.movementAsset(.12));
        assertEquals("RPG_Rally_Howl_Movement",SupportNativeEffects.movementAsset(.22));
        assertEquals("RPG_Rally_Movement",SupportNativeEffects.movementAsset(.1));
        assertThrows(IllegalStateException.class,()->SupportNativeEffects.movementAsset(.3));
    }
    @Test void dotOutgoingSnapshotSurvivesBuffExpiryWithoutReapplication(){
        var h=new Harness("battle_cry");h.cast();var base=new ModifierBuckets(List.of(.15),List.of(),List.of(),List.of());
        var captured=h.runtime.finite().outgoingModifiers(h.world,h.actor,base,0);assertEquals(1.25,captured.factor(),1e-9);
        assertEquals(1.15,h.runtime.finite().outgoingModifiers(h.world,h.actor,base,9).factor(),1e-9);
        assertEquals(1.25,h.runtime.finite().victimModifiers(h.world,h.actor,UUID.randomUUID(),captured,9).factor(),1e-9);
    }
    @Test void lateOutgoingBuffDoesNotEnterAlreadyCapturedDotSnapshot(){
        var h=new Harness("battle_cry");var captured=h.runtime.finite().outgoingModifiers(h.world,h.actor,ModifierBuckets.NONE,0);h.cast();
        assertEquals(1,h.runtime.finite().victimModifiers(h.world,h.actor,UUID.randomUUID(),captured,1).factor());
        assertEquals(1.1,h.runtime.finite().damageModifiers(h.world,h.actor,UUID.randomUUID(),captured,1).factor(),1e-9);
    }
    @Test void reflectionHasNoShieldOrInitialDamage(){
        var h=new Harness("reflective_hide");assertTrue(h.cast().committed());assertEquals(90,h.mana);assertEquals(40,h.health);
        var e=h.runtime.finite().reflection(h.world,h.actor,1).orElseThrow();assertEquals(.2,e.magnitude());
        assertEquals(50,h.runtime.finite().shieldHit(h.world,h.actor,50,true,1,(shield,amount)->fail("No shield")).remainder());
        assertTrue(h.runtime.finite().reflection(h.world,h.actor,3).isEmpty());
    }
    @Test void reflectionAndRedirectFlagsBlockRecursionLeechAndCredit(){
        for(var origin:List.of(HytaleDamageMetadata.Origin.REFLECTED,HytaleDamageMetadata.Origin.REDIRECTED)){
            var metadata=new HytaleDamageMetadata(UUID.randomUUID(),"root","instance","corr",10,100,"secondary",false,origin);
            assertTrue(SupportDamageSystems.secondaryCannotReflect(metadata));assertTrue(metadata.noCredit());assertTrue(metadata.noLeech());assertFalse(metadata.canProc());
        }
        assertFalse(SupportDamageSystems.secondaryCannotReflect(null));
    }
    @Test void correctedCatalogDoesNotPretendReflectionAbsorbsOrShieldAttacks(){
        var h=new Harness("reflective_hide");var reflect=h.bundle.catalog().skill(new SkillId("reflective_hide")).orElseThrow();
        assertFalse(reflect.linkCompatibilityTags().contains("ABSORBS_DAMAGE"));assertFalse(reflect.canCrit());
        var shield=h.bundle.catalog().skill(new SkillId("spirit_shield")).orElseThrow();assertFalse(shield.canCrit());
        assertFalse(shield.linkCompatibilityTags().contains("DIRECT_HIT"));assertTrue(shield.linkCompatibilityTags().contains("SCALABLE_PAYLOAD"));
    }
    @Test void shieldUsesWisdomPotencyAndNoCrit(){
        var h=new Harness("spirit_shield");h.link("potency",PassiveSlot.PASSIVE01);assertTrue(h.cast().committed());
        assertEquals(82,h.mana);assertEquals(40,h.health);assertEquals(30*1.03*1.15,h.shield(),1e-8);
    }
    @Test void selfShieldDoesNotRedirectAndAbsorbsBeforeHealth(){
        var h=new Harness("spirit_shield");h.cast();var hit=h.runtime.finite().shieldHit(h.world,h.actor,40,true,0,(shield,amount)->fail("Self cannot redirect"));
        assertEquals(30.9,hit.absorbed(),1e-8);assertEquals(9.1,hit.remainder(),1e-8);assertEquals(0,hit.redirected());assertEquals(0,h.runtime.finite().size());
    }
    @Test void allyShieldSplitsOnceBeforeAbsorb(){
        var h=new Harness("spirit_shield");h.target=UUID.randomUUID();h.cast();int[] transfers={0};
        var hit=h.runtime.finite().shieldHit(h.world,h.target,40,true,0,(shield,amount)->{transfers[0]++;assertEquals(8,amount);return true;});
        assertEquals(1,transfers[0]);assertEquals(8,hit.redirected());assertEquals(30.9,hit.absorbed(),1e-8);assertEquals(1.1,hit.remainder(),1e-8);
        assertEquals(40,hit.redirected()+hit.absorbed()+hit.remainder(),1e-8);
    }
    @Test void rejectedRedirectDoesNotEraseTwentyPercentDamage(){
        var h=new Harness("spirit_shield");h.target=UUID.randomUUID();h.cast();
        var hit=h.runtime.finite().shieldHit(h.world,h.target,50,true,0,(shield,amount)->false);
        assertEquals(0,hit.redirected());assertEquals(50,hit.absorbed()+hit.remainder(),1e-8);
    }
    @Test void shieldExpiryAndOwnerRemovalHaveNoRefund(){
        var h=new Harness("spirit_shield");h.cast();
        assertEquals(20,h.runtime.finite().shieldHit(h.world,h.target,20,true,8,(shield,amount)->true).remainder());assertEquals(82,h.mana);
        h.runtime.finite().applyShield(h.context,List.of(h.target),8,40,9);h.runtime.finite().forget(h.actor);assertEquals(0,h.runtime.finite().size());
    }
    @Test void replacementDoesNotAddCapacityAndRespectsNewCap(){
        var h=new Harness("spirit_shield");h.cast();h.runtime.finite().shieldHit(h.world,h.actor,10,false,0,(shield,amount)->false);
        h.runtime.finite().applyShield(h.context,List.of(h.actor),8,20,1);assertEquals(20,h.shield());assertEquals(1,h.runtime.finite().size());
    }
    @Test void severalCasterShieldsStillRedirectOnlyOnce(){
        var h=new Harness("spirit_shield");h.target=UUID.randomUUID();h.cast();var other=new Harness("spirit_shield");other.cast();
        h.runtime.finite().applyShield(world(other.context,h.world,h.target),List.of(h.target),8,30,0);
        int[] count={0};var hit=h.runtime.finite().shieldHit(h.world,h.target,100,true,0,(shield,amount)->{count[0]++;return true;});
        assertEquals(1,count[0]);assertEquals(20,hit.redirected());assertEquals(100,hit.redirected()+hit.absorbed()+hit.remainder(),1e-8);
    }
    @Test void flameWeaponNativeGateRejectsBeforeCostAndCooldown(){
        var h=new Harness("flame_weapon");var r=h.cast();assertFalse(r.committed());assertTrue(r.code().contains("NATIVE_ROOT_WEAPON_CONTACT_ID_UNAVAILABLE"));
        assertEquals(100,h.mana);assertTrue(h.kernel.cooldowns().canActivate(h.actor,"flame_weapon"));assertEquals(0,h.runtime.finite().size());
    }
    @Test void authenticatedFixtureImbueConsumesContactOnce(){
        var h=imbue();var evidence=hit(h,"root","contact",h.target,true,false);var p=h.runtime.imbues().contact(evidence,0).orElseThrow();
        assertEquals(.3,p.fireCoefficient());assertTrue(p.applyBurn());assertEquals(4,p.burnSeconds());assertEquals(.1,p.burnCoefficientPerSecond());
        assertTrue(h.runtime.imbues().contact(evidence,0).isEmpty());assertEquals(85,h.mana);
    }
    @Test void burnHasOneSecondPerTargetIcdButNewContactsStillGetFire(){
        var h=imbue();assertTrue(h.runtime.imbues().contact(hit(h,"r1","c1",h.target,true,false),0).orElseThrow().applyBurn());
        assertFalse(h.runtime.imbues().contact(hit(h,"r2","c2",h.target,true,false),.5).orElseThrow().applyBurn());
        assertTrue(h.runtime.imbues().contact(hit(h,"r3","c3",h.target,true,false),1).orElseThrow().applyBurn());
    }
    @Test void untrustedAndDerivedContactsDoNotCreateImbuePayload(){
        var h=imbue();assertTrue(h.runtime.imbues().contact(hit(h,"r","c",h.target,false,false),0).isEmpty());
        assertTrue(h.runtime.imbues().contact(hit(h,"r","c",h.target,true,true),0).isEmpty());
    }
    @Test void swapAndExpiryTerminateImbue(){
        var h=imbue();var e=new WeaponImbueContacts.Hit(h.world,h.actor,h.target,"r","c","different",true,true,true,false);
        assertTrue(h.runtime.imbues().contact(e,0).isEmpty());assertEquals(0,h.runtime.finite().size());
        var h2=imbue();assertTrue(h2.runtime.imbues().contact(hit(h2,"r","c",h2.target,true,false),12).isEmpty());
    }
    @Test void rootImbueSecondaryBudgetIsSixteen(){
        var h=imbue();for(int i=0;i<16;i++)assertTrue(h.runtime.imbues().contact(hit(h,"r","c"+i,UUID.randomUUID(),true,false),0).isPresent());
        assertThrows(IllegalStateException.class,()->h.runtime.imbues().contact(hit(h,"r","c17",UUID.randomUUID(),true,false),0));
    }
    static Harness imbue(){var h=new Harness("flame_weapon");h.rootContact=true;assertTrue(h.cast().committed());h.target=UUID.randomUUID();return h;}
    static WeaponImbueContacts.Hit hit(Harness h,String root,String contact,UUID target,boolean authenticated,boolean derived){
        return new WeaponImbueContacts.Hit(h.world,h.actor,target,root,contact,"fixture",authenticated,true,true,derived);
    }
    static SkillExecutionContext world(SkillExecutionContext c,UUID world,UUID target){
        return new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),
                new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,target),false);
    }
    static class Harness extends Stage09SupportRuntimeTest.Harness {
        UUID target=actor;boolean rootContact;
        Harness(String skill){super(skill);}
        double shield(){return runtime.finite().forTarget(world,target,0).stream().mapToDouble(FiniteSupportEffects.Effect::shieldRemaining).sum();}
        @Override public boolean rootWeaponContactAvailable(){return rootContact;}
        @Override public Equipment equipment(){var kind=profile.allowedMainHandKinds().stream().findFirst().orElse("STAFF");
            return new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);}
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,target);}
        @Override public void finiteEffect(SkillExecutionContext c,FiniteSupportEffects effects,double now){
            var targets=c.profile().support().recipientBurst()?List.copyOf(members):List.of(target);
            if(c.profile().support().kind()==SupportProfile.Kind.SHIELD)effects.applyShield(c,targets,c.profile().support().durationSeconds(),SupportMagnitude.shield(c,masteryMultiplier(c)),now);
            else effects.apply(c,targets,c.profile().support().durationSeconds(),now);
        }
    }
}
