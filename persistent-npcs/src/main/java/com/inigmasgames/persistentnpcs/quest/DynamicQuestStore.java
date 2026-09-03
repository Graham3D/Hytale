package com.inigmasgames.persistentnpcs.quest;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DynamicQuestStore {
    private final Path path;
    private final List<DynamicQuest> quests = new ArrayList<>();

    public DynamicQuestStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/quests.json");
    }

    public synchronized void load() {
        quests.clear();
        if (!Files.exists(path)) {
            save();
            return;
        }
        DynamicQuest[] loaded = JsonFiles.read(path, DynamicQuest[].class);
        if (loaded != null) {
            Arrays.stream(loaded).map(DynamicQuest::normalized).forEach(quests::add);
        }
    }

    public synchronized DynamicQuest put(DynamicQuest quest) {
        DynamicQuest normalized = quest.normalized();
        quests.removeIf(existing -> existing.questId().equals(normalized.questId()));
        quests.add(normalized);
        save();
        return normalized;
    }

    public synchronized Optional<DynamicQuest> get(UUID questId) {
        return quests.stream().filter(quest -> quest.questId().equals(questId)).findFirst();
    }

    public synchronized List<DynamicQuest> all() {
        return List.copyOf(quests);
    }

    public synchronized List<DynamicQuest> activeForPlayer(UUID playerId) {
        return quests.stream().filter(quest -> !quest.terminal()
                && quest.participantPlayerIds().contains(playerId)).toList();
    }

    public Path path() {
        return path;
    }

    private void save() {
        JsonFiles.writeAtomic(path, quests);
    }
}
