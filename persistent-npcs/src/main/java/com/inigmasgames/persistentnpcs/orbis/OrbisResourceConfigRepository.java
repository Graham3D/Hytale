package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OrbisResourceConfigRepository {
    private final Path path;

    public OrbisResourceConfigRepository(Path dataDirectory) {
        path = dataDirectory.resolve("orbis-resources.json");
    }

    public Path path() { return path; }

    public OrbisResourceConfig load() {
        if (!Files.isRegularFile(path)) {
            OrbisResourceConfig defaults = OrbisResourceConfig.defaults();
            JsonFiles.writeAtomic(path, defaults);
            return defaults;
        }
        OrbisResourceConfig loaded = JsonFiles.read(path, OrbisResourceConfig.class);
        OrbisResourceConfig validated = loaded == null
                ? OrbisResourceConfig.defaults() : loaded.validated();
        JsonFiles.writeAtomic(path, validated);
        return validated;
    }

    public void savePolicy(ResourcePolicy policy) {
        OrbisResourceConfig current = load();
        JsonFiles.writeAtomic(path, new OrbisResourceConfig(current.schemaVersion(), policy,
                current.backendOverrides(), current.maximumQueuedRequests(),
                current.maximumConcurrentStt(), current.maximumConcurrentLlm(),
                current.maximumConcurrentTts(), current.maximumConcurrentBackground(),
                current.maximumConcurrentLocalGpu(), current.gpuPressureUtilizationPercent(),
                current.vramPressureUsedPercent(), current.minimumFreeRamMiB(),
                current.defaultAdmissionTimeoutMillis(),
                current.hytaleGpuSafetyReserveMiB()).validated());
    }
}
