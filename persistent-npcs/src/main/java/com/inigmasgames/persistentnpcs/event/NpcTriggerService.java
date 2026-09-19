package com.inigmasgames.persistentnpcs.event;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Configurable, cooldown-protected event-to-response routing. */
public final class NpcTriggerService {
    private final Map<String, NpcTriggerDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Instant> lastFired = new ConcurrentHashMap<>();
    private final MemoryStore memories;
    private final java.util.concurrent.atomic.LongAdder fired =
            new java.util.concurrent.atomic.LongAdder();

    public NpcTriggerService(Path dataDirectory, MemoryStore memories) {
        this.memories = memories;
        Path path = dataDirectory.resolve("triggers.json");
        JsonFiles.copyResourceIfMissing(NpcTriggerService.class,
                "/defaults/triggers.json", path);
        NpcTriggerDefinition[] loaded = JsonFiles.read(path, NpcTriggerDefinition[].class);
        if (loaded != null) {
            Arrays.stream(loaded).forEach(this::register);
        }
    }

    public synchronized void register(NpcTriggerDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()
                || definition.eventType() == null || definition.responseType() == null) {
            throw new IllegalArgumentException("Trigger id, eventType and responseType are required");
        }
        String id = definition.id().strip();
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate trigger " + id);
        }
    }

    public void onEvent(NpcFrameworkEvent event) {
        for (NpcTriggerDefinition definition : definitions.values()) {
            if (definition.eventType() != event.type() || !claimCooldown(definition, event)) {
                continue;
            }
            if (definition.responseType() == TriggerResponseType.MEMORY
                    && event.npcId() != null) {
                memories.append(new MemoryRecord(UUID.randomUUID(), event.npcId(),
                        event.targetEntityId(), event.occurredAt(), MemoryType.WORLD_EVENT,
                        definition.importance(), render(definition.template(), event),
                        1.0, "EVENT:" + event.type(),
                        entities(event), event.facts().getOrDefault("location", ""), ""));
            }
            fired.increment();
            // Thought/dialogue/reasoning/task/action routes are deliberately exposed but
            // require an explicitly registered consumer before they may mutate game state.
        }
    }

    public Map<String, NpcTriggerDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    public long firedCount() {
        return fired.sum();
    }

    private boolean claimCooldown(NpcTriggerDefinition definition, NpcFrameworkEvent event) {
        String key = definition.id() + ":" + event.npcId();
        Instant prior = lastFired.get(key);
        if (prior != null && event.occurredAt().isBefore(
                prior.plusSeconds(Math.max(0, definition.cooldownSeconds())))) {
            return false;
        }
        lastFired.put(key, event.occurredAt());
        return true;
    }

    private static String render(String template, NpcFrameworkEvent event) {
        String value = template == null ? event.type().name() : template;
        for (Map.Entry<String, String> fact : event.facts().entrySet()) {
            value = value.replace("{{" + fact.getKey() + "}}", fact.getValue());
        }
        return value;
    }

    private static java.util.List<UUID> entities(NpcFrameworkEvent event) {
        return java.util.stream.Stream.of(event.actorEntityId(), event.targetEntityId())
                .filter(java.util.Objects::nonNull).toList();
    }
}
