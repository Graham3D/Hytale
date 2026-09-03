package com.inigmasgames.persistentnpcs.economy;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ObligationStore {
    private final Path path;
    private final List<ObligationRecord> records = new ArrayList<>();

    public ObligationStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/obligations.json");
    }

    public synchronized void load() {
        records.clear();
        if (Files.exists(path)) {
            ObligationRecord[] loaded = JsonFiles.read(path, ObligationRecord[].class);
            if (loaded != null) {
                records.addAll(Arrays.asList(loaded));
            }
        } else {
            save();
        }
    }

    public synchronized ObligationRecord create(ObligationRecord record) {
        records.add(record);
        save();
        return record;
    }

    public synchronized ObligationRecord add(UUID id, long delta) {
        for (int i = 0; i < records.size(); i++) {
            ObligationRecord current = records.get(i);
            if (current.obligationId().equals(id) && !current.settled()) {
                ObligationRecord updated = new ObligationRecord(current.obligationId(),
                        current.creditorEntityId(), current.debtorEntityId(),
                        Math.max(0, current.amount() + delta), current.unit(), current.reason(),
                        current.recurring(), current.recurrenceGameDays(), current.createdAt(),
                        current.amount() + delta <= 0);
                records.set(i, updated);
                save();
                return updated;
            }
        }
        return null;
    }

    public synchronized ObligationRecord settle(UUID id) {
        for (int i = 0; i < records.size(); i++) {
            ObligationRecord current = records.get(i);
            if (current.obligationId().equals(id)) {
                ObligationRecord updated = new ObligationRecord(current.obligationId(),
                        current.creditorEntityId(), current.debtorEntityId(), current.amount(),
                        current.unit(), current.reason(), current.recurring(),
                        current.recurrenceGameDays(), current.createdAt(), true);
                records.set(i, updated);
                save();
                return updated;
            }
        }
        return null;
    }

    public synchronized List<ObligationRecord> activeFor(UUID entityId) {
        return records.stream().filter(record -> !record.settled()
                && (entityId.equals(record.creditorEntityId())
                        || entityId.equals(record.debtorEntityId()))).toList();
    }

    public synchronized List<ObligationRecord> activeBetween(UUID left, UUID right) {
        return records.stream().filter(record -> !record.settled()
                && ((left.equals(record.creditorEntityId()) && right.equals(record.debtorEntityId()))
                        || (right.equals(record.creditorEntityId())
                                && left.equals(record.debtorEntityId())))).toList();
    }

    private void save() {
        JsonFiles.writeAtomic(path, records);
    }
}
