package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Test-only lease evidence. It exercises the real verifier; it does not certify Guardian or a native role. */
public final class Me12QaAuthorityFixture {
    private Me12QaAuthorityFixture() {}
    public static Me12QaAuthority activate(Path root,HywindWorldConfiguration config,
            EnemyWorldAdmission admission,EnemyNativeBindings bindings,UUID world,String roleId) throws Exception {
        var json=JsonParser.parseString(Files.readString(config.path())).getAsJsonObject();
        json.addProperty("masterEnemiesV2","DRAIN");Files.writeString(config.path(),json.toString());config.reload();
        var identity=new Me12QaAuthority.Identity("a".repeat(40),"test-revision","test-version","b".repeat(64),
                com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256(),bindings.assetsSha256());
        var receipt=root.resolve("build/guardian/verification.json");Files.createDirectories(receipt.getParent());
        var log=root.resolve("build/guardian/check.log");Files.writeString(log,"BUILD SUCCESSFUL\n");
        var reports=new ArrayList<String>();
        for(var test:List.of("NativeEnemyProductionBirthOwnerTest","HroMasterEnemiesTest","Me12QaAuthorityTest")){
            var report=root.resolve("build/test-results/nativeControlTest/TEST-"+test+".xml");
            Files.createDirectories(report.getParent());
            Files.writeString(report,"<testsuite tests=\"1\" failures=\"0\" errors=\"0\"/>");
            reports.add("{\"path\":\"build/test-results/nativeControlTest/TEST-"+test+".xml\",\"sha256\":\""
                    +hash(report)+"\",\"tests\":1}");
        }
        Files.writeString(receipt,"""
                {"sourceCommit":"%s","revision":"%s","version":"%s","jarSha256":"%s",
                 "gradleCheck":"PASS","packageValidation":"PASS","checkLogPath":"build/guardian/check.log",
                 "checkLogSha256":"%s","reports":[%s]}
                """.formatted(identity.sourceCommit(),identity.revision(),identity.version(),identity.jarSha256(),
                hash(log),String.join(",",reports)));
        var authority=new Me12QaAuthority(config,admission,bindings,()->true,
                ()->bindings.productionEligibilityRejection(roleId).isEmpty(),()->identity,System::nanoTime);
        authority.activate(new Me12QaAuthority.Request(identity.sourceCommit(),world,"QaWorld",60,receipt));
        return authority;
    }
    private static String hash(Path path) throws Exception {return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
