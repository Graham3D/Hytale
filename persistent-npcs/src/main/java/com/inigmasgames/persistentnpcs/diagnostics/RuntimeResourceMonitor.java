package com.inigmasgames.persistentnpcs.diagnostics;

import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.voice.TurboVoiceWorker;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Low-frequency, off-thread host/GPU snapshot for operator diagnostics only. */
public final class RuntimeResourceMonitor implements AutoCloseable {
    private static final double SERVER_FRAME_PRESSURE_MILLIS = 75.0;

    public record GpuProcess(long pid, String processName, long usedVramMiB,
            String allocationStatus, String category) {
        public GpuProcess {
            processName = safe(processName);
            allocationStatus = safe(allocationStatus);
            category = safe(category);
        }
    }

    public record ProviderExpectation(String provider, String model,
            boolean expectedResident, long estimatedVramMiB) {
        public ProviderExpectation {
            provider = safe(provider);
            model = safe(model);
            estimatedVramMiB = Math.max(0, estimatedVramMiB);
        }
    }

    public record ModelResidency(String provider, String model, String state,
            boolean expectedResident, long workerPid, long estimatedVramMiB,
            ExecutionPlacement placement, String evidence) {
        public ModelResidency {
            provider = safe(provider);
            model = safe(model);
            state = safe(state);
            estimatedVramMiB = Math.max(0, estimatedVramMiB);
            placement = placement == null ? ExecutionPlacement.UNKNOWN : placement;
            evidence = safe(evidence);
        }

        public boolean loaded() { return "LOADED".equals(state); }
    }

    public record ResidencyTransition(String provider, String model, String transition,
            Instant observedAt, long freeVramBeforeMiB, long freeVramAfterMiB,
            long workerPid) { }

    public record FramePressure(Instant sampledAt, double serverFrameTimeMillis,
            double serverFps, boolean serverFramePressure, double pressureThresholdMillis,
            double clientFrameTimeMillis, double clientFps, String clientPressure,
            String source) {
        static FramePressure unknown() {
            return new FramePressure(Instant.now(), -1, -1, false,
                    SERVER_FRAME_PRESSURE_MILLIS, -1, -1, "UNKNOWN",
                    "server tick delta unavailable; client FPS is not exposed to server plugins");
        }
    }

