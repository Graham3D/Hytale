package com.inigmasgames.hytalerpg.ui.inventory;

import java.text.Normalizer;
import java.util.Locale;

/** Accepts visible plain names only; never falls back to an internal item ID. */
public final class VisibleItemSearch {
    private VisibleItemSearch() { }
    public static String normalize(String visible) {
        return Normalizer.normalize(visible == null ? "" : visible, Normalizer.Form.NFC)
                .replaceAll("(?i)\u00a7[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
    }
    public static boolean matches(String visibleName, String query) {
        return normalize(visibleName).contains(normalize(query));
    }
}
