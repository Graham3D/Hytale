package com.inigmasgames.persistentnpcs.diagnostics;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

/** Stable identity for the material GPU-process/provider-residency portion of a snapshot. */
public final class ResourceSnapshotIdentity {
    private static final long MATERIAL_PROCESS_VRAM_MIB = 64;

    private ResourceSnapshotIdentity() { }

    public static String id(RuntimeResourceMonitor.Snapshot snapshot) {
        if (snapshot == null) return "rs-unknown";
        StringBuilder material = new StringBuilder(512)
                .append("probe=").append(snapshot.perProcessGpuProbeStatus())
                .append("|failure=").append(snapshot.failure())
                .append("|hytaleClient=").append(snapshot.hytaleClientPresent())
                .append("|chatterbox=").append(snapshot.chatterboxTtsPresent());
        snapshot.gpuProcesses().stream().sorted(Comparator
                .comparingLong(RuntimeResourceMonitor.GpuProcess::pid)
                .thenComparing(RuntimeResourceMonitor.GpuProcess::processName))
                .forEach(process -> material.append("|p:")
                        .append(process.pid()).append(':').append(process.processName())
                        .append(':').append(materialVram(process.usedVramMiB()))
                        .append(':').append(process.allocationStatus())
                        .append(':').append(process.category()));
        snapshot.modelResidencies().stream().sorted(Comparator
                .comparing(RuntimeResourceMonitor.ModelResidency::provider)
                .thenComparing(RuntimeResourceMonitor.ModelResidency::model))
                .forEach(residency -> material.append("|r:")
                        .append(residency.provider()).append(':').append(residency.model())
                        .append(':').append(residency.state())
                        .append(':').append(residency.expectedResident())
                        .append(':').append(residency.workerPid())
                        .append(':').append(residency.estimatedVramMiB())
                        .append(':').append(residency.placement()));
        return "rs-" + digest(material.toString());
    }

    /** Fallback for trace-start suppliers that do not expose the cached Snapshot object. */
    public static String id(JsonObject snapshot) {
        if (snapshot == null) return "rs-unknown";
        if (snapshot.has("resourceSnapshotId")) {
            String existing = snapshot.get("resourceSnapshotId").getAsString();
            if (!existing.isBlank()) return existing;
        }
        JsonObject copy = snapshot.deepCopy();
        copy.remove("resourceSnapshotId");
        return "rs-" + digest(copy.toString());
    }

    private static long materialVram(long value) {
        if (value < 0) return -1;
        return Math.round((double) value / MATERIAL_PROCESS_VRAM_MIB)
                * MATERIAL_PROCESS_VRAM_MIB;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
