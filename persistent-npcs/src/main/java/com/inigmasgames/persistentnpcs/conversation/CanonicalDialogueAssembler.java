package com.inigmasgames.persistentnpcs.conversation;

/**
 * Assembles one model completion into one canonical lexical NPC response.
 *
 * <p>Line layout is provider formatting, not dialogue structure. This deliberately preserves
 * every word and punctuation mark while folding line/paragraph separators and repeated
 * whitespace into the single-space boundary used when canonical speech chunks are joined.</p>
 */
public final class CanonicalDialogueAssembler {
    private CanonicalDialogueAssembler() { }

    public static String assemble(String generatedText) {
        if (generatedText == null || generatedText.isBlank()) return "";
        return generatedText
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace('\f', ' ')
                .replace('\u2028', ' ')
                .replace('\u2029', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }
}
