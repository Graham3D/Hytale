package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonParser;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;

public final class R018SingleplayerVoiceSkinTest {
    private R018SingleplayerVoiceSkinTest() { }

    public static void main(String[] args) throws Exception {
        Path save = Files.createTempDirectory("persistent-npcs-r018-");
        Path data = save.resolve("mods").resolve("ImmersiveNPCs");
        var voice = new VoiceRuntimeConfigRepository(data).load();
        assert voice.voiceEnabled();
        assert voice.forceSingleplayerVoice();
        assert VoiceModule.class.getMethod("setVoiceEnabled", boolean.class) != null;
        assert AppearanceRepository.class.getMethod("queueApply", String.class,
                Ref.class, CommandBuffer.class) != null;

        Path installedData = Path.of(System.getenv("APPDATA"), "Hytale", "data",
                "pre-release", "Saves", "ImmersiveNPCs", "mods",
                "ImmersiveNPCs");
        AppearanceRepository appearances = new AppearanceRepository(
                installedData, ignored -> { });
        Path skin = appearances.resolveSkinFile("Mara").orElseThrow();
        Path model = appearances.resolveModelFile("Mara").orElseThrow();
        assert JsonParser.parseString(Files.readString(skin)).isJsonObject();
        assert JsonParser.parseString(Files.readString(model)).isJsonObject();
        System.out.println("R018 single-player voice and command-buffer skin tests passed.");
    }
}
