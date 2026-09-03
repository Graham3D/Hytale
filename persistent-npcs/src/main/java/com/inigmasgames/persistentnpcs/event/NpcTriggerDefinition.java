package com.inigmasgames.persistentnpcs.event;

public record NpcTriggerDefinition(
        String id,
        NpcEventType eventType,
        TriggerResponseType responseType,
        int cooldownSeconds,
        double importance,
        String requiredCapability,
        String template) {
}
