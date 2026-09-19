package com.inigmasgames.persistentnpcs.home;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;

public final class HomeBehaviorConfigRepository {
    private final Path path;

    public HomeBehaviorConfigRepository(Path dataDirectory) {
        path = dataDirectory.resolve("home-behavior.json");
    }

    public HomeBehaviorConfig load() {
        JsonFiles.copyResourceIfMissing(HomeBehaviorConfigRepository.class,
                "/defaults/home-behavior.json", path);
        return JsonFiles.read(path, HomeBehaviorConfig.class);
    }
}
