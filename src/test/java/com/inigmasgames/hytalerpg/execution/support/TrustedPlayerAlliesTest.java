package com.inigmasgames.hytalerpg.execution.support;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustedPlayerAlliesTest {
    @TempDir Path directory;
    @Test void mutualConsentPersistsAndRevocationInvalidatesOldGeneration(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var store=new TrustedPlayerAllies(directory);
        assertFalse(store.trusted(a,b));assertTrue(store.request(a,b));
        assertFalse(store.trusted(a,b));assertEquals(java.util.Set.of(a),store.pendingFor(b));
        assertThrows(IllegalStateException.class,()->store.accept(a,b));
        var loaded=new TrustedPlayerAllies(directory);
        assertTrue(loaded.accept(b,a));UUID original=loaded.generation(a,b);
        assertNotNull(original);assertEquals(original,new TrustedPlayerAllies(directory).generation(b,a));
        assertTrue(loaded.revoke(a,b));assertFalse(loaded.trusted(a,b));
        var restarted=new TrustedPlayerAllies(directory);
        assertFalse(restarted.trusted(a,b));
        assertTrue(restarted.request(a,b));assertTrue(restarted.accept(b,a));
        assertNotEquals(original,restarted.generation(a,b));
    }
    @Test void corruptRecordFailsClosedWithoutInferringTrust() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var store=new TrustedPlayerAllies(directory);
        store.request(a,b);store.accept(b,a);
        Files.writeString(directory.resolve("trusted-player-allies.journal"),"corrupt",java.nio.file.StandardOpenOption.APPEND);
        var restarted=new TrustedPlayerAllies(directory);
        assertFalse(restarted.trusted(a,b));
        assertThrows(IllegalStateException.class,()->restarted.request(a,b));
    }
    @Test void noSelfTrustAndNoUnilateralActivation(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();var store=new TrustedPlayerAllies(directory);
        assertThrows(IllegalArgumentException.class,()->store.request(a,a));
        assertThrows(IllegalStateException.class,()->store.accept(a,b));
        store.request(a,b);store.request(b,a);
        assertFalse(store.trusted(a,b));
        assertTrue(store.accept(b,a));assertTrue(store.trusted(a,b));
        assertTrue(store.revoke(b,a));assertFalse(store.trusted(a,b));
    }
}
