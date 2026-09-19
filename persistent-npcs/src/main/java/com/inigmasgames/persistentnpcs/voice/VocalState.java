package com.inigmasgames.persistentnpcs.voice;

import java.util.Locale;
import java.util.Optional;

/** Semantic performance metadata. Numeric provider controls are mapped deterministically. */
public record VocalState(
        VocalEmotion emotion,
        VocalIntensity intensity,
        VocalPace pace,
        Optional<ParalinguisticEvent> paralinguisticEvent) {

    public VocalState {
        emotion = emotion == null ? VocalEmotion.CALM : emotion;
        intensity = intensity == null ? VocalIntensity.LOW : intensity;
        pace = pace == null ? VocalPace.NORMAL : pace;
        paralinguisticEvent = paralinguisticEvent == null
                ? Optional.empty() : paralinguisticEvent;
    }

    public VocalState(
            VocalEmotion emotion, VocalIntensity intensity, VocalPace pace) {
        this(emotion, intensity, pace, Optional.empty());
    }

    public static VocalState infer(String dialogue) {
        String text = dialogue == null ? "" : dialogue.toLowerCase(Locale.ROOT);
        boolean exceptionalTopic = contains(text, "goblin flamethrower", "rare ore",
                "lightning discovery", "lightning discoveries", "fox", "foxes");
        boolean explicitExcitement = contains(text, "excited", "amazing", "incredible",
                "remarkable", "extraordinary") || text.contains("!");
        VocalEmotion emotion = contains(text, "run now", "get away", "flee", "danger!")
                ? VocalEmotion.AFRAID
                : text.strip().equals("excited") || exceptionalTopic && explicitExcitement
                ? VocalEmotion.EXCITED
                : text.contains("curious") || text.contains("wonder") || text.endsWith("?")
                ? VocalEmotion.CURIOUS
                : text.contains("uneasy") || text.contains("wary") || text.contains("nervous")
                        ? VocalEmotion.UNEASY
                : text.contains("afraid") || text.contains("scared")
                ? VocalEmotion.AFRAID
                : text.contains("angry") || text.contains("damn")
                        ? VocalEmotion.ANGRY
                        : text.contains("sad") || text.contains("sorry")
                                ? VocalEmotion.SAD
                                : text.contains("tender") || text.contains("affection")
                                        ? VocalEmotion.TENDER
                                        : text.contains("amused") || text.contains("chuckle")
                                                || text.contains("dry humor")
                                                ? VocalEmotion.AMUSED
                                : text.contains("whisper")
                                        ? VocalEmotion.WHISPERING
                                        : text.contains("thank") || text.contains("glad")
                                                ? VocalEmotion.FRIENDLY
                                                : VocalEmotion.CALM;
        return forEmotion(emotion);
    }

    public static VocalState forEmotion(VocalEmotion emotion) {
        VocalEmotion resolved = emotion == null ? VocalEmotion.CALM : emotion;
        VocalIntensity intensity = resolved == VocalEmotion.ANGRY
                        || resolved == VocalEmotion.AFRAID
                        || resolved == VocalEmotion.SCARED
                ? VocalIntensity.HIGH : resolved == VocalEmotion.CALM
                        || resolved == VocalEmotion.WHISPERING
                        || resolved == VocalEmotion.TENDER
                        || resolved == VocalEmotion.AFFECTIONATE
                        || resolved == VocalEmotion.AMUSED
                                ? VocalIntensity.LOW : VocalIntensity.MEDIUM;
        VocalPace pace = resolved == VocalEmotion.AFRAID || resolved == VocalEmotion.SCARED
                ? VocalPace.FAST : resolved == VocalEmotion.SAD
                        || resolved == VocalEmotion.WHISPERING
                        || resolved == VocalEmotion.TENDER
                        || resolved == VocalEmotion.AFFECTIONATE
                                ? VocalPace.SLOW : VocalPace.NORMAL;
        return new VocalState(resolved, intensity, pace);
    }

    public VocalState withEvent(ParalinguisticEvent event) {
        return new VocalState(emotion, intensity, pace, Optional.ofNullable(event));
    }

    public VocalState withoutEvent() {
        return paralinguisticEvent.isEmpty() ? this
                : new VocalState(emotion, intensity, pace, Optional.empty());
    }

    private static boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
