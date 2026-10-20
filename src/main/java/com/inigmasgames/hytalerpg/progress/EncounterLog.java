package com.inigmasgames.hytalerpg.progress;

import java.io.IOException;
import java.util.List;

/** Versioned storage boundary. The v1 implementation remains an explicit rollback compatibility path. */
interface EncounterLog extends AutoCloseable {
    EncounterJournal.Entry entry(EncounterJournal.Key key);
    void reserve(int records) throws IOException;
    void release();
    void append(EncounterContributions.Snapshot value) throws IOException;
    void appendGroup(List<EncounterContributions.Snapshot> values) throws IOException;
    void checkpoint() throws IOException;
    long sequence();
    @Override void close() throws IOException;
}
