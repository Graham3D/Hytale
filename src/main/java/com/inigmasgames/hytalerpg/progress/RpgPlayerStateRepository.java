package com.inigmasgames.hytalerpg.progress;

import java.util.List;
import java.util.UUID;

public interface RpgPlayerStateRepository {
    LoadResult load(UUID playerUuid);
    void save(RpgPlayerState state);

    record LoadResult(RpgPlayerState state, boolean existed, boolean migrated, int sourceSchema,
                      List<String> warnings) {
        public LoadResult { warnings = List.copyOf(warnings); }
    }
}
