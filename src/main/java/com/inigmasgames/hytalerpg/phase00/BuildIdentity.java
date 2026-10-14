package com.inigmasgames.hytalerpg.phase00;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata generated from gradle.properties. */
public final class BuildIdentity {
    private static final Properties VALUES = load();

    public static final String REVISION = required("rpg.revision");
    public static final String DISPLAY_REVISION = displayRevision(REVISION);
    public static final String VERSION = required("rpg.version");
    public static final String STAGE = required("rpg.stage");
    public static final String HYTALE_VERSION = required("hytale.version");
    public static final String SOURCE_COMMIT = required("rpg.sourceCommit");

    private BuildIdentity() {
    }

    private static Properties load() {
        Properties values = new Properties();
        try (InputStream input = BuildIdentity.class.getResourceAsStream("/rpg-build.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing rpg-build.properties");
            }
            values.load(input);
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load RPG build identity", exception);
        }
    }

    private static String required(String key) {
        String value = VALUES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing build identity value: " + key);
        }
        return value;
    }

    static String displayRevision(String revision) {
        if (revision == null || revision.isEmpty() || revision.charAt(0) != 'R') {
            throw new IllegalArgumentException("Revision must start with R and a number");
        }
        int end = 1;
        while (end < revision.length() && revision.charAt(end) >= '0' && revision.charAt(end) <= '9') {
            end++;
        }
        if (end == 1) throw new IllegalArgumentException("Revision must start with R and a number");
        return revision.substring(0, end);
    }
}
