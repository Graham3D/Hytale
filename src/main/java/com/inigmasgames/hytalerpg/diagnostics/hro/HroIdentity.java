package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.nio.file.*;
import java.util.*;

/** Worker-only origin fingerprint. No Git, archive hashing or reflection on a gameplay callback. */
final class HroIdentity {
    private HroIdentity() { }
    static Map<String,Object> capture() {
        Map<String,Object> result=new LinkedHashMap<>();
        try(var input=HroIdentity.class.getResourceAsStream("/rpg-build.properties")) {
            Properties properties=new Properties();if(input!=null)properties.load(input);
            properties.forEach((k,v)->result.put(k.toString(),v.toString()));
            Path jar=Path.of(HroIdentity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            result.put("jarSha256",Files.isRegularFile(jar)?HroBundle.sha256(jar):"EXPLODED_CLASSES_NOT_A_JAR");
            var server=Class.forName("com.hypixel.hytale.server.core.HytaleServer",false,HroIdentity.class.getClassLoader());
            Path nativeJar=Path.of(server.getProtectionDomain().getCodeSource().getLocation().toURI());
            result.put("serverSha256",HroBundle.sha256(nativeJar));
            result.put("serverVersion",String.valueOf(server.getPackage().getImplementationVersion()));
            var assets=nativeJar.getParent().getParent().resolve("Assets.zip");
            result.put("assetsSha256",Files.isRegularFile(assets)?HroBundle.sha256(assets):"UNAVAILABLE_RUNTIME_ASSET_ARCHIVE");
        }catch(Exception unavailable){result.put("identityGap",unavailable.getClass().getSimpleName());}
        result.put("clientVersion","UNOBSERVABLE");result.put("assetIdentity","SEE_PACKAGED_OFFLINE_AUDIT_RUNTIME_ARCHIVE_NOT_ASSUMED");
        result.put("registrationWitnesses",HroRuntime.witnesses());
        result.put("preloadRegistration",HroRuntime.witnesses().containsAll(Set.of("CyclonePreload","SharedAegisPreload","AsyncPlayerConnect")));
        try(var input=HroIdentity.class.getResourceAsStream("/hro-native-identity.properties")) {
            Properties nativeIdentity=new Properties();if(input!=null)nativeIdentity.load(input);
            result.put("offlineNativeAudit",Map.copyOf(nativeIdentity));
        }catch(java.io.IOException unavailable){result.put("offlineNativeAudit","UNAVAILABLE");}
        result.put("entrypoint","com.inigmasgames.hywind.HyArpgPlugin");
        var policies=com.inigmasgames.hytalerpg.enemies.Me12Policies.canonical();
        result.put("me12PolicyDigest",policies.digest());result.put("me12PolicyReferences",policies.references());
        result.put("me12World",HroRuntime.enemyIdentity());
        result.put("monsterAffixGate",Map.of("state","OPEN","ownerAccepted",false));
        return Map.copyOf(result);
    }
}