    public record Snapshot(Instant at, double systemCpuPercent, long ramUsedMiB,
            long ramTotalMiB, long hytaleHeapUsedMiB, long hytaleHeapMaxMiB,
            long hytaleCpuMillis, int gpuUtilizationPercent, long vramUsedMiB,
            long vramFreeMiB, long vramTotalMiB, String cpuModel, int logicalProcessors,
            String gpuName, String ollamaResidency, boolean hytaleClientPresent,
            boolean chatterboxTtsPresent, String failure,
            List<GpuProcess> gpuProcesses, List<ModelResidency> modelResidencies,
            List<ResidencyTransition> residencyTransitions, FramePressure framePressure,
            String perProcessGpuProbeStatus) {
        public Snapshot {
            gpuProcesses = List.copyOf(gpuProcesses == null ? List.of() : gpuProcesses);
            modelResidencies = List.copyOf(
                    modelResidencies == null ? List.of() : modelResidencies);
            residencyTransitions = List.copyOf(
                    residencyTransitions == null ? List.of() : residencyTransitions);
            framePressure = framePressure == null ? FramePressure.unknown() : framePressure;
            perProcessGpuProbeStatus = safe(perProcessGpuProbeStatus);
        }

        /** Source-compatible constructor for existing tests and cached callers. */
        public Snapshot(Instant at, double systemCpuPercent, long ramUsedMiB,
                long ramTotalMiB, long hytaleHeapUsedMiB, long hytaleHeapMaxMiB,
                long hytaleCpuMillis, int gpuUtilizationPercent, long vramUsedMiB,
                long vramFreeMiB, long vramTotalMiB, String cpuModel, int logicalProcessors,
                String gpuName, String ollamaResidency, boolean hytaleClientPresent,
                boolean chatterboxTtsPresent, String failure) {
            this(at, systemCpuPercent, ramUsedMiB, ramTotalMiB, hytaleHeapUsedMiB,
                    hytaleHeapMaxMiB, hytaleCpuMillis, gpuUtilizationPercent, vramUsedMiB,
                    vramFreeMiB, vramTotalMiB, cpuModel, logicalProcessors, gpuName,
                    ollamaResidency, hytaleClientPresent, chatterboxTtsPresent, failure,
                    List.of(), List.of(), List.of(), FramePressure.unknown(), "UNKNOWN");
        }

        public String display(String model) {
            String residentLine = matchingResidency(model);
            String mode = residentLine.contains("100% GPU") ? "GPU"
                    : residentLine.contains("100% CPU") ? "CPU"
                            : residentLine.isBlank() ? "NOT_RESIDENT" : "PARTIAL_OFFLOAD";
            return "sampledAt=" + at + "\ninferenceMode=" + mode
                    + "\nmodelResident=" + !residentLine.isBlank()
                    + (residentLine.isBlank() ? "" : "\nollama=" + residentLine)
                    + "\nmodelResidencies=" + modelResidencies
                    + "\nGPUProcesses=" + gpuProcesses
                    + "\nCPU=" + cpuDisplay() + " RAM=" + ramDisplay()
                    + "\nHytaleServerHeap=" + hytaleHeapUsedMiB + "/" + hytaleHeapMaxMiB
                    + " MiB processCpu=" + hytaleCpuMillis + "ms"
                    + "\nGPU=" + gpuDisplay()
                    + "\nHytaleClientGpuMiB=" + allocationFor("HYTALE_CLIENT")
                    + " HytaleServerGpuMiB=" + allocationFor("HYTALE_SERVER")
                    + "\nserverFrameMs=" + framePressure.serverFrameTimeMillis()
                    + " serverFps=" + framePressure.serverFps()
                    + " serverFramePressure=" + framePressure.serverFramePressure()
                    + " clientFps=" + unknown(framePressure.clientFps())
                    + " clientFramePressure=" + framePressure.clientPressure()
                    + "\nHytaleClientPresent=" + hytaleClientPresent
                    + " ChatterboxTtsPresent=" + chatterboxTtsPresent
                    + (failure.isBlank() ? "" : "\nresourceProbeFailure=" + failure);
        }

        private String matchingResidency(String model) {
            if (ollamaResidency == null || ollamaResidency.isBlank()) return "";
            String needle = model == null ? "" : model.strip().toLowerCase(Locale.ROOT);
            return ollamaResidency.lines().filter(line -> needle.isBlank()
                    || line.toLowerCase(Locale.ROOT).contains(needle)).findFirst().orElse("");
        }

        public ExecutionPlacement inferencePlacement(String providerOrModel) {
            String residentLine = matchingResidency(providerOrModel);
            if (residentLine.isBlank() && ollamaResidency != null
                    && ollamaResidency.lines().count() == 1) residentLine = ollamaResidency;
            String normalized = residentLine.toUpperCase(Locale.ROOT);
            if (normalized.contains("100% GPU")) return ExecutionPlacement.LOCAL_GPU;
            if (normalized.contains("100% CPU")) return ExecutionPlacement.LOCAL_CPU;
            if (normalized.contains("CPU/GPU") || normalized.contains("GPU/CPU")) {
                return ExecutionPlacement.LOCAL_PARTIAL_GPU;
            }
            return ExecutionPlacement.UNKNOWN;
        }

        public long allocationFor(String category) {
            List<GpuProcess> matches = gpuProcesses.stream()
                    .filter(value -> category.equals(value.category())).toList();
            if (matches.isEmpty() || matches.stream().anyMatch(value ->
                    value.usedVramMiB() < 0)) return -1;
            return matches.stream().mapToLong(GpuProcess::usedVramMiB).sum();
        }

        public String cpuDisplay() {
            String load = systemCpuPercent < 0 ? "UNKNOWN"
                    : "%.1f%%".formatted(systemCpuPercent);
            return (cpuModel == null || cpuModel.isBlank() ? "UNKNOWN" : cpuModel)
                    + " threads=" + (logicalProcessors <= 0 ? "UNKNOWN" : logicalProcessors)
                    + " load=" + load;
        }

        public String ramDisplay() {
            return ramTotalMiB <= 0 ? "UNKNOWN" : ramUsedMiB + "/" + ramTotalMiB + " MiB";
        }

        public String gpuDisplay() {
            if (gpuUtilizationPercent < 0 && vramTotalMiB <= 0
                    && (gpuName == null || gpuName.isBlank())) return "UNKNOWN";
            return (gpuName == null || gpuName.isBlank() ? "UNKNOWN" : gpuName)
                    + " load=" + (gpuUtilizationPercent < 0
                            ? "UNKNOWN" : gpuUtilizationPercent + "%")
                    + " VRAM=" + (vramTotalMiB <= 0 ? "UNKNOWN"
                            : vramUsedMiB + "/" + vramTotalMiB + " MiB");
        }
    }

