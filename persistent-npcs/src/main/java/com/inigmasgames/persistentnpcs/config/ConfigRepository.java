package com.inigmasgames.persistentnpcs.config;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;

public final class ConfigRepository {
    private final Path path;

    public ConfigRepository(Path dataDirectory) {
        this.path = dataDirectory.resolve("config.json");
    }

    public FrameworkConfig load() {
        JsonFiles.copyResourceIfMissing(ConfigRepository.class, "/defaults/config.json", path);
        return JsonFiles.read(path, FrameworkConfig.class).validated();
    }

    public Path path() {
        return path;
    }
}

