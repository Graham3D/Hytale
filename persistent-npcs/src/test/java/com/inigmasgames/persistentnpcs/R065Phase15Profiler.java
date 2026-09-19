package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlamaCppProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Strictly bounded three-profile Phase 1.5 warm TTFT profiler. */
public final class R065Phase15Profiler {
    private static final String SYSTEM = """
            You are Mara, an adult human apprentice blacksmith in Hytale. You are Mara, not the
            player and not Lycander. You are curious, mechanically minded, warm, direct, and
            dryly funny. Lycander is your grandfather and only remaining close family. Treat
            supplied world state, relationships, memories, capabilities, and constraints as
            authoritative. Never invent current objects, events, actions, memories, locations,
            relationships, or tool results. Speak one short, natural, in-character reply without
            labels, formatting, narration, or stage directions.
            """;
    private static final List<Profile> PROFILES = List.of(
            new Profile("BASELINE_8T_128U", 8, 128),
            new Profile("THREAD_PARITY_12T_128U", 12, 128),
            new Profile("OLLAMA_PARITY_12T_512U", 12, 512));
    private static final List<Probe> PROBES = List.of(
            new Probe("FAST", "World: player nearby; no danger.", "Hello Mara."),
            new Probe("GROUNDED", "Held item: Onyxium dagger. Visible nearby: player only.",
                    "Can you see what's in my hand?"));

