package com.inigmasgames.persistentnpcs.social;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class GossipStore {
    private final Path path;
    private final List<GossipRecord> records = new ArrayList<>();

    public GossipStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/gossip.json");
    }

    public synchronized void load() {
        records.clear();
        if (Files.exists(path)) {
            GossipRecord[] loaded = JsonFiles.read(path, GossipRecord[].class);
            if (loaded != null) {
                Arrays.stream(loaded).map(GossipRecord::normalized).forEach(records::add);
            }
        } else {
            save();
        }
    }

    public synchronized GossipRecord append(GossipRecord record) {
        GossipRecord normalized = record.normalized();
        records.add(normalized);
        save();
        return normalized;
    }

    public synchronized List<GossipRecord> knownBy(UUID npcId) {
        return records.stream().filter(record -> npcId.equals(record.toldToNpcId())).toList();
    }

    private void save() {
        JsonFiles.writeAtomic(path, records);
    }
}
