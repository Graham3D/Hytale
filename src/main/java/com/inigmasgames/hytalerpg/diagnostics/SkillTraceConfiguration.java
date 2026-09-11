package com.inigmasgames.hytalerpg.diagnostics;

import java.io.InputStream;
import java.util.Properties;

public record SkillTraceConfiguration(boolean enabled, String level, int maxFileMb, int retainedFiles,
                                      boolean developmentEntitlements, String archiveCompression,
                                      boolean archiveVerify) {
    public SkillTraceConfiguration(boolean enabled, String level, int maxFileMb, int retainedFiles,
                                   boolean developmentEntitlements) {
        this(enabled, level, maxFileMb, retainedFiles, developmentEntitlements, "GZIP", true);
    }
    public SkillTraceConfiguration {
        level=SkillTraceLevel.parse(level).name();
        if (maxFileMb < 1||maxFileMb>64) throw new IllegalArgumentException("skillTrace.maxFileMb must be 1..64");
        if (retainedFiles < 1||retainedFiles>32) throw new IllegalArgumentException("skillTrace.retainedFiles must be 1..32");
        archiveCompression=TraceArchiveManager.Compression.valueOf(archiveCompression.trim().toUpperCase(java.util.Locale.ROOT)).name();
    }
    public static SkillTraceConfiguration load() {
        Properties properties = new Properties();
        try (InputStream input = SkillTraceConfiguration.class.getResourceAsStream("/rpg-skill-trace.properties")) {
            if (input == null) throw new IllegalStateException("Missing rpg-skill-trace.properties");
            properties.load(input);
            return new SkillTraceConfiguration(
                    Boolean.parseBoolean(properties.getProperty("skillTrace.enabled", "true")),
                    System.getProperty("rpg.skillTrace.level",properties.getProperty("skillTrace.level", "NORMAL")),
                    Integer.parseInt(System.getProperty("rpg.trace.segment.maxMiB",properties.getProperty("skillTrace.maxFileMb", "4"))),
                    Integer.parseInt(properties.getProperty("skillTrace.retainedFiles", "4")),
                    Boolean.parseBoolean(properties.getProperty("developmentEntitlements.enabled", "true")),
                    System.getProperty("rpg.trace.archive.compression",properties.getProperty("trace.archive.compression", "GZIP")),
                    Boolean.parseBoolean(System.getProperty("rpg.trace.archive.verify",properties.getProperty("trace.archive.verify", "true"))));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load RPG trace configuration", error);
        }
    }
}
