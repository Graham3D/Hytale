package com.inigmasgames.hytalerpg.ui.inventory;

import java.util.function.Function;
import java.util.regex.Pattern;

/** Converts native localized rich text to the owned tooltip's plain label text. */
final class NativeTooltipText {
    private static final Pattern REFERENCES = Pattern.compile(
            "<(item|msg)\\s+(?:is|key)\\s*=\\s*([\"'])(.*?)\\2\\s*/>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATTING = Pattern.compile(
            "</?(?:i|b|s|color)(?:\\s+[^<>]*)?\\s*>", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DEPTH = 8;

    private NativeTooltipText() { }

    static String plain(String text, Function<String, String> itemName,
                        Function<String, String> translation) {
        return plain(text, itemName, translation, 0);
    }

    private static String plain(String text, Function<String, String> itemName,
                                Function<String, String> translation, int depth) {
        if (text == null || text.isBlank()) return "";
        var matcher = REFERENCES.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            boolean item = matcher.group(1).equalsIgnoreCase("item");
            String resolved = null;
            if (depth < MAX_DEPTH) {
                try { resolved = (item ? itemName : translation).apply(matcher.group(3)); }
                catch (RuntimeException unavailable) { /* Partial assets must not break hovering. */ }
            }
            String replacement = resolved == null || resolved.isBlank()
                    ? (item ? "Item" : "") : plain(resolved, itemName, translation, depth + 1);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return FORMATTING.matcher(result).replaceAll("")
                .replace("\\n", "\n").replace("\r", "").strip();
    }
}
