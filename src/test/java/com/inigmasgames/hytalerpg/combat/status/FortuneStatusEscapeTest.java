package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.combat.attribute.FortuneBreakService;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FortuneStatusEscapeTest {
    private final CombatBalanceProfile balance = CombatBalanceProfile.loadCanonical();
    private final UUID source = UUID.randomUUID(), target = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final AtomicInteger draws = new AtomicInteger();
    private StatusService service(double... rolls) {
        var service = new StatusService(balance, clock::get, new CriticalRoller(() -> {
            int index = draws.getAndIncrement();
            assertTrue(index < rolls.length, "unexpected RNG draw " + index);
            return rolls[index];
        }));
        service.configureFortuneEscape(id -> .2);
        return service;
    }
    @Test void qualificationUsesOnlyAllocationAndConfiguredAnchors() {
        var fortune = new FortuneBreakService(balance);
        assertFalse(fortune.allocated(389, 0).active());
        assertEquals(0, fortune.allocated(389, 0).statusEscapeChance());
        assertEquals(.0125, fortune.allocated(390, 0).statusEscapeChance(), 1e-12);
        assertEquals(.2, fortune.allocated(490, 0).statusEscapeChance());
        assertEquals(.2, fortune.allocated(10000, 0).statusEscapeChance());
        assertEquals(0, fortune.allocated(10000, 1).fortune());
        var raw = new HashMap<>(Map.of("LUCK", 500, "STR", 10, "DEX", 10, "INT", 10, "WIS", 10));
        assertTrue(fortune.savedAllocation(raw).active());
        for (String key : List.of("STR", "DEX", "INT", "WIS")) {
            raw.put(key, 11); assertFalse(fortune.savedAllocation(raw).active()); raw.put(key, 10);
        }
        balance.fortuneBreak.exponent = 2;
        assertEquals(.05, new FortuneBreakService(balance).allocated(390, 0).statusEscapeChance(), 1e-12);
    }
    @Test void protectionImmunityAndNoMutationNeverRoll() {
        var statuses = service();
        var app = StatusApplication.hostile(source, target, RpgStatusType.CHILL);
        assertEquals(StatusService.Admission.PROTECTED, statuses.admit(app, new ControlProfile(true,false,false), true));
        statuses.configureAdmission(id -> 0, (id,type) -> true, (a,b) -> true);
        assertEquals(StatusService.Admission.IMMUNE, statuses.admit(app, ControlProfile.NORMAL, true));
        statuses.configureAdmission(id -> 0, (id,type) -> false, (a,b) -> true);
        assertEquals(StatusService.Admission.NO_STATUS_MUTATION, statuses.admit(app, ControlProfile.NORMAL, false));
        statuses.apply(target,RpgStatusType.FROZEN,ControlProfile.NORMAL);
        assertEquals(StatusService.Admission.NO_STATUS_MUTATION, statuses.admit(app,ControlProfile.NORMAL,true));
        assertEquals(0,draws.get());
    }
    @Test void resistanceRollPrecedesExactlyOneEscapeRoll() {
        var statuses = service(.18, .17, .19, .17, .20);
        statuses.configureAdmission(id -> .5, (id,type) -> false, (a,b) -> true);
        var app = new StatusApplication(source,target,"ME_WEAKENED",StatusApplication.merge(0,.35,1),0,true,false,false,false);
        assertEquals(StatusService.Admission.STATUS_CHANCE_FAILED,statuses.admit(app,ControlProfile.NORMAL,true));
        assertEquals(1,draws.get());
        assertEquals(StatusService.Admission.FORTUNE_STATUS_AVOIDED,statuses.admit(app,ControlProfile.NORMAL,true));
        assertEquals(3,draws.get());
        assertEquals(StatusService.Admission.ACCEPTED,statuses.admit(app,ControlProfile.NORMAL,true));
        assertEquals(5,draws.get());
    }
    @Test void escapedRefreshDoesNotChangeStacksDurationThresholdOrDeepFreezeLock() {
        var statuses = service(.1,.9);
        for(int i=0;i<4;i++)statuses.apply(target,RpgStatusType.CHILL,ControlProfile.NORMAL);
        clock.addAndGet(1_000_000_000L);
        var before=statuses.inspect(target);
        var escaped=statuses.applyChillHostile(source,"root",target,ControlProfile.NORMAL,2,true,6);
        assertEquals("FORTUNE_STATUS_AVOIDED",escaped.bonusGate());
        assertEquals(before,statuses.inspect(target));
        assertEquals(0,statuses.retainedChillBonusCount());
        var applied=statuses.applyChillHostile(source,"root",target,ControlProfile.NORMAL,2,true,6);
        assertEquals(StatusService.Outcome.THRESHOLD,applied.results().getFirst().outcome());
        assertTrue(statuses.inspect(target).active().containsKey(RpgStatusType.FROZEN));
        assertFalse(statuses.inspect(target).active().containsKey(RpgStatusType.CHILL));
        assertEquals(2,draws.get(),"threshold conversion must not roll escape again");
    }
    @Test void escapeDoesNotConsumeControlHistoryAndHasNoCooldown() {
        var statuses=service(.1,.1,.1,.9,.9,.9,.9);
        for(int i=0;i<3;i++)assertEquals("FORTUNE_STATUS_AVOIDED",
                statuses.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).detail());
        assertEquals(2,statuses.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).remainingSeconds());
        assertEquals(1,statuses.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).remainingSeconds());
        assertEquals(.5,statuses.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).remainingSeconds());
        assertEquals(StatusService.Outcome.REJECTED,statuses.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).outcome());
    }
    @Test void exclusionsBypassEscapeWithoutAddingAnAvoidanceTerm() {
        var statuses=service();
        for(var app:List.of(
                new StatusApplication(source,target,"ROOT",1,0,true,true,false,false),
                new StatusApplication(source,target,"ROOT",1,0,true,false,true,false),
                new StatusApplication(source,target,"ROOT",1,0,false,false,false,false),
                StatusApplication.hostile(target,target,RpgStatusType.ROOT),
                StatusApplication.hostile(source,target,RpgStatusType.TAUNT)))
            assertEquals(StatusService.Admission.ACCEPTED,statuses.admit(app,ControlProfile.NORMAL,true));
        assertEquals(0,draws.get());
    }
    @Test void allEligibleStatusFamiliesAndMultiStackPayloadUseSharedGate() {
        for(var type:List.of(RpgStatusType.CHILL,RpgStatusType.BURN,RpgStatusType.POISON,RpgStatusType.ROOT,
                RpgStatusType.FROZEN,RpgStatusType.STAGGER,RpgStatusType.FEAR)) {
            draws.set(0); var statuses=service(.1);
            assertEquals("FORTUNE_STATUS_AVOIDED",statuses.applyHostile(source,target,type,ControlProfile.NORMAL,4).detail());
            assertTrue(statuses.inspect(target).active().isEmpty());
        }
        draws.set(0);var statuses=service(.9,.1);
        assertEquals(2,statuses.applyElectrifiedHostile(source,target,ControlProfile.NORMAL,2,6).stacks());
        clock.addAndGet(1_000_000_000L);var before=statuses.inspect(target);
        assertEquals("FORTUNE_STATUS_AVOIDED",statuses.applyElectrifiedHostile(source,target,ControlProfile.NORMAL,2,6).detail());
        assertEquals(before,statuses.inspect(target));
        statuses.projectPeriodic(target,RpgStatusType.POISON,3,5);
        assertEquals(2,draws.get(),"read-model projection is not a new application");
    }
    @Test void expiredFrozenImmunityCheckedBeforeEscapeWithoutMutatingState() {
        var statuses=service();statuses.apply(target,RpgStatusType.FROZEN,ControlProfile.NORMAL);
        clock.addAndGet(2_000_000_000L);
        assertEquals(StatusService.Admission.IMMUNE,statuses.admit(StatusApplication.hostile(source,target,RpgStatusType.FROZEN),ControlProfile.NORMAL,true));
        assertEquals(0,draws.get());
    }
}
