package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcCognitionStateStore {
    private final Path path;
    private final Map<UUID, NpcCognitionRuntimeState> states = new LinkedHashMap<>();

    public NpcCognitionStateStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/cognition-state.json");
    }

    public synchronized void load() {
        states.clear();
        if (Files.exists(path)) {
            NpcCognitionRuntimeState[] loaded = JsonFiles.read(
                    path, NpcCognitionRuntimeState[].class);
            if (loaded != null) Arrays.stream(loaded).forEach(state ->
                    states.put(state.npcId(), state));
        } else {
            save();
        }
    }

    public synchronized NpcCognitionRuntimeState get(UUID npcId) {
        return states.computeIfAbsent(npcId, NpcCognitionRuntimeState::initial);
    }

    public synchronized NpcCognitionRuntimeState put(NpcCognitionRuntimeState state) {
        states.put(state.npcId(), state);
        save();
        return state;
    }

    public Path path() {
        return path;
    }

    private void save() {
        JsonFiles.writeAtomic(path, states.values());
    }
}
