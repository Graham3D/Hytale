package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.support.CycloneActivationTransaction;
import com.inigmasgames.hytalerpg.execution.support.CycloneArmorFormula;
import com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger;
import com.inigmasgames.hytalerpg.execution.support.SupportMagnitude;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CycloneDurableLiveTest {
    @TempDir Path root;

    private static final class Live extends Stage09SupportRuntimeTest.Harness {
        final CycloneBarrierLedger ledger;
        final CycloneBarrierLedger.Lease lease;
        final CycloneActivationTransaction activation;
        UUID generation;
        int presentations;
        java.util.concurrent.CompletableFuture<Void> authorityGate;
        Live(Path root){
            super("spirit_shield");
            ledger=new CycloneBarrierLedger(root);lease=ledger.attach(actor);
            activation=new CycloneActivationTransaction(ledger);
        }
        @Override public boolean durableCycloneEnabled(){return true;}
        @Override public java.util.concurrent.CompletionStage<Void> prepareDurable(SkillExecutionContext context){
            return authorityGate==null?java.util.concurrent.CompletableFuture.completedStage(null):authorityGate;
        }
        @Override public CycloneAuthority cycloneAuthority(){return new CycloneAuthority(activation,lease);}
        @Override public Validation familyPrerequisites(Stage04SkillProfile profile,CompiledSkillPlan plan){
            if(ledger.state(lease).phase()==CycloneBarrierLedger.Phase.ACTIVE)
                return Validation.reject("CYCLONE_BARRIER_ALREADY_ACTIVE_OR_PENDING");
            return super.familyPrerequisites(profile,plan);
        }
        @Override public void cyclonePublished(SkillExecutionContext context,UUID generation){this.generation=generation;}
        @Override public SkillExecutionResult executeSupport(SkillExecutionContext context){
            this.context=context;presentations++;
            return SkillExecutionResult.committed("CYCLONE_BARRIER_ACTIVE",1,0);
        }
        @Override public double maximum(ResourceType type){return type==ResourceType.MANA?200:100;}
        @Override public void setCurrent(ResourceType type,double value){mana=Math.max(0,Math.min(maximum(type)-reserved,value));}
        @Override public void setReservedMana(double value){reserved=value;mana=Math.min(mana,maximum(ResourceType.MANA)-reserved);}
    }

    @Test void balanceUsesEffectiveBaselineAndSixtyPercentAtTwenty(){
        var balance=CombatBalanceProfile.loadCanonical();
        double baseline=new EffectiveAttributeService(balance).effective(balance.startingRawAttribute);
        assertEquals(100,CycloneArmorFormula.durableCapacity(baseline,baseline,1));
        assertEquals(160,CycloneArmorFormula.durableCapacity(baseline,baseline,20));
        assertEquals(240,CycloneArmorFormula.durableCapacity(baseline+50,baseline,20));
        assertEquals(320,CycloneArmorFormula.durableCapacity(baseline+50,baseline+50,20));
        assertEquals(130,CycloneArmorFormula.durableCapacity(baseline+20,baseline+10,1));
        assertEquals(106,CycloneArmorFormula.durableCapacity(baseline,baseline,3));
        assertThrows(IllegalArgumentException.class,()->CycloneArmorFormula.durableCapacity(baseline,baseline,21));
    }

    @Test void castChargesAllSpendableManaAndCreatesOnlyDurableBarrier(){
        var h=new Live(root);h.mana=175;
        var cast=h.cast();
        assertTrue(cast.committed(),cast.toString());
        assertEquals(0,h.mana);
        assertEquals(1,h.presentations);
        assertEquals(0,h.runtime.finite().size());
        var state=h.ledger.state(h.lease);
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,state.phase());
        assertEquals(175,state.manaPaid());
        assertEquals(100,state.capacity());
        assertEquals(h.generation,state.generation());
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"spirit_shield"));
        var hit=h.ledger.debit(h.lease,h.generation,"hit-1",35);
        assertEquals(35,hit.absorbed());
        assertEquals(65,h.ledger.state(h.lease).remaining());
        assertEquals(0,h.ledger.debit(h.lease,h.generation,"hit-1",35).absorbed());
    }

    @Test void reservationAndMinimumAreEnforcedBeforePayment(){
        var h=new Live(root);h.mana=200;
        h.kernel.reservations().addPercentage(h.actor,"managuard",.5,h);
        assertEquals(100,h.mana);
        var exact=h.cast();assertTrue(exact.committed(),exact.toString());
        assertEquals(0,h.mana);assertEquals(100,h.ledger.state(h.lease).manaPaid());
        var low=new Live(root.resolve("low"));low.mana=99;
        var rejected=low.cast();assertFalse(rejected.committed());
        assertEquals(99,low.mana);
        assertEquals(CycloneBarrierLedger.Phase.NONE,low.ledger.state(low.lease).phase());
        assertTrue(low.kernel.cooldowns().canActivate(low.actor,"spirit_shield"));
    }

    @Test void activeRecastRejectsAndDepletedGenerationMayRecastAfterCooldown(){
        var h=new Live(root);h.mana=150;assertTrue(h.cast().committed());
        h.mana=150;
        var rejected=h.cast();assertFalse(rejected.committed());
        assertEquals(150,h.mana);assertEquals(1,h.presentations);
        var hit=h.ledger.debit(h.lease,h.generation,"last",150);
        assertEquals(100,hit.absorbed());assertEquals(50,hit.damageRemaining());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,h.ledger.state(h.lease).phase());
        h.kernel.cooldowns().clear(h.actor,"spirit_shield");
        var again=h.cast();assertTrue(again.committed(),again.toString());
        assertEquals(2,h.presentations);assertEquals(0,h.mana);
        assertNotEquals(hit.generation(),h.generation);
    }

    @Test void costReductionAndHealthConversionCannotBypassFullManaPayment(){
        var efficient=new Live(root.resolve("efficient"));efficient.mana=101;
        efficient.link("efficiency",PassiveSlot.PASSIVE01);
        assertTrue(efficient.cast().committed());
        assertEquals(0,efficient.mana);
        assertEquals(101,efficient.ledger.state(efficient.lease).manaPaid());
        var converted=new Live(root.resolve("converted"));converted.mana=101;
        converted.link("lifeblood",PassiveSlot.PASSIVE01);
        assertTrue(converted.cast().committed());
        assertEquals(0,converted.mana);
        assertEquals(101,converted.ledger.state(converted.lease).manaPaid());
    }

    @Test void reflectiveWardUsesOnlyConfirmedDebitAndNoFiniteShield(){
        var h=new Live(root);h.mana=120;h.link("reflective_ward",PassiveSlot.PASSIVE01);
        assertTrue(h.cast().committed());
        assertEquals(80,h.ledger.state(h.lease).capacity());
        assertEquals(0,h.runtime.finite().size());
        var first=h.ledger.debit(h.lease,h.generation,"ward-1",20);
        assertEquals(4,SupportMagnitude.wardReflection(h.context,first.absorbed()));
        var duplicate=h.ledger.debit(h.lease,h.generation,"ward-1",20);
        assertEquals(0,SupportMagnitude.wardReflection(h.context,duplicate.absorbed()));
        var last=h.ledger.debit(h.lease,h.generation,"ward-last",100);
        assertEquals(60,last.absorbed());
        assertEquals(12,SupportMagnitude.wardReflection(h.context,last.absorbed()));
        assertEquals(40,last.damageRemaining());
    }

    @Test void presentationIsInfiniteAndUsesAnActualStatusIcon() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/Server/Entity/Effects/RPG/RPG_Cyclone_Armor_Durable.json"))).getAsJsonObject();
        assertTrue(json.get("Infinite").getAsBoolean());
        assertFalse(json.has("Duration"));
        var icon=json.get("StatusEffectIcon").getAsString();
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/Common").resolve(icon)));
    }

    @Test void reconnectForfeitsProtectionWithoutAnotherManaCharge(){
        var h=new Live(root);h.mana=150;assertTrue(h.cast().committed());
        assertEquals(0,h.mana);
        h.ledger.detach(h.lease);
        h.ledger.preload(h.actor);
        var reconnect=h.ledger.attachPrepared(h.actor);
        assertTrue(h.ledger.paymentRecoveryRequired(reconnect));
        h.ledger.invalidateUncertainPayment(reconnect);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,h.ledger.state(reconnect).phase());
        assertEquals(0,h.mana);
        assertEquals(0,h.ledger.debit(reconnect,h.generation,"after-reconnect",20).absorbed());
    }

    @Test void deathForfeitsBarrierWithoutRestoringMana(){
        var h=new Live(root);h.mana=140;assertTrue(h.cast().committed());
        h.ledger.clearForDeath(h.lease);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,h.ledger.state(h.lease).phase());
        assertEquals(0,h.mana);
        assertEquals(0,h.ledger.debit(h.lease,h.generation,"post-death",15).absorbed());
    }

    @Test void sharedAegisLinkAllowsPaidParentActivation(){
        var h=new Live(root);h.mana=150;h.link("shared_aegis",PassiveSlot.PASSIVE01);
        var result=h.cast();
        assertTrue(result.committed(),result.toString());
        assertEquals(0,h.mana);
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,h.ledger.state(h.lease).phase());
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"spirit_shield"));
    }

    @Test void finalCommitRequotesTheEntireCurrentSpendableBalance(){
        var h=new Live(root);h.mana=120;h.authorityGate=new java.util.concurrent.CompletableFuture<>();
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());
        h.mana=175;h.authorityGate.complete(null);
        var completed=h.execution.completePersistence(h.actor,h);
        assertTrue(completed.committed(),completed.toString());
        assertEquals(0,h.mana);
        assertEquals(175,h.ledger.state(h.lease).manaPaid());
    }

    @Test void fallingBelowMinimumDuringPendingCommitRejectsWithoutPayment(){
        var h=new Live(root);h.mana=120;h.authorityGate=new java.util.concurrent.CompletableFuture<>();
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());
        h.mana=99;h.authorityGate.complete(null);
        var completed=h.execution.completePersistence(h.actor,h);
        assertFalse(completed.committed());
        assertEquals(99,h.mana);
        assertEquals(CycloneBarrierLedger.Phase.NONE,h.ledger.state(h.lease).phase());
        assertTrue(h.kernel.cooldowns().canActivate(h.actor,"spirit_shield"));
    }

    @Test void failedPrecommitAuthorityCannotPublishOrCharge(){
        var h=new Live(root);h.mana=130;h.authorityGate=new java.util.concurrent.CompletableFuture<>();
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());
        h.authorityGate.completeExceptionally(new IllegalStateException("DURABLE_PREPARE_FAILED"));
        var result=h.execution.completePersistence(h.actor,h);
        assertFalse(result.committed());
        assertEquals(130,h.mana);
        assertEquals(CycloneBarrierLedger.Phase.NONE,h.ledger.state(h.lease).phase());
        assertTrue(h.kernel.cooldowns().canActivate(h.actor,"spirit_shield"));
    }

    @Test void liveActivationFreezesEffectiveStats(){
        var h=new Live(root);h.mana=150;
        assertTrue(h.bundle.service().setDevelopmentAttribute(h.actor,RpgAttribute.WIS,150).success());
        assertTrue(h.bundle.service().setDevelopmentAttribute(h.actor,RpgAttribute.INT,110).success());
        assertTrue(h.cast().committed());
        var stats=h.context.snapshot().derivedStats();
        double expected=CycloneArmorFormula.durableCapacity(stats.effective(RpgAttribute.WIS),
                stats.effective(RpgAttribute.INT),h.context.effectiveSkillLevel());
        assertEquals(expected,h.ledger.state(h.lease).capacity());
        assertTrue(h.bundle.service().setDevelopmentAttribute(h.actor,RpgAttribute.WIS,10).success());
        assertTrue(h.bundle.service().setDevelopmentAttribute(h.actor,RpgAttribute.INT,10).success());
        assertEquals(expected,h.ledger.state(h.lease).capacity());
    }
}
