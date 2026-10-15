package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests lease mechanics with synthetic prerequisites; never certifies a native role or Guardian result. */
class Me12QaAuthorityTest {
    @TempDir Path root;
    @AfterEach void restoreBalance(){EnemyBalance.activate(EnemyBalance.base(),root.resolve("balance-reset"));}

    private HywindWorldConfiguration config() throws Exception {
        var config=new HywindWorldConfiguration(root.resolve("QaWorld"),root.resolve("legacy/world-spawn-density.json"));
        var json=JsonParser.parseString(Files.readString(config.path())).getAsJsonObject();
        json.addProperty("masterEnemiesV2","DRAIN");
        Files.writeString(config.path(),json.toString());config.reload();return config;
    }
    private EnemyWorldAdmission admitted(UUID world){
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(),List.of())),
                new EnemyPackCapacity(EnemyBalance.canonical()),true);
        gate.begin(world).toCompletableFuture().join();gate.rebindComplete(world,List.of());return gate;
    }
    private Path receipt(Me12QaAuthority.Identity identity) throws Exception {
        var path=root.resolve("build/guardian/verification.json");Files.createDirectories(path.getParent());
        var log=root.resolve("build/guardian/check.log");Files.writeString(log,"BUILD SUCCESSFUL\n");
        var reports=new ArrayList<String>();
        for(var test:List.of("NativeEnemyProductionBirthOwnerTest","HroMasterEnemiesTest","Me12QaAuthorityTest")){
            var report=root.resolve("build/test-results/test/TEST-"+test+".xml");
            Files.createDirectories(report.getParent());Files.writeString(report,"<testsuite tests=\"1\" failures=\"0\" errors=\"0\"/>");
            reports.add("{\"path\":\"build/test-results/test/TEST-"+test+".xml\",\"sha256\":\""+hash(report)+"\",\"tests\":1}");
        }
        Files.writeString(path,"""
                {"sourceCommit":"%s","revision":"%s","version":"%s","jarSha256":"%s",
                 "gradleCheck":"PASS","packageValidation":"PASS",
                 "checkLogPath":"build/guardian/check.log","checkLogSha256":"%s","reports":[%s]}
                """.formatted(identity.sourceCommit(),identity.revision(),identity.version(),identity.jarSha256(),
                hash(log),String.join(",",reports)));
        return path;
    }
    private static String hash(Path path) throws Exception {return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    @Test void actualManifestAdmitsCertifiedRolesWithoutInventingProjectileCertificates() throws Exception {
        var config=config();var world=UUID.randomUUID();var gate=admitted(world);
        var bindings=EnemyNativeBindings.load();var now=new AtomicLong(0);
        var identity=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),bindings.assetsSha256());
        var authority=new Me12QaAuthority(config,gate,bindings,()->true,
                ()->bindings.productionEligibilityRejection("Trork_Warrior").isEmpty(),()->identity,now::get);
        authority.activate(new Me12QaAuthority.Request(
                identity.sourceCommit(),world,"QaWorld",30,receiptUnchecked(identity)));
        assertTrue(authority.allows(world,"Trork_Warrior"));
        assertFalse(authority.allows(world,"Goblin_Turret"));
        assertFalse(authority.allows(world,"Scarak_Seeker"));
        assertFalse(authority.allows(world,"Golem_Firesteel"));
        assertFalse(authority.allows(world,"Trork_Warrior_Patrol"));
        assertFalse(authority.allows(world,"Coffer_Goblin_Turret"));
        assertFalse(authority.allows(world,"Unknown_Role"));
    }
    private Path receiptUnchecked(Me12QaAuthority.Identity identity){try{return receipt(identity);}catch(Exception error){throw new RuntimeException(error);}}
    @Test void automaticQaCoversBothRecoveredWorldsButRemainsBoundedAndRevocable() throws Exception {
        var config=config();var first=UUID.randomUUID();var second=UUID.randomUUID();
        var gate=new EnemyWorldAdmission(id->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(),List.of())),
                new EnemyPackCapacity(EnemyBalance.canonical()),true);
        for(var world:List.of(first,second)){gate.begin(world).toCompletableFuture().join();gate.rebindComplete(world,List.of());}
        var bindings=EnemyNativeBindings.load();var now=new AtomicLong();var owners=new AtomicBoolean(true);
        var identity=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                bindings.serverJarSha256(),bindings.assetsSha256());
        var authority=new Me12QaAuthority(config,gate,bindings,owners::get,()->true,()->identity,now::get);
        var proofA=authority.verify(new Me12QaAuthority.Request(identity.sourceCommit(),first,"QaWorld",600,receipt(identity)));
        var proofB=authority.verify(new Me12QaAuthority.Request(identity.sourceCommit(),second,"QaWorld",600,receipt(identity)));
        authority.activateAutomaticVerified(proofA);authority.activateAutomaticVerified(proofB);
        assertTrue(authority.allows(first,"Trork_Warrior"));assertTrue(authority.allows(second,"Trork_Warrior"));
        assertFalse(authority.allows(first,"Goblin_Turret"));
        assertTrue(authority.status(first).startsWith("ACTIVE_AUTO"));
        assertThrows(IllegalStateException.class,()->authority.activateAutomaticVerified(proofA));
        owners.set(false);assertFalse(authority.allows(first));owners.set(true);
        authority.worldUnload(first);assertFalse(authority.allows(first));assertTrue(authority.allows(second));
        now.set(Me12QaAuthority.AUTOMATIC_QA_SECONDS*1_000_000_000L);
        assertFalse(authority.allows(second));
        now.set(0);authority.revoke();assertFalse(authority.allows(second));
        assertThrows(IllegalStateException.class,()->authority.activateAutomaticVerified(
                authority.verify(new Me12QaAuthority.Request(identity.sourceCommit(),second,"QaWorld",600,receipt(identity)))));
    }
    @Test void syntheticPrerequisitesExerciseBoundedLeaseRevocationAndWorldLifetime() throws Exception {
        var config=config();var world=UUID.randomUUID();var gate=admitted(world);
        var bindings=EnemyNativeBindings.load();var now=new AtomicLong(0);var owner=new AtomicBoolean(true);
        var identity=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),bindings.assetsSha256());
        var authority=new Me12QaAuthority(config,gate,bindings,owner::get,()->true,()->identity,now::get);
        var request=new Me12QaAuthority.Request(identity.sourceCommit(),world,"QaWorld",2,receipt(identity));
        assertThrows(IllegalStateException.class,()->authority.activate(new Me12QaAuthority.Request(
                identity.sourceCommit(),UUID.randomUUID(),"QaWorld",2,request.guardianReceipt())));
        authority.activate(request);assertTrue(authority.allows(world));
        assertFalse(authority.allows(UUID.randomUUID()));
        owner.set(false);assertFalse(authority.allows(world));owner.set(true);
        now.set(2_000_000_000L);assertFalse(authority.allows(world));
        now.set(0);authority.activate(request);authority.revoke();assertFalse(authority.allows(world));
        authority.activate(request);authority.worldUnload(world);assertFalse(authority.allows(world));
        authority.activate(request);config.reload();assertFalse(authority.allows(world));
        authority.beginVerification(request);var pending=authority.verify(request);authority.revoke();
        assertThrows(IllegalStateException.class,()->authority.activateVerified(pending));
        authority.beginVerification(request);var unloading=authority.verify(request);authority.worldUnload(world);
        assertThrows(IllegalStateException.class,()->authority.activateVerified(unloading));
        authority.beginVerification(request);var changed=authority.verify(request);config.reload();
        assertThrows(IllegalStateException.class,()->authority.activateVerified(changed));
    }
    @Test void receiptAndIdentityFailuresCannotArm() throws Exception {
        var config=config();var world=UUID.randomUUID();var gate=admitted(world);
        var bindings=EnemyNativeBindings.load();var identity=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),bindings.assetsSha256());
        var authority=new Me12QaAuthority(config,gate,bindings,()->true,()->true,()->identity,()->0);
        var request=new Me12QaAuthority.Request(identity.sourceCommit(),world,"QaWorld",2,receipt(identity));
        assertThrows(IllegalStateException.class,()->authority.activate(new Me12QaAuthority.Request(
                "c".repeat(40),world,"QaWorld",2,request.guardianReceipt())));
        Files.writeString(root.resolve("build/guardian/check.log"),"TAMPERED");
        assertThrows(IllegalStateException.class,()->authority.activate(request));
        Files.writeString(request.guardianReceipt(),"{}");
        assertThrows(IllegalStateException.class,()->authority.activate(request));assertFalse(authority.allows(world));
        assertThrows(IllegalArgumentException.class,()->authority.activate(new Me12QaAuthority.Request(
                identity.sourceCommit(),world,"QaWorld",601,request.guardianReceipt())));
    }
    @Test void historicalOrUnknownServerCannotStandInForTheInstalled072Server() throws Exception {
        var config=config();var world=UUID.randomUUID();var gate=admitted(world);
        var bindings=EnemyNativeBindings.load();
        var patched=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),
                bindings.assetsSha256());
        var request=new Me12QaAuthority.Request(patched.sourceCommit(),world,"QaWorld",30,receipt(patched));
        for(var serverHash:List.of("a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca",
                "c".repeat(64))){
            var runtime=new Me12QaAuthority.Identity(patched.sourceCommit(),patched.revision(),patched.version(),
                    patched.jarSha256(),serverHash,patched.assetsSha256());
            var authority=new Me12QaAuthority(config,gate,bindings,()->true,()->true,()->runtime,()->0);
            assertThrows(IllegalStateException.class,()->authority.activate(request));
            assertFalse(authority.allows(world));
        }
    }
    @Test void missingProductionOwnerEvidenceKeepsTheLeaseClosed() throws Exception {
        var config=config();var world=UUID.randomUUID();var gate=admitted(world);
        var bindings=EnemyNativeBindings.load();
        var identity=new Me12QaAuthority.Identity("a".repeat(40),"rev","version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),bindings.assetsSha256());
        var authority=new Me12QaAuthority(config,gate,bindings,()->true,()->true,()->identity,()->0);
        var request=new Me12QaAuthority.Request(identity.sourceCommit(),world,"QaWorld",30,receipt(identity));
        Files.delete(root.resolve("build/test-results/test/TEST-NativeEnemyProductionBirthOwnerTest.xml"));
        assertThrows(IllegalStateException.class,()->authority.activate(request));
        assertFalse(authority.allows(world,"Trork_Warrior"));
    }
}
