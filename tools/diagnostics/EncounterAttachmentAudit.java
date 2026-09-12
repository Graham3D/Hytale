import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Separate diagnostic of the existing bug, NOT a release assertion defining desired behavior.
 * Creates an empty isolated store only. Never pass a save directory. */
class EncounterAttachmentAudit {
    public static void main(String[] args)throws Exception{
        Path base=Path.of("run").toRealPath();
        Path directory=base.resolve("encounter-attachment-audit-"+UUID.randomUUID());
        Files.createDirectory(directory);
        try(var store=FileEncounterStore.durableV2(directory,ignored->{},ignored->{},false);
            var runtime=new PersistentEncounterRuntime(store,(player,reward)->{})){
            UUID world=UUID.randomUUID(),enemy=UUID.randomUUID();
            boolean accepted=runtime.attachNative(world,enemy,"Unclassified_Diagnostic_Role",Optional.empty())
                    .toCompletableFuture().get(5,TimeUnit.SECONDS);
            System.out.println("ENCOUNTER_ATTACHMENT_DIAGNOSTIC accepted="+accepted+" observing="+runtime.observing(world,enemy)
                    +" attaching="+runtime.attaching(world,enemy)+" spawnPresent="+runtime.spawn(world,enemy).isPresent());
            try{
                if(runtime.observing(world,enemy)&&!runtime.attaching(world,enemy))runtime.spawn(world,enemy).orElseThrow();
                System.out.println("ENCOUNTER_LOOKUP_DIAGNOSTIC missingDescriptorThrow=false");
            }catch(NoSuchElementException error){
                System.out.println("ENCOUNTER_LOOKUP_DIAGNOSTIC missingDescriptorThrow=true message="+error.getMessage());
            }
            System.out.println("ENCOUNTER_ATTACHMENT_DIAGNOSTIC persistenceUnavailable="+runtime.unavailable()+" connectedProof=false gameplayChanged=false");
        }
    }
}
