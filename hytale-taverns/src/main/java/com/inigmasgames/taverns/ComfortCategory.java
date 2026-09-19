package com.inigmasgames.taverns;

/** The eleven Comfort categories defined by the Tavern design document. */
enum ComfortCategory {
    CONTAINERS,
    WARDROBES,
    TABLES,
    SEATING,
    DOORS,
    WINDOWS,
    LIGHTING,
    BEDS,
    SHELVES,
    SIGNS,
    DECO;

    String tooltipTranslationKey() {
        return "server.taverns.comfort.category." + name().toLowerCase(java.util.Locale.ROOT);
    }

    String tooltipToken() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
