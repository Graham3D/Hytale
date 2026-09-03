package com.inigmasgames.persistentnpcs.background;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BackgroundLifeStore {
    private final Path path;
    private final Map<UUID, BackgroundLifeState> states = new LinkedHashMap<>();

    public BackgroundLifeStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/background-life.json");
    }

    public synchronized void load() {
        states.clear();
        if (Files.exists(path)) {
            BackgroundLifeState[] loaded = JsonFiles.read(path, BackgroundLifeState[].class);
            if (loaded != null) Arrays.stream(loaded).map(BackgroundLifeState::normalized)
                    .forEach(state -> states.put(state.npcId(), state));
        } else save();
    }

    public synchronized BackgroundLifeState getOrCreate(UUID npcId, UUID worldId, String home) {
        return states.computeIfAbsent(npcId,
                ignored -> BackgroundLifeState.initial(npcId, worldId, home));
    }

    public synchronized BackgroundLifeState get(UUID npcId) {
        return states.get(npcId);
    }

    public synchronized BackgroundLifeState put(BackgroundLifeState state) {
        BackgroundLifeState normalized = state.normalized();
        states.put(normalized.npcId(), normalized);
        save();
        return normalized;
    }

    public Path path() { return path; }

    private void save() { JsonFiles.writeAtomic(path, states.values()); }
}
