package com.inigmasgames.hytalerpg.diagnostics;

import java.io.InputStream;
import java.util.Properties;

public record SkillTraceConfiguration(boolean enabled, String level, int maxFileMb, int retainedFiles,
                                      boolean developmentEntitlements) {
    public SkillTraceConfiguration {
        if (maxFileMb < 1||maxFileMb>64) throw new IllegalArgumentException("skillTrace.maxFileMb must be 1..64");
        if (retainedFiles < 1||retainedFiles>32) throw new IllegalArgumentException("skillTrace.retainedFiles must be 1..32");
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
