package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Installed-API gate for dynamically sizing the real A6 waveform. */
public final class R138NpcAuthoringStudioA6WaveformBindingTest {
    private R138NpcAuthoringStudioA6WaveformBindingTest() { }

    public static void main(String[] args) throws Exception {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(5));
        anchor.setHeight(Value.of(116));
        UICommandBuilder builder = new UICommandBuilder();
        builder.setObject("#VoiceWaveformBar0.Anchor", anchor);
        var commands = builder.getCommands();
        assert commands.length == 1;
        assert "#VoiceWaveformBar0.Anchor".equals(commands[0].selector);
        assert commands[0].data != null && commands[0].data.contains("Height")
                && commands[0].data.contains("Width")
                : "Anchor codec must carry the complete fixed width and dynamic height";

        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/NpcProfilePage.java"));
        assert !page.contains("VoiceWaveformBar\" + index + \".Anchor.Height")
                : "Nested Anchor.Height is not a mutable Hytale markup property";
        assert page.contains("commands.setObject(\"#VoiceWaveformBar\" + index + \".Anchor\"")
                : "Waveform must replace the codec-backed Anchor property atomically";
        System.out.println("R138 A6 waveform Anchor codec gate passed.");
    }
}
