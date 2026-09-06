package com.inigmasgames.hytalerpg.diagnostics;

import java.io.InputStream;
import java.util.Properties;

public record SkillTraceConfiguration(boolean enabled, String level, int maxFileMb, int retainedFiles,
                                      boolean developmentEntitlements) {
    public SkillTraceConfiguration {
        if (maxFileMb < 1) throw new IllegalArgumentException("skillTrace.maxFileMb must be positive");
        if (retainedFiles < 1) throw new IllegalArgumentException("skillTrace.retainedFiles must be positive");
    }
    public static SkillTraceConfiguration load() {
        Properties properties = new Properties();
        try (InputStream input = SkillTraceConfiguration.class.getResourceAsStream("/rpg-skill-trace.properties")) {
            if (input == null) throw new IllegalStateException("Missing rpg-skill-trace.properties");
            properties.load(input);
            return new SkillTraceConfiguration(
                    Boolean.parseBoolean(properties.getProperty("skillTrace.enabled", "true")),
                    properties.getProperty("skillTrace.level", "NORMAL"),
                    Integer.parseInt(properties.getProperty("skillTrace.maxFileMb", "8")),
                    Integer.parseInt(properties.getProperty("skillTrace.retainedFiles", "4")),
                    Boolean.parseBoolean(properties.getProperty("developmentEntitlements.enabled", "true")));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load RPG trace configuration", error);
        }
    }
}
