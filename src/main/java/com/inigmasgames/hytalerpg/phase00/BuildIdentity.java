package com.inigmasgames.hytalerpg.phase00;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build metadata generated from gradle.properties. */
final class BuildIdentity {
    private static final Properties VALUES = load();

    static final String REVISION = required("rpg.revision");
    static final String VERSION = required("rpg.version");
    static final String STAGE = required("rpg.stage");
    static final String HYTALE_VERSION = required("hytale.version");

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
}
