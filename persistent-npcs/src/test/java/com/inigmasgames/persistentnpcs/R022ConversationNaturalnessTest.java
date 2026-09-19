package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.DialogueNaturalnessFilter;
import com.inigmasgames.persistentnpcs.social.NpcSocialAttentionService;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalIntensity;
import com.inigmasgames.persistentnpcs.voice.VocalPace;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class R022ConversationNaturalnessTest {
    private R022ConversationNaturalnessTest() { }

    public static void main(String[] args) throws Exception {
        String paragraph = DialogueNaturalnessFilter.filterResponse(
                "That is remarkable.\nWhere did you find it? Where did you find it?",
                List.of());
        assert paragraph.equals("That is remarkable. Where did you find it?") : paragraph;
        String noRepeat = DialogueNaturalnessFilter.filterResponse(
                "Did that obsidian come from the Goblin Flamethrower? It looks unusual.",
                List.of("Is that obsidian from the Goblin Flamethrower?"));
        assert noRepeat.equals("It looks unusual.") : noRepeat;

        List<String> chunks = new ArrayList<>();
        SpeechPhraseChunker chunker = new SpeechPhraseChunker(
                (index, phrase, state) -> chunks.add(index + ":" + phrase), List.of());
        VocalState curious = new VocalState(
                VocalEmotion.CURIOUS, VocalIntensity.MEDIUM, VocalPace.NORMAL);
        chunker.accept("Oh!\nWait! A flamethrower? That is a genuinely unusual discovery. ",
                curious);
        chunker.accept("Where did you find it?", curious);
        int count = chunker.complete("", curious);
        assert count == 1 : chunks;
        assert chunks.get(0).equals(
                "0:Oh! Wait! A flamethrower? That is a genuinely unusual discovery.") : chunks;

        assert VocalState.infer("Wonderful!").emotion() != VocalEmotion.EXCITED;
        VocalState exciting = VocalState.infer(
                "That Goblin Flamethrower is an incredible discovery!");
        assert exciting.emotion() == VocalEmotion.EXCITED;
        assert exciting.intensity() == VocalIntensity.MEDIUM;
        assert exciting.pace() == VocalPace.NORMAL;
        assert DialogueMode.classify("NPC_INITIATED_CURIOSITY: nearby", false, false)
                == DialogueMode.NPC_INITIATED_CURIOSITY;

        UUID npc = UUID.fromString("3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
        UUID player = UUID.fromString("73f9b698-2494-480d-8406-2943e4a7505b");
        boolean canFire = false;
        for (int minute = 0; minute < 120; minute++) {
            canFire |= NpcSocialAttentionService.shouldInitiateCuriosity(
                    npc, player, 0.80, Instant.ofEpochSecond(minute * 60L));
        }
        assert canFire;

        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String bridge = Files.readString(source.resolve("hytale/HytaleConversationBridge.java"));
        String pipeline = Files.readString(source.resolve("orbis/OrbisSpeechCoordinator.java"))
                + Files.readString(source.resolve("orbis/OrbisEventType.java"));
        assert !bridge.contains("new DialogueChunker");
        assert bridge.contains("tell(playerRef, current.name() + \": \"");
        assert pipeline.contains("CanonicalSpeechChunk");
        assert pipeline.contains("TTS_SYNTHESIZING");
        assert pipeline.contains("SPEAKING");
        assert pipeline.contains("CHUNK_PLAYBACK_COMPLETE");
        assert pipeline.contains("SPEECH_CANCELLED");
        System.out.println("R022 targeted conversation naturalness tests passed.");
    }
}