    private R065Phase15Profiler() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("base manifest required");
        Path base = Path.of(args[0]).toAbsolutePath().normalize();
        Path manifests = Path.of("build", "orbisllm", "phase15-profiles");
        Files.createDirectories(manifests);
        ArrayList<ProfileResult> profiles = new ArrayList<>();
        for (Profile profile : PROFILES) {
            Path manifest = profileManifest(base, manifests, profile);
            profiles.add(runProfile(profile, manifest));
        }
        Path output = Path.of("build", "benchmarks", "R065-phase15-ttft-profiles.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonFiles.GSON.toJson(
                new Report("R065-PHASE1.5", Instant.now(), profiles)),
                StandardCharsets.UTF_8);
        for (ProfileResult profile : profiles) {
            System.out.println(profile.name() + " avgTTFT=" + profile.averageTtftMillis()
                    + "ms avgTotal=" + profile.averageCompletionMillis()
                    + "ms avgPrefill=" + profile.averagePrefillMillis()
                    + "ms avgFirstDecode=" + profile.averageFirstDecodeMillis()
                    + "ms cancelDrain=" + profile.cancelDrainMillis()
                    + "ms usedVram=" + profile.usedVramMiB() + "MiB");
        }
        System.out.println("R065_PHASE15_REPORT=" + output.toAbsolutePath());
    }

    private static ProfileResult runProfile(Profile profile, Path manifest) throws Exception {
        Path data = Files.createTempDirectory("orbisllm-phase15-");
        ArrayList<Sample> samples = new ArrayList<>();
        long cancelDrain;
        long usedVram;
        long freeVram;
        long privateRam;
        try (OrbisLlamaCppProvider provider = new OrbisLlamaCppProvider(data, manifest,
                ignored -> { })) {
            provider.warmUp().get(120, TimeUnit.SECONDS);
            // One graph/JIT warmup is excluded uniformly for all profiles.
            provider.generateResponse(request(PROBES.getFirst(), 32)).get(30, TimeUnit.SECONDS);
            for (int repetition = 1; repetition <= 3; repetition++) {
                for (Probe probe : PROBES) {
                    LlmResult result = provider.generateResponse(request(probe, 32))
                            .get(30, TimeUnit.SECONDS);
                    JsonObject timing = provider.runtimeDiagnostics(UUID.randomUUID())
                            .getAsJsonObject("latestTiming");
                    samples.add(new Sample(probe.name(), repetition,
                            result.latency().timeToFirstTokenMillis(),
                            result.latency().completionMillis(),
                            number(timing, "sidecarQueueMillis"),
                            number(timing, "resourceDispatchMillis"),
                            number(timing, "templateMillis"),
                            number(timing, "tokenizationMillis"),
                            number(timing, "kvClearMillis"),
                            number(timing, "prefillMillis"),
                            number(timing, "firstDecodeMillis"),
                            number(timing, "javaDispatchToAcceptedMillis"),
                            number(timing, "javaObservedTtftMillis"),
                            number(timing, "ipcFirstTokenReturnMillis"),
                            number(timing, "promptTokens")));
                }
            }
            String longContext = "authoritative forge observation ".repeat(220);
            LlmRequest cancellation = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), List.of(
                            new ChatMessage("system", "You are Mara. " + longContext),
                            new ChatMessage("user", "Summarize the observation.")),
                    List.of(), null, 0.3, 256, UUID.randomUUID(), policy(256));
            var inFlight = provider.generateResponse(cancellation);
            Thread.sleep(50);
            long cancelStarted = System.nanoTime();
            provider.cancel(cancellation.providerRequestId());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !"READY".equals(provider
                    .runtimeDiagnostics(UUID.randomUUID()).get("orbisLlmState").getAsString())) {
                Thread.sleep(2);
            }
            cancelDrain = elapsed(cancelStarted);
            try { inFlight.get(1, TimeUnit.SECONDS); } catch (Exception expected) { }
            JsonObject resources = provider.runtimeDiagnostics(UUID.randomUUID())
                    .getAsJsonObject("resources");
            usedVram = number(resources, "usedVramMiB");
            freeVram = number(resources, "freeVramMiB");
            privateRam = number(resources, "processPrivateMiB");
        }
        return new ProfileResult(profile.name(), profile.threads(), profile.microbatch(),
                average(samples, Sample::ttftMillis),
                average(samples, Sample::completionMillis),
                average(samples, Sample::prefillMillis),
                average(samples, Sample::firstDecodeMillis),
                cancelDrain, usedVram, freeVram, privateRam, samples, manifest.toString());
    }

    private static Path profileManifest(Path base, Path directory, Profile profile)
            throws Exception {
        JsonObject root = JsonFiles.GSON.fromJson(Files.readString(base), JsonObject.class);
        JsonObject balanced = root.getAsJsonObject("profiles").getAsJsonObject("BALANCED");
        balanced.addProperty("threads", profile.threads());
        balanced.addProperty("microbatchSize", profile.microbatch());
        Path output = directory.resolve(profile.name().toLowerCase() + ".json");
        Files.writeString(output, JsonFiles.GSON.toJson(root), StandardCharsets.UTF_8);
        return output.toAbsolutePath().normalize();
    }

    private static LlmRequest request(Probe probe, int maxTokens) {
        return new LlmRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ChatMessage("system", SYSTEM + "\nAUTHORITATIVE CONTEXT:\n"
                        + probe.context()),
                new ChatMessage("user", probe.user())), List.of(), null, 0.3, maxTokens,
                UUID.randomUUID(), policy(maxTokens));
    }

    private static LlmExecutionPolicy policy(int maxTokens) {
        return new LlmExecutionPolicy("REALTIME",
                LlmExecutionPolicy.ReasoningMode.DISABLED,
                List.of("R065_PHASE15_PROFILE"), maxTokens);
    }

    private static long average(List<Sample> values,
            java.util.function.ToLongFunction<Sample> field) {
        return Math.round(values.stream().mapToLong(field).average().orElse(-1));
    }
    private static long number(JsonObject value, String key) {
        return value != null && value.has(key) ? value.get(key).getAsLong() : -1;
    }
    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private record Profile(String name, int threads, int microbatch) { }
    private record Probe(String name, String context, String user) { }
    private record Sample(String probe, int repetition, long ttftMillis,
            long completionMillis, long sidecarQueueMillis, long resourceDispatchMillis,
            long templateMillis, long tokenizationMillis, long kvClearMillis,
            long prefillMillis, long firstDecodeMillis,
            long javaDispatchToAcceptedMillis, long javaObservedTtftMillis,
            long ipcFirstTokenReturnMillis, long promptTokens) { }
    private record ProfileResult(String name, int threads, int microbatch,
            long averageTtftMillis, long averageCompletionMillis,
            long averagePrefillMillis, long averageFirstDecodeMillis,
            long cancelDrainMillis, long usedVramMiB, long freeVramMiB,
            long processPrivateMiB, List<Sample> samples, String manifest) { }
    private record Report(String revision, Instant createdAt, List<ProfileResult> profiles) { }
}
