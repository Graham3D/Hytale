package com.inigmasgames.persistentnpcs.monster;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persists only promoted agents; ordinary combat spawns remain ephemeral and memory-free. */
public final class ImmersiveAgentStore {
    private final Path path;
    private final List<ImmersiveEntityAgent> agents = new ArrayList<>();

    public ImmersiveAgentStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/immersive-agents.json");
    }

    public synchronized void load() {
        agents.clear();
        if (!Files.exists(path)) {
            save();
            return;
        }
        ImmersiveEntityAgent[] loaded = JsonFiles.read(path, ImmersiveEntityAgent[].class);
        if (loaded != null) {
            Arrays.stream(loaded).map(ImmersiveEntityAgent::normalized)
                    .filter(agent -> agent.persistence() == AgentPersistence.PERSISTENT)
                    .forEach(agents::add);
        }
    }

    public synchronized Optional<ImmersiveEntityAgent> put(ImmersiveEntityAgent agent) {
        ImmersiveEntityAgent normalized = agent.normalized();
        if (normalized.persistence() != AgentPersistence.PERSISTENT) {
            return Optional.empty();
        }
        agents.removeIf(value -> value.stableId().equals(normalized.stableId()));
        agents.add(normalized);
        save();
        return Optional.of(normalized);
    }

    public synchronized Optional<ImmersiveEntityAgent> get(UUID stableId) {
        return agents.stream().filter(agent -> agent.stableId().equals(stableId)).findFirst();
    }

    public synchronized List<ImmersiveEntityAgent> all() {
        return List.copyOf(agents);
    }

    private void save() {
        JsonFiles.writeAtomic(path, agents);
    }
}
