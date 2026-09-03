package com.inigmasgames.persistentnpcs.scene;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcAssignmentStore {
    private final Path path;
    private final Map<UUID, NpcAssignmentState> assignments = new LinkedHashMap<>();

    public NpcAssignmentStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/npc-assignments.json");
    }

    public synchronized void load() {
        assignments.clear();
        if (Files.exists(path)) {
            NpcAssignmentState[] loaded = JsonFiles.read(path, NpcAssignmentState[].class);
            if (loaded != null) Arrays.stream(loaded).map(NpcAssignmentState::normalized)
                    .forEach(value -> assignments.put(value.id(), value));
        } else save();
    }

    public synchronized NpcAssignmentState put(NpcAssignmentState value) {
        NpcAssignmentState normalized = value.normalized();
        assignments.put(normalized.id(), normalized);
        save();
        return normalized;
    }

    public synchronized NpcAssignmentState get(UUID id) {
        return assignments.get(id);
    }

    public synchronized List<NpcAssignmentState> all() {
        return List.copyOf(assignments.values());
    }

    private void save() {
        JsonFiles.writeAtomic(path, assignments.values());
    }
}
