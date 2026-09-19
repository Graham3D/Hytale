package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.google.gson.JsonObject;
import java.nio.file.Path;

public final class VoiceRuntimeConfigRepository {
    private final Path path;

    public VoiceRuntimeConfigRepository(Path dataDirectory) {
        path = dataDirectory.resolve("voice.json");
    }

    public VoiceRuntimeConfig load() {
        JsonFiles.copyResourceIfMissing(VoiceRuntimeConfigRepository.class,
                "/defaults/voice.json", path);
        JsonObject stored = JsonFiles.read(path, JsonObject.class);
        boolean changed = false;
        if (!stored.has("sttProvider")) {
            stored.addProperty("sttProvider", "AUTO");
            changed = true;
        }
        if (!stored.has("moonshineModel")) {
            stored.addProperty("moonshineModel", "TINY_STREAMING");
            changed = true;
        }
        if (!stored.has("conversationListenRadius")) {
            stored.addProperty("conversationListenRadius", 5.0);
            changed = true;
        }
        if (!stored.has("remoteHailRadius")) {
            stored.addProperty("remoteHailRadius", 15.0);
            changed = true;
        }
        if (!stored.has("npcSpeechMaxRadius")) {
            stored.addProperty("npcSpeechMaxRadius", 15.0);
            changed = true;
        }
        if (stored.has("utteranceGapMillis")
                && stored.get("utteranceGapMillis").getAsInt() == 350) {
            stored.addProperty("utteranceGapMillis", 250);
            changed = true;
        }
        if (changed) {
            JsonFiles.writeAtomic(path, stored);
        }
        return JsonFiles.read(path, VoiceRuntimeConfig.class).validated();
    }
}
