package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;

public final class LatencyBudgetRepository {
    private final Path path;

    public LatencyBudgetRepository(Path dataDirectory) {
        path = dataDirectory.resolve("latency-budgets.json");
    }

    public LatencyBudgetConfig load() {
        JsonFiles.copyResourceIfMissing(LatencyBudgetRepository.class,
                "/defaults/latency-budgets.json", path);
        return JsonFiles.read(path, LatencyBudgetConfig.class);
    }
}
