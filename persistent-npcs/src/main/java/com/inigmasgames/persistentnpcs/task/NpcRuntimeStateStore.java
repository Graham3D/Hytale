package com.inigmasgames.persistentnpcs.task;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Lightweight unloaded-NPC simulation; never moves a loaded entity. */
public final class NpcRuntimeStateStore {
    private final Path path;
    private final Map<UUID, NpcRuntimeState> states = new LinkedHashMap<>();

    public NpcRuntimeStateStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/runtime-state.json");
    }

    public synchronized void load() {
        states.clear();
        if (Files.exists(path)) {
            NpcRuntimeState[] loaded = JsonFiles.read(path, NpcRuntimeState[].class);
            if (loaded != null) {
                Arrays.stream(loaded).map(NpcRuntimeState::normalized)
                        .forEach(state -> states.put(state.npcId(), state));
            }
        } else {
            save();
        }
    }

    public synchronized NpcRuntimeState put(NpcRuntimeState state) {
        NpcRuntimeState normalized = state.normalized();
        states.put(normalized.npcId(), normalized);
        save();
        return normalized;
    }

    public synchronized NpcRuntimeState get(UUID npcId) {
        return states.get(npcId);
    }

    public synchronized NpcRuntimeState advanceUnloaded(UUID npcId, Instant gameNow) {
        NpcRuntimeState current = states.get(npcId);
        if (current == null || gameNow == null) {
            return current;
        }
        if (current.expectedArrival() != null
                && !gameNow.isBefore(current.expectedArrival())) {
            current = new NpcRuntimeState(current.npcId(), current.worldId(),
                    current.destination(), "IDLE", "", null,
                    current.scheduleState(), current.inventorySummary(),
                    current.economicBalance(), gameNow).normalized();
        } else {
            current = new NpcRuntimeState(current.npcId(), current.worldId(),
                    current.logicalLocation(), current.taskType(), current.destination(),
                    current.expectedArrival(), current.scheduleState(),
                    current.inventorySummary(), current.economicBalance(), gameNow).normalized();
        }
        return put(current);
    }

    private void save() {
        JsonFiles.writeAtomic(path, states.values());
    }
}
