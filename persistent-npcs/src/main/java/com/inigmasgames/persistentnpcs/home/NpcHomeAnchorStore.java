package com.inigmasgames.persistentnpcs.home;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

public final class NpcHomeAnchorStore {
    private final Path path;
    private final Map<UUID, NpcHomeAnchor> anchors = new LinkedHashMap<>();

    public NpcHomeAnchorStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/home-anchors.json");
    }

    public synchronized void load() {
        anchors.clear();
        if (Files.isRegularFile(path)) {
            NpcHomeAnchor[] loaded = JsonFiles.read(path, NpcHomeAnchor[].class);
            if (loaded != null) {
                Arrays.stream(loaded).map(NpcHomeAnchor::normalized)
                        .forEach(anchor -> anchors.put(anchor.npcId(), anchor));
            }
        } else {
            save();
        }
    }

    public synchronized NpcHomeAnchor initialize(
            UUID npcId, UUID worldId, Vector3d position, Instant nextWanderAt) {
        NpcHomeAnchor current = anchors.get(npcId);
        if (current != null) {
            return current;
        }
        NpcHomeAnchor created = new NpcHomeAnchor(npcId, worldId,
                position.x, position.y, position.z,
                position.x, position.y, position.z, false,
                NpcMovementState.IDLE_HOME, null, null, null, null,
                nextWanderAt).normalized();
        anchors.put(npcId, created);
        save();
        return created;
    }

    public synchronized NpcHomeAnchor get(UUID npcId) {
        return anchors.get(npcId);
    }

    public synchronized NpcHomeAnchor put(NpcHomeAnchor anchor) {
        NpcHomeAnchor normalized = anchor.normalized();
        anchors.put(normalized.npcId(), normalized);
        save();
        return normalized;
    }

    public Path path() {
        return path;
    }

    private void save() {
        JsonFiles.writeAtomic(path, anchors.values());
    }
}