    private final ScheduledExecutorService sampler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "immersive-npc-runtime-monitor");
                thread.setDaemon(true);
                return thread;
            });
    private final Supplier<List<ProviderExpectation>> providerExpectations;
    private final AtomicReference<Snapshot> latest = new AtomicReference<>(unknownSnapshot());
    private final AtomicLong lastFrameObservationNanos = new AtomicLong();
    private final ArrayDeque<Double> serverFrameSamples = new ArrayDeque<>();
    private final Map<String, ModelResidency> priorResidencies = new LinkedHashMap<>();
    private final ArrayDeque<ResidencyTransition> recentTransitions = new ArrayDeque<>();

    public RuntimeResourceMonitor() { this(List::of); }

    public RuntimeResourceMonitor(Supplier<List<ProviderExpectation>> providerExpectations) {
        this.providerExpectations = providerExpectations == null ? List::of
                : providerExpectations;
        sampler.scheduleWithFixedDelay(this::sampleSafely, 250, 2_000,
                TimeUnit.MILLISECONDS);
    }

    public Snapshot latest() { return latest.get(); }

    /** Update 6 server tick delta only. Client frame timing is not available server-side. */
    public void observeServerFrame(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0 || deltaSeconds > 5) return;
        long now = System.nanoTime();
        long prior = lastFrameObservationNanos.get();
        if (prior != 0 && now - prior < TimeUnit.MILLISECONDS.toNanos(2)) return;
        if (!lastFrameObservationNanos.compareAndSet(prior, now)) return;
        synchronized (serverFrameSamples) {
            serverFrameSamples.addLast((double) deltaSeconds * 1_000.0);
            while (serverFrameSamples.size() > 120) serverFrameSamples.removeFirst();
        }
    }

    private void sampleSafely() {
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean)
                    ManagementFactory.getOperatingSystemMXBean();
            long total = Math.max(0, os.getTotalMemorySize());
            long free = Math.max(0, os.getFreeMemorySize());
            Runtime runtime = Runtime.getRuntime();
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long processCpu = ProcessHandle.current().info().totalCpuDuration()
                    .map(Duration::toMillis).orElse(0L);
            Gpu gpu = gpu();
            Processes processes = processes();
            ProcessGpuResult perProcess = gpuProcesses(processes);
            CommandResult residency = command(2_000, "ollama", "ps");
            List<ProviderExpectation> expected = safeExpectations();
            List<ModelResidency> models = modelResidencies(residency.output(), expected,
                    perProcess.processes());
            updateTransitions(models, latest.get().vramFreeMiB(), gpu.freeMiB());
            String failure = joinFailure(gpu.failure(), residency.failure());
            failure = joinFailure(failure, perProcess.failure());
            latest.set(new Snapshot(Instant.now(), Math.max(0, os.getCpuLoad() * 100.0),
                    mib(total - free), mib(total), mib(heapUsed), mib(runtime.maxMemory()),
                    processCpu, gpu.utilization(), gpu.usedMiB(), gpu.freeMiB(), gpu.totalMiB(),
                    System.getenv().getOrDefault("PROCESSOR_IDENTIFIER",
                            System.getProperty("os.arch", "UNKNOWN")),
                    runtime.availableProcessors(), gpu.name(),
                    compactLines(residency.output(), 2_000), processes.hytaleClient(),
                    models.stream().anyMatch(value -> value.provider().equals("CHATTERBOX")
                            && value.loaded()), failure, perProcess.processes(), models,
                    List.copyOf(recentTransitions), framePressure(),
                    perProcess.failure().isBlank() ? "MEASURED_WHERE_EXPOSED"
                            : perProcess.failure()));
        } catch (RuntimeException failure) {
            Snapshot prior = latest.get();
            latest.set(new Snapshot(Instant.now(), prior.systemCpuPercent(), prior.ramUsedMiB(),
                    prior.ramTotalMiB(), prior.hytaleHeapUsedMiB(), prior.hytaleHeapMaxMiB(),
                    prior.hytaleCpuMillis(), prior.gpuUtilizationPercent(), prior.vramUsedMiB(),
                    prior.vramFreeMiB(), prior.vramTotalMiB(), prior.cpuModel(),
                    prior.logicalProcessors(), prior.gpuName(), prior.ollamaResidency(),
                    prior.hytaleClientPresent(), prior.chatterboxTtsPresent(),
                    failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()),
                    prior.gpuProcesses(), prior.modelResidencies(),
                    prior.residencyTransitions(), framePressure(),
                    prior.perProcessGpuProbeStatus()));
        }
    }

    private List<ProviderExpectation> safeExpectations() {
        try {
            List<ProviderExpectation> value = providerExpectations.get();
            return value == null ? List.of() : List.copyOf(value);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private FramePressure framePressure() {
        double average;
        synchronized (serverFrameSamples) {
            average = serverFrameSamples.isEmpty() ? -1
                    : serverFrameSamples.stream().mapToDouble(Double::doubleValue)
                            .average().orElse(-1);
        }
        return new FramePressure(Instant.now(), average,
                average > 0 ? 1_000.0 / average : -1,
                average >= SERVER_FRAME_PRESSURE_MILLIS, SERVER_FRAME_PRESSURE_MILLIS,
                -1, -1, "UNKNOWN",
                average < 0 ? "server tick delta unavailable; client FPS is not exposed"
                        : "Update 6 EntityTickingSystem delta; client FPS is not exposed");
    }

    private void updateTransitions(List<ModelResidency> current, long priorFree,
            long currentFree) {
        Map<String, ModelResidency> now = new LinkedHashMap<>();
        current.forEach(value -> now.put(residencyKey(value), value));
        for (Map.Entry<String, ModelResidency> entry : now.entrySet()) {
            ModelResidency before = priorResidencies.get(entry.getKey());
            ModelResidency after = entry.getValue();
            String transition = before == null && after.loaded() ? "LOAD_OBSERVED"
                    : before != null && !before.loaded() && after.loaded() ? "LOADED"
                    : before != null && before.loaded() && !after.loaded() ? "UNLOADED"
                    : "";
            if (!transition.isBlank()) addTransition(after, transition, priorFree, currentFree);
        }
        for (Map.Entry<String, ModelResidency> entry : priorResidencies.entrySet()) {
            if (!now.containsKey(entry.getKey()) && entry.getValue().loaded()) {
                addTransition(entry.getValue(), "UNLOADED", priorFree, currentFree);
            }
        }
        priorResidencies.clear();
        priorResidencies.putAll(now);
    }

    private void addTransition(ModelResidency model, String transition, long before, long after) {
        recentTransitions.addLast(new ResidencyTransition(model.provider(), model.model(),
                transition, Instant.now(), before, after, model.workerPid()));
        while (recentTransitions.size() > 12) recentTransitions.removeFirst();
    }

    private static String residencyKey(ModelResidency value) {
        return value.provider() + "\u0000" + value.model();
    }

    private static List<ModelResidency> modelResidencies(String ollama,
            List<ProviderExpectation> expectations, List<GpuProcess> gpuProcesses) {
        LinkedHashMap<String, ModelResidency> result = new LinkedHashMap<>();
        Map<String, ProviderExpectation> byModel = new LinkedHashMap<>();
        expectations.forEach(value -> byModel.put(value.model().toLowerCase(Locale.ROOT), value));
        List<GpuProcess> ollamaProcesses = gpuProcesses.stream()
                .filter(value -> value.category().equals("OLLAMA")).toList();
        long ollamaPid = ollamaProcesses.stream().map(GpuProcess::pid).distinct().count() == 1
                ? ollamaProcesses.getFirst().pid() : -1L;
        if (ollama != null) for (String raw : ollama.lines().toList()) {
            String line = raw.strip();
            if (line.isBlank() || line.startsWith("NAME ")) continue;
            String model = line.split("\\s+", 2)[0];
            ProviderExpectation expectation = byModel.get(model.toLowerCase(Locale.ROOT));
            String provider = expectation == null ? providerName(model)
                    : expectation.provider();
            boolean expected = expectation != null && expectation.expectedResident();
            long estimate = expectation == null ? 0 : expectation.estimatedVramMiB();
            String upper = line.toUpperCase(Locale.ROOT);
            ExecutionPlacement placement = upper.contains("100% GPU")
                    ? ExecutionPlacement.LOCAL_GPU : upper.contains("100% CPU")
                            ? ExecutionPlacement.LOCAL_CPU
                            : upper.contains("CPU/GPU") || upper.contains("GPU/CPU")
                                    ? ExecutionPlacement.LOCAL_PARTIAL_GPU
                                    : ExecutionPlacement.UNKNOWN;
            ModelResidency value = new ModelResidency(provider, model, "LOADED", expected,
                    ollamaPid, estimate, placement, "ollama ps: " + line);
            result.put(residencyKey(value), value);
        }
        for (TurboVoiceWorker.WorkerResidency worker : TurboVoiceWorker.runtimeResidencies()) {
            if (worker.role() != TurboVoiceWorker.WorkerRole.TTS) continue;
            ProviderExpectation expectation = expectations.stream().filter(value ->
                    value.provider().equals("CHATTERBOX")).findFirst().orElse(null);
            long workerVram = Math.max(worker.cudaAllocatedMiB(), worker.cudaReservedMiB());
            ModelResidency value = new ModelResidency("CHATTERBOX", "chatterbox-turbo",
                    worker.modelResident() ? worker.state() : "UNLOADED",
                    expectation != null && expectation.expectedResident(),
                    worker.pid(), workerVram > 0 ? workerVram
                            : expectation == null ? 0 : expectation.estimatedVramMiB(),
                    worker.ttsDevice().toLowerCase(Locale.ROOT).contains("cuda")
                            ? ExecutionPlacement.LOCAL_GPU : ExecutionPlacement.LOCAL_CPU,
                    "authoritative TurboVoiceWorker readiness; alive=" + worker.alive()
                            + "; actualDevice=" + worker.ttsDevice()
                            + "; cudaAllocatedMiB=" + worker.cudaAllocatedMiB()
                            + "; cudaReservedMiB=" + worker.cudaReservedMiB()
                            + "; cudaPeakAllocatedMiB=" + worker.cudaPeakAllocatedMiB()
                            + "; cudaPeakReservedMiB=" + worker.cudaPeakReservedMiB()
                            + "; modelResident=" + worker.modelResident()
                            + "; conditioningCacheEntries="
                            + worker.conditioningCacheEntries());
            result.put(residencyKey(value), value);
        }
        for (ProviderExpectation expectation : expectations) {
            boolean present = result.values().stream().anyMatch(value ->
                    value.provider().equalsIgnoreCase(expectation.provider())
                            || value.model().equalsIgnoreCase(expectation.model()));
            if (!present) {
                ModelResidency value = new ModelResidency(expectation.provider(),
                        expectation.model(), "UNLOADED", expectation.expectedResident(), -1,
                        expectation.estimatedVramMiB(), ExecutionPlacement.UNKNOWN,
                        "not present in authoritative worker state or ollama ps");
                result.put(residencyKey(value), value);
            }
        }
        return List.copyOf(result.values());
    }

    private static String providerName(String model) {
        String value = model.toLowerCase(Locale.ROOT);
        if (value.contains("nemotron")) return "NEMOTRON";
        if (value.contains("qwen")) return "QWEN";
        return "OLLAMA";
    }

    private static Gpu gpu() {
        CommandResult result = command(2_000, "nvidia-smi",
                "--query-gpu=name,utilization.gpu,memory.used,memory.free,memory.total",
                "--format=csv,noheader,nounits");
        try {
            String[] values = result.output().lines().findFirst().orElse("").split(",");
            if (values.length < 5) return new Gpu("UNKNOWN", -1, -1, -1, -1,
                    result.failure());
            return new Gpu(values[0].strip(), Integer.parseInt(values[1].strip()),
                    Long.parseLong(values[2].strip()), Long.parseLong(values[3].strip()),
                    Long.parseLong(values[4].strip()), result.failure());
        } catch (RuntimeException failure) {
            return new Gpu("UNKNOWN", -1, -1, -1, -1,
                    joinFailure(result.failure(), "nvidia GPU parse failed"));
        }
    }

    private static ProcessGpuResult gpuProcesses(Processes identities) {
        CommandResult result = command(2_000, "nvidia-smi",
                "--query-compute-apps=pid,process_name,used_gpu_memory",
                "--format=csv,noheader,nounits");
        LinkedHashMap<Long, GpuProcess> values = new LinkedHashMap<>();
        for (String line : result.output().lines().toList()) {
            if (line.isBlank()) continue;
            String[] fields = line.split(",", 3);
            if (fields.length < 3) continue;
            try {
                long pid = Long.parseLong(fields[0].strip());
                long memory = parseMemory(fields[2]);
                ProcessIdentity identity = identities.identities().get(pid);
                String name = identity == null ? fields[1].strip() : identity.name();
                String category = identity == null ? category(name, name) : identity.category();
                values.put(pid, new GpuProcess(pid, name, memory,
                        memory < 0 ? "UNAVAILABLE_FROM_NVIDIA_SMI" : "MEASURED",
                        category));
            } catch (RuntimeException ignored) { }
        }
        identities.identities().values().stream().filter(value ->
                value.category().startsWith("HYTALE_") || value.category().equals("CHATTERBOX"))
                .forEach(value -> values.putIfAbsent(value.pid(), new GpuProcess(value.pid(),
                        value.name(), -1, "NOT_REPORTED_BY_NVIDIA_SMI", value.category())));
        return new ProcessGpuResult(List.copyOf(values.values()), result.failure());
    }

    private static long parseMemory(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^0-9-]", "");
        if (normalized.isBlank() || normalized.equals("-")) return -1;
        return Long.parseLong(normalized);
    }

    private static Processes processes() {
        boolean hytale = false;
        LinkedHashMap<Long, ProcessIdentity> identities = new LinkedHashMap<>();
        for (ProcessHandle process : ProcessHandle.allProcesses().toList()) {
            String command = process.info().command().orElse("");
            String commandLine = process.info().commandLine().orElse("");
            String name;
            try {
                name = command.isBlank() ? "pid-" + process.pid()
                        : java.nio.file.Path.of(command).getFileName().toString();
            } catch (RuntimeException ignored) {
                name = "pid-" + process.pid();
            }
            String classification = category(name, commandLine);
            if (!classification.equals("OTHER")) {
                identities.put(process.pid(), new ProcessIdentity(process.pid(), name,
                        classification));
            }
            hytale |= classification.equals("HYTALE_CLIENT");
        }
        return new Processes(hytale, Map.copyOf(identities));
    }

    private static String category(String name, String commandLine) {
        String value = (safe(name) + " " + safe(commandLine)).toLowerCase(Locale.ROOT);
        if (value.contains("hytaleclient")) return "HYTALE_CLIENT";
        if (value.contains("hytaleserver") || value.contains("hytale-server")) {
            return "HYTALE_SERVER";
        }
        if (value.contains("immersive_voice_worker")) return "CHATTERBOX";
        if (value.contains("ollama") || value.contains("llama_server")) return "OLLAMA";
        return "OTHER";
    }

    private static CommandResult command(long timeoutMillis, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new CommandResult("", command[0] + " timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? new CommandResult(output, "")
                    : new CommandResult(output, command[0] + " exit=" + process.exitValue());
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new CommandResult("", command[0] + ": " + safe(failure.getMessage()));
        }
    }

    private static String compactLines(String value, int maximum) {
        String text = value == null ? "" : value.lines().map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith("NAME "))
                .reduce((left, right) -> left + " | " + right).orElse("");
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private static Snapshot unknownSnapshot() {
        return new Snapshot(Instant.now(), -1, -1, -1, -1, -1, 0, -1, -1, -1, -1,
                "UNKNOWN", Runtime.getRuntime().availableProcessors(), "UNKNOWN", "",
                false, false, "initial sample pending", List.of(), List.of(), List.of(),
                FramePressure.unknown(), "INITIAL_SAMPLE_PENDING");
    }

    private static String unknown(double value) {
        return value < 0 ? "UNKNOWN" : "%.2f".formatted(value);
    }
    private static long mib(long bytes) { return Math.max(0, bytes) / 1_048_576L; }
    private static String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
    private static String joinFailure(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + "; " + second;
    }

    @Override public void close() { sampler.shutdownNow(); }

    private record Gpu(String name, int utilization, long usedMiB, long freeMiB,
            long totalMiB, String failure) { }
    private record ProcessIdentity(long pid, String name, String category) { }
    private record Processes(boolean hytaleClient, Map<Long, ProcessIdentity> identities) { }
    private record ProcessGpuResult(List<GpuProcess> processes, String failure) { }
    private record CommandResult(String output, String failure) { }
}
