package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage09SupportRuntimeTest {
    @Test void healRunsRealCommitAndWisdomHealingNotDamage(){
        var h=new Harness("minor_heal");assertTrue(h.cast().committed());
        assertEquals(88,h.mana);assertEquals(60.6,h.health,1e-9);assertEquals(0,h.runtime.auraCount());
        assertTrue(h.events.contains("HEAL_RESOLVED"));
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"minor_heal"));
    }
    @Test void healingCapsToMissingAndNoOverhealWithoutOverflow(){
        var h=new Harness("minor_heal");h.health=99;h.cast();assertEquals(100,h.health);
    }
    @Test void auraConsumesReservationOnceAndIsIndefinite(){
        var h=new Harness("emanatism");h.cast();
        for(int i=1;i<=1300;i++)h.tick(i*.1);
        assertEquals(90,h.mana);assertEquals(1,h.runtime.auraCount());assertEquals(.25,h.bonus(h.actor));
    }
    @Test void membershipChangesRemoveAndRestoreOnlyEligibleRecipient(){
        var h=new Harness("emanatism");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        assertEquals(.25,h.bonus(ally));h.members.remove(ally);h.tick(.1);assertEquals(0,h.bonus(ally));
        h.members.add(ally);h.tick(.2);assertEquals(.25,h.bonus(ally));
    }
    @Test void staleMembershipCannotLeakARegenBonus(){
        var h=new Harness("emanatism");h.cast();h.now=1;assertEquals(0,h.bonus(h.actor));
    }
    @Test void disconnectReleasesCapacityWithoutRefundAndPersistsDeficit(){
        var h=new Harness("managuard");h.cast();assertEquals(0,h.hit(20));
        h.runtime.detach(h.actor,"LOGOUT",h);assertEquals(50,h.mana);assertEquals(0,h.reserved);
        assertEquals(20,h.saved.managuard().deficit());assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());
    }
    @Test void damageBeyondShieldReachesNativeRemainder(){
        var h=new Harness("managuard");h.cast();assertEquals(30,h.hit(80));assertEquals(50,h.saved.managuard().deficit());
    }
    @Test void failedDurableAbsorptionCannotGiveFreeShield(){
        var h=new Harness("managuard");h.cast();h.failSave=true;
        assertThrows(IllegalStateException.class,()->h.hit(20));assertEquals(0,h.saved.managuard().deficit());
    }
    @Test void failedAuraSaveRollsBackReservationBeforeAnyLiveEffect(){
        var h=new Harness("managuard");h.failSave=true;h.cast();
        assertEquals(100,h.mana);assertEquals(0,h.reserved);assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());
    }
    @Test void insufficientCurrentManaRejectedBeforeActivation(){
        var h=new Harness("managuard");h.mana=49;
        assertFalse(h.cast().committed());assertEquals(49,h.mana);assertEquals(0,h.budget.size());
    }
    @Test void toggleCannotMintManaOrShield(){
        var h=new Harness("managuard");h.cast();h.hit(40);h.advance(3);
        h.runtime.execute(h.context,h.now,h);assertEquals(50,h.mana);assertEquals(0,h.reserved);
        h.advance(6);h.runtime.execute(h.context,h.now,h);assertEquals(0,h.mana);
        assertEquals(40,h.runtime.state(h.actor).managuard().deficit());assertEquals(10,h.runtime.state(h.actor).managuard().current(50));
    }
    @Test void sixSecondDamageDelayThenTenPercentRecharge(){
        var h=new Harness("managuard");h.cast();h.hit(40);h.advance(6);assertEquals(40,h.runtime.state(h.actor).managuard().deficit(),1e-8);
        h.advance(8);assertEquals(30,h.runtime.state(h.actor).managuard().deficit(),1e-8);
    }
    @Test void fullyAbsorbedHostileHitRestartsRechargeDelay(){
        var h=new Harness("managuard");h.cast();h.hit(20);h.advance(5);h.hit(10);h.advance(10);
        assertEquals(30,h.runtime.state(h.actor).managuard().deficit(),1e-8);
    }
    @Test void unobservedLongGapDoesNotCreditRecharge(){
        var h=new Harness("managuard");h.cast();h.hit(40);h.tick(100);
        assertEquals(40,h.runtime.state(h.actor).managuard().deficit());
    }
    @Test void worldOrLoadoutChangeClearsAuraAndMembership(){
        var h=new Harness("emanatism");h.cast();h.valid="WORLD_CHANGED";h.tick(.1);
        assertEquals(0,h.runtime.auraCount());assertEquals(0,h.bonus(h.actor));assertEquals(90,h.mana);assertEquals(0,h.reserved);
    }
    @Test void noArbitraryMaxLifetimeAndTeardownHasNoFieldLeak(){
        var h=new Harness("managuard");h.cast();h.tick(121);assertEquals(1,h.runtime.auraCount());
        h.runtime.cancel(h.actor,"WORLD_UNLOAD",h);assertEquals(0,h.budget.size());
    }
    @Test void targetOverflowRejectsWholeAuraBeforeReservation(){
        var h=new Harness("emanatism");for(int i=0;i<64;i++)h.members.add(UUID.randomUUID());
        h.cast();assertEquals(0,h.runtime.auraCount());assertEquals(100,h.mana);assertEquals(0,h.budget.size());
    }
    @Test void allocationChangesPreserveDeficitAndNeverRefundMana(){
        var h=new Harness("managuard");h.cast();h.hit(40);h.advance(3);
        h.runtime.allocateManaguard(h.actor,10,h);assertEquals(50,h.mana);assertEquals(10,h.reserved);
        assertEquals(40,h.saved.managuard().deficit());
        h.advance(6);h.runtime.allocateManaguard(h.actor,50,h);
        assertEquals(10,h.mana);assertEquals(40,h.saved.managuard().deficit());assertEquals(10,h.saved.managuard().current(50));
    }
    @Test void allocationWhileInactiveIsSavedWithoutCreatingAuraOrChargingMana(){
        var h=new Harness("managuard");h.runtime.allocateManaguard(h.actor,23,h);
        assertEquals(100,h.mana);assertEquals(0,h.runtime.auraCount());h.cast();assertEquals(77,h.mana);
    }
    @Test void healPotencyAndEchoReuseSnapshotMagnitudeWithoutSecondCost(){
        var h=new Harness("minor_heal");h.link("potency",PassiveSlot.PASSIVE01);h.link("echo",PassiveSlot.PASSIVE02);
        h.health=10;h.cast();h.tick(.45);
        assertEquals(10+20*1.03*1.15*1.7,h.health,1e-8);assertEquals(88,h.mana);
        assertEquals(2,h.events.stream().filter("HEAL_RESOLVED"::equals).count());
    }
    @Test void expandedAuraRadiusDoesNotReduceUtilityRegenOrAlterReservation(){
        var h=new Harness("emanatism");h.link("expanded_radius",PassiveSlot.PASSIVE01);h.cast();
        assertEquals(10,h.lastRadius);assertEquals(.25,h.bonus(h.actor));assertEquals(90,h.mana);
    }
    @Test void failedDownsizeAtZeroManaRestoresReservationWithoutTryingToPayAgain(){
        var h=new Harness("managuard");h.cast();h.mana=0;h.advance(3);h.failSave=true;
        assertThrows(IllegalStateException.class,()->h.runtime.allocateManaguard(h.actor,10,h));
        assertEquals(0,h.mana);assertEquals(50,h.reserved);assertEquals(50,h.saved.managuard().allocationPercent());
        assertEquals(50,h.kernel.reservations().reserved(h.actor,100));
    }
    static class Harness implements SkillExecutionPort,NativeResourcePort,SupportWorldPort {
        final UUID actor=UUID.randomUUID(),world=UUID.randomUUID();
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final RpgCombatKernel kernel=RpgCombatKernel.createProduction();
        final OwnedFieldBudget budget=new OwnedFieldBudget();
        final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());
        final Stage04SkillProfile profile;
        final SupportRuntime runtime;
        final SkillExecutionService execution;
        SupportProgress saved=SupportProgress.INITIAL;
        double mana=100,reserved,health=40,now,lastRadius;boolean failSave;String valid="PASS";
        final List<UUID> members=new ArrayList<>();final List<String> events=new ArrayList<>();
        SkillExecutionContext context;
        Harness(String skill){
            profile=profiles.require(skill);members.add(actor);
            runtime=new SupportRuntime(kernel.reservations(),budget,new SupportProgressStore(){
                public SupportProgress read(UUID id){return saved;}
                public SupportProgress save(UUID id,SupportProgress next){
                    if(failSave)throw new IllegalStateException("fixture disk failure");
                    assertEquals(saved.revision(),next.revision());saved=next.nextRevision();return saved;
                }
            });
            assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
            execution=new SkillExecutionService(bundle.service(),profiles,kernel,SkillExecutorRegistry.runtime(),new SkillInstanceLifecycle(),bundle.tracer(),()->Math.round(now*1e9));
            runtime.tick(actor,0,true,this);
        }
        SkillExecutionResult cast(){return execution.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"test",1,"stage09",Vec3.FORWARD),this);}
        void tick(double time){now=time;runtime.tick(actor,time,true,this);execution.tickScheduled(actor,this);}
        void link(String passive,PassiveSlot slot){assertTrue(bundle.service().equipPassive(actor,slot,new PassiveId(passive)).success());assertTrue(bundle.service().link(actor,LinkNodeId.valueOf(slot.name()),LinkNodeId.SKILL01).success());}
        void advance(double to){while(now<to-1e-8)tick(Math.min(to,now+.1));}
        double hit(double amount){runtime.hostileDamage(actor,now);return runtime.absorb(actor,amount,now,this);}
        double bonus(UUID id){return runtime.manaRegenerationIncreased(id,now);}
        public boolean actorAliveAndUsable(){return true;}
        public Equipment equipment(){return new Equipment(new Item("fixture","STAFF",new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);}
        public NativeResourcePort resources(){return this;}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){var v=runtime.preflight(actor,p.skillId(),p.support(),plan.supportModifiers(),this);return v.equals("PASS")?Validation.pass():Validation.reject(v);}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,actor);}
        public Validation validateRelease(SkillExecutionContext context){return valid.equals("PASS")?Validation.pass():Validation.reject(valid);}
        public SkillExecutionResult executeSupport(SkillExecutionContext value){context=value;return runtime.execute(value,now,this);}
        public SkillExecutionResult executeStrike(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeMovement(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeReaction(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){throw new AssertionError();}
        public double current(ResourceType type){return mana;}
        public double maximum(ResourceType type){return 100;}
        public void setCurrent(ResourceType type,double value){mana=Math.max(0,Math.min(100-reserved,value));}
        public void setReservedMana(double value){reserved=value;mana=Math.min(mana,100-reserved);}
        public String valid(SkillExecutionContext c){return valid;}
        public Health health(UUID target){return new Health(health,100);}
        public UUID nearestAlly(SkillExecutionContext context){return members.stream().filter(id->!id.equals(actor)).findFirst().orElse(null);}
        public List<UUID> allies(SkillExecutionContext c,double radius){lastRadius=radius;return List.copyOf(members);}
        public double heal(SkillExecutionContext c,UUID target,double amount){assertTrue(members.contains(target));double before=health;health=Math.min(100,health+amount);
            runtime.finite().healingResolved(c,target,amount,before,health,100,now);return health-before;}
        public void present(SkillExecutionContext c,double radius,double seconds){}
        public void trace(SkillExecutionContext c,String event,Map<String,?> details){events.add(event);}
    }
}
