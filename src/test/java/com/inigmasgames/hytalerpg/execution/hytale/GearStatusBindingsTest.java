package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.ControlledGearSnapshot;
import com.inigmasgames.hytalerpg.combat.status.GearStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearStatusBindingsTest {
    @Test void acceptedNativeSourceClassifiesOnlyChannelsWithPositivePower() {
        var snapshot=ControlledGearSnapshot.with("WA-053");
        var hit=new GearCombatEffects.Hit(snapshot.items().getFirst().identity(),"revision","root",
                Map.of(GearCombatEffects.Channel.PHYSICAL,100d,GearCombatEffects.Channel.FIRE,20d),
                Map.of(),Map.of(),false,1.5,snapshot,null);
        var context=NativeGearContactContext.basic(hit,UUID.randomUUID(),UUID.randomUUID(),.35,4,6);
        assertTrue(GearStatusBindings.requires(hit));
        assertFalse(GearStatusBindings.requires(new GearCombatEffects.Hit(null,"revision","control",Map.of(),
                Map.of(),Map.of(),false,1.5,GearEffectSnapshot.EMPTY,null)));
        assertEquals(.35,context.itemProcCoefficient());
        assertEquals(12,context.periodicProfiles().get(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BLEED).damagePerSecond());
        assertEquals(2,context.periodicProfiles().get(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.BURN).damagePerSecond());
        assertEquals(6,context.periodicProfiles().get(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.POISON).damagePerSecond());
        assertEquals(3,context.periodicProfiles().get(com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind.POISON).sourceCap());
        assertThrows(IllegalArgumentException.class,()->NativeGearContactContext.basic(hit,UUID.randomUUID(),UUID.randomUUID(),1.1,4,6));
        assertEquals(GearStatusBindings.stableRoll("root",context.actorId(),RpgStatusType.BLEED,"opportunity"),
                GearStatusBindings.stableRoll("root",context.actorId(),RpgStatusType.BLEED,"opportunity"));
        assertNotEquals(GearStatusBindings.stableRoll("root",context.actorId(),RpgStatusType.BLEED,"opportunity"),
                GearStatusBindings.stableRoll("root",context.actorId(),RpgStatusType.BLEED,"source"));
    }
    @Test void rootGetsOneSharedSkillAndItemOpportunityEvenWithMultipleContacts() {
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var contacts=new GearStatusRuntime.Contacts();var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var gear=ControlledGearSnapshot.with("WA-056");
        var hit=new GearStatusRuntime.AppliedHit(actor,"same-root","first",victim,true,true,true,false,10,.5,
                Map.of(GearCombatEffects.Channel.WATER,10d));
        var first=GearStatusRuntime.admit(statuses,contacts,hit,RpgStatusType.CHILL,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,.2,0,0,null);
        assertEquals("ADMITTED",first.gate());assertTrue(first.skillSucceeded());assertTrue(first.gearSucceeded());
        var second=GearStatusRuntime.admit(statuses,contacts,new GearStatusRuntime.AppliedHit(actor,"same-root","second",
                victim,true,true,true,false,10,.5,Map.of(GearCombatEffects.Channel.WATER,10d)),
                RpgStatusType.CHILL,ControlProfile.NORMAL,gear,GearEffectSnapshot.EMPTY,0,.2,0,0,null);
        assertEquals("DUPLICATE_CONTACT",second.gate());
        assertEquals(1,statuses.inspect(victim).active().get(RpgStatusType.CHILL).stacks());
        var control=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),hit,RpgStatusType.CHILL,
                ControlProfile.NORMAL,GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,0,.2,.5,0,null);
        assertEquals("CHANCE_MISS",control.gate());
        var negative=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                new GearStatusRuntime.AppliedHit(actor,"other","physical",victim,true,true,true,false,10,.5,
                        Map.of(GearCombatEffects.Channel.PHYSICAL,10d)),RpgStatusType.CHILL,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,.2,0,0,null);
        assertEquals("INELIGIBLE_HIT",negative.gate());
    }
    @Test void channelSteadinessAppliesAtAdmissionOnlyAndPlayerSlowKeepsExactValue(){
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var gear=ControlledGearSnapshot.focus("WA-109");
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        var active=statuses.apply(first,RpgStatusType.STUN,ControlProfile.NORMAL,2,gear,true);
        var idle=statuses.apply(second,RpgStatusType.STUN,ControlProfile.NORMAL,2,gear,false);
        assertEquals(2*(1-gear.percent("WA-109")),active.remainingSeconds(),1e-9);
        assertEquals(2,idle.remainingSeconds(),1e-9);
        assertEquals(.137f,NativePlayerStatusMovement.contributedSpeed(.2f,.315),1e-6f);
        assertEquals(.2f,NativePlayerStatusMovement.contributedSpeed(.2f,0));
        assertThrows(IllegalArgumentException.class,()->NativePlayerStatusMovement.contributedSpeed(.2f,-.01));
    }
}
