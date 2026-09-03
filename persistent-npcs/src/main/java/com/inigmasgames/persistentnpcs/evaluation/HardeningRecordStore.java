package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Append-by-ID engineering history; never stored in NPC production persistence. */
public final class HardeningRecordStore {
    private final Path root;
    public HardeningRecordStore(Path evaluationRoot) {
        root = evaluationRoot.toAbsolutePath().normalize().resolve("history");
    }
    public Path write(HardeningRecord record) {
        if (!record.id().matches("[A-Za-z0-9_.-]{1,96}")) throw new IllegalArgumentException(
                "safe hardening record id required");
        Path path = root.resolve(record.id() + ".json").normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("unsafe record path");
        JsonFiles.writeAtomic(path, record); return path;
    }
    public record HardeningRecord(String id, Instant at, String sourceFailure,
            EvaluationContracts.BoundaryId earliestBoundary, String sourceFix,
            List<String> regressions, Map<String, String> gateEvidence) {
        public HardeningRecord {
            if (id == null || at == null || sourceFailure == null || sourceFailure.isBlank()
                    || earliestBoundary == null || sourceFix == null || sourceFix.isBlank()) {
                throw new IllegalArgumentException(
                        "hardening record requires source failure, boundary, and source fix");
            }
            regressions = List.copyOf(regressions == null ? List.of() : regressions);
            gateEvidence = Map.copyOf(gateEvidence == null ? Map.of() : gateEvidence);
            if (regressions.isEmpty() || gateEvidence.isEmpty()) throw new IllegalArgumentException(
                    "hardening record requires regressions and gate evidence");
        }
    }
}
