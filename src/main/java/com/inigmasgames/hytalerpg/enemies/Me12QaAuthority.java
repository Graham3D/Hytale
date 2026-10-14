package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.phase00.BuildIdentity;
import com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.*;

/** A short-lived, world-scoped QA lease. It never changes world configuration or production ENABLED. */
public final class Me12QaAuthority {
    public record Identity(String sourceCommit,String revision,String version,String jarSha256,
                           String serverSha256,String assetsSha256){
        public Identity{Objects.requireNonNull(sourceCommit);Objects.requireNonNull(revision);
            Objects.requireNonNull(version);Objects.requireNonNull(jarSha256);
            Objects.requireNonNull(serverSha256);Objects.requireNonNull(assetsSha256);}
        public static Identity installed(){
            try{
                var jarSource=Objects.requireNonNull(Me12QaAuthority.class.getProtectionDomain().getCodeSource(),"ME12_QA_JAR_SOURCE_UNAVAILABLE");
                var serverSource=Objects.requireNonNull(com.hypixel.hytale.server.core.HytaleServer.class.getProtectionDomain().getCodeSource(),"ME12_QA_SERVER_SOURCE_UNAVAILABLE");
                var jar=Path.of(Objects.requireNonNull(jarSource.getLocation()).toURI());
                var server=Path.of(Objects.requireNonNull(serverSource.getLocation()).toURI());
                var serverDirectory=server.getParent();
                var packageDirectory=serverDirectory==null?null:serverDirectory.getParent();
                if(packageDirectory==null)throw new IllegalStateException("ME12_QA_SERVER_PACKAGE_UNAVAILABLE");
                var assets=packageDirectory.resolve("Assets.zip");
                if(!Files.isRegularFile(jar)||!Files.isRegularFile(server)||!Files.isRegularFile(assets))
                    throw new IllegalStateException("ME12_QA_RUNTIME_ARTIFACT_UNAVAILABLE");
                return new Identity(BuildIdentity.SOURCE_COMMIT,BuildIdentity.REVISION,BuildIdentity.VERSION,
                        sha256(jar),sha256(server),sha256(assets));
            }catch(Exception unavailable){throw new IllegalStateException("ME12_QA_RUNTIME_IDENTITY_UNAVAILABLE",unavailable);}
        }
    }
    public record Request(String candidateSha,UUID world,String saveName,long seconds,Path guardianReceipt){
        public Request{Objects.requireNonNull(candidateSha);Objects.requireNonNull(world);
            Objects.requireNonNull(saveName);Objects.requireNonNull(guardianReceipt);}
    }
    /** Immutable evidence prepared off the world thread; admission is rechecked before lease publication. */
    public static final class Verified {
        private final Me12QaAuthority owner;
        private final Request request;
        private final long verifiedAtNanos;
        private final long epoch;
        private final HywindWorldConfiguration.Snapshot policy;
        private final Object lifetime;
        private final java.util.concurrent.atomic.AtomicBoolean used=new java.util.concurrent.atomic.AtomicBoolean();
        private Verified(Me12QaAuthority owner,Request request,long verifiedAtNanos,long epoch,
                HywindWorldConfiguration.Snapshot policy,Object lifetime){
            this.owner=owner;this.request=request;this.verifiedAtNanos=verifiedAtNanos;this.epoch=epoch;
            this.policy=policy;this.lifetime=lifetime;}
    }
    private record Lease(UUID world,HywindWorldConfiguration.Snapshot snapshot,String candidate,long expiresNanos){}
    private final HywindWorldConfiguration config;
    private final EnemyWorldAdmission admission;
    private final EnemyNativeBindings bindings;
    private final BooleanSupplier ownersCertified;
    private final BooleanSupplier mandatoryCertified;
    private final Supplier<Identity> installed;
    private final LongSupplier clock;
    private volatile Lease lease;
    private final java.util.concurrent.atomic.AtomicLong epoch=new java.util.concurrent.atomic.AtomicLong();
    private volatile UUID pendingWorld;
    public Me12QaAuthority(HywindWorldConfiguration config,EnemyWorldAdmission admission,
            EnemyNativeBindings bindings,BooleanSupplier ownersCertified){
        this(config,admission,bindings,ownersCertified,()->mandatoryNativeCerts(bindings),Identity::installed,System::nanoTime);
    }
    Me12QaAuthority(HywindWorldConfiguration config,EnemyWorldAdmission admission,
            EnemyNativeBindings bindings,BooleanSupplier ownersCertified,BooleanSupplier mandatoryCertified,
            Supplier<Identity> installed,LongSupplier clock){
        this.config=Objects.requireNonNull(config);this.admission=Objects.requireNonNull(admission);
        this.bindings=Objects.requireNonNull(bindings);this.ownersCertified=Objects.requireNonNull(ownersCertified);
        this.mandatoryCertified=Objects.requireNonNull(mandatoryCertified);
        this.installed=Objects.requireNonNull(installed);this.clock=Objects.requireNonNull(clock);
    }
    private static boolean mandatoryNativeCerts(EnemyNativeBindings bindings){
        // Admission is per concrete native role. An unresolved role must not
        // prevent an independently certified role from being exercised in QA.
        return bindings.roles().stream().filter(EnemyNativeBindings.Role::productionPromotionEnabled)
                .filter(role->!EnemyIncomingOnlyAttachment.certified(role))
                .flatMap(role->role.nativeRoleIds().stream())
                .anyMatch(role->bindings.productionEligibilityRejection(role).isEmpty());
    }
    public synchronized void beginVerification(Request request){Objects.requireNonNull(request);revoke();pendingWorld=request.world();}
    public synchronized void activate(Request request){beginVerification(request);activateVerified(verify(request));}
    public Verified verify(Request request){
        Objects.requireNonNull(request);
        long token=epoch.get();
        var policy=config.snapshot();var lifetime=admission.lifetime(request.world());
        if(!request.candidateSha().matches("[0-9a-f]{40}")||request.seconds()<1||request.seconds()>600)
            throw new IllegalArgumentException("ME12_QA_REQUEST_BOUNDS");
        var identity=installed.get();
        if(!identity.sourceCommit().equals(request.candidateSha())
                ||!identity.serverSha256().equalsIgnoreCase(
                        com.inigmasgames.hytalerpg.execution.hytale.PackboundNativeMutationBridge.patchedServerSha256())
                ||!identity.assetsSha256().equalsIgnoreCase(bindings.assetsSha256()))
            throw new IllegalStateException("ME12_QA_CANDIDATE_OR_NATIVE_IDENTITY_MISMATCH");
        verifyGuardian(request.guardianReceipt(),identity);
        return new Verified(this,request,clock.getAsLong(),token,policy,lifetime);
    }
    public synchronized void activateVerified(Verified verified){
        Objects.requireNonNull(verified);
        if(verified.owner!=this||verified.epoch!=epoch.get())
            throw new IllegalStateException("ME12_QA_FOREIGN_OR_REVOKED_VERIFICATION");
        lease=null;
        long age=clock.getAsLong()-verified.verifiedAtNanos;
        if(!verified.used.compareAndSet(false,true)||age<0||age>30_000_000_000L)
            throw new IllegalStateException("ME12_QA_VERIFICATION_EXPIRED_OR_REUSED");
        var request=verified.request;
        var snapshot=config.snapshot();snapshot.me12FeatureState().validateStage2();
        if(snapshot!=verified.policy||!admission.current(request.world(),verified.lifetime))
            throw new IllegalStateException("ME12_QA_WORLD_OR_POLICY_CHANGED");
        if(snapshot.me12FeatureState()!=Me12FeatureState.DRAIN)
            throw new IllegalStateException("ME12_QA_DRAIN_REQUIRED");
        var saveDirectory=config.path().getParent();
        var saveName=saveDirectory==null?null:saveDirectory.getFileName();
        if(saveName==null||!saveName.toString().equals(request.saveName())
                ||!admission.admits(request.world()))throw new IllegalStateException("ME12_QA_SAVE_OR_WORLD_MISMATCH");
        if(!ownersCertified.getAsBoolean()||!mandatoryCertified.getAsBoolean())
            throw new IllegalStateException("ME12_QA_NATIVE_CERTIFICATION_BLOCKED");
        long now=clock.getAsLong();long duration=Math.multiplyExact(request.seconds(),1_000_000_000L);
        epoch.incrementAndGet();
        lease=new Lease(request.world(),snapshot,request.candidateSha(),Math.addExact(now,duration));
        pendingWorld=null;
    }
    public boolean allows(UUID world){
        var active=lease;
        return active!=null&&active.world().equals(world)&&clock.getAsLong()<active.expiresNanos()
                &&config.snapshot()==active.snapshot()&&config.snapshot().me12FeatureState()==Me12FeatureState.DRAIN
                &&admission.admits(world)&&ownersCertified.getAsBoolean()&&mandatoryCertified.getAsBoolean();
    }
    /** The lease is world-scoped, but each natural birth still needs its own pinned role. */
    public boolean allows(UUID world,String nativeRole){
        return allows(world)&&bindings.productionEligibilityRejection(nativeRole).isEmpty()
                &&bindings.role(nativeRole).filter(EnemyNativeBindings.Role::productionPromotionEnabled)
                    .filter(role->!EnemyIncomingOnlyAttachment.certified(role)).isPresent();
    }
    public synchronized void revoke(){epoch.incrementAndGet();lease=null;pendingWorld=null;}
    public synchronized void worldUnload(UUID world){if(lease!=null&&lease.world().equals(world)
            ||world.equals(pendingWorld))revoke();}
    public String status(UUID world){var active=lease;return allows(world)?"ACTIVE candidate="+active.candidate()+
            " world="+world+" secondsRemaining="+Math.max(0,(active.expiresNanos()-clock.getAsLong())/1_000_000_000L):"INACTIVE";}
    static void verifyGuardian(Path path,Identity identity){
        try{
            if(!Files.isRegularFile(path)||Files.size(path)>4*1024*1024)
                throw new IllegalStateException("ME12_QA_GUARDIAN_RECEIPT_UNAVAILABLE");
            var real=path.toRealPath();
            var guardian=real.getParent();var build=guardian==null?null:guardian.getParent();
            var repository=build==null?null:build.getParent();
            var receiptName=real.getFileName();var guardianName=guardian==null?null:guardian.getFileName();
            var buildName=build==null?null:build.getFileName();
            if(receiptName==null||!receiptName.toString().equals("verification.json")
                    ||guardianName==null||!guardianName.toString().equals("guardian")
                    ||buildName==null||!buildName.toString().equals("build")
                    ||repository==null)
                throw new IllegalStateException("ME12_QA_GUARDIAN_RECEIPT_LOCATION");
            var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(!text(root,"sourceCommit").equals(identity.sourceCommit())
                    ||!text(root,"revision").equals(identity.revision())
                    ||!text(root,"version").equals(identity.version())
                    ||!text(root,"jarSha256").equalsIgnoreCase(identity.jarSha256())
                    ||!text(root,"gradleCheck").equals("PASS")
                    ||!text(root,"packageValidation").equals("PASS"))
                throw new IllegalStateException("ME12_QA_GUARDIAN_RECEIPT_MISMATCH");
            var logPath=text(root,"checkLogPath");
            if(!logPath.startsWith("build/guardian/"))
                throw new IllegalStateException("ME12_QA_GUARDIAN_LOG_LOCATION");
            verifyEvidence(repository,logPath,text(root,"checkLogSha256"));
            var reports=root.getAsJsonArray("reports");if(reports==null)
                throw new IllegalStateException("ME12_QA_GUARDIAN_TESTS_MISSING");
            var found=new HashSet<String>();
            for(var entry:reports){var item=entry.getAsJsonObject();
                var report=text(item,"path");
                if(report.startsWith("build/test-results/")&&item.has("tests")&&item.get("tests").getAsInt()>0
                        &&List.of("NativeEnemyProductionBirthOwnerTest.xml","HroMasterEnemiesTest.xml",
                                "Me12QaAuthorityTest.xml").stream().anyMatch(report::endsWith)){
                    verifyEvidence(repository,report,text(item,"sha256"));found.add(report);
                }}
            for(String required:List.of("NativeEnemyProductionBirthOwnerTest.xml",
                    "HroMasterEnemiesTest.xml","Me12QaAuthorityTest.xml"))
                if(found.stream().noneMatch(value->value.endsWith(required)))
                    throw new IllegalStateException("ME12_QA_GUARDIAN_TESTS_MISSING:"+required);
        }catch(IOException|JsonParseException failure){throw new IllegalStateException("ME12_QA_GUARDIAN_RECEIPT_INVALID",failure);}
    }
    private static void verifyEvidence(Path repository,String relative,String expected) throws IOException {
        var path=Path.of(relative);
        if(path.isAbsolute()||relative.contains("\\")||relative.contains("..")||!expected.matches("(?i)[0-9a-f]{64}"))
            throw new IllegalStateException("ME12_QA_GUARDIAN_EVIDENCE_PATH");
        var resolved=repository.resolve(path).normalize();
        if(!resolved.startsWith(repository)||!Files.isRegularFile(resolved)
                ||!resolved.toRealPath().startsWith(repository.toRealPath()))
            throw new IllegalStateException("ME12_QA_GUARDIAN_EVIDENCE_UNAVAILABLE");
        try{if(!sha256(resolved).equalsIgnoreCase(expected))
            throw new IllegalStateException("ME12_QA_GUARDIAN_EVIDENCE_CHANGED");}
        catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
        catch(Exception failure){if(failure instanceof IOException io)throw io;
            if(failure instanceof RuntimeException runtime)throw runtime;throw new IllegalStateException(failure);}
    }
    private static String text(JsonObject source,String key){var value=source.get(key);
        if(value==null||!value.isJsonPrimitive())throw new IllegalStateException("ME12_QA_GUARDIAN_FIELD_MISSING:"+key);
        return value.getAsString();}
    private static String sha256(Path path)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){
            byte[] bytes=new byte[64*1024];int count;while((count=input.read(bytes))>=0)if(count>0)digest.update(bytes,0,count);
        }return HexFormat.of().formatHex(digest.digest());
    }
}
