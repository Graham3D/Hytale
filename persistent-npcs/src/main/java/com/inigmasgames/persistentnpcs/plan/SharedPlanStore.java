package com.inigmasgames.persistentnpcs.plan;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SharedPlanStore {
    private final Path path;
    private final Map<UUID, SharedPlan> plans = new LinkedHashMap<>();

    public SharedPlanStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/shared-plans.json");
    }

    public synchronized void load() {
        plans.clear();
        if (Files.exists(path)) {
            SharedPlan[] loaded = JsonFiles.read(path, SharedPlan[].class);
            if (loaded != null) Arrays.stream(loaded).map(SharedPlan::normalized)
                    .forEach(plan -> plans.put(plan.id(), plan));
        } else {
            save();
        }
    }

    public synchronized SharedPlan put(SharedPlan plan) {
        SharedPlan value = plan.normalized();
        plans.put(value.id(), value);
        save();
        return value;
    }

    public synchronized SharedPlan get(UUID id) {
        return plans.get(id);
    }

    public synchronized List<SharedPlan> activeFor(UUID participant) {
        return plans.values().stream().filter(plan -> plan.involves(participant))
                .filter(plan -> !plan.status().terminal()).toList();
    }

    public synchronized List<SharedPlan> all() {
        return List.copyOf(plans.values());
    }

    public Path path() {
        return path;
    }

    private void save() {
        JsonFiles.writeAtomic(path, plans.values());
    }
}
