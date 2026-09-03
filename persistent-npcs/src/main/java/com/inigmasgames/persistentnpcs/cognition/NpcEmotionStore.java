package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcEmotionStore {
    private final Path path;
    private final Map<UUID, NpcEmotionalState> states = new LinkedHashMap<>();

    public NpcEmotionStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/emotions.json");
    }

    public synchronized void load() {
        states.clear();
        if (Files.exists(path)) {
            NpcEmotionalState[] loaded = JsonFiles.read(path, NpcEmotionalState[].class);
            if (loaded != null) {
                Arrays.stream(loaded).map(NpcEmotionalState::normalized)
                        .forEach(state -> states.put(state.npcId(), state));
            }
        } else {
            save();
        }
    }

    public synchronized NpcEmotionalState get(UUID npcId, Instant now) {
        return states.getOrDefault(npcId,
                new NpcEmotionalState(npcId, NpcEmotion.CALM, 0, now, "baseline"))
                .decayed(now);
    }

    public synchronized NpcEmotionalState update(
            UUID npcId, NpcEmotion emotion, double intensity, Instant now, String source) {
        NpcEmotionalState current = get(npcId, now);
        NpcEmotionalState updated;
        if (emotion == current.emotion()) {
            updated = new NpcEmotionalState(npcId, emotion,
                    Math.max(current.intensity(), Math.min(1.0, intensity)), now, source);
        } else if (intensity + 0.15 < current.intensity()) {
            updated = current;
        } else {
            updated = new NpcEmotionalState(npcId, emotion, intensity, now, source);
        }
        states.put(npcId, updated.normalized());
        save();
        return updated.normalized();
    }

    private void save() {
        JsonFiles.writeAtomic(path, states.values());
    }
}
