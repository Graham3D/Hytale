package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.epistemic.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Startup ordering, readiness split, cache identity/fallback, and E4 snapshot-tail gate. */
public final class R086StartupCriticalPathTest {
    public static void main(String[] args) throws Exception {
        sourceContracts();
        snapshotAndOrderedTail();
        System.out.println("R086 startup critical-path/cache/readiness tests passed.");
    }

    private static void sourceContracts() throws Exception {
        String plugin=Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/"
                + "PersistentNpcsPlugin.java"));
        String startup=Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/"
                + "orbis/OrbisStartupCoordinator.java"));
        String worker=Files.readString(Path.of("src/main/resources/tools/"
                + "immersive_voice_worker.py"));
        String ollama=Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/llm/OpenAiCompatibleProvider.java"));
        assert plugin.indexOf("startupCoordinator.trigger(\"PluginSetupEvent\")")
                < plugin.indexOf("relationships.load()") : "providers did not start before stores";
        assert plugin.contains("AllWorldsLoadedEvent") && plugin.contains("worldReady(");
        assert plugin.contains("AddPlayerToWorldEvent") && plugin.contains("entityBindingReady(");
        assert startup.contains("State { INITIALIZING, WARMING, FOREGROUND_READY, FULLY_WARM");
        assert startup.contains("providersReady.get() && dataReady.get() && worldReady.get()")
                && startup.contains("entityBindingReady.get()");
        int moon=startup.indexOf("phase(\"MOONSHINE\"");
        int nem=startup.indexOf("phase(\"NEMOTRON\"");
        int chatter=startup.indexOf("phase(\"CHATTERBOX\"");
        assert moon>=0 && moon<nem && nem<chatter : "GPU-sensitive startup order changed";
        assert plugin.indexOf("startupCoordinator.close()") < plugin.indexOf("aiServices.close()")
                : "coordinator must cancel before worker shutdown";
        assert worker.contains("CONDITIONING_CACHE_SCHEMA")
                && worker.contains("sha256_file(resolved)")
                && worker.contains("profile_revision_hash(resolved)")
                && worker.contains("importlib.metadata.version(\"chatterbox-tts\")")
                && worker.contains("os.replace(temporary, target)");
        assert worker.contains("CORRUPT:") && worker.contains("target.unlink(missing_ok=True)")
                : "invalid conditioning cache must rebuild safely";
        assert worker.contains("def ensure_whisper")
                && worker.indexOf("self.ensure_whisper()") > worker.indexOf("self.moonshine is None")
                : "Faster-Whisper fallback must not block successful Moonshine startup";
        assert ollama.contains("UNLOAD_ON_SERVER_SHUTDOWN")
                && ollama.contains("unloadResidentModel().orTimeout(5")
                : "managed Ollama residency must be released on server shutdown";
        assert ollama.contains("activateStartupSteadyStateProfile")
                && ollama.contains("STARTUP_STEADY_STATE")
                : "startup pressure must converge in one approved residency transition";
        assert plugin.contains("hytaleGpuSafetyReserveMiB()")
                : "Hytale reserve must remain authoritative";
    }

    private static void snapshotAndOrderedTail() throws Exception {
        Path root=Files.createTempDirectory("r086-tail-");
        UUID npc=UUID.randomUUID(), subject=UUID.randomUUID();
        try(SourcedBeliefStore first=new SourcedBeliefStore(root)) {
            first.load();
            for(int i=0;i<32;i++) first.assertBelief(proposal(npc,subject,"FACT_"+i,"v"+i));
            first.awaitIdle();
            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(2).toNanos();
            while(!Files.isRegularFile(first.snapshotPath())&&System.nanoTime()<deadline)
                Thread.sleep(10);
            assert Files.readString(first.snapshotPath()).contains("eventByteOffset");
            long snapshottedBytes=Files.size(first.eventPath());
            first.assertBelief(proposal(npc,subject,"TAIL_FACT","tail"));
            first.awaitIdle();
            deadline=System.nanoTime()+java.time.Duration.ofSeconds(2).toNanos();
            while(Files.size(first.eventPath())<=snapshottedBytes&&System.nanoTime()<deadline)
                Thread.sleep(10);
            try(SourcedBeliefStore restored=new SourcedBeliefStore(root)) {
                restored.load();
                assert restored.restorationStats().snapshotHit();
                assert restored.restorationStats().tailEvents() >= 1
                        : restored.restorationStats();
                assert restored.current(npc,subject,"TAIL_FACT").stream()
                        .anyMatch(v->v.value().equals("tail"));
            }
        }
    }

    private static BeliefProposal proposal(UUID npc,UUID subject,String predicate,String value){
        Instant now=Instant.now();
        return new BeliefProposal(null,npc,subject,"fixture",predicate,value,
                predicate+"="+value,BeliefAssertion.Polarity.POSITIVE,EpistemicStatus.KNOWN,1,
                new BeliefProvenance(EvidenceSourceKind.DIRECT_OBSERVATION,npc,
                        List.of("R086:"+UUID.randomUUID()),false,false),
                BeliefAssertion.TemporalScope.stable(now),
                BeliefAssertion.AssertionScope.ENTITY,List.of(),now);
    }
}
