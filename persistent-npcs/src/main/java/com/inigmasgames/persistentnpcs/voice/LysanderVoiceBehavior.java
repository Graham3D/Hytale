package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.Locale;

/** Character-specific affect selection; recording scripts never become dialogue. */
public final class LysanderVoiceBehavior {
    private LysanderVoiceBehavior() { }

    public static boolean appliesTo(NpcProfile profile) {
        if (profile == null) return false;
        return isName(profile.name()) || isName(profile.selfIdentity());
    }

    public static VocalEmotion select(
            String context, boolean danger, boolean environmentQuestion,
            boolean unknownEnvironment) {
        String text = normalize(context);
        boolean aboutMara = text.contains("mara");
        if (contains(text, "dishonest", "dishonesty", "lied", "liar", "betray",
                "betrayed", "cheat", "hypocrisy", "broke your word")
                || aboutMara && contains(text, "threat", "kill", "attack", "harm",
                        "hurt her", "coming for")) {
            return VocalEmotion.ANGRY;
        }
        if (contains(text, "family loss", "lost your family", "all you have left",
                "fear of losing mara", "lose mara", "mara died", "mara is dead",
                "regret", "too hard on her")) {
            return VocalEmotion.SAD;
        }
        if (aboutMara && contains(text, "proud", "love", "affection", "care about",
                "granddaughter", "your family")) {
            return VocalEmotion.TENDER;
        }
        if (danger || aboutMara && contains(text, "missing", "in danger", "unsafe",
                "worried", "concerned", "uncertain")) {
            return VocalEmotion.UNEASY;
        }
        if (contains(text, "exceptional craftsmanship", "masterwork", "masterpiece",
                "perfect edge", "finest work", "extraordinary workmanship")) {
            return VocalEmotion.EXCITED;
        }
        if (contains(text, "dry humor", "joke", "funny", "pulled it off",
                "reluctant approval", "you did well")) {
            return VocalEmotion.AMUSED;
        }
        if (contains(text, "craftsmanship", "workmanship", "worth examining",
                "examine this", "unusual work", "where did you find", "how was this made")
                || environmentQuestion && unknownEnvironment) {
            return VocalEmotion.CURIOUS;
        }
        return VocalEmotion.CALM;
    }

    public static String guidance(NpcProfile profile, VocalState state) {
        if (!appliesTo(profile) || state == null) return "Match the selected emotion naturally.";
        return switch (state.emotion()) {
            case CURIOUS -> "Controlled interest in unusual craftsmanship or useful information; examine rather than gush.";
            case EXCITED -> "Rare, restrained admiration for exceptional work; do not become broadly cheerful.";
            case UNEASY, AFRAID -> "Controlled concern about danger, uncertainty, or Mara; remain practical.";
            case ANGRY -> "Hard, direct anger at dishonesty, betrayal, broken integrity, or threats toward Mara; avoid melodrama.";
            case SAD -> "Quiet grief, family loss, regret, or fear of losing Mara; remain restrained.";
            case TENDER -> "Restrained affection toward or about Mara, expressed through pride and responsibility.";
            case AMUSED -> "Dry humor, reluctant approval, or understated enjoyment; never exuberant.";
            default -> "Stern, controlled, practical, and concise; this is the default delivery.";
        };
    }

    private static boolean isName(String value) {
        if (value == null) return false;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        // Existing authored data spells the stable identity Lycander; support both safely.
        return normalized.equals("lysander") || normalized.equals("lycander");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
