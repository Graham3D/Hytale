package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyStatusDefenseTest {
    private final UUID source=UUID.randomUUID(),target=UUID.randomUUID();
    @Test void encounterOverlayComposesWithBaseProvidersAndBlocksBeforeRandomOrOpportunity(){
        var guarded=new AtomicBoolean(false);var slow=new AtomicBoolean(true);
        var draws=new java.util.concurrent.atomic.AtomicInteger();var claims=new java.util.concurrent.atomic.AtomicInteger();
        var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->1_000_000_000L,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->{draws.incrementAndGet();return .9;}));
        status.configureAdmission(id->.5,(id,type)->type.equals("POISON"),(a,b)->!java.util.Objects.equals(a,source));
        status.configureSlowImmunity(id->false);
        status.configureEncounterAdmission((id,type)->type.equals("STAGGER"),(a,b)->!guarded.get(),id->slow.get());
        assertFalse(status.allowsExternalMutation(source,target));
        UUID other=UUID.randomUUID();
        assertEquals("IMMUNE",status.applyHostile(other,target,RpgStatusType.POISON,ControlProfile.NORMAL,6).detail());
        assertEquals("IMMUNE",status.applyHostile(other,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,6).detail());
        assertEquals("STATUS_CHANCE_FAILED",status.applyHostile(other,target,RpgStatusType.ROOT,ControlProfile.NORMAL,6).detail());
        status.applySlow(target,"existing",.35,5);assertEquals(0,status.strongestSlow(target).magnitude());
        slow.set(false);assertEquals(.35,status.strongestSlow(target).magnitude());
        guarded.set(true);int before=draws.get();
        var admitted=status.admit(StatusApplication.hostile(other,target,RpgStatusType.ROOT),ControlProfile.NORMAL,true,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->{draws.incrementAndGet();return 0;}),()->{claims.incrementAndGet();return true;});
        assertEquals(StatusService.Admission.PROTECTED,admitted);assertEquals(before,draws.get());assertEquals(0,claims.get());
        assertFalse(status.allowsExternalMutation(null,target));assertFalse(status.allowsExternalMutation(target,target));
        guarded.set(false);assertTrue(status.allowsExternalMutation(null,target));
    }
    @Test void slowImmunityRetainsChillThresholdAndIndependentHardControl(){
        var immune=new AtomicBoolean(true);var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->1_000_000_000L);
        status.configureSlowImmunity(id->immune.get());
        status.applyChillHostile(source,"root",target,ControlProfile.NORMAL,4,false,6);
        status.applySlow(target,"existing-slow",.35,5);
        assertEquals(0,status.strongestSlow(target).magnitude());assertEquals(4,status.inspect(target).active().get(RpgStatusType.CHILL).stacks());
        immune.set(false);assertEquals(.35,status.strongestSlow(target).magnitude());immune.set(true);
        status.applyChillHostile(source,"root2",target,ControlProfile.NORMAL,1,false,6);
        assertTrue(status.inspect(target).active().containsKey(RpgStatusType.FROZEN));assertEquals(0,status.strongestSlow(target).magnitude());
    }
    @Test void stunFamilyImmunityDoesNotBecomeGeneralHardControlImmunity(){
        var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->1_000_000_000L);
        status.configureAdmission(id->0,(id,type)->type.equals("STAGGER")||type.equals("STUN"),(a,b)->true);
        assertEquals("IMMUNE",status.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).detail());
        assertEquals(StatusService.Outcome.APPLIED,status.applyHostile(source,target,RpgStatusType.ROOT,ControlProfile.NORMAL,2).outcome());
        assertEquals(StatusService.Outcome.APPLIED,status.applyHostile(source,target,RpgStatusType.FEAR,ControlProfile.NORMAL,2).outcome());
    }
    @Test void sameProtectionPredicateRejectsApplicationAndExternalRemoval(){
        var guarded=new AtomicBoolean(false);var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->1_000_000_000L);
        status.configureAdmission(id->0,(id,type)->false,(a,b)->!guarded.get());
        status.applyHostile(source,target,RpgStatusType.POISON,ControlProfile.NORMAL,6);var before=status.inspect(target);
        guarded.set(true);assertFalse(status.removeExternal(source,target,RpgStatusType.POISON));
        assertEquals("PROTECTED",status.applyHostile(source,target,RpgStatusType.POISON,ControlProfile.NORMAL,8).detail());assertEquals(before,status.inspect(target));
        guarded.set(false);assertTrue(status.removeExternal(source,target,RpgStatusType.POISON));
    }
}
