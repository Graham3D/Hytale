package com.inigmasgames.hytalerpg.execution.support;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CycloneSharedAegisLedgerTest {
    @TempDir Path directory;
    private static CycloneBarrierLedger.State paid(UUID caster,UUID generation){
        return new CycloneBarrierLedger.State(CycloneBarrierLedger.SCHEMA,caster,"spirit_shield",
                generation,UUID.randomUUID(),4,CycloneBarrierLedger.Phase.ACTIVE,161,161,"paid","cooldown",100);
    }
    @Test void childUsesFrozenHalfCapacityAndNeverRefillsAfterRestart(){
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),generation=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,gen)->false);var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,generation),recipient,lease));
        assertEquals(80.5,journal.view(lease).capacity());
        assertEquals(30,journal.debit(lease,"first",30).absorbed());
        assertTrue(journal.debit(lease,"first",30).duplicate());
        assertEquals(50.5,journal.view(lease).remaining());
        journal.detach(lease);
        var restarted=new CycloneSharedAegisLedger(directory);
        restarted.preload(recipient,(id,gen)->id.equals(caster)&&gen.equals(generation));
        var recovered=restarted.attachPrepared(recipient);
        assertEquals(50.5,restarted.view(recovered).remaining());
        assertTrue(restarted.debit(recovered,"first",30).duplicate());
        assertEquals(50.5,restarted.debit(recovered,"second",51).absorbed());
        assertNull(restarted.view(recovered));
    }
    @Test void missingNativePaymentProofForfeitsChildWithoutAnotherCharge(){
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),generation=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,gen)->false);var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,generation),recipient,lease));
        assertEquals(20,journal.debit(lease,"first",20).absorbed());journal.detach(lease);
        var restarted=new CycloneSharedAegisLedger(directory);
        restarted.preload(recipient,(id,gen)->false);var recovered=restarted.attachPrepared(recipient);
        assertNull(restarted.view(recovered));
        assertFalse(restarted.grant(paid(caster,generation),recipient,recovered));
        assertEquals(0,restarted.debit(recovered,"later",20).absorbed());
        restarted.detach(recovered);
        var later=new CycloneSharedAegisLedger(directory);
        later.preload(recipient,(id,gen)->true);
        assertNull(later.view(later.attachPrepared(recipient)));
    }
    @Test void parentIntentCannotSelectTwoDifferentRecipients(){
        UUID caster=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID(),generation=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);var parent=paid(caster,generation);
        journal.preload(first,(id,gen)->false);journal.preload(second,(id,gen)->false);
        var firstLease=journal.attachPrepared(first);var secondLease=journal.attachPrepared(second);
        assertTrue(journal.grant(parent,first,firstLease));
        assertFalse(journal.grant(parent,second,secondLease));
        assertNull(journal.view(secondLease));
        assertTrue(journal.grant(parent,first,firstLease));
        assertEquals(80.5,journal.view(firstLease).remaining());
    }
    @Test void revocationAndRenewedConsentCannotReviveAnOldChild(){
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),parent=UUID.randomUUID();
        var trust=new TrustedPlayerAllies(directory.resolve("trust"));
        trust.request(caster,recipient);trust.accept(recipient,caster);
        UUID firstConsent=trust.generation(caster,recipient);
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,generation)->false);
        var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,parent),recipient,lease,firstConsent));
        assertEquals(12,journal.debit(lease,"accepted-hit",12,
                grant->grant.trustGeneration().equals(trust.generation(caster,recipient))).absorbed());
        trust.revoke(caster,recipient);
        assertEquals(0,journal.debit(lease,"revoked-hit",12,
                grant->grant.trustGeneration().equals(trust.generation(caster,recipient))).absorbed());
        journal.detach(lease);
        trust.request(caster,recipient);trust.accept(recipient,caster);
        assertNotEquals(firstConsent,trust.generation(caster,recipient));
        var restarted=new CycloneSharedAegisLedger(directory);
        restarted.preload(recipient,(id,generation)->true,
                grant->grant.trustGeneration().equals(trust.generation(caster,recipient)));
        assertNull(restarted.view(restarted.attachPrepared(recipient)));
    }
    @Test void staleSessionAndDeathCannotRestoreConfirmedDamage(){
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),parent=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,generation)->false);
        var first=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,parent),recipient,first));
        assertEquals(20,journal.debit(first,"hit",20).absorbed());
        journal.detach(first);
        var second=journal.attachPrepared(recipient);
        assertThrows(IllegalStateException.class,()->journal.debit(first,"late-hit",20));
        journal.clearForDeath(second);
        journal.detach(second);
        var restarted=new CycloneSharedAegisLedger(directory);
        restarted.preload(recipient,(id,generation)->true);
        assertNull(restarted.view(restarted.attachPrepared(recipient)));
    }
    @Test void damagedChildJournalIsQuarantinedWithoutRestoringProtection() throws Exception {
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),parent=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,generation)->false);
        var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,parent),recipient,lease));
        assertEquals(20,journal.debit(lease,"hit",20).absorbed());
        journal.detach(lease);
        java.nio.file.Files.writeString(directory.resolve("shared-aegis/children")
                .resolve(recipient.toString()).resolve(parent+".journal"),"torn");
        var restarted=new CycloneSharedAegisLedger(directory);
        assertTrue(restarted.childGrants(recipient).isEmpty());
        restarted.preload(recipient,(id,generation)->true);
        var after=restarted.attachPrepared(recipient);
        assertNull(restarted.view(after));
        assertEquals(0,restarted.debit(after,"later",20).absorbed());
    }
    @Test void casterDeathForfeitsDisconnectedRecipientsWithoutTouchingOtherCasters(){
        UUID caster=UUID.randomUUID(),otherCaster=UUID.randomUUID();
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(first,(id,generation)->false);journal.preload(second,(id,generation)->false);
        var firstLease=journal.attachPrepared(first);var secondLease=journal.attachPrepared(second);
        assertTrue(journal.grant(paid(caster,UUID.randomUUID()),first,firstLease));
        assertTrue(journal.grant(paid(otherCaster,UUID.randomUUID()),second,secondLease));
        journal.detach(firstLease);journal.detach(secondLease);
        journal.clearCasterForDeath(caster);
        var restarted=new CycloneSharedAegisLedger(directory);
        restarted.preload(first,(id,generation)->true);restarted.preload(second,(id,generation)->true);
        assertNull(restarted.view(restarted.attachPrepared(first)));
        assertNotNull(restarted.view(restarted.attachPrepared(second)));
    }
    @Test void concurrentHitsNeverAbsorbMoreThanTheFrozenChildCapacity() throws Exception {
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),parent=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,generation)->false);
        var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,parent),recipient,lease));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
        try{
            var ready=new java.util.concurrent.CountDownLatch(4);
            var start=new java.util.concurrent.CountDownLatch(1);
            var jobs=new java.util.ArrayList<java.util.concurrent.Future<Double>>();
            for(int t=0;t<4;t++){
                int worker=t;
                jobs.add(pool.submit(()->{
                    ready.countDown();start.await();double sum=0;
                    for(int i=0;i<10;i++)sum+=journal.debit(lease,"hit-"+worker+"-"+i,3).absorbed();
                    return sum;
                }));
            }
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            double total=0;
            for(var job:jobs)total+=job.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(80.5,total,.001);
            journal.detach(lease);
            var restarted=new CycloneSharedAegisLedger(directory);
            restarted.preload(recipient,(id,generation)->true);
            var recovered=restarted.attachPrepared(recipient);
            assertNull(restarted.view(recovered));
            assertEquals(0,restarted.debit(recovered,"fresh-hit",5).absorbed());
        }finally{pool.shutdownNow();}
    }
    @Test void depletedChildCompactionKeepsParentIdempotencyWithoutLifetimeGrantExhaustion(){
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,generation)->false);
        var lease=journal.attachPrepared(recipient);
        for(int i=0;i<40;i++){
            UUID generation=UUID.randomUUID();var parent=paid(caster,generation);
            assertTrue(journal.grant(parent,recipient,lease),"grant "+i);
            assertEquals(80.5,journal.debit(lease,"finish-"+i,100).absorbed(),.001);
            assertFalse(journal.hasUnspentGrant(generation));
            assertFalse(journal.grant(parent,recipient,lease),"closed parent cannot grant again");
        }
        assertNull(journal.view(lease));
        journal.detach(lease);
        var restarted=new CycloneSharedAegisLedger(directory);
        assertTrue(restarted.childGrants(recipient).isEmpty());
        restarted.preload(recipient,(id,generation)->true);
        assertNull(restarted.view(restarted.attachPrepared(recipient)));
    }
    @Test void failedForcedChildWriteCannotReduceNativeDamage() throws Exception {
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),generation=UUID.randomUUID();
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,parent)->false);var lease=journal.attachPrepared(recipient);
        assertTrue(journal.grant(paid(caster,generation),recipient,lease));
        var child=directory.resolve("shared-aegis/children").resolve(recipient.toString())
                .resolve(generation+".journal");
        java.nio.file.Files.delete(child);
        java.nio.file.Files.createDirectory(child);
        var rejected=journal.debit(lease,"io-failure",12);
        assertEquals(0,rejected.absorbed());
        assertEquals(12,rejected.nativeRemainder());
        assertNull(journal.view(lease));
    }
    @Test void forcedParentIntentWithFailedChildAdmissionCanRetryOnlyItsOriginalRecipient() throws Exception {
        UUID caster=UUID.randomUUID(),recipient=UUID.randomUUID(),other=UUID.randomUUID();
        UUID generation=UUID.randomUUID();var parent=paid(caster,generation);
        var journal=new CycloneSharedAegisLedger(directory);
        journal.preload(recipient,(id,gen)->false);journal.preload(other,(id,gen)->false);
        var lease=journal.attachPrepared(recipient);var otherLease=journal.attachPrepared(other);
        var child=directory.resolve("shared-aegis/children").resolve(recipient.toString())
                .resolve(generation+".journal");
        java.nio.file.Files.createDirectories(child);
        assertThrows(RuntimeException.class,()->journal.grant(parent,recipient,lease));
        assertFalse(journal.grant(parent,other,otherLease));
        java.nio.file.Files.delete(child);
        assertTrue(journal.grant(parent,recipient,lease));
        assertEquals(80.5,journal.view(lease).remaining());
    }
}
